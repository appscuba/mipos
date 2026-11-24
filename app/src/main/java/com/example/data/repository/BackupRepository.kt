package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.core.money.MoneyMath
import com.example.core.security.BackupCrypto
import com.example.core.security.BackupFileEnvelope
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AppSettingsEntity
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.local.db.entity.ExpenseEntity
import com.example.data.local.db.entity.InventoryAdjustmentEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.PermissionEntity
import com.example.data.local.db.entity.ProductEntity
import com.example.data.local.db.entity.ProductPresentationEntity
import com.example.data.local.db.entity.PurchaseDetailEntity
import com.example.data.local.db.entity.PurchaseEntity
import com.example.data.local.db.entity.RoleEntity
import com.example.data.local.db.entity.RolePermissionCrossRef
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.local.db.entity.SaleEntity
import com.example.data.local.db.entity.SupplierEntity
import com.example.data.local.db.entity.UserEntity
import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal

@Serializable
data class WorkspacePreferencesDto(
  val themeMode: String = "DARK",
  val activeBusinessId: Long = 0L,
  val activeUserId: Long = 0L,
  val lastBackupTimestamp: Long = 0L,
)

@Serializable
data class FullBackupPayload(
  val schemaVersion: Int = BackupFileEnvelope.CURRENT_SCHEMA_VERSION,
  val exportedAt: Long = System.currentTimeMillis(),
  val businesses: List<BusinessDto>,
  val roles: List<RoleDto>,
  val permissions: List<PermissionDto>,
  val rolePermissions: List<RolePermissionDto>,
  val users: List<UserDto>,
  val settings: List<AppSettingsDto>,
  val exchangeRates: List<ExchangeRateDto>,
  val categories: List<CategoryDto>,
  val products: List<ProductDto>,
  val presentations: List<ProductPresentationDto>,
  val suppliers: List<SupplierDto>,
  val purchases: List<PurchaseDto>,
  val purchaseDetails: List<PurchaseDetailDto>,
  val customers: List<CustomerDto>,
  val sales: List<SaleDto>,
  val saleDetails: List<SaleDetailDto>,
  val payments: List<PaymentDto>,
  val debts: List<DebtDto>,
  val expenseCategories: List<ExpenseCategoryDto>,
  val expenses: List<ExpenseDto>,
  val cashSessions: List<CashSessionDto>,
  val cashMovements: List<CashMovementDto>,
  val inventoryMovements: List<InventoryMovementDto>,
  val inventoryAdjustments: List<InventoryAdjustmentDto>,
  val auditLogs: List<AuditLogDto>,
  val preferences: WorkspacePreferencesDto? = null,
  val businessLogosBase64: Map<Long, String> = emptyMap(),
)

@Serializable data class BusinessDto(val id: Long, val name: String, val businessType: String, val logoUri: String?, val phone: String, val email: String, val address: String, val taxId: String, val primaryCurrency: String, val isActive: Boolean, val createdAt: Long, val updatedAt: Long)
@Serializable data class RoleDto(val id: Long, val code: String, val name: String, val description: String, val isSystemDefault: Boolean)
@Serializable data class PermissionDto(val id: Long, val code: String, val name: String, val module: String)
@Serializable data class RolePermissionDto(val roleId: Long, val permissionId: Long)
@Serializable data class UserDto(val id: Long, val businessId: Long, val roleId: Long, val fullName: String, val username: String, val pinHash: String?, val isActive: Boolean, val createdAt: Long)
@Serializable data class AppSettingsDto(val id: Long, val businessId: Long, val languageCode: String, val gramsPerPound: String, val allowNegativeStock: Boolean, val defaultLowStockUnits: String, val defaultLowStockGrams: String, val defaultCostMethod: String, val enabledPaymentMethods: String, val backupReminderIntervalDays: Int, val updatedAt: Long)
@Serializable data class ExchangeRateDto(val id: Long, val businessId: Long, val currencyCode: String, val currencyName: String, val symbol: String, val rateToBase: String, val isBaseCurrency: Boolean, val isActive: Boolean, val updatedAt: Long)
@Serializable data class CategoryDto(val id: Long, val businessId: Long, val name: String, val description: String, val colorHex: String, val isActive: Boolean)
@Serializable data class ProductDto(val id: Long, val businessId: Long, val categoryId: Long?, val name: String, val sku: String, val modality: String, val baseUnit: String, val stockBaseQty: String, val avgCostPerSaleUnit: String, val salePrice: String, val defaultSaleUnit: String, val lowStockThresholdBase: String, val trackInventory: Boolean, val isActive: Boolean, val createdAt: Long, val updatedAt: Long)
@Serializable data class ProductPresentationDto(val id: Long, val productId: Long, val name: String, val baseUnitsPerPresentation: String, val salePrice: String?, val isBuyPresentation: Boolean, val isSellPresentation: Boolean, val isActive: Boolean)
@Serializable data class SupplierDto(val id: Long, val businessId: Long, val name: String, val phone: String, val address: String, val notes: String, val balanceOwedBase: String, val isActive: Boolean, val createdAt: Long)
@Serializable data class PurchaseDto(val id: Long, val businessId: Long, val supplierId: Long?, val userId: Long?, val invoiceNumber: String, val purchaseDate: Long, val currencyCode: String, val exchangeRateToBase: String, val subtotalOriginal: String, val discountOriginal: String, val transportCostOriginal: String, val extraExpensesOriginal: String, val totalOriginal: String, val totalConvertedBase: String, val paidAmountBase: String, val pendingDebtBase: String, val paymentMethod: String, val status: String, val notes: String, val createdAt: Long)
@Serializable data class PurchaseDetailDto(val id: Long, val purchaseId: Long, val productId: Long, val presentationLabel: String, val enteredQuantity: String, val appliedEquivalence: String, val baseQuantityAdded: String, val unitPriceOriginal: String, val allocatedExtraCostBase: String, val realUnitCostBase: String, val lineTotalBase: String)
@Serializable data class CustomerDto(val id: Long, val businessId: Long, val name: String, val phone: String, val address: String, val notes: String, val debtBalanceBase: String, val isActive: Boolean, val createdAt: Long)
@Serializable data class SaleDto(val id: Long, val businessId: Long, val customerId: Long?, val userId: Long?, val cashSessionId: Long?, val receiptNumber: String, val clientRequestId: String, val saleDate: Long, val currencyCode: String, val exchangeRateToBase: String, val subtotalBase: String, val discountBase: String, val totalBase: String, val totalOriginal: String, val paidBase: String, val pendingDebtBase: String, val totalCostOfGoodsBase: String, val grossProfitBase: String, val isCreditSale: Boolean, val status: String, val notes: String, val createdAt: Long)
@Serializable data class SaleDetailDto(val id: Long, val saleId: Long, val productId: Long, val productNameSnapshot: String, val unitLabel: String, val enteredQuantity: String, val appliedEquivalence: String, val baseQuantityDeducted: String, val returnedBaseQuantity: String, val unitSalePriceBase: String, val historicalUnitCostBase: String, val lineTotalBase: String, val lineCostBase: String, val lineGrossProfitBase: String)
@Serializable data class PaymentDto(val id: Long, val businessId: Long, val userId: Long?, val referenceType: String, val referenceId: Long, val paymentMethod: String, val currencyCode: String, val originalAmount: String, val exchangeRateToBase: String, val convertedAmountBase: String, val isPhysicalCash: Boolean, val paymentDate: Long, val note: String)
@Serializable data class DebtDto(val id: Long, val businessId: Long, val partyType: String, val partyId: Long, val referenceType: String, val referenceId: Long, val currencyCode: String, val originalAmount: String, val exchangeRateToBase: String, val totalBase: String, val paidBase: String, val remainingBase: String, val status: String, val dueDate: Long?, val createdAt: Long)
@Serializable data class ExpenseCategoryDto(val id: Long, val businessId: Long, val name: String, val isSystemDefault: Boolean, val isActive: Boolean)
@Serializable data class ExpenseDto(val id: Long, val businessId: Long, val expenseCategoryId: Long, val userId: Long?, val cashSessionId: Long?, val description: String, val currencyCode: String, val originalAmount: String, val exchangeRateToBase: String, val convertedAmountBase: String, val paymentMethod: String, val paidFromCashDrawer: Boolean, val expenseDate: Long, val status: String, val createdAt: Long)
@Serializable data class CashSessionDto(val id: Long, val businessId: Long, val openedByUserId: Long?, val closedByUserId: Long?, val openedAt: Long, val closedAt: Long?, val openingCashBase: String, val expectedCashBase: String, val countedCashBase: String?, val differenceBase: String?, val totalCashSalesBase: String, val totalTransferSalesBase: String, val totalCashInflowsBase: String, val totalCashExpensesBase: String, val totalOwnerWithdrawalsBase: String, val status: String, val notes: String)
@Serializable data class CashMovementDto(val id: Long, val businessId: Long, val cashSessionId: Long, val userId: Long?, val movementType: String, val affectsPhysicalCash: Boolean, val currencyCode: String, val originalAmount: String, val exchangeRateToBase: String, val signedAmountBase: String, val description: String, val referenceType: String?, val referenceId: Long?, val timestamp: Long)
@Serializable data class InventoryMovementDto(val id: Long, val businessId: Long, val productId: Long, val userId: Long?, val movementType: String, val quantityBaseDelta: String, val stockAfterBase: String, val enteredQuantity: String, val enteredUnitLabel: String, val appliedEquivalence: String, val unitCostSnapshot: String, val referenceType: String, val referenceId: Long?, val note: String, val timestamp: Long)
@Serializable data class InventoryAdjustmentDto(val id: Long, val businessId: Long, val productId: Long, val userId: Long?, val previousStockBase: String, val newStockBase: String, val deltaBase: String, val reason: String, val timestamp: Long)
@Serializable data class AuditLogDto(val id: Long, val businessId: Long, val userId: Long?, val action: String, val entityType: String, val entityId: Long?, val details: String, val timestamp: Long)

data class RestoreSummary(
  val businessName: String,
  val productsCount: Int,
  val salesCount: Int,
  val purchasesCount: Int,
  val safetyBackupPath: String?,
  val usersCount: Int = 0,
  val categoriesCount: Int = 0,
  val customersCount: Int = 0,
  val suppliersCount: Int = 0,
  val cashSessionsCount: Int = 0,
  val expensesCount: Int = 0,
  val debtsCount: Int = 0,
  val paymentsCount: Int = 0,
  val inventoryMovementsCount: Int = 0,
  val auditLogsCount: Int = 0,
)

class BackupRepository(
  private val db: AppDatabase,
  private val preferences: AppPreferencesDataStore,
) {
  private val backupDao = db.backupDao()
  private val securityDao = db.securityDao()
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  suspend fun exportEncryptedBackup(
    businessId: Long,
    passphrase: String?,
    actorUserId: Long? = null,
    recordAuditAndTimestamp: Boolean = true,
    context: Context? = null,
  ): ByteArray {
    val payload = buildFullPayload(context)
    val businessName = payload.businesses.firstOrNull { it.id == businessId }?.name
      ?: payload.businesses.firstOrNull()?.name
      ?: "Stock+"
    val plainJson = json.encodeToString(payload)
    val encrypted = BackupCrypto.encryptBackup(plainJson, businessName, passphrase)

    if (recordAuditAndTimestamp) {
      val now = System.currentTimeMillis()
      preferences.updateLastBackupTimestamp(now)
      if (businessId > 0L) {
        securityDao.insertAuditLog(
          AuditLogEntity(
            businessId = businessId,
            userId = actorUserId,
            action = "BACKUP_EXPORTED",
            entityType = "BACKUP",
            details = "Respaldo completo cifrado (${payload.users.size} usu., ${payload.products.size} prod., ${payload.sales.size} ventas, ${payload.cashSessions.size} cajas, ${payload.expenses.size} gastos)",
            timestamp = now,
          ),
        )
      }
    }
    return encrypted
  }

  fun inspectBackup(fileBytes: ByteArray): BackupFileEnvelope =
    BackupCrypto.inspectEnvelope(fileBytes)

  suspend fun restoreEncryptedBackup(
    context: Context,
    fileBytes: ByteArray,
    passphrase: String?,
    currentBusinessId: Long,
    actorUserId: Long? = null,
  ): RestoreSummary {
    val plainJson = BackupCrypto.decryptBackup(fileBytes, passphrase)
    val payload = try {
      json.decodeFromString<FullBackupPayload>(plainJson)
    } catch (e: Exception) {
      throw IllegalArgumentException("El contenido del respaldo está corrupto o incompleto.", e)
    }

    require(payload.businesses.isNotEmpty()) {
      "El respaldo no contiene ningún negocio válido."
    }

    // Automatic pre-restore safety snapshot stored internally before replacing DB
    var safetyPath: String? = null
    val existingBusinesses = backupDao.getAllBusinesses()
    if (existingBusinesses.isNotEmpty()) {
      val targetSafetyBizId = if (currentBusinessId > 0L) currentBusinessId else existingBusinesses.first().id
      val safetyBytes = exportEncryptedBackup(
        businessId = targetSafetyBizId,
        passphrase = null,
        actorUserId = actorUserId,
        recordAuditAndTimestamp = false,
        context = context,
      )
      val dir = File(context.filesDir, "auto_backups").apply { mkdirs() }
      val file = File(dir, "pre_restore_${System.currentTimeMillis()}.gcbk")
      file.writeBytes(safetyBytes)
      safetyPath = file.absolutePath
    }

    // Restore embedded business logos cleanly into internal app storage
    val restoredLogoUris = mutableMapOf<Long, String>()
    if (payload.businessLogosBase64.isNotEmpty()) {
      val logosDir = File(context.filesDir, "restored_logos").apply { mkdirs() }
      val decoder = java.util.Base64.getDecoder()
      for ((bizId, base64Data) in payload.businessLogosBase64) {
        runCatching {
          val bytes = decoder.decode(base64Data)
          val outFile = File(logosDir, "business_${bizId}_logo.bin")
          outFile.writeBytes(bytes)
          restoredLogoUris[bizId] = android.net.Uri.fromFile(outFile).toString()
        }
      }
    }

    val businessesToInsert = payload.businesses.map { dto ->
      val localRestoredUri = restoredLogoUris[dto.id]
      if (localRestoredUri != null) dto.copy(logoUri = localRestoredUri).toEntity() else dto.toEntity()
    }

    db.withTransaction {
      backupDao.clearAuditLogs()
      backupDao.clearCashMovements()
      backupDao.clearCashSessions()
      backupDao.clearExpenses()
      backupDao.clearExpenseCategories()
      backupDao.clearDebts()
      backupDao.clearPayments()
      backupDao.clearSaleDetails()
      backupDao.clearSales()
      backupDao.clearCustomers()
      backupDao.clearPurchaseDetails()
      backupDao.clearPurchases()
      backupDao.clearSuppliers()
      backupDao.clearInventoryAdjustments()
      backupDao.clearInventoryMovements()
      backupDao.clearPresentations()
      backupDao.clearProducts()
      backupDao.clearCategories()
      backupDao.clearExchangeRates()
      backupDao.clearSettings()
      backupDao.clearUsers()
      backupDao.clearRolePermissions()
      backupDao.clearPermissions()
      backupDao.clearRoles()
      backupDao.clearBusinesses()

      backupDao.insertBusinesses(businessesToInsert)
      backupDao.insertRoles(payload.roles.map { it.toEntity() })
      backupDao.insertPermissions(payload.permissions.map { it.toEntity() })
      backupDao.insertRolePermissions(payload.rolePermissions.map { RolePermissionCrossRef(it.roleId, it.permissionId) })
      backupDao.insertUsers(payload.users.map { it.toEntity() })
      backupDao.insertSettings(payload.settings.map { it.toEntity() })
      backupDao.insertExchangeRates(payload.exchangeRates.map { it.toEntity() })
      backupDao.insertCategories(payload.categories.map { it.toEntity() })
      backupDao.insertProducts(payload.products.map { it.toEntity() })
      backupDao.insertPresentations(payload.presentations.map { it.toEntity() })
      backupDao.insertSuppliers(payload.suppliers.map { it.toEntity() })
      backupDao.insertPurchases(payload.purchases.map { it.toEntity() })
      backupDao.insertPurchaseDetails(payload.purchaseDetails.map { it.toEntity() })
      backupDao.insertCustomers(payload.customers.map { it.toEntity() })
      backupDao.insertSales(payload.sales.map { it.toEntity() })
      backupDao.insertSaleDetails(payload.saleDetails.map { it.toEntity() })
      backupDao.insertPayments(payload.payments.map { it.toEntity() })
      backupDao.insertDebts(payload.debts.map { it.toEntity() })
      backupDao.insertExpenseCategories(payload.expenseCategories.map { it.toEntity() })
      backupDao.insertExpenses(payload.expenses.map { it.toEntity() })
      backupDao.insertCashSessions(payload.cashSessions.map { it.toEntity() })
      backupDao.insertCashMovements(payload.cashMovements.map { it.toEntity() })
      backupDao.insertInventoryMovements(payload.inventoryMovements.map { it.toEntity() })
      backupDao.insertInventoryAdjustments(payload.inventoryAdjustments.map { it.toEntity() })
      backupDao.insertAuditLogs(payload.auditLogs.map { it.toEntity() })
    }

    val savedPrefs = payload.preferences
    val restoredBiz = savedPrefs?.activeBusinessId?.let { id ->
      payload.businesses.firstOrNull { it.id == id }
    } ?: payload.businesses.firstOrNull { it.isActive } ?: payload.businesses.first()

    val restoredUser = savedPrefs?.activeUserId?.let { id ->
      payload.users.firstOrNull { it.id == id && it.businessId == restoredBiz.id && it.isActive }
    } ?: payload.users.firstOrNull { it.businessId == restoredBiz.id && it.isActive }
      ?: payload.users.firstOrNull()

    preferences.markOnboardingCompleted(
      businessId = restoredBiz.id,
      userId = restoredUser?.id ?: 1L,
    )
    savedPrefs?.themeMode?.let { themeName ->
      com.example.ui.theme.ThemeMode.entries.firstOrNull { it.name == themeName }?.let { mode ->
        preferences.setThemeMode(mode)
      }
    }
    if ((savedPrefs?.lastBackupTimestamp ?: 0L) > 0L) {
      preferences.updateLastBackupTimestamp(savedPrefs!!.lastBackupTimestamp)
    }

    securityDao.insertAuditLog(
      AuditLogEntity(
        businessId = restoredBiz.id,
        userId = restoredUser?.id,
        action = "BACKUP_RESTORED",
        entityType = "BACKUP",
        details = "Respaldo completo restaurado (${payload.users.size} usu., ${payload.products.size} prod., ${payload.sales.size} ventas, ${payload.cashSessions.size} cajas, ${payload.debts.size} deudas)",
      ),
    )

    return RestoreSummary(
      businessName = restoredBiz.name,
      productsCount = payload.products.size,
      salesCount = payload.sales.size,
      purchasesCount = payload.purchases.size,
      safetyBackupPath = safetyPath,
      usersCount = payload.users.size,
      categoriesCount = payload.categories.size,
      customersCount = payload.customers.size,
      suppliersCount = payload.suppliers.size,
      cashSessionsCount = payload.cashSessions.size,
      expensesCount = payload.expenses.size,
      debtsCount = payload.debts.size,
      paymentsCount = payload.payments.size,
      inventoryMovementsCount = payload.inventoryMovements.size,
      auditLogsCount = payload.auditLogs.size,
    )
  }

  private suspend fun buildFullPayload(context: Context?): FullBackupPayload {
    val businesses = backupDao.getAllBusinesses().map { it.toDto() }
    val currentPrefs = preferences.preferencesFlow.firstOrNull()?.let { p ->
      WorkspacePreferencesDto(
        themeMode = p.themeMode.name,
        activeBusinessId = p.activeBusinessId,
        activeUserId = p.activeUserId,
        lastBackupTimestamp = p.lastBackupTimestamp,
      )
    }
    val logoMap = encodeBusinessLogos(context, businesses)
    return FullBackupPayload(
      businesses = businesses,
      roles = backupDao.getAllRoles().map { it.toDto() },
      permissions = backupDao.getAllPermissions().map { it.toDto() },
      rolePermissions = backupDao.getAllRolePermissions().map { RolePermissionDto(it.roleId, it.permissionId) },
      users = backupDao.getAllUsers().map { it.toDto() },
      settings = backupDao.getAllSettings().map { it.toDto() },
      exchangeRates = backupDao.getAllExchangeRates().map { it.toDto() },
      categories = backupDao.getAllCategories().map { it.toDto() },
      products = backupDao.getAllProducts().map { it.toDto() },
      presentations = backupDao.getAllPresentations().map { it.toDto() },
      suppliers = backupDao.getAllSuppliers().map { it.toDto() },
      purchases = backupDao.getAllPurchases().map { it.toDto() },
      purchaseDetails = backupDao.getAllPurchaseDetails().map { it.toDto() },
      customers = backupDao.getAllCustomers().map { it.toDto() },
      sales = backupDao.getAllSales().map { it.toDto() },
      saleDetails = backupDao.getAllSaleDetails().map { it.toDto() },
      payments = backupDao.getAllPayments().map { it.toDto() },
      debts = backupDao.getAllDebts().map { it.toDto() },
      expenseCategories = backupDao.getAllExpenseCategories().map { it.toDto() },
      expenses = backupDao.getAllExpenses().map { it.toDto() },
      cashSessions = backupDao.getAllCashSessions().map { it.toDto() },
      cashMovements = backupDao.getAllCashMovements().map { it.toDto() },
      inventoryMovements = backupDao.getAllInventoryMovements().map { it.toDto() },
      inventoryAdjustments = backupDao.getAllInventoryAdjustments().map { it.toDto() },
      auditLogs = backupDao.getAllAuditLogs().map { it.toDto() },
      preferences = currentPrefs,
      businessLogosBase64 = logoMap,
    )
  }

  private fun encodeBusinessLogos(context: Context?, businesses: List<BusinessDto>): Map<Long, String> {
    if (context == null) return emptyMap()
    val result = mutableMapOf<Long, String>()
    val encoder = java.util.Base64.getEncoder()
    for (biz in businesses) {
      val uriStr = biz.logoUri ?: continue
      runCatching {
        val uri = android.net.Uri.parse(uriStr)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null && bytes.isNotEmpty() && bytes.size <= 1_500_000) {
          result[biz.id] = encoder.encodeToString(bytes)
        }
      }
    }
    return result
  }
}

private fun BigDecimal.str(): String = toPlainString()
private fun String.bd(): BigDecimal = MoneyMath.parseDecimalOrNull(this) ?: BigDecimal.ZERO

private fun BusinessEntity.toDto() = BusinessDto(id, name, businessType, logoUri, phone, email, address, taxId, primaryCurrency, isActive, createdAt, updatedAt)
private fun BusinessDto.toEntity() = BusinessEntity(id, name, businessType, logoUri, phone, email, address, taxId, primaryCurrency, isActive, createdAt, updatedAt)

private fun RoleEntity.toDto() = RoleDto(id, code, name, description, isSystemDefault)
private fun RoleDto.toEntity() = RoleEntity(id, code, name, description, isSystemDefault)

private fun PermissionEntity.toDto() = PermissionDto(id, code, name, module)
private fun PermissionDto.toEntity() = PermissionEntity(id, code, name, module)

private fun UserEntity.toDto() = UserDto(id, businessId, roleId, fullName, username, pinHash, isActive, createdAt)
private fun UserDto.toEntity() = UserEntity(id, businessId, roleId, fullName, username, pinHash, isActive, createdAt)

private fun AppSettingsEntity.toDto() = AppSettingsDto(id, businessId, languageCode, gramsPerPound.str(), allowNegativeStock, defaultLowStockUnits.str(), defaultLowStockGrams.str(), defaultCostMethod, enabledPaymentMethods, backupReminderIntervalDays, updatedAt)
private fun AppSettingsDto.toEntity() = AppSettingsEntity(id, businessId, languageCode, gramsPerPound.bd(), allowNegativeStock, defaultLowStockUnits.bd(), defaultLowStockGrams.bd(), defaultCostMethod, enabledPaymentMethods, backupReminderIntervalDays, updatedAt)

private fun ExchangeRateEntity.toDto() = ExchangeRateDto(id, businessId, currencyCode, currencyName, symbol, rateToBase.str(), isBaseCurrency, isActive, updatedAt)
private fun ExchangeRateDto.toEntity() = ExchangeRateEntity(id, businessId, currencyCode, currencyName, symbol, rateToBase.bd(), isBaseCurrency, isActive, updatedAt)

private fun CategoryEntity.toDto() = CategoryDto(id, businessId, name, description, colorHex, isActive)
private fun CategoryDto.toEntity() = CategoryEntity(id, businessId, name, description, colorHex, isActive)

private fun ProductEntity.toDto() = ProductDto(id, businessId, categoryId, name, sku, modality, baseUnit, stockBaseQty.str(), avgCostPerSaleUnit.str(), salePrice.str(), defaultSaleUnit, lowStockThresholdBase.str(), trackInventory, isActive, createdAt, updatedAt)
private fun ProductDto.toEntity() = ProductEntity(id, businessId, categoryId, name, sku, modality, baseUnit, stockBaseQty.bd(), avgCostPerSaleUnit.bd(), salePrice.bd(), defaultSaleUnit, lowStockThresholdBase.bd(), trackInventory, isActive, createdAt, updatedAt)

private fun ProductPresentationEntity.toDto() = ProductPresentationDto(id, productId, name, baseUnitsPerPresentation.str(), salePrice?.str(), isBuyPresentation, isSellPresentation, isActive)
private fun ProductPresentationDto.toEntity() = ProductPresentationEntity(id, productId, name, baseUnitsPerPresentation.bd(), salePrice?.bd(), isBuyPresentation, isSellPresentation, isActive)

private fun SupplierEntity.toDto() = SupplierDto(id, businessId, name, phone, address, notes, balanceOwedBase.str(), isActive, createdAt)
private fun SupplierDto.toEntity() = SupplierEntity(id, businessId, name, phone, address, notes, balanceOwedBase.bd(), isActive, createdAt)

private fun PurchaseEntity.toDto() = PurchaseDto(id, businessId, supplierId, userId, invoiceNumber, purchaseDate, currencyCode, exchangeRateToBase.str(), subtotalOriginal.str(), discountOriginal.str(), transportCostOriginal.str(), extraExpensesOriginal.str(), totalOriginal.str(), totalConvertedBase.str(), paidAmountBase.str(), pendingDebtBase.str(), paymentMethod, status, notes, createdAt)
private fun PurchaseDto.toEntity() = PurchaseEntity(id, businessId, supplierId, userId, invoiceNumber, purchaseDate, currencyCode, exchangeRateToBase.bd(), subtotalOriginal.bd(), discountOriginal.bd(), transportCostOriginal.bd(), extraExpensesOriginal.bd(), totalOriginal.bd(), totalConvertedBase.bd(), paidAmountBase.bd(), pendingDebtBase.bd(), paymentMethod, status, notes, createdAt)

private fun PurchaseDetailEntity.toDto() = PurchaseDetailDto(id, purchaseId, productId, presentationLabel, enteredQuantity.str(), appliedEquivalence.str(), baseQuantityAdded.str(), unitPriceOriginal.str(), allocatedExtraCostBase.str(), realUnitCostBase.str(), lineTotalBase.str())
private fun PurchaseDetailDto.toEntity() = PurchaseDetailEntity(id, purchaseId, productId, presentationLabel, enteredQuantity.bd(), appliedEquivalence.bd(), baseQuantityAdded.bd(), unitPriceOriginal.bd(), allocatedExtraCostBase.bd(), realUnitCostBase.bd(), lineTotalBase.bd())

private fun CustomerEntity.toDto() = CustomerDto(id, businessId, name, phone, address, notes, debtBalanceBase.str(), isActive, createdAt)
private fun CustomerDto.toEntity() = CustomerEntity(id, businessId, name, phone, address, notes, debtBalanceBase.bd(), isActive, createdAt)

private fun SaleEntity.toDto() = SaleDto(id, businessId, customerId, userId, cashSessionId, receiptNumber, clientRequestId, saleDate, currencyCode, exchangeRateToBase.str(), subtotalBase.str(), discountBase.str(), totalBase.str(), totalOriginal.str(), paidBase.str(), pendingDebtBase.str(), totalCostOfGoodsBase.str(), grossProfitBase.str(), isCreditSale, status, notes, createdAt)
private fun SaleDto.toEntity() = SaleEntity(id, businessId, customerId, userId, cashSessionId, receiptNumber, clientRequestId, saleDate, currencyCode, exchangeRateToBase.bd(), subtotalBase.bd(), discountBase.bd(), totalBase.bd(), totalOriginal.bd(), paidBase.bd(), pendingDebtBase.bd(), totalCostOfGoodsBase.bd(), grossProfitBase.bd(), isCreditSale, status, notes, createdAt)

private fun SaleDetailEntity.toDto() = SaleDetailDto(id, saleId, productId, productNameSnapshot, unitLabel, enteredQuantity.str(), appliedEquivalence.str(), baseQuantityDeducted.str(), returnedBaseQuantity.str(), unitSalePriceBase.str(), historicalUnitCostBase.str(), lineTotalBase.str(), lineCostBase.str(), lineGrossProfitBase.str())
private fun SaleDetailDto.toEntity() = SaleDetailEntity(id, saleId, productId, productNameSnapshot, unitLabel, enteredQuantity.bd(), appliedEquivalence.bd(), baseQuantityDeducted.bd(), returnedBaseQuantity.bd(), unitSalePriceBase.bd(), historicalUnitCostBase.bd(), lineTotalBase.bd(), lineCostBase.bd(), lineGrossProfitBase.bd())

private fun PaymentEntity.toDto() = PaymentDto(id, businessId, userId, referenceType, referenceId, paymentMethod, currencyCode, originalAmount.str(), exchangeRateToBase.str(), convertedAmountBase.str(), isPhysicalCash, paymentDate, note)
private fun PaymentDto.toEntity() = PaymentEntity(id, businessId, userId, referenceType, referenceId, paymentMethod, currencyCode, originalAmount.bd(), exchangeRateToBase.bd(), convertedAmountBase.bd(), isPhysicalCash, paymentDate, note)

private fun DebtEntity.toDto() = DebtDto(id, businessId, partyType, partyId, referenceType, referenceId, currencyCode, originalAmount.str(), exchangeRateToBase.str(), totalBase.str(), paidBase.str(), remainingBase.str(), status, dueDate, createdAt)
private fun DebtDto.toEntity() = DebtEntity(id, businessId, partyType, partyId, referenceType, referenceId, currencyCode, originalAmount.bd(), exchangeRateToBase.bd(), totalBase.bd(), paidBase.bd(), remainingBase.bd(), status, dueDate, createdAt)

private fun ExpenseCategoryEntity.toDto() = ExpenseCategoryDto(id, businessId, name, isSystemDefault, isActive)
private fun ExpenseCategoryDto.toEntity() = ExpenseCategoryEntity(id, businessId, name, isSystemDefault, isActive)

private fun ExpenseEntity.toDto() = ExpenseDto(id, businessId, expenseCategoryId, userId, cashSessionId, description, currencyCode, originalAmount.str(), exchangeRateToBase.str(), convertedAmountBase.str(), paymentMethod, paidFromCashDrawer, expenseDate, status, createdAt)
private fun ExpenseDto.toEntity() = ExpenseEntity(id, businessId, expenseCategoryId, userId, cashSessionId, description, currencyCode, originalAmount.bd(), exchangeRateToBase.bd(), convertedAmountBase.bd(), paymentMethod, paidFromCashDrawer, expenseDate, status, createdAt)

private fun CashSessionEntity.toDto() = CashSessionDto(id, businessId, openedByUserId, closedByUserId, openedAt, closedAt, openingCashBase.str(), expectedCashBase.str(), countedCashBase?.str(), differenceBase?.str(), totalCashSalesBase.str(), totalTransferSalesBase.str(), totalCashInflowsBase.str(), totalCashExpensesBase.str(), totalOwnerWithdrawalsBase.str(), status, notes)
private fun CashSessionDto.toEntity() = CashSessionEntity(id, businessId, openedByUserId, closedByUserId, openedAt, closedAt, openingCashBase.bd(), expectedCashBase.bd(), countedCashBase?.bd(), differenceBase?.bd(), totalCashSalesBase.bd(), totalTransferSalesBase.bd(), totalCashInflowsBase.bd(), totalCashExpensesBase.bd(), totalOwnerWithdrawalsBase.bd(), status, notes)

private fun CashMovementEntity.toDto() = CashMovementDto(id, businessId, cashSessionId, userId, movementType, affectsPhysicalCash, currencyCode, originalAmount.str(), exchangeRateToBase.str(), signedAmountBase.str(), description, referenceType, referenceId, timestamp)
private fun CashMovementDto.toEntity() = CashMovementEntity(id, businessId, cashSessionId, userId, movementType, affectsPhysicalCash, currencyCode, originalAmount.bd(), exchangeRateToBase.bd(), signedAmountBase.bd(), description, referenceType, referenceId, timestamp)

private fun InventoryMovementEntity.toDto() = InventoryMovementDto(id, businessId, productId, userId, movementType, quantityBaseDelta.str(), stockAfterBase.str(), enteredQuantity.str(), enteredUnitLabel, appliedEquivalence.str(), unitCostSnapshot.str(), referenceType, referenceId, note, timestamp)
private fun InventoryMovementDto.toEntity() = InventoryMovementEntity(id, businessId, productId, userId, movementType, quantityBaseDelta.bd(), stockAfterBase.bd(), enteredQuantity.bd(), enteredUnitLabel, appliedEquivalence.bd(), unitCostSnapshot.bd(), referenceType, referenceId, note, timestamp)

private fun InventoryAdjustmentEntity.toDto() = InventoryAdjustmentDto(id, businessId, productId, userId, previousStockBase.str(), newStockBase.str(), deltaBase.str(), reason, timestamp)
private fun InventoryAdjustmentDto.toEntity() = InventoryAdjustmentEntity(id, businessId, productId, userId, previousStockBase.bd(), newStockBase.bd(), deltaBase.bd(), reason, timestamp)

private fun AuditLogEntity.toDto() = AuditLogDto(id, businessId, userId, action, entityType, entityId, details, timestamp)
private fun AuditLogDto.toEntity() = AuditLogEntity(id, businessId, userId, action, entityType, entityId, details, timestamp)
