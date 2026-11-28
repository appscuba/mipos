package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.license.LicenseGate
import com.example.core.license.LicensedFeature
import com.example.core.rates.ElToqueNetworkException
import com.example.core.rates.ElToqueParseException
import com.example.core.rates.ElToqueRatesClient
import com.example.core.rates.ElToqueRatesParser
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.rates.RateSyncRepository
import com.example.data.rates.RateSyncResult
import com.example.data.rates.RateSyncSource
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InitialBusinessSetupRequest
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de las tasas elTOQUE: parseo del HTML, cliente, conversión a la
 * moneda base y sincronización (con y sin licencia, con y sin red).
 *
 * La muestra HTML imita la tabla informal de
 * https://eltoque.com/tasas-de-cambio-cuba tal como se veía el 2026-11-28
 * (USD 980.00, EUR 1,050.00, MLC 768.76 CUP).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase9ElToqueRatesTest {

  private val sampleHtml = """
    <html><body><h2>Mercado Informal de Divisas en Cuba (Tiempo Real)</h2>
    <table>
    <tr><td>1 USD Dólar Estadounidense</td><td>980.00 CUP</td></tr>
    <tr><td>1 EUR Euro</td><td>1,050.00 CUP +10</td></tr>
    <tr><td>1 MLC USD en saldo de cuenta bancaria</td><td>768.76 CUP -18.76</td></tr>
    </table></body></html>
  """.trimIndent()

  class FakeLicenseGate(private val licensed: Boolean) : LicenseGate {
    override suspend fun isLicensed(feature: LicensedFeature): Boolean = licensed
  }

  // --- Parser (sin Android) ---

  @Test
  fun `parser lee USD EUR y MLC de la tabla informal de elTOQUE`() {
    val rates = ElToqueRatesParser.parse(sampleHtml, fetchedAtEpochMillis = 1_000L)
    assertEquals(BigDecimal("980.00"), rates.usdToCup)
    assertEquals(BigDecimal("1050.00"), rates.eurToCup)
    assertEquals(BigDecimal("768.76"), rates.mlcToCup)
    assertEquals(1_000L, rates.fetchedAtEpochMillis)
  }

  @Test
  fun `parser acepta coma decimal y separador de miles`() {
    assertEquals(BigDecimal("980.00"), ElToqueRatesParser.parseNumberOrNull("980,00"))
    assertEquals(BigDecimal("1050.00"), ElToqueRatesParser.parseNumberOrNull("1,050.00"))
    assertEquals(BigDecimal("980.00"), ElToqueRatesParser.parseNumberOrNull("980.00"))
    assertNull(ElToqueRatesParser.parseNumberOrNull("no-es-numero"))
  }

  @Test
  fun `parser falla si falta el EUR`() {
    val html = "<html><body>1 USD Dólar 980.00 CUP</body></html>"
    try {
      ElToqueRatesParser.parse(html)
      fail("Se esperaba ElToqueParseException")
    } catch (e: ElToqueParseException) {
      assertTrue(e.message!!.contains("EUR"))
    }
  }

  @Test
  fun `parser rechaza valores fuera de rango`() {
    val html = "<html><body>1 USD Dólar 99999.00 CUP 1 EUR Euro 1050.00 CUP</body></html>"
    try {
      ElToqueRatesParser.parse(html)
      fail("Se esperaba ElToqueParseException por USD fuera de rango")
    } catch (_: ElToqueParseException) {
      // Esperado.
    }
  }

  @Test
  fun `conversion a moneda base USD y EUR`() {
    val rates = ElToqueRatesParser.parse(sampleHtml)
    val toUsd = rates.toRatesToBase("USD")!!
    assertEquals(BigDecimal("1.0000"), toUsd.getValue("USD"))
    assertEquals(BigDecimal("1.0714"), toUsd.getValue("EUR"))
    val toCup = rates.toRatesToBase("CUP")!!
    assertEquals(BigDecimal("980.00"), toCup.getValue("USD"))
    assertNull(rates.toRatesToBase("CAD"))
  }

  // --- Cliente (con fetcher falso, sin red) ---

  @Test
  fun `cliente devuelve las tasas con un fetcher falso`() = runBlocking {
    var calls = 0
    val client = ElToqueRatesClient(
      fetcher = { calls++; sampleHtml },
      maxAttempts = 1,
    )
    val rates = client.fetchRates()
    assertEquals(BigDecimal("980.00"), rates.usdToCup)
    assertEquals(1, calls)
  }

  @Test
  fun `cliente sin red lanza ElToqueNetworkException tras los intentos`() = runBlocking {
    val client = ElToqueRatesClient(
      fetcher = { throw IOException("sin internet") },
      maxAttempts = 1,
    )
    try {
      client.fetchRates()
      fail("Se esperaba ElToqueNetworkException")
    } catch (e: ElToqueNetworkException) {
      assertTrue(e.message!!.contains("elTOQUE"))
    }
  }

  // --- Sincronización con Room (Robolectric) ---

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private var businessId: Long = 0L

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefs = AppPreferencesDataStore(context)
    businessRepo = BusinessRepository(db = db, preferences = prefs)
    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Comercial Horizonte",
        businessType = "Tienda",
        logoUri = null,
        phone = "",
        email = "",
        address = "",
        primaryCurrency = "CUP",
        secondaryCurrencyCode = "USD",
        secondaryCurrencyRate = BigDecimal("320.00"),
        gramsPerPound = BigDecimal("500"),
        allowNegativeStock = false,
      ),
    )
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun rateOf(code: String): BigDecimal = runBlocking {
    businessRepo.observeAllExchangeRates(businessId).first()
      .first { it.currencyCode == code }.rateToBase
  }

  @Test
  fun `sync con licencia actualiza el USD y conserva el resto`() = runBlocking {
    val repo = RateSyncRepository(
      businessRepository = businessRepo,
      preferences = prefs,
      client = ElToqueRatesClient(fetcher = { sampleHtml }, maxAttempts = 1),
      licenseGate = FakeLicenseGate(true),
    )
    val result = repo.sync(businessId, userId = null, source = RateSyncSource.MANUAL)
    assertTrue(result is RateSyncResult.Success)
    val success = result as RateSyncResult.Success
    assertEquals(listOf("USD"), success.updatedCodes)
    // EUR y MLC no existen en este negocio: se saltan, no se crean.
    assertTrue(success.skippedCodes.contains("EUR"))
    assertEquals(BigDecimal("980.0000"), rateOf("USD"))
    assertEquals(listOf("USD"), prefs.preferencesFlow.first().lastRatesSyncCodes)
  }

  @Test
  fun `sync sin licencia no hace ninguna llamada de red`() = runBlocking {
    var calls = 0
    val repo = RateSyncRepository(
      businessRepository = businessRepo,
      preferences = prefs,
      client = ElToqueRatesClient(fetcher = { calls++; sampleHtml }, maxAttempts = 1),
      licenseGate = FakeLicenseGate(false),
    )
    val result = repo.sync(businessId, userId = null, source = RateSyncSource.MANUAL)
    assertEquals(RateSyncResult.LicenseRequired, result)
    assertEquals(0, calls)
    assertEquals(BigDecimal("320.0000"), rateOf("USD"))
  }

  @Test
  fun `sync sin red conserva las tasas manuales`() = runBlocking {
    val repo = RateSyncRepository(
      businessRepository = businessRepo,
      preferences = prefs,
      client = ElToqueRatesClient(
        fetcher = { throw IOException("sin internet") },
        maxAttempts = 1,
      ),
      licenseGate = FakeLicenseGate(true),
    )
    val result = repo.sync(businessId, userId = null, source = RateSyncSource.AUTOMATIC)
    assertEquals(RateSyncResult.NoNetwork, result)
    assertEquals(BigDecimal("320.0000"), rateOf("USD"))
  }
}
