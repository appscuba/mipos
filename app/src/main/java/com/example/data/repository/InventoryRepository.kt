package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.InventoryAdjustmentEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.local.db.entity.ProductEntity
import com.example.data.local.db.entity.ProductPresentationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode

enum class ProductModality(val code: String, val label: String) {
  WEIGHT("WEIGHT", "Por Peso"),
  UNIT("UNIT", "Por Unidades"),
  PRESENTATION("PRESENTATION", "Por Presentaciones"),
  ;

  companion object {
    fun fromCode(code: String): ProductModality =
      entries.firstOrNull { it.code == code } ?: UNIT
  }
}

enum class StockAdjustmentMode(val label: String) {
  ADD("Entrada (+)"),
  SUBTRACT("Salida / Merma (-)"),
  SET_EXACT("Conteo físico (=)"),
}

data class PresentationDraft(
  val id: Long = 0L,
  val name: String,
  val baseUnitsPerPresentation: BigDecimal,
  val salePrice: BigDecimal? = null,
  val isBuyPresentation: Boolean = true,
  val isSellPresentation: Boolean = true,
)

data class SaveProductRequest(
  val productId: Long? = null,
  val businessId: Long,
  val categoryId: Long?,
  val name: String,
  val sku: String,
  val barcode: String,
  val modality: ProductModality,
  val defaultSaleUnit: String,
  val salePrice: BigDecimal,
  val avgCostPerSaleUnit: BigDecimal,
  val initialStockEnteredQty: BigDecimal = BigDecimal.ZERO,
  val initialStockUnitLabel: String = "u",
  val initialStockFactorToBase: BigDecimal = BigDecimal.ONE,
  val lowStockThresholdBase: BigDecimal = BigDecimal.ZERO,
  val trackInventory: Boolean = true,
  val presentations: List<PresentationDraft> = emptyList(),
  val userId: Long? = null,
)

data class StockAdjustmentRequest(
  val businessId: Long,
  val productId: Long,
  val userId: Long? = null,
  val mode: StockAdjustmentMode,
  val enteredQuantity: BigDecimal,
  val unitLabel: String,
  val conversionFactorToBase: BigDecimal,
  val reason: String,
)

data class ProductWithDetails(
  val product: ProductEntity,
  val category: CategoryEntity?,
  val presentations: List<ProductPresentationEntity>,
) {
  val isOutOfStock: Boolean
    get() = product.trackInventory && product.stockBaseQty <= BigDecimal.ZERO

  val isLowStock: Boolean
    get() = product.trackInventory &&
      product.stockBaseQty > BigDecimal.ZERO &&
      product.lowStockThresholdBase > BigDecimal.ZERO &&
      product.stockBaseQty <= product.lowStockThresholdBase
}

data class InventoryOverview(
  val items: List<ProductWithDetails>,
  val categories: List<CategoryEntity>,
  val gramsPerPound: BigDecimal,
  val primaryCurrency: String,
  val totalInventoryValueBase: BigDecimal,
  val lowStockCount: Int,
  val outOfStockCount: Int,
)

class InventoryRepository(private val db: AppDatabase) {
  private val inventoryDao = db.inventoryDao()
  private val businessConfigDao = db.businessConfigDao()
  private val securityDao = db.securityDao()

  fun observeInventoryOverview(businessId: Long): Flow<InventoryOverview> = combine(
    inventoryDao.observeActiveProducts(businessId),
    inventoryDao.observeActiveCategories(businessId),
    inventoryDao.observeActivePresentationsForBusiness(businessId),
    businessConfigDao.observeSettings(businessId),
    businessConfigDao.observeActiveBusiness(),
  ) { products, categories, presentations, settings, business ->
    val categoryMap = categories.associateBy { it.id }
    val presentationsByProduct = presentations.groupBy { it.productId }
    val gramsPerPound = settings?.gramsPerPound ?: UnitConverter.DEFAULT_GRAMS_PER_POUND
    val currency = business?.primaryCurrency ?: "CUP"

    val items = products.map { product ->
      ProductWithDetails(
        product = product,
        category = product.categoryId?.let { categoryMap[it] },
        presentations = presentationsByProduct[product.id].orEmpty(),
      )
    }

    var totalValue = MoneyMath.ZERO
    var lowCount = 0
    var outCount = 0
    for (item in items) {
      if (item.isOutOfStock) outCount++
      else if (item.isLowStock) lowCount++
      totalValue = MoneyMath.add(totalValue, computeProductInventoryValue(item.product, gramsPerPound))
    }

    InventoryOverview(
      items = items,
      categories = categories,
      gramsPerPound = gramsPerPound,
      primaryCurrency = currency,
      totalInventoryValueBase = totalValue,
      lowStockCount = lowCount,
      outOfStockCount = outCount,
    )
  }

  fun observeCategories(businessId: Long): Flow<List<CategoryEntity>> =
    inventoryDao.observeActiveCategories(businessId)

  fun observeProductDetail(productId: Long): Flow<ProductWithDetails?> = combine(
    inventoryDao.observeProductById(productId),
    inventoryDao.observePresentationsForProduct(productId),
  ) { product, presentations ->
    if (product == null) null
    else {
      val category = product.categoryId?.let { inventoryDao.getCategoryById(it) }
      ProductWithDetails(
        product = product,
        category = category,
        presentations = presentations,
      )
    }
  }

  fun observeProductMovements(productId: Long): Flow<List<InventoryMovementEntity>> =
    inventoryDao.observeMovementsForProduct(productId)

  fun observeProductAdjustments(productId: Long): Flow<List<InventoryAdjustmentEntity>> =
    inventoryDao.observeAdjustmentsForProduct(productId)

  suspend fun saveCategory(
    businessId: Long,
    categoryId: Long? = null,
    name: String,
    description: String = "",
    colorHex: String = "#FFA510",
  ): Long {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "El nombre de la categoría es obligatorio." }

    return db.withTransaction {
      val existingByName = inventoryDao.getCategoryByName(businessId, trimmed)
      if (existingByName != null && existingByName.id != (categoryId ?: 0L)) {
        if (!existingByName.isActive) {
          val reactivated = existingByName.copy(
            name = trimmed,
            description = description.trim(),
            colorHex = colorHex,
            isActive = true,
          )
          inventoryDao.updateCategory(reactivated)
          return@withTransaction reactivated.id
        }
        error("Ya existe una categoría con el nombre '$trimmed'.")
      }

      if (categoryId == null || categoryId <= 0L) {
        val newId = inventoryDao.insertCategory(
          CategoryEntity(
            businessId = businessId,
            name = trimmed,
            description = description.trim(),
            colorHex = colorHex,
            isActive = true,
          ),
        )
        securityDao.insertAuditLog(
          AuditLogEntity(
            businessId = businessId,
            action = "CATEGORY_CREATED",
            entityType = "CATEGORY",
            entityId = newId,
            details = "Categoría creada: $trimmed",
          ),
        )
        newId
      } else {
        val current = inventoryDao.getCategoryById(categoryId)
          ?: error("Categoría no encontrada.")
        inventoryDao.updateCategory(
          current.copy(
            name = trimmed,
            description = description.trim(),
            colorHex = colorHex,
          ),
        )
        categoryId
      }
    }
  }

  suspend fun archiveCategory(businessId: Long, categoryId: Long) {
    db.withTransaction {
      val category = inventoryDao.getCategoryById(categoryId) ?: return@withTransaction
      inventoryDao.deactivateCategory(categoryId)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          action = "CATEGORY_ARCHIVED",
          entityType = "CATEGORY",
          entityId = categoryId,
          details = "Categoría desactivada: ${category.name}",
        ),
      )
    }
  }

  suspend fun saveProduct(request: SaveProductRequest): Long {
    val cleanName = request.name.trim()
    require(cleanName.isNotEmpty()) { "El nombre del producto es obligatorio." }
    require(request.salePrice >= BigDecimal.ZERO) { "El precio de venta no puede ser negativo." }
    require(request.avgCostPerSaleUnit >= BigDecimal.ZERO) { "El costo no puede ser negativo." }

    val baseUnit = if (request.modality == ProductModality.WEIGHT) "g" else "u"
    val now = System.currentTimeMillis()

    return db.withTransaction {
      if (request.productId == null || request.productId <= 0L) {
        val initialBaseStock = if (request.initialStockEnteredQty > BigDecimal.ZERO) {
          UnitConverter.toBaseUnits(
            enteredQuantity = request.initialStockEnteredQty,
            conversionFactorToBase = request.initialStockFactorToBase,
          )
        } else {
          BigDecimal.ZERO.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
        }

        val productId = inventoryDao.insertProduct(
          ProductEntity(
            businessId = request.businessId,
            categoryId = request.categoryId,
            name = cleanName,
            sku = request.sku.trim(),
            barcode = request.barcode.trim(),
            modality = request.modality.code,
            baseUnit = baseUnit,
            stockBaseQty = initialBaseStock,
            avgCostPerSaleUnit = request.avgCostPerSaleUnit.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            salePrice = request.salePrice.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            defaultSaleUnit = request.defaultSaleUnit,
            lowStockThresholdBase = request.lowStockThresholdBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            trackInventory = request.trackInventory,
            isActive = true,
            createdAt = now,
            updatedAt = now,
          ),
        )

        request.presentations.forEach { draft ->
          if (draft.name.isNotBlank() && draft.baseUnitsPerPresentation > BigDecimal.ZERO) {
            inventoryDao.insertPresentation(
              ProductPresentationEntity(
                productId = productId,
                name = draft.name.trim(),
                baseUnitsPerPresentation = draft.baseUnitsPerPresentation.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
                salePrice = draft.salePrice?.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
                isBuyPresentation = draft.isBuyPresentation,
                isSellPresentation = draft.isSellPresentation,
                isActive = true,
              ),
            )
          }
        }

        if (initialBaseStock > BigDecimal.ZERO) {
          inventoryDao.insertMovement(
            InventoryMovementEntity(
              businessId = request.businessId,
              productId = productId,
              userId = request.userId,
              movementType = "INITIAL_STOCK",
              quantityBaseDelta = initialBaseStock,
              stockAfterBase = initialBaseStock,
              enteredQuantity = request.initialStockEnteredQty.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
              enteredUnitLabel = request.initialStockUnitLabel,
              appliedEquivalence = request.initialStockFactorToBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
              unitCostSnapshot = request.avgCostPerSaleUnit.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
              referenceType = "INITIAL",
              referenceId = productId,
              note = "Inventario inicial al crear producto",
              timestamp = now,
            ),
          )
        }

        securityDao.insertAuditLog(
          AuditLogEntity(
            businessId = request.businessId,
            userId = request.userId,
            action = "PRODUCT_CREATED",
            entityType = "PRODUCT",
            entityId = productId,
            details = "Producto creado: $cleanName (${request.modality.label})",
          ),
        )

        productId
      } else {
        val current = inventoryDao.getProductById(request.productId)
          ?: error("Producto no encontrado.")
        inventoryDao.updateProduct(
          current.copy(
            categoryId = request.categoryId,
            name = cleanName,
            sku = request.sku.trim(),
            barcode = request.barcode.trim(),
            modality = request.modality.code,
            baseUnit = baseUnit,
            defaultSaleUnit = request.defaultSaleUnit,
            salePrice = request.salePrice.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            avgCostPerSaleUnit = request.avgCostPerSaleUnit.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
            lowStockThresholdBase = request.lowStockThresholdBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
            trackInventory = request.trackInventory,
            updatedAt = now,
          ),
        )

        val existingPres = inventoryDao.getPresentationsForProduct(request.productId)
        val keptIds = request.presentations.mapNotNull { if (it.id > 0L) it.id else null }.toSet()
        existingPres.forEach { old ->
          if (old.id !in keptIds) {
            inventoryDao.deactivatePresentation(old.id)
          }
        }
        request.presentations.forEach { draft ->
          if (draft.name.isNotBlank() && draft.baseUnitsPerPresentation > BigDecimal.ZERO) {
            inventoryDao.insertPresentation(
              ProductPresentationEntity(
                id = draft.id,
                productId = request.productId,
                name = draft.name.trim(),
                baseUnitsPerPresentation = draft.baseUnitsPerPresentation.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
                salePrice = draft.salePrice?.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
                isBuyPresentation = draft.isBuyPresentation,
                isSellPresentation = draft.isSellPresentation,
                isActive = true,
              ),
            )
          }
        }

        securityDao.insertAuditLog(
          AuditLogEntity(
            businessId = request.businessId,
            userId = request.userId,
            action = "PRODUCT_UPDATED",
            entityType = "PRODUCT",
            entityId = request.productId,
            details = "Producto actualizado: $cleanName",
          ),
        )

        request.productId
      }
    }
  }

  suspend fun savePresentation(
    businessId: Long,
    productId: Long,
    presentationId: Long = 0L,
    name: String,
    baseUnitsPerPresentation: BigDecimal,
    salePrice: BigDecimal?,
    isBuyPresentation: Boolean,
    isSellPresentation: Boolean,
  ): Long {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "El nombre de la presentación es obligatorio." }
    require(baseUnitsPerPresentation > BigDecimal.ZERO) { "La equivalencia debe ser mayor que cero." }

    return db.withTransaction {
      val id = inventoryDao.insertPresentation(
        ProductPresentationEntity(
          id = presentationId,
          productId = productId,
          name = trimmed,
          baseUnitsPerPresentation = baseUnitsPerPresentation.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
          salePrice = salePrice?.setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP),
          isBuyPresentation = isBuyPresentation,
          isSellPresentation = isSellPresentation,
          isActive = true,
        ),
      )
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          action = "PRESENTATION_SAVED",
          entityType = "PRODUCT_PRESENTATION",
          entityId = id,
          details = "Presentación '$trimmed' (${MoneyMath.formatQuantity(baseUnitsPerPresentation)} base)",
        ),
      )
      id
    }
  }

  suspend fun deactivatePresentation(businessId: Long, presentationId: Long) {
    db.withTransaction {
      val pres = inventoryDao.getPresentationById(presentationId) ?: return@withTransaction
      inventoryDao.deactivatePresentation(presentationId)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          action = "PRESENTATION_ARCHIVED",
          entityType = "PRODUCT_PRESENTATION",
          entityId = presentationId,
          details = "Presentación desactivada: ${pres.name}",
        ),
      )
    }
  }

  suspend fun recordStockAdjustment(request: StockAdjustmentRequest) {
    require(request.enteredQuantity >= BigDecimal.ZERO) { "La cantidad no puede ser negativa." }
    if (request.mode != StockAdjustmentMode.SET_EXACT) {
      require(request.enteredQuantity > BigDecimal.ZERO) { "La cantidad debe ser mayor que cero." }
    }
    require(request.conversionFactorToBase > BigDecimal.ZERO) { "La equivalencia aplicada debe ser positiva." }
    val reason = request.reason.trim().ifEmpty { "Ajuste manual de inventario" }
    val now = System.currentTimeMillis()

    db.withTransaction {
      val product = inventoryDao.getProductById(request.productId)
        ?: error("Producto no encontrado.")
      val settings = businessConfigDao.getSettings(request.businessId)
      val allowNegative = settings?.allowNegativeStock ?: false

      val convertedBaseQty = UnitConverter.toBaseUnits(
        enteredQuantity = request.enteredQuantity,
        conversionFactorToBase = request.conversionFactorToBase,
      )

      val previousStock = product.stockBaseQty
      val newStock = when (request.mode) {
        StockAdjustmentMode.ADD -> previousStock.add(convertedBaseQty)
        StockAdjustmentMode.SUBTRACT -> previousStock.subtract(convertedBaseQty)
        StockAdjustmentMode.SET_EXACT -> convertedBaseQty
      }.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)

      if (!allowNegative && newStock < BigDecimal.ZERO) {
        error("Stock insuficiente. Disponible: ${MoneyMath.formatQuantity(previousStock)} ${product.baseUnit}.")
      }

      val deltaBase = newStock.subtract(previousStock).setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP)
      if (deltaBase.compareTo(BigDecimal.ZERO) == 0) return@withTransaction

      inventoryDao.updateProductStock(
        productId = product.id,
        newStockBase = newStock,
        updatedAt = now,
      )

      val adjustmentId = inventoryDao.insertAdjustment(
        InventoryAdjustmentEntity(
          businessId = request.businessId,
          productId = product.id,
          userId = request.userId,
          previousStockBase = previousStock,
          newStockBase = newStock,
          deltaBase = deltaBase,
          reason = reason,
          timestamp = now,
        ),
      )

      val movementType = if (deltaBase >= BigDecimal.ZERO) "ADJUSTMENT_IN" else "ADJUSTMENT_OUT"
      inventoryDao.insertMovement(
        InventoryMovementEntity(
          businessId = request.businessId,
          productId = product.id,
          userId = request.userId,
          movementType = movementType,
          quantityBaseDelta = deltaBase,
          stockAfterBase = newStock,
          enteredQuantity = request.enteredQuantity.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
          enteredUnitLabel = request.unitLabel,
          appliedEquivalence = request.conversionFactorToBase.setScale(MoneyMath.QUANTITY_SCALE, RoundingMode.HALF_UP),
          unitCostSnapshot = product.avgCostPerSaleUnit,
          referenceType = "ADJUSTMENT",
          referenceId = adjustmentId,
          note = reason,
          timestamp = now,
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = request.businessId,
          userId = request.userId,
          action = movementType,
          entityType = "PRODUCT",
          entityId = product.id,
          details = "${product.name}: ${MoneyMath.formatQuantity(previousStock)} -> ${MoneyMath.formatQuantity(newStock)} ${product.baseUnit} ($reason)",
        ),
      )
    }
  }

  suspend fun archiveProduct(businessId: Long, productId: Long, userId: Long? = null) {
    db.withTransaction {
      val product = inventoryDao.getProductById(productId) ?: return@withTransaction
      inventoryDao.deactivateProduct(productId, System.currentTimeMillis())
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = userId,
          action = "PRODUCT_ARCHIVED",
          entityType = "PRODUCT",
          entityId = productId,
          details = "Producto archivado: ${product.name}",
        ),
      )
    }
  }

  companion object {
    fun computeProductInventoryValue(
      product: ProductEntity,
      gramsPerPound: BigDecimal,
    ): BigDecimal {
      if (product.stockBaseQty <= BigDecimal.ZERO || product.avgCostPerSaleUnit <= BigDecimal.ZERO) {
        return MoneyMath.ZERO
      }
      val saleUnitQty = when (product.modality) {
        ProductModality.WEIGHT.code -> when (product.defaultSaleUnit) {
          "POUND" -> UnitConverter.gramsToPounds(product.stockBaseQty, gramsPerPound)
          "KG" -> UnitConverter.gramsToKilograms(product.stockBaseQty)
          else -> product.stockBaseQty
        }
        else -> product.stockBaseQty
      }
      return MoneyMath.multiply(product.avgCostPerSaleUnit, saleUnitQty)
    }
  }
}
