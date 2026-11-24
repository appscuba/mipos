package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.local.db.entity.ExpenseEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface FinanceDao {
  // Expense Categories
  @Query("SELECT * FROM expense_categories WHERE businessId = :businessId AND isActive = 1 ORDER BY isSystemDefault DESC, name ASC")
  fun observeActiveExpenseCategories(businessId: Long): Flow<List<ExpenseCategoryEntity>>

  @Query("SELECT * FROM expense_categories WHERE businessId = :businessId ORDER BY name ASC")
  fun observeExpenseCategoriesForReport(businessId: Long): Flow<List<ExpenseCategoryEntity>>

  @Query("SELECT * FROM expense_categories WHERE businessId = :businessId AND LOWER(name) = LOWER(:name) LIMIT 1")
  suspend fun getExpenseCategoryByName(businessId: Long, name: String): ExpenseCategoryEntity?

  @Query("SELECT * FROM expense_categories WHERE id = :categoryId LIMIT 1")
  suspend fun getExpenseCategoryById(categoryId: Long): ExpenseCategoryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertExpenseCategory(category: ExpenseCategoryEntity): Long

  @Update
  suspend fun updateExpenseCategory(category: ExpenseCategoryEntity)

  @Query("UPDATE expense_categories SET isActive = 0 WHERE id = :categoryId")
  suspend fun deactivateExpenseCategory(categoryId: Long)

  // Expenses
  @Query("SELECT * FROM expenses WHERE businessId = :businessId ORDER BY expenseDate DESC, id DESC")
  fun observeExpenses(businessId: Long): Flow<List<ExpenseEntity>>

  @Query("SELECT * FROM expenses WHERE id = :expenseId LIMIT 1")
  suspend fun getExpenseById(expenseId: Long): ExpenseEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertExpense(expense: ExpenseEntity): Long

  @Update
  suspend fun updateExpense(expense: ExpenseEntity)

  // Cash Sessions & Movements
  @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId ORDER BY openedAt DESC, id DESC")
  fun observeCashSessions(businessId: Long): Flow<List<CashSessionEntity>>

  @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
  fun observeOpenCashSession(businessId: Long): Flow<CashSessionEntity?>

  @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
  suspend fun getOpenCashSession(businessId: Long): CashSessionEntity?

  @Query("SELECT * FROM cash_sessions WHERE id = :sessionId LIMIT 1")
  suspend fun getCashSessionById(sessionId: Long): CashSessionEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCashSession(session: CashSessionEntity): Long

  @Update
  suspend fun updateCashSession(session: CashSessionEntity)

  @Query("SELECT * FROM cash_movements WHERE cashSessionId = :sessionId ORDER BY timestamp DESC, id DESC")
  fun observeCashMovementsForSession(sessionId: Long): Flow<List<CashMovementEntity>>

  @Query("SELECT * FROM cash_movements WHERE businessId = :businessId ORDER BY timestamp DESC, id DESC")
  fun observeAllCashMovements(businessId: Long): Flow<List<CashMovementEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCashMovement(movement: CashMovementEntity): Long

  // Customers & Receivable Debts
  @Query("SELECT * FROM customers WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeActiveCustomers(businessId: Long): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
  fun observeCustomerById(customerId: Long): Flow<CustomerEntity?>

  @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
  suspend fun getCustomerById(customerId: Long): CustomerEntity?

  @Query("UPDATE customers SET isActive = 0 WHERE id = :customerId")
  suspend fun deactivateCustomer(customerId: Long)

  @Query("UPDATE customers SET debtBalanceBase = :newBalance WHERE id = :customerId")
  suspend fun updateCustomerDebtBalance(customerId: Long, newBalance: BigDecimal)

  @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY saleDate DESC, id DESC")
  fun observeSalesForCustomer(customerId: Long): Flow<List<SaleEntity>>

  @Query("SELECT * FROM debts WHERE businessId = :businessId AND partyType = 'CUSTOMER_RECEIVABLE' AND partyId = :customerId ORDER BY createdAt DESC")
  fun observeDebtsForCustomer(businessId: Long, customerId: Long): Flow<List<DebtEntity>>

  @Query("SELECT * FROM debts WHERE businessId = :businessId AND partyType = 'CUSTOMER_RECEIVABLE' AND partyId = :customerId")
  suspend fun getDebtsForCustomerSync(businessId: Long, customerId: Long): List<DebtEntity>

  @Query("SELECT * FROM debts WHERE id = :debtId LIMIT 1")
  suspend fun getDebtById(debtId: Long): DebtEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertDebt(debt: DebtEntity): Long

  @Update
  suspend fun updateDebt(debt: DebtEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPayment(payment: PaymentEntity): Long

  // Summary helpers
  @Query("SELECT * FROM sales WHERE businessId = :businessId AND status != 'ANNULLED'")
  fun observeActiveSalesForBusiness(businessId: Long): Flow<List<SaleEntity>>

  @Query("SELECT * FROM suppliers WHERE businessId = :businessId AND isActive = 1")
  fun observeActiveSuppliersForFinance(businessId: Long): Flow<List<com.example.data.local.db.entity.SupplierEntity>>
}
