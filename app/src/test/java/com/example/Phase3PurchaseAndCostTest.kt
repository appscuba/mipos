package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.cost.CostEngine
import com.example.core.cost.PurchaseCostLineInput
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.ConfirmPurchaseRequest
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PresentationDraft
import com.example.data.repository.ProductModality
import com.example.data.repository.PurchaseLineInput
import com.example.data.repository.PurchaseRepository
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
@Config(sdk = [35])
class Phase3PurchaseAndCostTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var inventoryRepo: InventoryRepository
  private lateinit var purchaseRepo: PurchaseRepository
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

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Distribuidora Central",
        businessType = "Tienda / Bodega",
        logoUri = null,
        phone = "555-1234",
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
  fun `costo promedio ponderado movil calcula 450 CUP por libra tras compras a 400 y 500 CUP`() = runBlocking {
    val arrozId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Arroz",
        sku = "ARR-001",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal.ZERO,
        initialStockEnteredQty = BigDecimal.ZERO,
      ),
    )

    // Compra 1: 10 libras a 400 CUP = 4,000 CUP
    purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = null,
        invoiceNumber = "FAC-001",
        currencyCode = "CUP",
        exchangeRateToBase = BigDecimal("1.0000"),
        paidAmountOriginal = BigDecimal("4000.00"),
        paymentMethod = "CASH",
        lines = listOf(
          PurchaseLineInput(
            productId = arrozId,
            productName = "Arroz",
            unitLabel = "lb",
            enteredQuantity = BigDecimal("10"),
            conversionFactorToBase = BigDecimal("500"),
            unitPriceOriginal = BigDecimal("400.00"),
          ),
        ),
      ),
    )

    var arroz = db.inventoryDao().getProductById(arrozId)!!
    assertEquals(0, BigDecimal("5000").compareTo(arroz.stockBaseQty))
    assertEquals(0, BigDecimal("400").compareTo(arroz.avgCostPerSaleUnit))

    // Compra 2: 10 libras a 500 CUP = 5,000 CUP -> Total 20 lb (10,000 g), costo promedio = 450 CUP/lb
    purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = null,
        invoiceNumber = "FAC-002",
        currencyCode = "CUP",
        exchangeRateToBase = BigDecimal("1.0000"),
        paidAmountOriginal = BigDecimal("5000.00"),
        paymentMethod = "CASH",
        lines = listOf(
          PurchaseLineInput(
            productId = arrozId,
            productName = "Arroz",
            unitLabel = "lb",
            enteredQuantity = BigDecimal("10"),
            conversionFactorToBase = BigDecimal("500"),
            unitPriceOriginal = BigDecimal("500.00"),
          ),
        ),
      ),
    )

    arroz = db.inventoryDao().getProductById(arrozId)!!
    assertEquals(0, BigDecimal("10000").compareTo(arroz.stockBaseQty))
    assertEquals(0, BigDecimal("450").compareTo(arroz.avgCostPerSaleUnit))
  }

  @Test
  fun `prorrateo de transporte gastos adicionales y descuentos se distribuye exacto sin generar gastos operativos`() = runBlocking {
    val cafeId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Café",
        sku = "CAF-1",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("350.00"),
        avgCostPerSaleUnit = BigDecimal.ZERO,
      ),
    )
    val jugoId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Jugo",
        sku = "JUG-1",
        modality = ProductModality.PRESENTATION,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("150.00"),
        avgCostPerSaleUnit = BigDecimal.ZERO,
        presentations = listOf(
          PresentationDraft(name = "Caja x 10", baseUnitsPerPresentation = BigDecimal("10")),
        ),
      ),
    )

    // Subtotal: Café 10 u * 100 = 1000 CUP + Jugo 2 cajas (20 u) * 1000 = 2000 CUP => Subtotal = 3000 CUP
    // Transporte 250 + Extra 100 - Descuento 50 = +300 CUP neto -> 100 CUP al Café (total 1100, costo 110/u), 200 CUP al Jugo (total 2200, costo 110/u)
    val purchaseId = purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = null,
        invoiceNumber = "FAC-PRORRATEO",
        currencyCode = "CUP",
        exchangeRateToBase = BigDecimal("1.0000"),
        discountOriginal = BigDecimal("50.00"),
        transportCostOriginal = BigDecimal("250.00"),
        extraExpensesOriginal = BigDecimal("100.00"),
        paidAmountOriginal = BigDecimal("3300.00"),
        paymentMethod = "TRANSFER",
        lines = listOf(
          PurchaseLineInput(
            productId = cafeId,
            productName = "Café",
            unitLabel = "u",
            enteredQuantity = BigDecimal("10"),
            conversionFactorToBase = BigDecimal.ONE,
            unitPriceOriginal = BigDecimal("100.00"),
          ),
          PurchaseLineInput(
            productId = jugoId,
            productName = "Jugo",
            unitLabel = "Caja x 10",
            enteredQuantity = BigDecimal("2"),
            conversionFactorToBase = BigDecimal("10"),
            unitPriceOriginal = BigDecimal("1000.00"),
          ),
        ),
      ),
    )

    val details = db.purchaseDao().getPurchaseDetails(purchaseId)
    assertEquals(2, details.size)
    assertEquals(0, BigDecimal("100.00").compareTo(details[0].allocatedExtraCostBase))
    assertEquals(0, BigDecimal("200.00").compareTo(details[1].allocatedExtraCostBase))

    val cafe = db.inventoryDao().getProductById(cafeId)!!
    val jugo = db.inventoryDao().getProductById(jugoId)!!
    assertEquals(0, BigDecimal("10").compareTo(cafe.stockBaseQty))
    assertEquals(0, BigDecimal("20").compareTo(jugo.stockBaseQty))
    assertEquals(0, BigDecimal("110").compareTo(cafe.avgCostPerSaleUnit))
    assertEquals(0, BigDecimal("110").compareTo(jugo.avgCostPerSaleUnit))

    // Las compras de mercancía NO deben crear gastos operativos automáticamente
    val expenseRows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM expenses WHERE businessId = $businessId")
    expenseRows.use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getInt(0))
    }

    // CostEngine conserva ajuste exacto incluso con fracciones impares
    val unevenResults = CostEngine.allocatePurchaseCosts(
      lines = listOf(
        PurchaseCostLineInput(1L, BigDecimal("1"), BigDecimal("100.00")),
        PurchaseCostLineInput(2L, BigDecimal("1"), BigDecimal("100.00")),
        PurchaseCostLineInput(3L, BigDecimal("1"), BigDecimal("100.00")),
      ),
      exchangeRateToBase = BigDecimal("1.0000"),
      discountOriginal = BigDecimal.ZERO,
      transportOriginal = BigDecimal("10.00"),
      extraExpensesOriginal = BigDecimal.ZERO,
    )
    val sumAllocated = unevenResults.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.allocatedAdjustmentBase) }
    assertEquals(0, BigDecimal("10.00").compareTo(sumAllocated))
  }

  @Test
  fun `compra en USD registra deuda con proveedor abonos parcial y total preservando tasa historica y permite anular`() = runBlocking {
    val supplierId = purchaseRepo.saveSupplier(
      businessId = businessId,
      name = "Importadora Caribe",
      phone = "555-8888",
    )
    val aceiteId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Aceite",
        sku = "ACE-1",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("850.00"),
        avgCostPerSaleUnit = BigDecimal.ZERO,
      ),
    )

    // Compra 10 botellas a 2.00 USD = 20.00 USD @ 320 CUP/USD = 6,400.00 CUP; paga 5.00 USD (1,600 CUP) -> Deuda 4,800.00 CUP
    val purchaseId = purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = supplierId,
        invoiceNumber = "USD-99",
        currencyCode = "USD",
        exchangeRateToBase = BigDecimal("320.0000"),
        paidAmountOriginal = BigDecimal("5.00"),
        paymentMethod = "USD",
        lines = listOf(
          PurchaseLineInput(
            productId = aceiteId,
            productName = "Aceite",
            unitLabel = "u",
            enteredQuantity = BigDecimal("10"),
            conversionFactorToBase = BigDecimal.ONE,
            unitPriceOriginal = BigDecimal("2.00"),
          ),
        ),
      ),
    )

    // Cambiar la tasa de cambio vigente del negocio NO debe alterar la compra histórica
    db.businessConfigDao().upsertExchangeRate(
      ExchangeRateEntity(
        businessId = businessId,
        currencyCode = "USD",
        currencyName = "Dólar Estadounidense",
        symbol = "US$",
        rateToBase = BigDecimal("350.0000"),
        isBaseCurrency = false,
      ),
    )

    val storedPurchase = db.purchaseDao().getPurchaseById(purchaseId)!!
    assertEquals(0, BigDecimal("320.0000").compareTo(storedPurchase.exchangeRateToBase))
    assertEquals(0, BigDecimal("6400.00").compareTo(storedPurchase.totalConvertedBase))
    assertEquals(0, BigDecimal("4800.00").compareTo(storedPurchase.pendingDebtBase))

    val supplierBeforePay = db.purchaseDao().getSupplierById(supplierId)!!
    assertEquals(0, BigDecimal("4800.00").compareTo(supplierBeforePay.balanceOwedBase))

    // Archivar proveedor con deuda debe fallar
    var archiveBlocked = false
    try {
      purchaseRepo.archiveSupplier(businessId, supplierId)
    } catch (_: IllegalArgumentException) {
      archiveBlocked = true
    }
    assertTrue(archiveBlocked)

    // Abono parcial de 1,800.00 CUP -> restante 3,000.00 CUP (status = PARTIAL)
    val debt = db.purchaseDao().getDebtByReference("PURCHASE", purchaseId)!!
    purchaseRepo.paySupplierDebt(
      businessId = businessId,
      debtId = debt.id,
      amountOriginal = BigDecimal("1800.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal("1.0000"),
      paymentMethod = "CASH",
    )

    val debtAfterPartial = db.purchaseDao().getDebtById(debt.id)!!
    assertEquals("PARTIAL", debtAfterPartial.status)
    assertEquals(0, BigDecimal("3000.00").compareTo(debtAfterPartial.remainingBase))

    // Saldo restante pagado completo -> status = PAID
    purchaseRepo.paySupplierDebt(
      businessId = businessId,
      debtId = debt.id,
      amountOriginal = BigDecimal("3000.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal("1.0000"),
      paymentMethod = "TRANSFER",
    )

    val debtAfterPaid = db.purchaseDao().getDebtById(debt.id)!!
    assertEquals("PAID", debtAfterPaid.status)
    assertEquals(0, BigDecimal.ZERO.compareTo(debtAfterPaid.remainingBase))
    assertEquals(0, BigDecimal.ZERO.compareTo(db.purchaseDao().getSupplierById(supplierId)!!.balanceOwedBase))

    // Registrar una segunda compra a crédito y anularla de forma no destructiva
    val annulPurchaseId = purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = supplierId,
        invoiceNumber = "FAC-ANUL",
        currencyCode = "CUP",
        exchangeRateToBase = BigDecimal("1.0000"),
        paidAmountOriginal = BigDecimal.ZERO,
        paymentMethod = "CASH",
        lines = listOf(
          PurchaseLineInput(
            productId = aceiteId,
            productName = "Aceite",
            unitLabel = "u",
            enteredQuantity = BigDecimal("5"),
            conversionFactorToBase = BigDecimal.ONE,
            unitPriceOriginal = BigDecimal("600.00"),
          ),
        ),
      ),
    )

    assertEquals(0, BigDecimal("15").compareTo(db.inventoryDao().getProductById(aceiteId)!!.stockBaseQty))

    purchaseRepo.annulPurchase(businessId, annulPurchaseId, "Error de factura")

    val annulled = db.purchaseDao().getPurchaseById(annulPurchaseId)
    assertNotNull(annulled)
    assertEquals("ANNULLED", annulled!!.status)
    assertEquals(0, BigDecimal("10").compareTo(db.inventoryDao().getProductById(aceiteId)!!.stockBaseQty))
    assertEquals(0, BigDecimal.ZERO.compareTo(db.purchaseDao().getSupplierById(supplierId)!!.balanceOwedBase))
    assertEquals("ANNULLED", db.purchaseDao().getDebtByReference("PURCHASE", annulPurchaseId)!!.status)

    val movements = inventoryRepo.observeProductMovements(aceiteId).first()
    assertTrue(movements.any { it.movementType == "PURCHASE_ANNUL_OUT" })
  }
}
