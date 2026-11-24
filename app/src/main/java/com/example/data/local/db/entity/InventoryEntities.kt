package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
  tableName = "categories",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["businessId"]),
    Index(value = ["businessId", "name"], unique = true),
  ],
)
data class CategoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val name: String,
  val description: String = "",
  val colorHex: String = "#1D2B50",
  val isActive: Boolean = true,
)

@Entity(
  tableName = "products",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["businessId"]),
    Index(value = ["categoryId"]),
    Index(value = ["businessId", "sku"]),
    Index(value = ["businessId", "name"]),
  ],
)
data class ProductEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val categoryId: Long? = null,
  val name: String,
  val sku: String = "",
  val barcode: String = "",
  /** WEIGHT (internal grams), UNIT (internal units), PRESENTATION (package-based internal units) */
  val modality: String,
  /** Internal base unit label: "g" or "u" */
  val baseUnit: String,
  /** Current stock in base units (grams for WEIGHT, units for UNIT/PRESENTATION) */
  val stockBaseQty: BigDecimal = BigDecimal.ZERO,
  /** Weighted average cost per display unit (per pound for WEIGHT if priced by pound, or per base unit) */
  val avgCostPerSaleUnit: BigDecimal = BigDecimal.ZERO,
  val salePrice: BigDecimal = BigDecimal.ZERO,
  /** For WEIGHT products: "POUND" or "GRAM" or "KG"; for UNIT: "UNIT" */
  val defaultSaleUnit: String = "UNIT",
  val lowStockThresholdBase: BigDecimal = BigDecimal.ZERO,
  val trackInventory: Boolean = true,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "product_presentations",
  foreignKeys = [
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["productId"])],
)
data class ProductPresentationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val productId: Long,
  val name: String,
  /** Number of internal base units (grams or units) per 1 presentation package */
  val baseUnitsPerPresentation: BigDecimal,
  val salePrice: BigDecimal? = null,
  val isBuyPresentation: Boolean = true,
  val isSellPresentation: Boolean = true,
  val isActive: Boolean = true,
)

@Entity(
  tableName = "inventory_movements",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = UserEntity::class,
      parentColumns = ["id"],
      childColumns = ["userId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["businessId"]),
    Index(value = ["productId"]),
    Index(value = ["userId"]),
    Index(value = ["timestamp"]),
    Index(value = ["referenceType", "referenceId"]),
  ],
)
data class InventoryMovementEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val productId: Long,
  val userId: Long? = null,
  /** PURCHASE_IN, SALE_OUT, SALE_RETURN_IN, SALE_ANNUL_IN, PURCHASE_ANNUL_OUT, ADJUSTMENT_IN, ADJUSTMENT_OUT */
  val movementType: String,
  val quantityBaseDelta: BigDecimal,
  val stockAfterBase: BigDecimal,
  val enteredQuantity: BigDecimal,
  val enteredUnitLabel: String,
  /** Snapshot of equivalence applied at movement time (e.g. 500 g/lb or 12 u/caja) */
  val appliedEquivalence: BigDecimal,
  val unitCostSnapshot: BigDecimal,
  val referenceType: String,
  val referenceId: Long? = null,
  val note: String = "",
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "inventory_adjustments",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = UserEntity::class,
      parentColumns = ["id"],
      childColumns = ["userId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["businessId"]),
    Index(value = ["productId"]),
    Index(value = ["userId"]),
    Index(value = ["timestamp"]),
  ],
)
data class InventoryAdjustmentEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val productId: Long,
  val userId: Long? = null,
  val previousStockBase: BigDecimal,
  val newStockBase: BigDecimal,
  val deltaBase: BigDecimal,
  val reason: String,
  val timestamp: Long = System.currentTimeMillis(),
)
