package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
  @Query("SELECT * FROM categories WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeCategories(businessId: Long): Flow<List<CategoryEntity>>

  @Query("SELECT COUNT(*) FROM categories WHERE businessId = :businessId")
  suspend fun countCategories(businessId: Long): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertCategories(categories: List<CategoryEntity>)

  @Query("SELECT * FROM expense_categories WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeExpenseCategories(businessId: Long): Flow<List<ExpenseCategoryEntity>>

  @Query("SELECT COUNT(*) FROM expense_categories WHERE businessId = :businessId")
  suspend fun countExpenseCategories(businessId: Long): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertExpenseCategories(categories: List<ExpenseCategoryEntity>)

  @Query("SELECT COUNT(*) FROM products WHERE businessId = :businessId AND isActive = 1")
  fun observeActiveProductCount(businessId: Long): Flow<Int>

  @Query("SELECT COUNT(*) FROM sales WHERE businessId = :businessId AND status != 'ANNULLED'")
  fun observeConfirmedSaleCount(businessId: Long): Flow<Int>
}
