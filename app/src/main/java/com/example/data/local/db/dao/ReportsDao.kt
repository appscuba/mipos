package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.data.local.db.entity.PaymentEntity
import com.example.data.local.db.entity.PurchaseEntity
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.local.db.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportsDao {
  @Query("SELECT * FROM sales WHERE businessId = :businessId AND status != 'ANNULLED' ORDER BY saleDate DESC")
  fun observeValidSales(businessId: Long): Flow<List<SaleEntity>>

  @Query(
    """
    SELECT sd.* FROM sale_details sd
    INNER JOIN sales s ON sd.saleId = s.id
    WHERE s.businessId = :businessId AND s.status != 'ANNULLED'
    """
  )
  fun observeValidSaleDetails(businessId: Long): Flow<List<SaleDetailEntity>>

  @Query("SELECT * FROM purchases WHERE businessId = :businessId AND status = 'CONFIRMED' ORDER BY purchaseDate DESC")
  fun observeConfirmedPurchases(businessId: Long): Flow<List<PurchaseEntity>>

  @Query("SELECT * FROM payments WHERE businessId = :businessId ORDER BY paymentDate DESC")
  fun observePayments(businessId: Long): Flow<List<PaymentEntity>>
}
