package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

@Dao
interface BackupDao {
  // --- Export Queries ---
  @Query("SELECT * FROM businesses ORDER BY id ASC")
  suspend fun getAllBusinesses(): List<BusinessEntity>

  @Query("SELECT * FROM roles ORDER BY id ASC")
  suspend fun getAllRoles(): List<RoleEntity>

  @Query("SELECT * FROM permissions ORDER BY id ASC")
  suspend fun getAllPermissions(): List<PermissionEntity>

  @Query("SELECT * FROM role_permissions ORDER BY roleId ASC, permissionId ASC")
  suspend fun getAllRolePermissions(): List<RolePermissionCrossRef>

  @Query("SELECT * FROM users ORDER BY id ASC")
  suspend fun getAllUsers(): List<UserEntity>

  @Query("SELECT * FROM app_settings ORDER BY id ASC")
  suspend fun getAllSettings(): List<AppSettingsEntity>

  @Query("SELECT * FROM exchange_rates ORDER BY id ASC")
  suspend fun getAllExchangeRates(): List<ExchangeRateEntity>

  @Query("SELECT * FROM categories ORDER BY id ASC")
  suspend fun getAllCategories(): List<CategoryEntity>

  @Query("SELECT * FROM products ORDER BY id ASC")
  suspend fun getAllProducts(): List<ProductEntity>

  @Query("SELECT * FROM product_presentations ORDER BY id ASC")
  suspend fun getAllPresentations(): List<ProductPresentationEntity>

  @Query("SELECT * FROM suppliers ORDER BY id ASC")
  suspend fun getAllSuppliers(): List<SupplierEntity>

  @Query("SELECT * FROM purchases ORDER BY id ASC")
  suspend fun getAllPurchases(): List<PurchaseEntity>

  @Query("SELECT * FROM purchase_details ORDER BY id ASC")
  suspend fun getAllPurchaseDetails(): List<PurchaseDetailEntity>

  @Query("SELECT * FROM customers ORDER BY id ASC")
  suspend fun getAllCustomers(): List<CustomerEntity>

  @Query("SELECT * FROM sales ORDER BY id ASC")
  suspend fun getAllSales(): List<SaleEntity>

  @Query("SELECT * FROM sale_details ORDER BY id ASC")
  suspend fun getAllSaleDetails(): List<SaleDetailEntity>

  @Query("SELECT * FROM payments ORDER BY id ASC")
  suspend fun getAllPayments(): List<PaymentEntity>

  @Query("SELECT * FROM debts ORDER BY id ASC")
  suspend fun getAllDebts(): List<DebtEntity>

  @Query("SELECT * FROM expense_categories ORDER BY id ASC")
  suspend fun getAllExpenseCategories(): List<ExpenseCategoryEntity>

  @Query("SELECT * FROM expenses ORDER BY id ASC")
  suspend fun getAllExpenses(): List<ExpenseEntity>

  @Query("SELECT * FROM cash_sessions ORDER BY id ASC")
  suspend fun getAllCashSessions(): List<CashSessionEntity>

  @Query("SELECT * FROM cash_movements ORDER BY id ASC")
  suspend fun getAllCashMovements(): List<CashMovementEntity>

  @Query("SELECT * FROM inventory_movements ORDER BY id ASC")
  suspend fun getAllInventoryMovements(): List<InventoryMovementEntity>

  @Query("SELECT * FROM inventory_adjustments ORDER BY id ASC")
  suspend fun getAllInventoryAdjustments(): List<InventoryAdjustmentEntity>

  @Query("SELECT * FROM audit_logs ORDER BY id ASC")
  suspend fun getAllAuditLogs(): List<AuditLogEntity>

  // --- Clear (child-to-parent order for foreign key safety) ---
  @Query("DELETE FROM audit_logs")
  suspend fun clearAuditLogs()

  @Query("DELETE FROM cash_movements")
  suspend fun clearCashMovements()

  @Query("DELETE FROM cash_sessions")
  suspend fun clearCashSessions()

  @Query("DELETE FROM expenses")
  suspend fun clearExpenses()

  @Query("DELETE FROM expense_categories")
  suspend fun clearExpenseCategories()

  @Query("DELETE FROM debts")
  suspend fun clearDebts()

  @Query("DELETE FROM payments")
  suspend fun clearPayments()

  @Query("DELETE FROM sale_details")
  suspend fun clearSaleDetails()

  @Query("DELETE FROM sales")
  suspend fun clearSales()

  @Query("DELETE FROM customers")
  suspend fun clearCustomers()

  @Query("DELETE FROM purchase_details")
  suspend fun clearPurchaseDetails()

  @Query("DELETE FROM purchases")
  suspend fun clearPurchases()

  @Query("DELETE FROM suppliers")
  suspend fun clearSuppliers()

  @Query("DELETE FROM inventory_adjustments")
  suspend fun clearInventoryAdjustments()

  @Query("DELETE FROM inventory_movements")
  suspend fun clearInventoryMovements()

  @Query("DELETE FROM product_presentations")
  suspend fun clearPresentations()

  @Query("DELETE FROM products")
  suspend fun clearProducts()

  @Query("DELETE FROM categories")
  suspend fun clearCategories()

  @Query("DELETE FROM exchange_rates")
  suspend fun clearExchangeRates()

  @Query("DELETE FROM app_settings")
  suspend fun clearSettings()

  @Query("DELETE FROM users")
  suspend fun clearUsers()

  @Query("DELETE FROM role_permissions")
  suspend fun clearRolePermissions()

  @Query("DELETE FROM permissions")
  suspend fun clearPermissions()

  @Query("DELETE FROM roles")
  suspend fun clearRoles()

  @Query("DELETE FROM businesses")
  suspend fun clearBusinesses()

  // --- Restore Inserts (parent-to-child order) ---
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBusinesses(items: List<BusinessEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRoles(items: List<RoleEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPermissions(items: List<PermissionEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRolePermissions(items: List<RolePermissionCrossRef>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUsers(items: List<UserEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSettings(items: List<AppSettingsEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExchangeRates(items: List<ExchangeRateEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCategories(items: List<CategoryEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProducts(items: List<ProductEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPresentations(items: List<ProductPresentationEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSuppliers(items: List<SupplierEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPurchases(items: List<PurchaseEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPurchaseDetails(items: List<PurchaseDetailEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCustomers(items: List<CustomerEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSales(items: List<SaleEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSaleDetails(items: List<SaleDetailEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayments(items: List<PaymentEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDebts(items: List<DebtEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpenseCategories(items: List<ExpenseCategoryEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertExpenses(items: List<ExpenseEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCashSessions(items: List<CashSessionEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCashMovements(items: List<CashMovementEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertInventoryMovements(items: List<InventoryMovementEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertInventoryAdjustments(items: List<InventoryAdjustmentEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAuditLogs(items: List<AuditLogEntity>)
}
