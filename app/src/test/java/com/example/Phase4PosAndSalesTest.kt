package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.receipt.ReceiptFormatter
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.ConfirmPurchaseRequest
import com.example.data.repository.ConfirmSaleRequest
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PresentationDraft
import com.example.data.repository.ProductModality
import com.example.data.repository.PurchaseLineInput
import com.example.data.repository.PurchaseRepository
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
@Config(sdk = [35])
class Phase4PosAndSalesTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var inventoryRepo: InventoryRepository
  private lateinit var purchaseRepo: PurchaseRepository
  private lateinit var saleRepo: SaleRepository
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

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Minimercado Victoria",
        businessType = "Tienda / Bodega",
        logoUri = null,
        phone = "555-9000",
        email = "",
        address = "Calle 10 #45",
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
  fun `venta de 0_5 libras de arroz calcula 275 total 200 costo 75 ganancia bruta y conserva costo historico`() = runBlocking {
    val arrozId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Arroz",
        sku = "ARR-1",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
      ),
    )

    val saleId = saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        clientRequestId = "req-arroz-05",
        payments = listOf(
          SalePaymentInput(
            paymentMethod = "CASH",
            currencyCode = "CUP",
            originalAmount = BigDecimal("275.00"),
            exchangeRateToBase = BigDecimal("1.0000"),
          ),
        ),
        lines = listOf(
          SaleLineInput(
            productId = arrozId,
            productName = "Arroz",
            unitLabel = "lb",
            enteredQuantity = BigDecimal("0.5"),
            conversionFactorToBase = BigDecimal("500"),
          ),
        ),
      ),
    )

    // Idempotency: repetir el mismo clientRequestId retorna el mismo saleId sin volver a descontar stock
    val duplicateSaleId = saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        clientRequestId = "req-arroz-05",
        payments = listOf(
          SalePaymentInput(
            paymentMethod = "CASH",
            currencyCode = "CUP",
            originalAmount = BigDecimal("275.00"),
            exchangeRateToBase = BigDecimal("1.0000"),
          ),
        ),
        lines = listOf(
          SaleLineInput(
            productId = arrozId,
            productName = "Arroz",
            unitLabel = "lb",
            enteredQuantity = BigDecimal("0.5"),
            conversionFactorToBase = BigDecimal("500"),
          ),
        ),
      ),
    )
    assertEquals(saleId, duplicateSaleId)

    val sale = db.saleDao().getSaleById(saleId)!!
    assertEquals(0, BigDecimal("275.00").compareTo(sale.totalBase))
    assertEquals(0, BigDecimal("200.00").compareTo(sale.totalCostOfGoodsBase))
    assertEquals(0, BigDecimal("75.00").compareTo(sale.grossProfitBase))

    // 5000 g - 250 g = 4750 g
    var arroz = db.inventoryDao().getProductById(arrozId)!!
    assertEquals(0, BigDecimal("4750").compareTo(arroz.stockBaseQty))

    // Si luego entra una compra más cara que cambia avgCostPerSaleUnit, la venta pasada conserva su costo histórico
    purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = null,
        currencyCode = "CUP",
        exchangeRateToBase = BigDecimal("1.0000"),
        paidAmountOriginal = BigDecimal("6000.00"),
        lines = listOf(
          PurchaseLineInput(
            productId = arrozId,
            productName = "Arroz",
            unitLabel = "lb",
            enteredQuantity = BigDecimal("10"),
            conversionFactorToBase = BigDecimal("500"),
            unitPriceOriginal = BigDecimal("600.00"),
          ),
        ),
      ),
    )

    arroz = db.inventoryDao().getProductById(arrozId)!!
    assertTrue(arroz.avgCostPerSaleUnit > BigDecimal("450.00"))

    val historicalDetail = db.saleDao().getSaleDetails(saleId).single()
    assertEquals(0, BigDecimal("400.00").compareTo(historicalDetail.historicalUnitCostBase))
    assertEquals(0, BigDecimal("200.00").compareTo(historicalDetail.lineCostBase))
    assertEquals(0, BigDecimal("75.00").compareTo(historicalDetail.lineGrossProfitBase))
  }

  @Test
  fun `venta mixta en CUP y USD con saldo a credito devolucion parcial y anulacion no destructiva`() = runBlocking {
    val customerId = saleRepo.saveCustomer(
      businessId = businessId,
      name = "Carlos Pérez",
      phone = "555-3333",
    )
    val jugoId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Jugo",
        sku = "JUG-1",
        modality = ProductModality.PRESENTATION,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("120.00"),
        avgCostPerSaleUnit = BigDecimal("80.00"),
        initialStockEnteredQty = BigDecimal("5"),
        initialStockUnitLabel = "Caja x 12",
        initialStockFactorToBase = BigDecimal("12"),
        presentations = listOf(
          PresentationDraft(
            name = "Caja x 12",
            baseUnitsPerPresentation = BigDecimal("12"),
            salePrice = BigDecimal("1400.00"),
          ),
        ),
      ),
    )

    // Vender 1 Caja x 12 (1400 CUP) + 3 unidades (360 CUP) = 1760 CUP, descuento 60 CUP -> Total 1700 CUP
    // Pago mixto: 100 CUP efectivo + 1.00 USD (320 CUP) = 420 CUP pagado -> Deuda cliente = 1280 CUP
    val saleId = saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        customerId = customerId,
        discountBase = BigDecimal("60.00"),
        payments = listOf(
          SalePaymentInput(
            paymentMethod = "CASH",
            currencyCode = "CUP",
            originalAmount = BigDecimal("100.00"),
            exchangeRateToBase = BigDecimal("1.0000"),
          ),
          SalePaymentInput(
            paymentMethod = "USD",
            currencyCode = "USD",
            originalAmount = BigDecimal("1.00"),
            exchangeRateToBase = BigDecimal("320.0000"),
            isPhysicalCash = false,
          ),
        ),
        lines = listOf(
          SaleLineInput(
            productId = jugoId,
            productName = "Jugo",
            unitLabel = "Caja x 12",
            enteredQuantity = BigDecimal("1"),
            conversionFactorToBase = BigDecimal("12"),
            overrideUnitSalePriceBase = BigDecimal("1400.00"),
          ),
          SaleLineInput(
            productId = jugoId,
            productName = "Jugo",
            unitLabel = "u",
            enteredQuantity = BigDecimal("3"),
            conversionFactorToBase = BigDecimal.ONE,
          ),
        ),
      ),
    )

    // Stock inicial 60 - 15 = 45 u
    assertEquals(0, BigDecimal("45").compareTo(db.inventoryDao().getProductById(jugoId)!!.stockBaseQty))
    var sale = db.saleDao().getSaleById(saleId)!!
    assertEquals(0, BigDecimal("1700.00").compareTo(sale.totalBase))
    assertEquals(0, BigDecimal("420.00").compareTo(sale.paidBase))
    assertEquals(0, BigDecimal("1280.00").compareTo(sale.pendingDebtBase))
    assertEquals(0, BigDecimal("1280.00").compareTo(db.saleDao().getCustomerById(customerId)!!.debtBalanceBase))

    // Devolución parcial de 2 unidades de la segunda línea (240 CUP venta, 160 CUP costo)
    val details = db.saleDao().getSaleDetails(saleId)
    val unitLine = details[1]
    saleRepo.returnSaleItem(
      businessId = businessId,
      saleId = saleId,
      saleDetailId = unitLine.id,
      returnEnteredQty = BigDecimal("2"),
      reason = "Cliente cambió de opinión",
    )

    // Stock regresa de 45 -> 47 u; deuda disminuye 1280 - 240 = 1040 CUP
    assertEquals(0, BigDecimal("47").compareTo(db.inventoryDao().getProductById(jugoId)!!.stockBaseQty))
    sale = db.saleDao().getSaleById(saleId)!!
    assertEquals("PARTIALLY_RETURNED", sale.status)
    assertEquals(0, BigDecimal("1460.00").compareTo(sale.totalBase))
    assertEquals(0, BigDecimal("1040.00").compareTo(sale.pendingDebtBase))
    assertEquals(0, BigDecimal("1040.00").compareTo(db.saleDao().getCustomerById(customerId)!!.debtBalanceBase))

    // Comprobante incluye datos del negocio, líneas y pagos
    val receipt = ReceiptFormatter.buildTextReceipt(
      business = db.businessConfigDao().getBusinessById(businessId),
      sale = sale,
      details = db.saleDao().getSaleDetails(saleId),
      payments = db.saleDao().getPaymentsForReference("SALE", saleId),
      customerName = "Carlos Pérez",
    )
    assertTrue(receipt.contains("MINIMERCADO VICTORIA"))
    assertTrue(receipt.contains(sale.receiptNumber))
    assertTrue(receipt.contains("Carlos Pérez"))

    // Anulación no destructiva del resto (restaura las 13 u restantes -> 60 u, deuda -> 0)
    saleRepo.annulSale(businessId, saleId, "Cancelación total")
    val annulled = db.saleDao().getSaleById(saleId)
    assertNotNull(annulled)
    assertEquals("ANNULLED", annulled!!.status)
    assertEquals(0, BigDecimal("60").compareTo(db.inventoryDao().getProductById(jugoId)!!.stockBaseQty))
    assertEquals(0, BigDecimal.ZERO.compareTo(db.saleDao().getCustomerById(customerId)!!.debtBalanceBase))
    assertEquals("ANNULLED", db.saleDao().getDebtByReference("SALE", saleId)!!.status)

    val movements = inventoryRepo.observeProductMovements(jugoId).first()
    assertTrue(movements.any { it.movementType == "SALE_RETURN_IN" })
    assertTrue(movements.any { it.movementType == "SALE_ANNUL_IN" })
  }

  @Test
  fun `validacion impide vender cantidad superior a stock disponible sumando lineas del carrito`() = runBlocking {
    val cafeId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Café",
        sku = "CAF-1",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("350.00"),
        avgCostPerSaleUnit = BigDecimal("250.00"),
        initialStockEnteredQty = BigDecimal("3"),
        initialStockUnitLabel = "u",
        initialStockFactorToBase = BigDecimal.ONE,
      ),
    )

    var rejected = false
    try {
      saleRepo.confirmSale(
        ConfirmSaleRequest(
          businessId = businessId,
          payments = listOf(
            SalePaymentInput(
              paymentMethod = "CASH",
              currencyCode = "CUP",
              originalAmount = BigDecimal("1400.00"),
              exchangeRateToBase = BigDecimal.ONE,
            ),
          ),
          lines = listOf(
            SaleLineInput(cafeId, "Café", "u", BigDecimal("2"), BigDecimal.ONE),
            SaleLineInput(cafeId, "Café", "u", BigDecimal("2"), BigDecimal.ONE),
          ),
        ),
      )
    } catch (_: IllegalStateException) {
      rejected = true
    }

    assertTrue(rejected)
    assertEquals(0, BigDecimal("3").compareTo(db.inventoryDao().getProductById(cafeId)!!.stockBaseQty))
  }
}
