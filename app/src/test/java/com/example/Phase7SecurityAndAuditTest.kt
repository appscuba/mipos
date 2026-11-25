package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.PinHasher
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.AppPermissions
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.SecurityRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7SecurityAndAuditTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var securityRepo: SecurityRepository
  private var businessId: Long = 0L

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefs = AppPreferencesDataStore(context)
    businessRepo = BusinessRepository(db = db, preferences = prefs)
    securityRepo = SecurityRepository(db = db, preferences = prefs)

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Tienda Segura",
        businessType = "Tienda",
        logoUri = null,
        phone = "",
        email = "",
        address = "",
        primaryCurrency = "CUP",
        secondaryCurrencyCode = null,
        secondaryCurrencyRate = null,
        gramsPerPound = BigDecimal("500"),
        allowNegativeStock = false,
      ),
    )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `pin hasher verifica pin correcto rechaza incorrecto y gestiona roles propietario vs dependiente`() = runBlocking {
    val hash = PinHasher.hashPin("4821")
    assertFalse(hash.contains("4821"))
    assertTrue(PinHasher.verifyPin("4821", hash))
    assertFalse(PinHasher.verifyPin("9999", hash))

    val owner = db.securityDao().observeUsers(businessId).first().first()
    val clerkId = securityRepo.createUser(
      businessId = businessId,
      fullName = "Marta Cajera",
      username = "marta",
      roleCode = "CLERK",
      pin = "1234",
      actorUserId = owner.id,
    )

    // Dependiente puede vender y cerrar caja, pero no acceder a reportes ni crear usuarios
    securityRepo.requirePermission(clerkId, AppPermissions.POS_SELL)
    securityRepo.requirePermission(clerkId, AppPermissions.CASH_CLOSE)

    var deniedReports = false
    try {
      securityRepo.requirePermission(clerkId, AppPermissions.REPORTS_COST_PROFIT)
    } catch (_: IllegalArgumentException) {
      deniedReports = true
    }
    assertTrue(deniedReports)

    // Cambiar usuario a Marta exige PIN válido
    var rejectedWrongPin = false
    try {
      securityRepo.switchUser(businessId, clerkId, "0000")
    } catch (_: IllegalArgumentException) {
      rejectedWrongPin = true
    }
    assertTrue(rejectedWrongPin)

    securityRepo.switchUser(businessId, clerkId, "1234")
    val session = securityRepo.observeActiveSession(flowOf(businessId)).first()
    assertTrue(session.hasPermission(AppPermissions.POS_SELL))
    assertFalse(session.hasPermission(AppPermissions.REPORTS_COST_PROFIT))
    assertFalse(session.hasPermission(AppPermissions.USERS_MANAGE))

    // Bloquear y desbloquear sesión con PIN
    securityRepo.lockSession()
    assertTrue(securityRepo.observeActiveSession(flowOf(businessId)).first().isPinLocked)
    assertFalse(securityRepo.unlockSession(clerkId, "5555"))
    assertTrue(securityRepo.unlockSession(clerkId, "1234"))
    assertFalse(securityRepo.observeActiveSession(flowOf(businessId)).first().isPinLocked)

    // Bitácora de auditoría registra creación y cambio de usuario
    val logs = securityRepo.observeAuditLogs(businessId).first()
    assertTrue(logs.any { it.action == "USER_CREATED" })
    assertTrue(logs.any { it.action == "USER_SWITCHED" })
    assertTrue(logs.any { it.action == "SESSION_UNLOCKED" })
  }

  @Test
  fun `propietario modifica y elimina usuarios con protecciones`() = runBlocking {
    val owner = db.securityDao().observeUsers(businessId).first().first()
    val clerkId = securityRepo.createUser(
      businessId = businessId,
      fullName = "Marta Cajera",
      username = "marta",
      roleCode = "CLERK",
      pin = "1234",
      actorUserId = owner.id,
    )

    // Modificar: nombre, usuario y rol (asciende a Propietario)
    securityRepo.updateUserProfile(
      businessId = businessId,
      targetUserId = clerkId,
      fullName = "Marta Supervisora",
      username = "marta2",
      roleCode = "OWNER",
      actorUserId = owner.id,
    )
    val updated = securityRepo.observeUsersWithRoles(businessId).first().first { it.user.id == clerkId }
    assertTrue(updated.user.fullName == "Marta Supervisora")
    assertTrue(updated.user.username == "marta2")
    assertTrue(updated.role?.code == "OWNER")

    // Usuario duplicado al modificar: rechazado
    var duplicateRejected = false
    try {
      securityRepo.updateUserProfile(
        businessId = businessId,
        targetUserId = clerkId,
        fullName = "Marta Supervisora",
        username = owner.username,
        roleCode = "OWNER",
        actorUserId = owner.id,
      )
    } catch (_: IllegalArgumentException) {
      duplicateRejected = true
    }
    assertTrue(duplicateRejected)

    // Sin permiso de gestión: un dependiente no puede modificar ni eliminar
    val clerk2Id = securityRepo.createUser(
      businessId = businessId,
      fullName = "Pedro Cajero",
      username = "pedro",
      roleCode = "CLERK",
      pin = null,
      actorUserId = owner.id,
    )
    var editDenied = false
    try {
      securityRepo.updateUserProfile(
        businessId = businessId,
        targetUserId = clerk2Id,
        fullName = "Otro Nombre",
        username = "pedro",
        roleCode = "CLERK",
        actorUserId = clerk2Id,
      )
    } catch (_: IllegalArgumentException) {
      editDenied = true
    }
    assertTrue(editDenied)

    // Eliminar el usuario en sesión: rechazado
    var selfDeleteRejected = false
    try {
      securityRepo.deleteUser(businessId = businessId, targetUserId = owner.id, actorUserId = owner.id)
    } catch (_: IllegalArgumentException) {
      selfDeleteRejected = true
    }
    assertTrue(selfDeleteRejected)

    // Eliminar a la otra propietaria (queda el propietario en sesión): permitido
    securityRepo.deleteUser(businessId = businessId, targetUserId = clerkId, actorUserId = owner.id)
    val afterDelete = securityRepo.observeUsersWithRoles(businessId).first()
    assertTrue(afterDelete.none { it.user.id == clerkId })

    // Degradar al único Propietario activo restante: rechazado
    var demoteRejected = false
    try {
      securityRepo.updateUserProfile(
        businessId = businessId,
        targetUserId = owner.id,
        fullName = owner.fullName,
        username = owner.username,
        roleCode = "CLERK",
        actorUserId = owner.id,
      )
    } catch (_: IllegalArgumentException) {
      demoteRejected = true
    }
    assertTrue(demoteRejected)

    // Bitácora: actualización y eliminación registradas
    val logs = securityRepo.observeAuditLogs(businessId).first()
    assertTrue(logs.any { it.action == "USER_UPDATED" })
    assertTrue(logs.any { it.action == "USER_DELETED" })
  }
}
