package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.InventoryAdjustmentEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.local.db.entity.ProductEntity
import com.example.data.local.db.entity.ProductPresentationEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface InventoryDao {
  @Query("SELECT * FROM categories WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeActiveCategories(businessId: Long): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
  suspend fun getCategoryById(categoryId: Long): CategoryEntity?

  @Query("SELECT * FROM categories WHERE businessId = :businessId AND LOWER(name) = LOWER(:name) LIMIT 1")
  suspend fun getCategoryByName(businessId: Long, name: String): CategoryEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertCategory(category: CategoryEntity): Long

  @Update
  suspend fun updateCategory(category: CategoryEntity)

  @Query("UPDATE categories SET isActive = 0 WHERE id = :categoryId")
  suspend fun deactivateCategory(categoryId: Long)

  @Query("SELECT COUNT(*) FROM products WHERE categoryId = :categoryId AND isActive = 1")
  suspend fun countProductsInCategory(categoryId: Long): Int

  @Query("UPDATE products SET categoryId = NULL, updatedAt = :updatedAt WHERE categoryId = :categoryId")
  suspend fun clearCategoryFromProducts(categoryId: Long, updatedAt: Long)

  @Query("DELETE FROM categories WHERE id = :categoryId")
  suspend fun deleteCategoryById(categoryId: Long)

  @Query("SELECT * FROM products WHERE businessId = :businessId AND isActive = 1 ORDER BY name ASC")
  fun observeActiveProducts(businessId: Long): Flow<List<ProductEntity>>

  @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
  fun observeProductById(productId: Long): Flow<ProductEntity?>

  @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
  suspend fun getProductById(productId: Long): ProductEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertProduct(product: ProductEntity): Long

  @Update
  suspend fun updateProduct(product: ProductEntity)

  @Query("UPDATE products SET stockBaseQty = :newStockBase, updatedAt = :updatedAt WHERE id = :productId")
  suspend fun updateProductStock(productId: Long, newStockBase: BigDecimal, updatedAt: Long)

  @Query("UPDATE products SET isActive = 0, updatedAt = :updatedAt WHERE id = :productId")
  suspend fun deactivateProduct(productId: Long, updatedAt: Long)

  @Query("SELECT * FROM product_presentations WHERE productId IN (SELECT id FROM products WHERE businessId = :businessId AND isActive = 1) AND isActive = 1 ORDER BY baseUnitsPerPresentation ASC")
  fun observeActivePresentationsForBusiness(businessId: Long): Flow<List<ProductPresentationEntity>>

  @Query("SELECT * FROM product_presentations WHERE productId = :productId AND isActive = 1 ORDER BY baseUnitsPerPresentation ASC")
  fun observePresentationsForProduct(productId: Long): Flow<List<ProductPresentationEntity>>

  @Query("SELECT * FROM product_presentations WHERE productId = :productId AND isActive = 1 ORDER BY baseUnitsPerPresentation ASC")
  suspend fun getPresentationsForProduct(productId: Long): List<ProductPresentationEntity>

  @Query("SELECT * FROM product_presentations WHERE id = :presentationId LIMIT 1")
  suspend fun getPresentationById(presentationId: Long): ProductPresentationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPresentation(presentation: ProductPresentationEntity): Long

  @Update
  suspend fun updatePresentation(presentation: ProductPresentationEntity)

  @Query("UPDATE product_presentations SET isActive = 0 WHERE id = :presentationId")
  suspend fun deactivatePresentation(presentationId: Long)

  @Query("SELECT * FROM inventory_movements WHERE productId = :productId ORDER BY timestamp DESC, id DESC")
  fun observeMovementsForProduct(productId: Long): Flow<List<InventoryMovementEntity>>

  @Query("SELECT * FROM inventory_movements WHERE businessId = :businessId ORDER BY timestamp DESC, id DESC LIMIT :limit")
  fun observeRecentMovementsForBusiness(businessId: Long, limit: Int = 100): Flow<List<InventoryMovementEntity>>

  @Insert
  suspend fun insertMovement(movement: InventoryMovementEntity): Long

  @Insert
  suspend fun insertAdjustment(adjustment: InventoryAdjustmentEntity): Long

  @Query("SELECT * FROM inventory_adjustments WHERE productId = :productId ORDER BY timestamp DESC, id DESC")
  fun observeAdjustmentsForProduct(productId: Long): Flow<List<InventoryAdjustmentEntity>>
}
