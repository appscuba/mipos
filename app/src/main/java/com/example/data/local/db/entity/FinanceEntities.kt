package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
  tableName = "debts",
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
    Index(value = ["partyType", "partyId"]),
    Index(value = ["status"]),
  ],
)
data class DebtEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  /** CUSTOMER_RECEIVABLE or SUPPLIER_PAYABLE */
  val partyType: String,
  val partyId: Long,
  val referenceType: String,
  val referenceId: Long,
  val currencyCode: String,
  val originalAmount: BigDecimal,
  val exchangeRateToBase: BigDecimal,
  val totalBase: BigDecimal,
  val paidBase: BigDecimal = BigDecimal.ZERO,
  val remainingBase: BigDecimal,
  /** OPEN, PARTIAL, PAID, ANNULLED */
  val status: String = "OPEN",
  val dueDate: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "expense_categories",
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
data class ExpenseCategoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val name: String,
  val isSystemDefault: Boolean = false,
  val isActive: Boolean = true,
)

@Entity(
  tableName = "expenses",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ExpenseCategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["expenseCategoryId"],
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
    Index(value = ["expenseCategoryId"]),
    Index(value = ["userId"]),
    Index(value = ["expenseDate"]),
  ],
)
data class ExpenseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val expenseCategoryId: Long,
  val userId: Long? = null,
  val cashSessionId: Long? = null,
  val description: String,
  val currencyCode: String,
  val originalAmount: BigDecimal,
  val exchangeRateToBase: BigDecimal,
  val convertedAmountBase: BigDecimal,
  /** CASH, TRANSFER, OTHER */
  val paymentMethod: String,
  val paidFromCashDrawer: Boolean,
  val expenseDate: Long = System.currentTimeMillis(),
  /** CONFIRMED, ANNULLED */
  val status: String = "CONFIRMED",
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "cash_sessions",
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
      childColumns = ["openedByUserId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [
    Index(value = ["businessId"]),
    Index(value = ["openedByUserId"]),
    Index(value = ["status"]),
  ],
)
data class CashSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val openedByUserId: Long? = null,
  val closedByUserId: Long? = null,
  val openedAt: Long = System.currentTimeMillis(),
  val closedAt: Long? = null,
  val openingCashBase: BigDecimal,
  val expectedCashBase: BigDecimal = BigDecimal.ZERO,
  val countedCashBase: BigDecimal? = null,
  val differenceBase: BigDecimal? = null,
  val totalCashSalesBase: BigDecimal = BigDecimal.ZERO,
  val totalTransferSalesBase: BigDecimal = BigDecimal.ZERO,
  val totalCashInflowsBase: BigDecimal = BigDecimal.ZERO,
  val totalCashExpensesBase: BigDecimal = BigDecimal.ZERO,
  val totalOwnerWithdrawalsBase: BigDecimal = BigDecimal.ZERO,
  /** OPEN, CLOSED */
  val status: String = "OPEN",
  val notes: String = "",
)

@Entity(
  tableName = "cash_movements",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = CashSessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["cashSessionId"],
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
    Index(value = ["cashSessionId"]),
    Index(value = ["userId"]),
    Index(value = ["timestamp"]),
  ],
)
data class CashMovementEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val cashSessionId: Long,
  val userId: Long? = null,
  /** OPENING, SALE_CASH, SALE_TRANSFER, EXTRA_INCOME, INVESTMENT, EXPENSE_CASH, OWNER_WITHDRAWAL, DEBT_COLLECTION_CASH, REFUND_CASH */
  val movementType: String,
  val affectsPhysicalCash: Boolean,
  val currencyCode: String,
  val originalAmount: BigDecimal,
  val exchangeRateToBase: BigDecimal,
  val signedAmountBase: BigDecimal,
  val description: String,
  val referenceType: String? = null,
  val referenceId: Long? = null,
  val timestamp: Long = System.currentTimeMillis(),
)
