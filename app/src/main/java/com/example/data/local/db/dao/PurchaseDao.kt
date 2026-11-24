package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.PurchaseDetailEntity
import com.example.data.local.db.entity.PurchaseEntity
import com.example.data.local.db.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface PurchaseDao {
  @Query("SELECT * FROM suppliers WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeActiveSuppliers(businessId: Long): Flow<List<SupplierEntity>>

  @Query("SELECT * FROM suppliers WHERE businessId = :businessId ORDER BY name ASC")
  fun observeAllSuppliers(businessId: Long): Flow<List<SupplierEntity>>

  @Query("SELECT * FROM suppliers WHERE id = :supplierId LIMIT 1")
  fun observeSupplierById(supplierId: Long): Flow<SupplierEntity?>

  @Query("SELECT * FROM suppliers WHERE id = :supplierId LIMIT 1")
  suspend fun getSupplierById(supplierId: Long): SupplierEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertSupplier(supplier: SupplierEntity): Long

  @Update
  suspend fun updateSupplier(supplier: SupplierEntity)

  @Query("UPDATE suppliers SET balanceOwedBase = :newBalance WHERE id = :supplierId")
  suspend fun updateSupplierBalance(supplierId: Long, newBalance: BigDecimal)

  @Query("UPDATE suppliers SET isActive = 0 WHERE id = :supplierId")
  suspend fun deactivateSupplier(supplierId: Long)

  @Query("SELECT * FROM purchases WHERE businessId = :businessId ORDER BY purchaseDate DESC, id DESC")
  fun observePurchases(businessId: Long): Flow<List<PurchaseEntity>>

  @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY purchaseDate DESC, id DESC")
  fun observePurchasesForSupplier(supplierId: Long): Flow<List<PurchaseEntity>>

  @Query("SELECT * FROM purchases WHERE id = :purchaseId LIMIT 1")
  suspend fun getPurchaseById(purchaseId: Long): PurchaseEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPurchase(purchase: PurchaseEntity): Long

  @Query("UPDATE purchases SET status = :status WHERE id = :purchaseId")
  suspend fun updatePurchaseStatus(purchaseId: Long, status: String)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPurchaseDetails(details: List<PurchaseDetailEntity>)

  @Query("SELECT * FROM purchase_details WHERE purchaseId = :purchaseId ORDER BY id ASC")
  fun observePurchaseDetails(purchaseId: Long): Flow<List<PurchaseDetailEntity>>

  @Query("SELECT * FROM purchase_details WHERE purchaseId = :purchaseId ORDER BY id ASC")
  suspend fun getPurchaseDetails(purchaseId: Long): List<PurchaseDetailEntity>

  @Query("SELECT * FROM purchase_details WHERE purchaseId IN (SELECT id FROM purchases WHERE businessId = :businessId)")
  fun observeAllPurchaseDetailsForBusiness(businessId: Long): Flow<List<PurchaseDetailEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertDebt(debt: DebtEntity): Long

  @Update
  suspend fun updateDebt(debt: DebtEntity)

  @Query("SELECT * FROM debts WHERE id = :debtId LIMIT 1")
  suspend fun getDebtById(debtId: Long): DebtEntity?

  @Query("SELECT * FROM debts WHERE referenceType = :referenceType AND referenceId = :referenceId LIMIT 1")
  suspend fun getDebtByReference(referenceType: String, referenceId: Long): DebtEntity?

  @Query("SELECT * FROM debts WHERE businessId = :businessId AND partyType = 'SUPPLIER_PAYABLE' AND partyId = :supplierId ORDER BY createdAt DESC")
  fun observeDebtsForSupplier(businessId: Long, supplierId: Long): Flow<List<DebtEntity>>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPayment(payment: PaymentEntity): Long

  @Query("SELECT * FROM payments WHERE referenceType = :referenceType AND referenceId = :referenceId ORDER BY paymentDate DESC")
  fun observePaymentsForReference(referenceType: String, referenceId: Long): Flow<List<PaymentEntity>>
}
