package com.example.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "businesses")
data class BusinessEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val businessType: String,
  val logoUri: String? = null,
  val phone: String = "",
  val email: String = "",
  val address: String = "",
  val taxId: String = "",
  val primaryCurrency: String = "CUP",
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "roles",
  indices = [Index(value = ["code"], unique = true)],
)
data class RoleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val code: String,
  val name: String,
  val description: String = "",
  val isSystemDefault: Boolean = true,
)

@Entity(
  tableName = "permissions",
  indices = [Index(value = ["code"], unique = true)],
)
data class PermissionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val code: String,
  val name: String,
  val module: String,
)

@Entity(
  tableName = "role_permissions",
  primaryKeys = ["roleId", "permissionId"],
  foreignKeys = [
    ForeignKey(
      entity = RoleEntity::class,
      parentColumns = ["id"],
      childColumns = ["roleId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = PermissionEntity::class,
      parentColumns = ["id"],
      childColumns = ["permissionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["permissionId"])],
)
data class RolePermissionCrossRef(
  val roleId: Long,
  val permissionId: Long,
)

@Entity(
  tableName = "users",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = RoleEntity::class,
      parentColumns = ["id"],
      childColumns = ["roleId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["businessId"]),
    Index(value = ["roleId"]),
    Index(value = ["businessId", "username"], unique = true),
  ],
)
data class UserEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val roleId: Long,
  val fullName: String,
  val username: String,
  val pinHash: String? = null,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "app_settings",
  foreignKeys = [
    ForeignKey(
      entity = BusinessEntity::class,
      parentColumns = ["id"],
      childColumns = ["businessId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["businessId"], unique = true)],
)
data class AppSettingsEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val languageCode: String = "es",
  val gramsPerPound: BigDecimal = BigDecimal("500.0000"),
  val allowNegativeStock: Boolean = false,
  val defaultLowStockUnits: BigDecimal = BigDecimal("5.0000"),
  val defaultLowStockGrams: BigDecimal = BigDecimal("1000.0000"),
  val defaultCostMethod: String = "WEIGHTED_AVERAGE",
  val enabledPaymentMethods: String = "CASH,TRANSFER,USD",
  val backupReminderIntervalDays: Int = 7,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "exchange_rates",
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
    Index(value = ["businessId", "currencyCode"], unique = true),
  ],
)
data class ExchangeRateEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val currencyCode: String,
  val currencyName: String,
  val symbol: String,
  val rateToBase: BigDecimal,
  val isBaseCurrency: Boolean = false,
  val isActive: Boolean = true,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "audit_logs",
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
    Index(value = ["timestamp"]),
  ],
)
data class AuditLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val businessId: Long,
  val userId: Long? = null,
  val action: String,
  val entityType: String,
  val entityId: Long? = null,
  val details: String = "",
  val timestamp: Long = System.currentTimeMillis(),
)
