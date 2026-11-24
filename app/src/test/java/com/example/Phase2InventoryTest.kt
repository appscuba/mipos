package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PresentationDraft
import com.example.data.repository.ProductModality
import com.example.data.repository.SaveProductRequest
import com.example.data.repository.StockAdjustmentMode
import com.example.data.repository.StockAdjustmentRequest
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
@Config(sdk = [35])
class Phase2InventoryTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var inventoryRepo: InventoryRepository
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

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Almacén Prueba",
        businessType = "Tienda / Bodega",
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
  fun `producto por peso registra 10 lb como 5000 g descuenta 250 g y 0_35 lb y conserva equivalencia historica`() = runBlocking {
    val arrozId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Arroz",
        sku = "ARR-01",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
        lowStockThresholdBase = BigDecimal("1000"),
      ),
    )

    var arroz = db.inventoryDao().getProductById(arrozId)!!
    assertEquals(0, BigDecimal("5000").compareTo(arroz.stockBaseQty))

    // Descuento directo de 250 gramos -> quedan 4,750 g
    inventoryRepo.recordStockAdjustment(
      StockAdjustmentRequest(
        businessId = businessId,
        productId = arrozId,
        mode = StockAdjustmentMode.SUBTRACT,
        enteredQuantity = BigDecimal("250"),
        unitLabel = "g",
        conversionFactorToBase = BigDecimal.ONE,
        reason = "Prueba 250g",
      ),
    )
    arroz = db.inventoryDao().getProductById(arrozId)!!
    assertEquals(0, BigDecimal("4750").compareTo(arroz.stockBaseQty))

    // Descuento de 0.35 libras (175 g) -> quedan 4,575 g
    inventoryRepo.recordStockAdjustment(
      StockAdjustmentRequest(
        businessId = businessId,
        productId = arrozId,
        mode = StockAdjustmentMode.SUBTRACT,
        enteredQuantity = BigDecimal("0.35"),
        unitLabel = "lb",
        conversionFactorToBase = BigDecimal("500"),
        reason = "Prueba 0.35 lb",
      ),
    )
    arroz = db.inventoryDao().getProductById(arrozId)!!
    assertEquals(0, BigDecimal("4575").compareTo(arroz.stockBaseQty))

    // Cambiar equivalencia global a 454 g/lb NO altera la equivalencia histórica guardada en el kárdex
    businessRepo.updateBusinessSettings(
      businessId = businessId,
      businessName = "Almacén Prueba",
      businessType = "Tienda / Bodega",
      logoUri = null,
      phone = "",
      email = "",
      address = "",
      primaryCurrency = "CUP",
      gramsPerPound = BigDecimal("454"),
      allowNegativeStock = false,
    )

    val movements = inventoryRepo.observeProductMovements(arrozId).first()
    assertEquals(3, movements.size)
    val initialMovement = movements.last()
    assertEquals(0, BigDecimal("500").compareTo(initialMovement.appliedEquivalence))
  }

  @Test
  fun `10 cajas de 12 unidades generan 120 unidades y descontar 3 deja 117`() = runBlocking {
    val jugoId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Jugo de Mango",
        sku = "JUG-12",
        modality = ProductModality.PRESENTATION,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("120.00"),
        avgCostPerSaleUnit = BigDecimal("80.00"),
        initialStockEnteredQty = BigDecimal("10"),
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

    var jugo = db.inventoryDao().getProductById(jugoId)!!
    assertEquals(0, BigDecimal("120").compareTo(jugo.stockBaseQty))

    inventoryRepo.recordStockAdjustment(
      StockAdjustmentRequest(
        businessId = businessId,
        productId = jugoId,
        mode = StockAdjustmentMode.SUBTRACT,
        enteredQuantity = BigDecimal("3"),
        unitLabel = "u",
        conversionFactorToBase = BigDecimal.ONE,
        reason = "Salida 3 unidades",
      ),
    )
    jugo = db.inventoryDao().getProductById(jugoId)!!
    assertEquals(0, BigDecimal("117").compareTo(jugo.stockBaseQty))
  }

  @Test
  fun `proteccion contra stock negativo rechaza salida superior a existencias`() = runBlocking {
    val cafeId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Café Molido",
        sku = "CAF-01",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("350.00"),
        avgCostPerSaleUnit = BigDecimal("250.00"),
        initialStockEnteredQty = BigDecimal("2"),
        initialStockUnitLabel = "u",
        initialStockFactorToBase = BigDecimal.ONE,
      ),
    )

    var failed = false
    try {
      inventoryRepo.recordStockAdjustment(
        StockAdjustmentRequest(
          businessId = businessId,
          productId = cafeId,
          mode = StockAdjustmentMode.SUBTRACT,
          enteredQuantity = BigDecimal("5"),
          unitLabel = "u",
          conversionFactorToBase = BigDecimal.ONE,
          reason = "Intento negativo",
        ),
      )
    } catch (_: IllegalStateException) {
      failed = true
    }

    assertTrue(failed)
    val cafe = db.inventoryDao().getProductById(cafeId)!!
    assertEquals(0, BigDecimal("2").compareTo(cafe.stockBaseQty))
  }

  @Test
  fun `valoracion total de inventario calcula exactamente en BigDecimal y gestiona categorias`() = runBlocking {
    val catId = inventoryRepo.saveCategory(
      businessId = businessId,
      name = "Lácteos",
      description = "Productos refrigerados",
    )

    inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = catId,
        name = "Queso",
        sku = "QUE-1",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("600.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("2.5"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
      ),
    )

    val overview = inventoryRepo.observeInventoryOverview(businessId).first()
    // 2.5 lb * 400.00 = 1,000.00 CUP
    assertEquals(0, BigDecimal("1000.00").compareTo(overview.totalInventoryValueBase))
    assertTrue(overview.categories.any { it.name == "Lácteos" })

    // Editar categoría existente
    inventoryRepo.saveCategory(
      businessId = businessId,
      categoryId = catId,
      name = "Lácteos y Quesos",
      description = "Frescos",
      colorHex = "#38BDF8",
    )
    val afterEdit = inventoryRepo.observeInventoryOverview(businessId).first()
    assertTrue(afterEdit.categories.any { it.id == catId && it.name == "Lácteos y Quesos" })

    // Eliminar categoría y verificar que el producto sigue existiendo sin categoría
    inventoryRepo.deleteCategory(businessId, catId)
    val afterDelete = inventoryRepo.observeInventoryOverview(businessId).first()
    assertTrue(afterDelete.categories.none { it.id == catId })
    val queso = afterDelete.items.first { it.product.name == "Queso" }.product
    assertEquals(null, queso.categoryId)
  }
}
