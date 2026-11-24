package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
  tableName = "suppliers",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["businessId"]), Index(value = ["businessId", "name"])],
)
data class SupplierEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val name: String,
  val phone: String = "",
  val address: String = "",
  val notes: String = "",
  val balanceOwedBase: BigDecimal = BigDecimal.ZERO,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "purchases",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = SupplierEntity::class,
      parentColumns = ["id"],
      childColumns = ["supplierId"],
      onDelete = ForeignKey.SET_NULL,
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
    Index(value = ["supplierId"]),
    Index(value = ["userId"]),
    Index(value = ["purchaseDate"]),
  ],
)
data class PurchaseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val supplierId: Long? = null,
  val userId: Long? = null,
  val invoiceNumber: String = "",
  val purchaseDate: Long = System.currentTimeMillis(),
  val currencyCode: String = "CUP",
  val exchangeRateToBase: BigDecimal = BigDecimal("1.0000"),
  val subtotalOriginal: BigDecimal,
  val discountOriginal: BigDecimal = BigDecimal.ZERO,
  val transportCostOriginal: BigDecimal = BigDecimal.ZERO,
  val extraExpensesOriginal: BigDecimal = BigDecimal.ZERO,
  val totalOriginal: BigDecimal,
  val totalConvertedBase: BigDecimal,
  val paidAmountBase: BigDecimal = BigDecimal.ZERO,
  val pendingDebtBase: BigDecimal = BigDecimal.ZERO,
  val paymentMethod: String = "CASH",
  /** CONFIRMED, ANNULLED (never physically deleted) */
  val status: String = "CONFIRMED",
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "purchase_details",
  foreignKeys = [
    ForeignKey(
      entity = PurchaseEntity::class,
      parentColumns = ["id"],
      childColumns = ["purchaseId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [Index(value = ["purchaseId"]), Index(value = ["productId"])],
)
data class PurchaseDetailEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val purchaseId: Long,
  val productId: Long,
  val presentationLabel: String,
  val enteredQuantity: BigDecimal,
  val appliedEquivalence: BigDecimal,
  val baseQuantityAdded: BigDecimal,
  val unitPriceOriginal: BigDecimal,
  val allocatedExtraCostBase: BigDecimal = BigDecimal.ZERO,
  val realUnitCostBase: BigDecimal,
  val lineTotalBase: BigDecimal,
)

@Entity(
  tableName = "customers",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["businessId"]), Index(value = ["businessId", "name"])],
)
data class CustomerEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val name: String,
  val phone: String = "",
  val address: String = "",
  val notes: String = "",
  val debtBalanceBase: BigDecimal = BigDecimal.ZERO,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "sales",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["id"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.SET_NULL,
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
    Index(value = ["customerId"]),
    Index(value = ["userId"]),
    Index(value = ["saleDate"]),
    Index(value = ["clientRequestId"], unique = true),
  ],
)
data class SaleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val customerId: Long? = null,
  val userId: Long? = null,
  val cashSessionId: Long? = null,
  val receiptNumber: String,
  /** Idempotency token preventing duplicate sales on double-tap */
  val clientRequestId: String,
  val saleDate: Long = System.currentTimeMillis(),
  val currencyCode: String = "CUP",
  val exchangeRateToBase: BigDecimal = BigDecimal("1.0000"),
  val subtotalBase: BigDecimal,
  val discountBase: BigDecimal = BigDecimal.ZERO,
  val totalBase: BigDecimal,
  val totalOriginal: BigDecimal,
  val paidBase: BigDecimal,
  val pendingDebtBase: BigDecimal = BigDecimal.ZERO,
  val totalCostOfGoodsBase: BigDecimal,
  val grossProfitBase: BigDecimal,
  val isCreditSale: Boolean = false,
  /** CONFIRMED, PARTIALLY_RETURNED, ANNULLED (never physically deleted) */
  val status: String = "CONFIRMED",
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "sale_details",
  foreignKeys = [
    ForeignKey(
      entity = SaleEntity::class,
      parentColumns = ["id"],
      childColumns = ["saleId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ProductEntity::class,
      parentColumns = ["id"],
      childColumns = ["productId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [Index(value = ["saleId"]), Index(value = ["productId"])],
)
data class SaleDetailEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val saleId: Long,
  val productId: Long,
  val productNameSnapshot: String,
  val unitLabel: String,
  val enteredQuantity: BigDecimal,
  val appliedEquivalence: BigDecimal,
  val baseQuantityDeducted: BigDecimal,
  val returnedBaseQuantity: BigDecimal = BigDecimal.ZERO,
  val unitSalePriceBase: BigDecimal,
  val historicalUnitCostBase: BigDecimal,
  val lineTotalBase: BigDecimal,
  val lineCostBase: BigDecimal,
  val lineGrossProfitBase: BigDecimal,
)

@Entity(
  tableName = "payments",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
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
    Index(value = ["userId"]),
    Index(value = ["referenceType", "referenceId"]),
    Index(value = ["paymentDate"]),
  ],
)
data class PaymentEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val userId: Long? = null,
  /** SALE, PURCHASE, DEBT_CUSTOMER, DEBT_SUPPLIER */
  val referenceType: String,
  val referenceId: Long,
  /** CASH, TRANSFER, USD, OTHER */
  val paymentMethod: String,
  val currencyCode: String,
  val originalAmount: BigDecimal,
  val exchangeRateToBase: BigDecimal,
  val convertedAmountBase: BigDecimal,
  val isPhysicalCash: Boolean,
  val paymentDate: Long = System.currentTimeMillis(),
  val note: String = "",
)
