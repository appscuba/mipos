package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AppSettingsEntity
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.local.db.entity.PermissionEntity
import com.example.data.local.db.entity.ProductEntity
import com.example.data.local.db.entity.PurchaseEntity
import com.example.data.local.db.entity.RoleEntity
import com.example.data.local.db.entity.RolePermissionCrossRef
import com.example.data.local.db.entity.SupplierEntity
import com.example.data.local.db.entity.UserEntity
import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.math.BigDecimal

data class InitialBusinessSetupRequest(
  val businessName: String,
  val businessType: String,
  val logoUri: String?,
  val phone: String,
  val email: String,
  val address: String,
  val primaryCurrency: String,
  val secondaryCurrencyCode: String?,
  val secondaryCurrencyRate: BigDecimal?,
  val gramsPerPound: BigDecimal,
  val allowNegativeStock: Boolean,
)

data class BusinessWorkspaceSnapshot(
  val business: BusinessEntity?,
  val settings: AppSettingsEntity?,
  val exchangeRates: List<ExchangeRateEntity>,
  val activeProductCount: Int,
  val confirmedSaleCount: Int,
  val categoriesCount: Int,
  val expenseCategoriesCount: Int,
  val lowStockCount: Int = 0,
  val outOfStockCount: Int = 0,
  val inventoryValueBase: BigDecimal = MoneyMath.ZERO,
  val confirmedPurchaseCount: Int = 0,
  val supplierPayableBase: BigDecimal = MoneyMath.ZERO,
)

class BusinessRepository(
  private val db: AppDatabase,
  private val preferences: AppPreferencesDataStore,
) {
  private val businessConfigDao = db.businessConfigDao()
  private val securityDao = db.securityDao()
  private val catalogDao = db.catalogDao()
  private val inventoryDao = db.inventoryDao()
  private val purchaseDao = db.purchaseDao()

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeWorkspaceSnapshot(): Flow<BusinessWorkspaceSnapshot> =
    businessConfigDao.observeActiveBusiness().flatMapLatest { business ->
      if (business == null) {
        flowOf(
          BusinessWorkspaceSnapshot(
            business = null,
            settings = null,
            exchangeRates = emptyList(),
            activeProductCount = 0,
            confirmedSaleCount = 0,
            categoriesCount = 0,
            expenseCategoriesCount = 0,
          ),
        )
      } else {
        combine(
          businessConfigDao.observeSettings(business.id),
          businessConfigDao.observeExchangeRates(business.id),
          inventoryDao.observeActiveProducts(business.id),
          catalogDao.observeConfirmedSaleCount(business.id),
          catalogDao.observeCategories(business.id),
          catalogDao.observeExpenseCategories(business.id),
          purchaseDao.observePurchases(business.id),
          purchaseDao.observeActiveSuppliers(business.id),
        ) { values ->
          val settings = values[0] as AppSettingsEntity?
          @Suppress("UNCHECKED_CAST")
          val rates = values[1] as List<ExchangeRateEntity>
          @Suppress("UNCHECKED_CAST")
          val products = values[2] as List<ProductEntity>
          val saleCount = values[3] as Int
          @Suppress("UNCHECKED_CAST")
          val categories = values[4] as List<CategoryEntity>
          @Suppress("UNCHECKED_CAST")
          val expenseCategories = values[5] as List<ExpenseCategoryEntity>
          @Suppress("UNCHECKED_CAST")
          val purchases = values[6] as List<PurchaseEntity>
          @Suppress("UNCHECKED_CAST")
          val suppliers = values[7] as List<SupplierEntity>

          val gramsPerPound = settings?.gramsPerPound ?: UnitConverter.DEFAULT_GRAMS_PER_POUND
          var lowCount = 0
          var outCount = 0
          var invValue = MoneyMath.ZERO
          for (p in products) {
            if (p.trackInventory && p.stockBaseQty <= BigDecimal.ZERO) {
              outCount++
            } else if (
              p.trackInventory &&
              p.lowStockThresholdBase > BigDecimal.ZERO &&
              p.stockBaseQty <= p.lowStockThresholdBase
            ) {
              lowCount++
            }
            invValue = MoneyMath.add(
              invValue,
              InventoryRepository.computeProductInventoryValue(p, gramsPerPound),
            )
          }

          val confirmedPurchases = purchases.count { it.status == "CONFIRMED" }
          val supplierDebt = suppliers.fold(MoneyMath.ZERO) { acc, s ->
            MoneyMath.add(acc, s.balanceOwedBase)
          }

          BusinessWorkspaceSnapshot(
            business = business,
            settings = settings,
            exchangeRates = rates,
            activeProductCount = products.size,
            confirmedSaleCount = saleCount,
            categoriesCount = categories.size,
            expenseCategoriesCount = expenseCategories.size,
            lowStockCount = lowCount,
            outOfStockCount = outCount,
            inventoryValueBase = invValue,
            confirmedPurchaseCount = confirmedPurchases,
            supplierPayableBase = supplierDebt,
          )
        }
      }
    }

  suspend fun completeInitialSetup(request: InitialBusinessSetupRequest): Long {
    val result = db.withTransaction {
      seedSystemRolesAndPermissions()
      val ownerRole = securityDao.getRoleByCode("OWNER")
        ?: error("Owner role could not be seeded")

      val existing = businessConfigDao.getActiveBusiness()
      val businessId = if (existing == null) {
        businessConfigDao.insertBusiness(
          BusinessEntity(
            name = request.businessName.trim(),
            businessType = request.businessType.trim(),
            logoUri = request.logoUri,
            phone = request.phone.trim(),
            email = request.email.trim(),
            address = request.address.trim(),
            primaryCurrency = request.primaryCurrency.trim().uppercase(),
            isActive = true,
          ),
        )
      } else {
        val updated = existing.copy(
          name = request.businessName.trim(),
          businessType = request.businessType.trim(),
          logoUri = request.logoUri,
          phone = request.phone.trim(),
          email = request.email.trim(),
          address = request.address.trim(),
          primaryCurrency = request.primaryCurrency.trim().uppercase(),
          updatedAt = System.currentTimeMillis(),
        )
        businessConfigDao.updateBusiness(updated)
        updated.id
      }

      val existingSettings = businessConfigDao.getSettings(businessId)
      businessConfigDao.insertOrUpdateSettings(
        (existingSettings ?: AppSettingsEntity(businessId = businessId)).copy(
          languageCode = "es",
          gramsPerPound = request.gramsPerPound,
          allowNegativeStock = request.allowNegativeStock,
          updatedAt = System.currentTimeMillis(),
        ),
      )

      val baseCode = request.primaryCurrency.trim().uppercase()
      businessConfigDao.clearBaseCurrencyFlag(businessId)
      val existingBaseRate = businessConfigDao.getExchangeRateByCode(businessId, baseCode)
      businessConfigDao.upsertExchangeRate(
        (existingBaseRate ?: ExchangeRateEntity(
          businessId = businessId,
          currencyCode = baseCode,
          currencyName = currencyLabelFor(baseCode),
          symbol = symbolFor(baseCode),
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
        )).copy(
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
          isActive = true,
          updatedAt = System.currentTimeMillis(),
        ),
      )

      val secCode = request.secondaryCurrencyCode?.trim()?.uppercase().orEmpty()
      val secRate = request.secondaryCurrencyRate
      if (secCode.isNotEmpty() && secCode != baseCode && secRate != null && secRate > BigDecimal.ZERO) {
        val existingSecondary = businessConfigDao.getExchangeRateByCode(businessId, secCode)
        businessConfigDao.upsertExchangeRate(
          (existingSecondary ?: ExchangeRateEntity(
            businessId = businessId,
            currencyCode = secCode,
            currencyName = currencyLabelFor(secCode),
            symbol = symbolFor(secCode),
            rateToBase = secRate,
            isBaseCurrency = false,
          )).copy(
            rateToBase = secRate,
            isBaseCurrency = false,
            isActive = true,
            updatedAt = System.currentTimeMillis(),
          ),
        )
      }

      val userId = if (securityDao.countUsersForBusiness(businessId) == 0) {
        securityDao.insertUser(
          UserEntity(
            businessId = businessId,
            roleId = ownerRole.id,
            fullName = "Propietario",
            username = "propietario",
            pinHash = null,
            isActive = true,
          ),
        )
      } else {
        1L
      }

      if (catalogDao.countCategories(businessId) == 0) {
        catalogDao.insertCategories(
          listOf(
            CategoryEntity(businessId = businessId, name = "Alimentos", colorHex = "#FFA510"),
            CategoryEntity(businessId = businessId, name = "Granos y Víveres", colorHex = "#1D2B50"),
            CategoryEntity(businessId = businessId, name = "Bebidas", colorHex = "#38BDF8"),
            CategoryEntity(businessId = businessId, name = "Aseo y Hogar", colorHex = "#22C55E"),
            CategoryEntity(businessId = businessId, name = "Otros", colorHex = "#94A3C2"),
          ),
        )
      }

      if (catalogDao.countExpenseCategories(businessId) == 0) {
        catalogDao.insertExpenseCategories(
          listOf(
            ExpenseCategoryEntity(businessId = businessId, name = "Transporte", isSystemDefault = true),
            ExpenseCategoryEntity(businessId = businessId, name = "Electricidad", isSystemDefault = true),
            ExpenseCategoryEntity(businessId = businessId, name = "Salarios", isSystemDefault = true),
            ExpenseCategoryEntity(businessId = businessId, name = "Alquiler", isSystemDefault = true),
            ExpenseCategoryEntity(businessId = businessId, name = "Comunicaciones", isSystemDefault = true),
            ExpenseCategoryEntity(businessId = businessId, name = "Mantenimiento", isSystemDefault = true),
            ExpenseCategoryEntity(businessId = businessId, name = "Otros", isSystemDefault = true),
          ),
        )
      }

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "BUSINESS_CONFIGURED",
          entityType = "BUSINESS",
          entityId = businessId,
          details = "Negocio '${request.businessName.trim()}' configurado ($baseCode, 1 lb = ${request.gramsPerPound.stripTrailingZeros().toPlainString()} g)",
        ),
      )

      businessId to userId
    }

    preferences.markOnboardingCompleted(businessId = result.first, userId = result.second)
    return result.first
  }

  suspend fun updateBusinessSettings(
    businessId: Long,
    businessName: String,
    businessType: String,
    logoUri: String?,
    phone: String,
    email: String,
    address: String,
    primaryCurrency: String,
    gramsPerPound: BigDecimal,
    allowNegativeStock: Boolean,
  ) {
    db.withTransaction {
      val currentBusiness = businessConfigDao.getBusinessById(businessId)
        ?: error("Negocio no encontrado")
      val normalizedBase = primaryCurrency.trim().uppercase()
      businessConfigDao.updateBusiness(
        currentBusiness.copy(
          name = businessName.trim(),
          businessType = businessType.trim(),
          logoUri = logoUri,
          phone = phone.trim(),
          email = email.trim(),
          address = address.trim(),
          primaryCurrency = normalizedBase,
          updatedAt = System.currentTimeMillis(),
        ),
      )

      val currentSettings = businessConfigDao.getSettings(businessId)
        ?: AppSettingsEntity(businessId = businessId)
      businessConfigDao.insertOrUpdateSettings(
        currentSettings.copy(
          gramsPerPound = gramsPerPound,
          allowNegativeStock = allowNegativeStock,
          updatedAt = System.currentTimeMillis(),
        ),
      )

      businessConfigDao.clearBaseCurrencyFlag(businessId)
      val baseRate = businessConfigDao.getExchangeRateByCode(businessId, normalizedBase)
      businessConfigDao.upsertExchangeRate(
        (baseRate ?: ExchangeRateEntity(
          businessId = businessId,
          currencyCode = normalizedBase,
          currencyName = currencyLabelFor(normalizedBase),
          symbol = symbolFor(normalizedBase),
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
        )).copy(
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
          isActive = true,
          updatedAt = System.currentTimeMillis(),
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          action = "SETTINGS_UPDATED",
          entityType = "BUSINESS",
          entityId = businessId,
          details = "Configuración actualizada: $businessName | $normalizedBase | 1 lb = ${gramsPerPound.stripTrailingZeros().toPlainString()} g",
        ),
      )
    }
  }

  fun observeAllExchangeRates(businessId: Long): Flow<List<ExchangeRateEntity>> =
    businessConfigDao.observeAllExchangeRates(businessId)

  suspend fun saveCustomExchangeRate(
    businessId: Long,
    currencyCode: String,
    currencyName: String,
    symbol: String,
    rateToBase: BigDecimal,
    userId: Long? = null,
  ) {
    val cleanCode = currencyCode.trim().uppercase()
    val cleanName = currencyName.trim().ifEmpty { currencyLabelFor(cleanCode) }
    val cleanSymbol = symbol.trim().ifEmpty { symbolFor(cleanCode) }
    require(cleanCode.length in 2..6) { "El código de moneda debe tener entre 2 y 6 letras (ej. USD, EUR)." }
    require(rateToBase > BigDecimal.ZERO) { "La tasa de cambio debe ser mayor que cero." }

    db.withTransaction {
      val business = businessConfigDao.getBusinessById(businessId)
        ?: error("Negocio no encontrado.")
      val isBase = business.primaryCurrency.equals(cleanCode, ignoreCase = true)
      val normalizedRate = if (isBase) BigDecimal("1.0000") else rateToBase.setScale(MoneyMath.RATE_SCALE, java.math.RoundingMode.HALF_UP)

      val existing = businessConfigDao.getExchangeRateByCode(businessId, cleanCode)
      businessConfigDao.upsertExchangeRate(
        (existing ?: ExchangeRateEntity(
          businessId = businessId,
          currencyCode = cleanCode,
          currencyName = cleanName,
          symbol = cleanSymbol,
          rateToBase = normalizedRate,
          isBaseCurrency = isBase,
        )).copy(
          currencyName = cleanName,
          symbol = cleanSymbol,
          rateToBase = normalizedRate,
          isBaseCurrency = isBase,
          isActive = true,
          updatedAt = System.currentTimeMillis(),
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "EXCHANGE_RATE_UPDATED",
          entityType = "EXCHANGE_RATE",
          details = "Moneda $cleanCode ($cleanName): 1 $cleanCode = $normalizedRate ${business.primaryCurrency}",
        ),
      )
    }
  }

  suspend fun setExchangeRateActive(
    businessId: Long,
    currencyCode: String,
    isActive: Boolean,
    userId: Long? = null,
  ) {
    val cleanCode = currencyCode.trim().uppercase()
    db.withTransaction {
      val business = businessConfigDao.getBusinessById(businessId)
        ?: error("Negocio no encontrado.")
      require(!business.primaryCurrency.equals(cleanCode, ignoreCase = true) || isActive) {
        "No puedes desactivar la moneda principal del negocio ($cleanCode)."
      }
      businessConfigDao.setExchangeRateActive(businessId, cleanCode, isActive)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = if (isActive) "EXCHANGE_RATE_ENABLED" else "EXCHANGE_RATE_DISABLED",
          entityType = "EXCHANGE_RATE",
          details = "Moneda $cleanCode ${if (isActive) "activada" else "desactivada"}",
        ),
      )
    }
  }

  suspend fun resetAllSystemData() {
    withContext(Dispatchers.IO) {
      db.clearAllTables()
    }
    preferences.resetAllWorkspaceData()
  }

  private suspend fun seedSystemRolesAndPermissions() {
    val ownerRoleId = securityDao.getRoleByCode("OWNER")?.id
      ?: securityDao.insertRole(
        RoleEntity(
          code = "OWNER",
          name = "Propietario",
          description = "Acceso completo al negocio, costos, ganancias, configuración y respaldos",
          isSystemDefault = true,
        ),
      )

    val clerkRoleId = securityDao.getRoleByCode("CLERK")?.id
      ?: securityDao.insertRole(
        RoleEntity(
          code = "CLERK",
          name = "Dependiente",
          description = "Punto de venta, consulta de productos, cobros e historial propio",
          isSystemDefault = true,
        ),
      )

    val allPermissions = listOf(
      Triple("POS_SELL", "Realizar ventas", "POS"),
      Triple("POS_DISCOUNT", "Aplicar descuentos autorizados", "POS"),
      Triple("POS_ANNUL", "Anular o devolver ventas", "POS"),
      Triple("PRODUCTS_MANAGE", "Gestionar productos e inventario", "INVENTORY"),
      Triple("PURCHASES_MANAGE", "Registrar compras y proveedores", "PURCHASES"),
      Triple("FINANCE_VIEW", "Ver finanzas y gastos", "FINANCE"),
      Triple("CASH_CLOSE", "Abrir y cerrar caja", "CASH"),
      Triple("REPORTS_COST_PROFIT", "Ver costos y ganancias", "REPORTS"),
      Triple("SETTINGS_MANAGE", "Configurar negocio y monedas", "SETTINGS"),
      Triple("USERS_MANAGE", "Administrar usuarios y auditoría", "SECURITY"),
      Triple("BACKUPS_MANAGE", "Exportar y restaurar respaldos", "BACKUP"),
    )

    val clerkPermissionCodes = setOf("POS_SELL", "CASH_CLOSE")

    for ((code, name, module) in allPermissions) {
      val existingPerm = securityDao.getPermissionByCode(code)
      val permId = existingPerm?.id ?: securityDao.insertPermission(
        PermissionEntity(code = code, name = name, module = module),
      )
      securityDao.insertRolePermission(RolePermissionCrossRef(roleId = ownerRoleId, permissionId = permId))
      if (code in clerkPermissionCodes) {
        securityDao.insertRolePermission(RolePermissionCrossRef(roleId = clerkRoleId, permissionId = permId))
      }
    }
  }

  private fun currencyLabelFor(code: String): String = when (code) {
    "CUP" -> "Peso Cubano"
    "USD" -> "Dólar Estadounidense"
    "EUR" -> "Euro"
    "MLC" -> "Moneda Libremente Convertible"
    else -> code
  }

  private fun symbolFor(code: String): String = when (code) {
    "CUP" -> "$"
    "USD" -> "US$"
    "EUR" -> "€"
    "MLC" -> "MLC"
    else -> code
  }
}
