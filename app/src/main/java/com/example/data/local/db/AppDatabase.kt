package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.db.dao.BackupDao
import com.example.data.local.db.dao.BusinessConfigDao
import com.example.data.local.db.dao.CatalogDao
import com.example.data.local.db.dao.FinanceDao
import com.example.data.local.db.dao.InventoryDao
import com.example.data.local.db.dao.PurchaseDao
import com.example.data.local.db.dao.ReportsDao
import com.example.data.local.db.dao.SaleDao
import com.example.data.local.db.dao.SecurityDao
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

@Database(
  entities = [
    BusinessEntity::class,
    RoleEntity::class,
    PermissionEntity::class,
    RolePermissionCrossRef::class,
    UserEntity::class,
    AppSettingsEntity::class,
    ExchangeRateEntity::class,
    AuditLogEntity::class,
    CategoryEntity::class,
    ProductEntity::class,
    ProductPresentationEntity::class,
    InventoryMovementEntity::class,
    InventoryAdjustmentEntity::class,
    SupplierEntity::class,
    PurchaseEntity::class,
    PurchaseDetailEntity::class,
    CustomerEntity::class,
    SaleEntity::class,
    SaleDetailEntity::class,
    PaymentEntity::class,
    DebtEntity::class,
    ExpenseCategoryEntity::class,
    ExpenseEntity::class,
    CashSessionEntity::class,
    CashMovementEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun businessConfigDao(): BusinessConfigDao
  abstract fun securityDao(): SecurityDao
  abstract fun catalogDao(): CatalogDao
  abstract fun inventoryDao(): InventoryDao
  abstract fun purchaseDao(): PurchaseDao
  abstract fun saleDao(): SaleDao
  abstract fun financeDao(): FinanceDao
  abstract fun reportsDao(): ReportsDao
  abstract fun backupDao(): BackupDao

  companion object {
    const val DATABASE_NAME = "gestor_comercial.db"

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          DATABASE_NAME,
        )
          .addMigrations(*Migrations.ALL_MIGRATIONS)
          .build()
          .also { INSTANCE = it }
      }

    fun resetForTests() {
      synchronized(this) {
        INSTANCE?.close()
        INSTANCE = null
      }
    }
  }
}
