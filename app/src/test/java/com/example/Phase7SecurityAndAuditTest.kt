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
    assertTrue(hash.startsWith("pbkdf2\$"))
    assertFalse(PinHasher.isLegacyHash(hash))
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
  fun `pin hasher usa PBKDF2 y migra hashes antiguos SHA-256 al iniciar sesion`() = runBlocking {
    // Nuevo esquema: PBKDF2 con sal distinta en cada hash
    val hashA = PinHasher.hashPin("4821")
    val hashB = PinHasher.hashPin("4821")
    assertTrue(hashA.startsWith("pbkdf2\$"))
    assertTrue(hashA != hashB)
    assertFalse(PinHasher.isLegacyHash(hashA))
    assertTrue(PinHasher.verifyPin("4821", hashA))
    assertFalse(PinHasher.verifyPin("9999", hashA))

    // Un hash antiguo (SHA-256 de una pasada) sigue verificando, pero se marca legacy
    val legacyHash = legacySha256Hash("4821")
    assertTrue(PinHasher.isLegacyHash(legacyHash))
    assertTrue(PinHasher.verifyPin("4821", legacyHash))
    assertFalse(PinHasher.verifyPin("9999", legacyHash))

    // Al cambiar de usuario con un PIN antiguo, el hash se migra a PBKDF2
    val owner = db.securityDao().observeUsers(businessId).first().first()
    val clerkId = securityRepo.createUser(
      businessId = businessId,
      fullName = "Luis Antiguo",
      username = "luis",
      roleCode = "CLERK",
      pin = "1357",
      actorUserId = owner.id,
    )
    val clerk = db.securityDao().getUserById(clerkId)!!
    db.securityDao().updateUser(clerk.copy(pinHash = legacySha256Hash("1357")))
    assertTrue(PinHasher.isLegacyHash(db.securityDao().getUserById(clerkId)!!.pinHash))

    securityRepo.switchUser(businessId, clerkId, "1357")
    val migrated = db.securityDao().getUserById(clerkId)!!
    assertFalse(PinHasher.isLegacyHash(migrated.pinHash))
    assertTrue(migrated.pinHash!!.startsWith("pbkdf2\$"))
    assertTrue(PinHasher.verifyPin("1357", migrated.pinHash))
  }

  /** Reconstruye el formato del hash antiguo: SHA-256(sal + PIN) con sal de 16 bytes. */
  private fun legacySha256Hash(pin: String): String {
    val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.update(salt)
    val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    return "${salt.toHex()}:${digest.toHex()}"
  }
}
