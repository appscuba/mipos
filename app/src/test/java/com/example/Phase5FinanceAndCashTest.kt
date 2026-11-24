package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.CashAdjustmentType
import com.example.data.repository.ConfirmSaleRequest
import com.example.data.repository.FinanceRepository
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductModality
import com.example.data.repository.SaleLineInput
import com.example.data.repository.SalePaymentInput
import com.example.data.repository.SaleRepository
import com.example.data.repository.SaveProductRequest
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
@Config(sdk = [36])
class Phase5FinanceAndCashTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var inventoryRepo: InventoryRepository
  private lateinit var saleRepo: SaleRepository
  private lateinit var financeRepo: FinanceRepository
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
    saleRepo = SaleRepository(db = db)
    financeRepo = FinanceRepository(db = db)

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Bodega Finanzas",
        businessType = "Tienda",
        logoUri = null,
        phone = "555-1234",
        email = "",
        address = "Centro",
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
  fun `caja diaria separa efectivo vs transferencia calcula esperado exacto y diferencia al cierre`() = runBlocking {
    val arrozId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Arroz",
        sku = "ARR",
        barcode = "",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("20"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
      ),
    )
    val customerId = financeRepo.saveCustomer(businessId = businessId, name = "Elena Torres", phone = "555-0001")

    // Apertura con 1000 CUP
    val sessionId = financeRepo.openCashSession(businessId, BigDecimal("1000.00"), "Turno mañana")

    // Venta en efectivo: 0.5 lb = 275.00 CUP -> Caja +275 = 1275
    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        payments = listOf(SalePaymentInput("CASH", "CUP", BigDecimal("275.00"), BigDecimal.ONE)),
        lines = listOf(SaleLineInput(arrozId, "Arroz", "lb", BigDecimal("0.5"), BigDecimal("500"))),
      ),
    )

    // Venta por transferencia (600.00) -> NO debe alterar expectedCashBase
    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        payments = listOf(SalePaymentInput("TRANSFER", "CUP", BigDecimal("550.00"), BigDecimal.ONE, isPhysicalCash = false)),
        lines = listOf(SaleLineInput(arrozId, "Arroz", "lb", BigDecimal("1"), BigDecimal("500"))),
      ),
    )

    // Venta a crédito de 1000 -> abono 200 efectivo -> Caja +200 = 1475
    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        customerId = customerId,
        payments = emptyList(),
        lines = listOf(
          SaleLineInput(arrozId, "Arroz", "lb", BigDecimal("2"), BigDecimal("500"), overrideUnitSalePriceBase = BigDecimal("500.00")),
        ),
      ),
    )
    val debt = financeRepo.observeCustomerDetail(businessId, customerId).first().debts.single()
    financeRepo.collectCustomerDebt(
      businessId = businessId,
      debtId = debt.id,
      amountOriginal = BigDecimal("200.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal.ONE,
      paymentMethod = "CASH",
    )

    // Ingreso adicional +500 -> Caja = 1975
    financeRepo.recordCashAdjustment(
      businessId = businessId,
      type = CashAdjustmentType.EXTRA_INCOME,
      amountOriginal = BigDecimal("500.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal.ONE,
      affectsPhysicalCash = true,
      description = "Cambio adicional",
    )

    // Gasto pagado desde caja -150 -> Caja = 1825
    val categories = db.financeDao().observeActiveExpenseCategories(businessId).first()
    financeRepo.recordExpense(
      businessId = businessId,
      expenseCategoryId = categories.first().id,
      description = "Bolsas y empaque",
      currencyCode = "CUP",
      originalAmount = BigDecimal("150.00"),
      exchangeRateToBase = BigDecimal.ONE,
      paymentMethod = "CASH",
      paidFromCashDrawer = true,
    )

    // Retiro propietario -300 -> Caja esperada = 1525.00
    financeRepo.recordCashAdjustment(
      businessId = businessId,
      type = CashAdjustmentType.OWNER_WITHDRAWAL,
      amountOriginal = BigDecimal("300.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal.ONE,
      affectsPhysicalCash = true,
      description = "Retiro parcial",
    )

    val openBeforeClose = financeRepo.observeOpenCashSession(businessId).first()!!
    assertEquals(0, BigDecimal("1525.00").compareTo(openBeforeClose.expectedCashBase))
    assertEquals(0, BigDecimal("550.00").compareTo(openBeforeClose.totalTransferSalesBase))

    val closed = financeRepo.closeCashSession(
      businessId = businessId,
      sessionId = sessionId,
      countedCashBase = BigDecimal("1520.00"),
      notes = "Faltante menor",
    )
    assertEquals("CLOSED", closed.status)
    assertEquals(0, BigDecimal("1525.00").compareTo(closed.expectedCashBase))
    assertEquals(0, BigDecimal("-5.00").compareTo(closed.differenceBase!!))
  }

  @Test
  fun `anulacion no destructiva de gasto revierte caja y ganancia neta y cobros a clientes respetan estados`() = runBlocking {
    val cafeId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Café",
        sku = "CAF",
        barcode = "",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("500.00"),
        avgCostPerSaleUnit = BigDecimal("300.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "u",
        initialStockFactorToBase = BigDecimal.ONE,
      ),
    )

    financeRepo.openCashSession(businessId, BigDecimal("500.00"))
    val customerId = financeRepo.saveCustomer(businessId = businessId, name = "Luis Gómez", phone = "555-7777")

    // Venta crédito por 1000 (2 u x 500, costo 600 -> ganancia bruta 400)
    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        customerId = customerId,
        payments = emptyList(),
        lines = listOf(SaleLineInput(cafeId, "Café", "u", BigDecimal("2"), BigDecimal.ONE)),
      ),
    )

    // Gasto caja 150 -> ganancia neta = 400 - 150 = 250; caja = 350
    val cat = db.financeDao().observeActiveExpenseCategories(businessId).first().first()
    val expenseId = financeRepo.recordExpense(
      businessId = businessId,
      expenseCategoryId = cat.id,
      description = "Electricidad",
      currencyCode = "CUP",
      originalAmount = BigDecimal("150.00"),
      exchangeRateToBase = BigDecimal.ONE,
      paymentMethod = "CASH",
      paidFromCashDrawer = true,
    )

    var overview = financeRepo.observeFinanceOverview(businessId).first()
    assertEquals(0, BigDecimal("250.00").compareTo(overview.netProfitBase))
    assertEquals(0, BigDecimal("350.00").compareTo(overview.openCashSession!!.expectedCashBase))

    // Anular gasto -> ganancia neta regresa a 400, caja regresa a 500, gasto sigue en BD como ANNULLED
    financeRepo.annulExpense(businessId, expenseId, "Error de monto")
    overview = financeRepo.observeFinanceOverview(businessId).first()
    assertEquals(0, BigDecimal("400.00").compareTo(overview.netProfitBase))
    assertEquals(0, BigDecimal("500.00").compareTo(overview.openCashSession!!.expectedCashBase))
    val keptExpense = db.financeDao().getExpenseById(expenseId)
    assertNotNull(keptExpense)
    assertEquals("ANNULLED", keptExpense!!.status)

    // No se puede archivar cliente mientras tenga deuda > 0
    var archiveBlocked = false
    try {
      financeRepo.archiveCustomer(businessId, customerId)
    } catch (_: IllegalArgumentException) {
      archiveBlocked = true
    }
    assertTrue(archiveBlocked)

    // Abono parcial 400 por TRANSFER (no altera caja física 500) -> status PARTIAL
    val debt = financeRepo.observeCustomerDetail(businessId, customerId).first().debts.single()
    financeRepo.collectCustomerDebt(businessId, debt.id, BigDecimal("400.00"), "CUP", BigDecimal.ONE, "TRANSFER")
    var updatedDebt = db.financeDao().getDebtById(debt.id)!!
    assertEquals("PARTIAL", updatedDebt.status)
    assertEquals(0, BigDecimal("600.00").compareTo(updatedDebt.remainingBase))
    assertEquals(0, BigDecimal("500.00").compareTo(financeRepo.observeOpenCashSession(businessId).first()!!.expectedCashBase))

    // Abono final 600 por CASH -> status PAID, caja = 1100, ahora sí puede archivarse
    financeRepo.collectCustomerDebt(businessId, debt.id, BigDecimal("600.00"), "CUP", BigDecimal.ONE, "CASH")
    updatedDebt = db.financeDao().getDebtById(debt.id)!!
    assertEquals("PAID", updatedDebt.status)
    assertEquals(0, BigDecimal("1100.00").compareTo(financeRepo.observeOpenCashSession(businessId).first()!!.expectedCashBase))

    financeRepo.archiveCustomer(businessId, customerId)
    assertTrue(!db.financeDao().getCustomerById(customerId)!!.isActive)
  }
}
