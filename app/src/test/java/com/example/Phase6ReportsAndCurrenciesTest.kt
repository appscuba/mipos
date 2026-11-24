package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.ConfirmPurchaseRequest
import com.example.data.repository.ConfirmSaleRequest
import com.example.data.repository.FinanceRepository
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductModality
import com.example.data.repository.PurchaseLineInput
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.ReportPeriodPreset
import com.example.data.repository.ReportsRepository
import com.example.data.repository.SaleLineInput
import com.example.data.repository.SalePaymentInput
import com.example.data.repository.SaleRepository
import com.example.data.repository.SaveProductRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6ReportsAndCurrenciesTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var inventoryRepo: InventoryRepository
  private lateinit var purchaseRepo: PurchaseRepository
  private lateinit var saleRepo: SaleRepository
  private lateinit var financeRepo: FinanceRepository
  private lateinit var reportsRepo: ReportsRepository
  private var businessId: Long = 0L

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefs = AppPreferencesDataStore(context)
    businessRepo = BusinessRepository(db = db, preferences = prefs)
    inventoryRepo = InventoryRepository(db = db)
    purchaseRepo = PurchaseRepository(db = db)
    saleRepo = SaleRepository(db = db)
    financeRepo = FinanceRepository(db = db)
    reportsRepo = ReportsRepository(db = db)

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

  @Test
  fun `reportes calculan ganancia bruta neta cogs valor inventario y rentabilidad por producto`() = runBlocking {
    val arrozId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Arroz",
        sku = "ARR",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
      ),
    )

    // Vender 2 lb = 1100 CUP, costo = 800 CUP, ganancia bruta = 300 CUP; inventario restante = 8 lb * 400 = 3200 CUP
    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        payments = listOf(SalePaymentInput("CASH", "CUP", BigDecimal("1100.00"), BigDecimal.ONE)),
        lines = listOf(SaleLineInput(arrozId, "Arroz", "lb", BigDecimal("2"), BigDecimal("500"))),
      ),
    )

    val catId = db.financeDao().observeActiveExpenseCategories(businessId).first().first().id
    financeRepo.recordExpense(
      businessId = businessId,
      expenseCategoryId = catId,
      description = "Transporte local",
      currencyCode = "CUP",
      originalAmount = BigDecimal("100.00"),
      exchangeRateToBase = BigDecimal.ONE,
      paymentMethod = "TRANSFER",
      paidFromCashDrawer = false,
    )

    val (start, end) = ReportsRepository.dateRangeForPreset(ReportPeriodPreset.TODAY)
    val report = reportsRepo.observeReport(businessId, start, end).first()

    assertEquals(1, report.salesCount)
    assertEquals(0, BigDecimal("1100.00").compareTo(report.netSalesBase))
    assertEquals(0, BigDecimal("800.00").compareTo(report.cogsBase))
    assertEquals(0, BigDecimal("300.00").compareTo(report.grossProfitBase))
    assertEquals(0, BigDecimal("100.00").compareTo(report.operatingExpensesBase))
    assertEquals(0, BigDecimal("200.00").compareTo(report.netProfitBase))
    assertEquals(0, BigDecimal("3200.00").compareTo(report.inventoryValueBase))

    val productRow = report.productProfitability.single()
    assertEquals("Arroz", productRow.productName)
    assertEquals(0, BigDecimal("300.00").compareTo(productRow.grossProfitBase))

    val textExport = ReportsRepository.formatExportSummary(report, "Comercial Horizonte", "Hoy")
    assertTrue(textExport.contains("GANANCIA NETA"))
    assertTrue(textExport.contains("Arroz"))
  }

  @Test
  fun `actualizar tasa de moneda preserva tasas historicas en compras y ventas pasadas`() = runBlocking {
    val azucarId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Azúcar",
        sku = "AZU",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("320.00"),
        avgCostPerSaleUnit = BigDecimal("200.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "u",
        initialStockFactorToBase = BigDecimal.ONE,
      ),
    )

    // Compra #1 con USD a tasa histórica 320.00 (10 USD = 3200 CUP)
    val p1Id = purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = null,
        currencyCode = "USD",
        exchangeRateToBase = BigDecimal("320.0000"),
        paidAmountOriginal = BigDecimal("10.00"),
        lines = listOf(PurchaseLineInput(azucarId, "Azúcar", "u", BigDecimal("5"), BigDecimal.ONE, BigDecimal("2.00"))),
      ),
    )

    // Cambiar tasa USD a 350.00 y añadir EUR a 380.00
    businessRepo.saveCustomExchangeRate(businessId, "USD", "Dólar Estadounidense", "US$", BigDecimal("350.00"))
    businessRepo.saveCustomExchangeRate(businessId, "EUR", "Euro", "€", BigDecimal("380.00"))

    val rates = businessRepo.observeAllExchangeRates(businessId).first()
    assertEquals(0, BigDecimal("350.0000").compareTo(rates.first { it.currencyCode == "USD" }.rateToBase))
    assertEquals(0, BigDecimal("380.0000").compareTo(rates.first { it.currencyCode == "EUR" }.rateToBase))

    // La compra #1 conserva su tasa histórica 320.0000 y total convertido 3200.00
    val historicalPurchase = db.purchaseDao().getPurchaseById(p1Id)!!
    assertEquals(0, BigDecimal("320.0000").compareTo(historicalPurchase.exchangeRateToBase))
    assertEquals(0, BigDecimal("3200.00").compareTo(historicalPurchase.totalConvertedBase))
  }
}
