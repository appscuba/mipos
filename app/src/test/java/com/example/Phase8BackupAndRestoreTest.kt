package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BackupRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.ConfirmSaleRequest
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
import java.io.File
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase8BackupAndRestoreTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var prefs: AppPreferencesDataStore
  private lateinit var businessRepo: BusinessRepository
  private lateinit var inventoryRepo: InventoryRepository
  private lateinit var saleRepo: SaleRepository
  private lateinit var backupRepo: BackupRepository
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
    backupRepo = BackupRepository(db = db, preferences = prefs)

    businessId = businessRepo.completeInitialSetup(
      InitialBusinessSetupRequest(
        businessName = "Negocio Original",
        businessType = "Bodega",
        logoUri = null,
        phone = "555-8888",
        email = "",
        address = "Av Central",
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
  fun `exportacion cifrada y restauracion completa recupera inventario ventas y crea copia preventiva`() = runBlocking {
    val arrozId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Arroz Respaldo",
        sku = "ARR-BK",
        barcode = "",
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
      ),
    )

    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        payments = listOf(SalePaymentInput("CASH", "CUP", BigDecimal("275.00"), BigDecimal.ONE)),
        lines = listOf(SaleLineInput(arrozId, "Arroz Respaldo", "lb", BigDecimal("0.5"), BigDecimal("500"))),
      ),
    )

    // Export encrypted backup protected with passphrase
    val backupBytes = backupRepo.exportEncryptedBackup(businessId, "MiClaveSegura99")
    val envelope = backupRepo.inspectBackup(backupBytes)
    assertTrue(envelope.requiresPassphrase)
    assertEquals("Negocio Original", envelope.businessName)

    // Wrong passphrase must fail cleanly
    var wrongPassFailed = false
    try {
      backupRepo.restoreEncryptedBackup(context, backupBytes, "ClaveMala", businessId)
    } catch (_: IllegalArgumentException) {
      wrongPassFailed = true
    }
    assertTrue(wrongPassFailed)

    // Tampered file must fail integrity/AES check
    val corrupted = backupBytes.copyOf().also { it[it.size - 10] = (it[it.size - 10].toInt() xor 0xFF).toByte() }
    var corruptedFailed = false
    try {
      backupRepo.restoreEncryptedBackup(context, corrupted, "MiClaveSegura99", businessId)
    } catch (_: IllegalArgumentException) {
      corruptedFailed = true
    }
    assertTrue(corruptedFailed)

    // Mutate current database (change business name and add extra product) before restoring
    businessRepo.updateBusinessSettings(
      businessId = businessId,
      businessName = "Negocio Modificado",
      businessType = "Otro",
      logoUri = null,
      phone = "",
      email = "",
      address = "",
      primaryCurrency = "CUP",
      gramsPerPound = BigDecimal("500"),
      allowNegativeStock = false,
    )
    inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = null,
        name = "Producto Temporal",
        sku = "TMP",
        barcode = "",
        modality = ProductModality.UNIT,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("100.00"),
        avgCostPerSaleUnit = BigDecimal("50.00"),
        initialStockEnteredQty = BigDecimal("5"),
        initialStockUnitLabel = "u",
        initialStockFactorToBase = BigDecimal.ONE,
      ),
    )

    // Restore original backup
    val summary = backupRepo.restoreEncryptedBackup(context, backupBytes, "MiClaveSegura99", businessId)
    assertEquals("Negocio Original", summary.businessName)
    assertEquals(1, summary.productsCount)
    assertEquals(1, summary.salesCount)
    assertNotNull(summary.safetyBackupPath)
    assertTrue(File(summary.safetyBackupPath!!).exists())

    // Verify exact data restored
    val restoredBiz = db.businessConfigDao().getActiveBusiness()!!
    assertEquals("Negocio Original", restoredBiz.name)
    val products = inventoryRepo.observeInventoryOverview(restoredBiz.id).first().items
    assertEquals(1, products.size)
    assertEquals("Arroz Respaldo", products.single().product.name)
    assertEquals(0, BigDecimal("4750").compareTo(products.single().product.stockBaseQty))
  }
}
