package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.db.AppDatabase
import com.example.data.license.LicenseRepository
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BackupRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.ReportsRepository
import com.example.data.repository.SaleRepository
import com.example.data.repository.SecurityRepository

class AppContainer(context: Context) {
  val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
  val preferences: AppPreferencesDataStore by lazy { AppPreferencesDataStore(context) }
  val licenseRepository: LicenseRepository by lazy { LicenseRepository(preferences) }
  val businessRepository: BusinessRepository by lazy {
    BusinessRepository(db = database, preferences = preferences)
  }
  val inventoryRepository: InventoryRepository by lazy {
    InventoryRepository(db = database)
  }
  val purchaseRepository: PurchaseRepository by lazy {
    PurchaseRepository(db = database)
  }
  val saleRepository: SaleRepository by lazy {
    SaleRepository(db = database)
  }
  val financeRepository: FinanceRepository by lazy {
    FinanceRepository(db = database)
  }
  val reportsRepository: ReportsRepository by lazy {
    ReportsRepository(db = database)
  }
  val securityRepository: SecurityRepository by lazy {
    SecurityRepository(db = database, preferences = preferences)
  }
  val backupRepository: BackupRepository by lazy {
    BackupRepository(db = database, preferences = preferences)
  }
}

class GestorComercialApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
