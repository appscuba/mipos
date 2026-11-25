package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.PinHasher
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BackupRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.CashAdjustmentType
import com.example.data.repository.ConfirmPurchaseRequest
import com.example.data.repository.ConfirmSaleRequest
import com.example.data.repository.FinanceRepository
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
import com.example.data.repository.SecurityRepository
import com.example.data.repository.StockAdjustmentMode
import com.example.data.repository.StockAdjustmentRequest
import com.example.ui.theme.ThemeMode
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
  private lateinit var purchaseRepo: PurchaseRepository
  private lateinit var saleRepo: SaleRepository
  private lateinit var financeRepo: FinanceRepository
  private lateinit var securityRepo: SecurityRepository
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
    purchaseRepo = PurchaseRepository(db = db)
    saleRepo = SaleRepository(db = db)
    financeRepo = FinanceRepository(db = db)
    securityRepo = SecurityRepository(db = db, preferences = prefs)
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
        modality = ProductModality.WEIGHT,
        defaultSaleUnit = "POUND",
        salePrice = BigDecimal("550.00"),
        avgCostPerSaleUnit = BigDecimal("400.00"),
        initialStockEnteredQty = BigDecimal("10"),
        initialStockUnitLabel = "lb",
        initialStockFactorToBase = BigDecimal("500"),
      ),
    )

    // Las ventas exigen una sesión de caja abierta, igual que en producción.
    financeRepo.openCashSession(businessId, BigDecimal.ZERO, "Apertura de prueba")

    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        payments = listOf(SalePaymentInput("CASH", "CUP", BigDecimal("275.00"), BigDecimal.ONE)),
        lines = listOf(SaleLineInput(arrozId, "Arroz Respaldo", "lb", BigDecimal("0.5"), BigDecimal("500"))),
      ),
    )

    // Export encrypted backup protected with passphrase
    val backupBytes = backupRepo.exportEncryptedBackup(businessId, "MiClaveSegura99", context = context)
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

  @Test
  fun `respaldo total incluye usuarios pin preferencias inventarios finanzas clientes proveedores y auditoria`() = runBlocking {
    // 1. Theme & extra exchange rate
    prefs.setThemeMode(ThemeMode.LIGHT)
    businessRepo.saveCustomExchangeRate(
      businessId = businessId,
      currencyCode = "EUR",
      currencyName = "Euro",
      symbol = "€",
      rateToBase = BigDecimal("350.00"),
    )

    // 2. Second user with PIN and switch active session to that user
        val ownerId = prefs.preferencesFlow.first().activeUserId
    val clerkId = securityRepo.createUser(
      businessId = businessId,
      fullName = "Carlos Dependiente",
      username = "carlos",
      roleCode = "CLERK",
      pin = "2580",
    )
    prefs.setActiveUser(clerkId)

    // 3. Custom category, product with presentation, and inventory adjustment
    val customCatId = inventoryRepo.saveCategory(businessId = businessId, name = "Abarrotes", colorHex = "#FF8800")
    val oilId = inventoryRepo.saveProduct(
      SaveProductRequest(
        businessId = businessId,
        categoryId = customCatId,
        name = "Aceite Vegetal",
        sku = "ACE-01",
        modality = ProductModality.PRESENTATION,
        defaultSaleUnit = "UNIT",
        salePrice = BigDecimal("600.00"),
        avgCostPerSaleUnit = BigDecimal("450.00"),
        initialStockEnteredQty = BigDecimal("24"),
        initialStockUnitLabel = "u",
        initialStockFactorToBase = BigDecimal.ONE,
        presentations = listOf(
          PresentationDraft(
            name = "Caja x12",
            baseUnitsPerPresentation = BigDecimal("12"),
            salePrice = BigDecimal("6900.00"),
          ),
        ),
      ),
    )
    inventoryRepo.recordStockAdjustment(
      StockAdjustmentRequest(
        businessId = businessId,
        productId = oilId,
        userId = ownerId,
        mode = StockAdjustmentMode.SET_EXACT,
        enteredQuantity = BigDecimal("30"),
        unitLabel = "u",
        conversionFactorToBase = BigDecimal.ONE,
        reason = "Conteo físico inicial",
      ),
    )

    // 4. Supplier + Purchase
    val supplierId = purchaseRepo.saveSupplier(businessId = businessId, name = "Distribuidora Sur", phone = "555-1212")
    purchaseRepo.confirmPurchase(
      ConfirmPurchaseRequest(
        businessId = businessId,
        supplierId = supplierId,
        userId = clerkId,
        invoiceNumber = "FAC-900",
        paidAmountOriginal = BigDecimal("1000.00"),
        lines = listOf(
          PurchaseLineInput(
            productId = oilId,
            productName = "Aceite Vegetal",
            unitLabel = "u",
            enteredQuantity = BigDecimal("5"),
            conversionFactorToBase = BigDecimal.ONE,
            unitPriceOriginal = BigDecimal("400.00"),
          ),
        ),
      ),
    )

    // 5. Open cash session + movement + expense
    val sessionId = financeRepo.openCashSession(
      businessId = businessId,
      openingCashBase = BigDecimal("2000.00"),
      notes = "Turno mañana",
      userId = clerkId,
    )
    financeRepo.recordCashAdjustment(
      businessId = businessId,
      type = CashAdjustmentType.EXTRA_INCOME,
      amountOriginal = BigDecimal("300.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal.ONE,
      affectsPhysicalCash = true,
      description = "Fondo cambio",
      userId = clerkId,
    )
    val expCats = db.catalogDao().observeExpenseCategories(businessId).first()
    financeRepo.recordExpense(
      businessId = businessId,
      expenseCategoryId = expCats.first().id,
      description = "Mensajería",
      currencyCode = "CUP",
      originalAmount = BigDecimal("150.00"),
      exchangeRateToBase = BigDecimal.ONE,
      paymentMethod = "CASH",
      paidFromCashDrawer = true,
      userId = clerkId,
    )

    // 6. Customer + Credit sale + manual debt
    val customerId = financeRepo.saveCustomer(businessId = businessId, name = "María Pérez", phone = "53000001")
    saleRepo.confirmSale(
      ConfirmSaleRequest(
        businessId = businessId,
        customerId = customerId,
        userId = clerkId,
        payments = listOf(SalePaymentInput("CASH", "CUP", BigDecimal("600.00"), BigDecimal.ONE)),
        lines = listOf(SaleLineInput(oilId, "Aceite Vegetal", "u", BigDecimal("2"), BigDecimal.ONE)),
      ),
    )
    val manualDebtId = financeRepo.createCustomerDebt(
      businessId = businessId,
      customerId = customerId,
      amountOriginal = BigDecimal("250.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal.ONE,
      note = "Saldo anterior",
      userId = clerkId,
    )
    financeRepo.collectCustomerDebt(
      businessId = businessId,
      debtId = manualDebtId,
      amountOriginal = BigDecimal("50.00"),
      currencyCode = "CUP",
      exchangeRateToBase = BigDecimal.ONE,
      paymentMethod = "CASH",
      note = "Abono parcial",
      userId = clerkId,
    )

    // Export full backup
    val backupBytes = backupRepo.exportEncryptedBackup(
      businessId = businessId,
      passphrase = null,
      actorUserId = clerkId,
      context = context,
    )

    // Wipe the entire system completely
    businessRepo.resetAllSystemData()
    assertEquals(0, db.backupDao().getAllBusinesses().size)
    assertEquals(0, db.backupDao().getAllUsers().size)
    assertEquals(0, db.backupDao().getAllProducts().size)
    assertEquals(0, db.backupDao().getAllCashSessions().size)

    // Restore from encrypted backup (simulating fresh setup restore)
    val summary = backupRepo.restoreEncryptedBackup(
      context = context,
      fileBytes = backupBytes,
      passphrase = null,
      currentBusinessId = 0L,
      actorUserId = null,
    )

    // Assert every module & preference restored 100%
    assertEquals("Negocio Original", summary.businessName)
    assertEquals(2, summary.usersCount)
    assertEquals(1, summary.productsCount)
    assertEquals(1, summary.purchasesCount)
    assertEquals(1, summary.salesCount)
    assertEquals(1, summary.customersCount)
    assertEquals(1, summary.suppliersCount)
    assertEquals(1, summary.cashSessionsCount)
    assertEquals(1, summary.expensesCount)
    assertEquals(3, summary.debtsCount) // 1 supplier purchase debt + 1 credit sale debt + 1 manual debt
    assertTrue(summary.paymentsCount >= 3)
    assertTrue(summary.inventoryMovementsCount >= 3)
    assertTrue(summary.auditLogsCount >= 5)

    val backupDao = db.backupDao()
    assertEquals(1, backupDao.getAllPresentations().size)
    assertEquals(1, backupDao.getAllInventoryAdjustments().size)
    assertEquals(3, backupDao.getAllExchangeRates().size) // CUP, USD, EUR

    // Verify user PIN hash & active user & theme restored faithfully
    val restoredPrefs = prefs.preferencesFlow.first()
    assertEquals(ThemeMode.LIGHT, restoredPrefs.themeMode)
    assertEquals(clerkId, restoredPrefs.activeUserId)
    assertEquals(businessId, restoredPrefs.activeBusinessId)
    val restoredClerk = db.securityDao().getUserById(clerkId)!!
    assertEquals("carlos", restoredClerk.username)
    assertTrue(PinHasher.verifyPin("2580", restoredClerk.pinHash))

    // Verify active cash session & customer balance restored
    val restoredSession = db.financeDao().getCashSessionById(sessionId)!!
    assertEquals("OPEN", restoredSession.status)
    val restoredCustomer = db.financeDao().getCustomerById(customerId)!!
    assertEquals(0, BigDecimal("800.00").compareTo(restoredCustomer.debtBalanceBase))
  }
}
