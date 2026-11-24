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
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.local.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface SaleDao {
  @Query("SELECT * FROM customers WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeActiveCustomers(businessId: Long): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE businessId = :businessId ORDER BY name ASC")
  fun observeAllCustomers(businessId: Long): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
  suspend fun getCustomerById(customerId: Long): CustomerEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCustomer(customer: CustomerEntity): Long

  @Update
  suspend fun updateCustomer(customer: CustomerEntity)

  @Query("UPDATE customers SET debtBalanceBase = :newBalance WHERE id = :customerId")
  suspend fun updateCustomerDebtBalance(customerId: Long, newBalance: BigDecimal)

  @Query("SELECT * FROM sales WHERE businessId = :businessId ORDER BY saleDate DESC, id DESC")
  fun observeSales(businessId: Long): Flow<List<SaleEntity>>

  @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY saleDate DESC, id DESC")
  fun observeSalesForCustomer(customerId: Long): Flow<List<SaleEntity>>

  @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
  suspend fun getSaleById(saleId: Long): SaleEntity?

  @Query("SELECT * FROM sales WHERE clientRequestId = :clientRequestId LIMIT 1")
  suspend fun getSaleByClientRequestId(clientRequestId: String): SaleEntity?

  @Query("SELECT COUNT(*) FROM sales WHERE businessId = :businessId")
  suspend fun countSalesForBusiness(businessId: Long): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertSale(sale: SaleEntity): Long

  @Update
  suspend fun updateSale(sale: SaleEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertSaleDetails(details: List<SaleDetailEntity>)

  @Update
  suspend fun updateSaleDetail(detail: SaleDetailEntity)

  @Query("SELECT * FROM sale_details WHERE id = :detailId LIMIT 1")
  suspend fun getSaleDetailById(detailId: Long): SaleDetailEntity?

  @Query("SELECT * FROM sale_details WHERE saleId = :saleId ORDER BY id ASC")
  suspend fun getSaleDetails(saleId: Long): List<SaleDetailEntity>

  @Query("SELECT * FROM sale_details WHERE saleId IN (SELECT id FROM sales WHERE businessId = :businessId)")
  fun observeAllSaleDetailsForBusiness(businessId: Long): Flow<List<SaleDetailEntity>>

  @Query("SELECT * FROM payments WHERE businessId = :businessId AND referenceType = 'SALE'")
  fun observeAllSalePaymentsForBusiness(businessId: Long): Flow<List<PaymentEntity>>

  @Query("SELECT * FROM payments WHERE referenceType = :referenceType AND referenceId = :referenceId ORDER BY paymentDate ASC")
  suspend fun getPaymentsForReference(referenceType: String, referenceId: Long): List<PaymentEntity>

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPayment(payment: PaymentEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertDebt(debt: DebtEntity): Long

  @Update
  suspend fun updateDebt(debt: DebtEntity)

  @Query("SELECT * FROM debts WHERE referenceType = :referenceType AND referenceId = :referenceId LIMIT 1")
  suspend fun getDebtByReference(referenceType: String, referenceId: Long): DebtEntity?

  @Query("SELECT * FROM cash_sessions WHERE businessId = :businessId AND status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
  suspend fun getOpenCashSession(businessId: Long): CashSessionEntity?

  @Update
  suspend fun updateCashSession(session: CashSessionEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCashMovement(movement: CashMovementEntity): Long
}
