package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InitialBusinessSetupRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase1DatabaseAndSetupTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var repository: BusinessRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefs = AppPreferencesDataStore(context)
    repository = BusinessRepository(db = db, preferences = prefs)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `configuracion inicial persiste negocio monedas equivalencia roles y categorias`() = runBlocking {
    val businessId = repository.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Mercado Central",
        businessType = "Tienda / Bodega",
        logoUri = null,
        phone = "5551234",
        email = "ventas@mercadocentral.cu",
        address = "Calle Real #10",
        primaryCurrency = "CUP",
        secondaryCurrencyCode = "USD",
        secondaryCurrencyRate = BigDecimal("320.0000"),
        gramsPerPound = BigDecimal("500.0000"),
        allowNegativeStock = false,
      ),
    )

    assertTrue(businessId > 0L)
    val snapshot = repository.observeWorkspaceSnapshot().first()
    assertNotNull(snapshot.business)
    assertEquals("Mercado Central", snapshot.business?.name)
    assertEquals("CUP", snapshot.business?.primaryCurrency)
    assertEquals(0, BigDecimal("500.0000").compareTo(snapshot.settings?.gramsPerPound))
    assertEquals(2, snapshot.exchangeRates.size)
    assertEquals(5, snapshot.categoriesCount)
    assertEquals(7, snapshot.expenseCategoriesCount)

    val ownerRole = db.securityDao().getRoleByCode("OWNER")
    val clerkRole = db.securityDao().getRoleByCode("CLERK")
    assertNotNull(ownerRole)
    assertNotNull(clerkRole)
    val ownerPermissions = db.securityDao().getPermissionsForRole(ownerRole!!.id)
    val clerkPermissions = db.securityDao().getPermissionsForRole(clerkRole!!.id)
    assertTrue(ownerPermissions.size >= 11)
    assertEquals(2, clerkPermissions.size)

    val userPrefs = prefs.preferencesFlow.first()
    assertTrue(userPrefs.isOnboardingCompleted)
    assertEquals(businessId, userPrefs.activeBusinessId)
  }

  @Test
  fun `actualizar configuracion modifica equivalencia y registra auditoria`() = runBlocking {
    val businessId = repository.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Café Habana",
        businessType = "Cafetería / Restaurante",
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

    repository.updateBusinessSettings(
      businessId = businessId,
      businessName = "Café Habana Express",
      businessType = "Cafetería / Restaurante",
      logoUri = null,
      phone = "5559999",
      email = "",
      address = "Obispo 25",
      primaryCurrency = "CUP",
      gramsPerPound = BigDecimal("454"),
      allowNegativeStock = true,
    )

    val snapshot = repository.observeWorkspaceSnapshot().first()
    assertEquals("Café Habana Express", snapshot.business?.name)
    assertEquals(0, BigDecimal("454").compareTo(snapshot.settings?.gramsPerPound))
    assertEquals(true, snapshot.settings?.allowNegativeStock)

    val logs = db.securityDao().observeRecentAuditLogs(businessId).first()
    assertTrue(logs.any { it.action == "SETTINGS_UPDATED" })
  }
}
