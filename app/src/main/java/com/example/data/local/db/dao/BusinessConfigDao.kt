package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.db.entity.AppSettingsEntity
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessConfigDao {
  @Query("SELECT * FROM businesses WHERE isActive = 1 ORDER BY id ASC LIMIT 1")
  fun observeActiveBusiness(): Flow<BusinessEntity?>

  @Query("SELECT * FROM businesses WHERE isActive = 1 ORDER BY id ASC LIMIT 1")
  suspend fun getActiveBusiness(): BusinessEntity?

  @Query("SELECT * FROM businesses WHERE id = :businessId LIMIT 1")
  suspend fun getBusinessById(businessId: Long): BusinessEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBusiness(business: BusinessEntity): Long

  @Update
  suspend fun updateBusiness(business: BusinessEntity)

  @Query("SELECT * FROM app_settings WHERE businessId = :businessId LIMIT 1")
  fun observeSettings(businessId: Long): Flow<AppSettingsEntity?>

  @Query("SELECT * FROM app_settings WHERE businessId = :businessId LIMIT 1")
  suspend fun getSettings(businessId: Long): AppSettingsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateSettings(settings: AppSettingsEntity): Long

  @Query("SELECT * FROM exchange_rates WHERE businessId = :businessId AND isActive = 1 ORDER BY isBaseCurrency DESC, currencyCode ASC")
  fun observeExchangeRates(businessId: Long): Flow<List<ExchangeRateEntity>>

  @Query("SELECT * FROM exchange_rates WHERE businessId = :businessId AND isActive = 1 ORDER BY isBaseCurrency DESC, currencyCode ASC")
  suspend fun getExchangeRates(businessId: Long): List<ExchangeRateEntity>

  @Query("SELECT * FROM exchange_rates WHERE businessId = :businessId ORDER BY isBaseCurrency DESC, isActive DESC, currencyCode ASC")
  fun observeAllExchangeRates(businessId: Long): Flow<List<ExchangeRateEntity>>

  @Query("SELECT * FROM exchange_rates WHERE businessId = :businessId AND currencyCode = :currencyCode LIMIT 1")
  suspend fun getExchangeRateByCode(businessId: Long, currencyCode: String): ExchangeRateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertExchangeRate(rate: ExchangeRateEntity): Long

  @Query("UPDATE exchange_rates SET isBaseCurrency = 0 WHERE businessId = :businessId")
  suspend fun clearBaseCurrencyFlag(businessId: Long)

  @Query("UPDATE exchange_rates SET isActive = :isActive, updatedAt = :updatedAt WHERE businessId = :businessId AND currencyCode = :currencyCode")
  suspend fun setExchangeRateActive(businessId: Long, currencyCode: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())
}
