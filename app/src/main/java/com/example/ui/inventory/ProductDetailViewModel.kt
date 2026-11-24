package com.example.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.units.UnitConverter
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.SaveProductRequest
import com.example.data.repository.StockAdjustmentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class ProductDetailUiState(
  val isLoading: Boolean = true,
  val businessId: Long = 0L,
  val detail: ProductWithDetails? = null,
  val movements: List<InventoryMovementEntity> = emptyList(),
  val categories: List<CategoryEntity> = emptyList(),
  val gramsPerPound: BigDecimal = UnitConverter.DEFAULT_GRAMS_PER_POUND,
  val primaryCurrency: String = "CUP",
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
)

class ProductDetailViewModel(
  private val productId: Long,
  private val inventoryRepository: InventoryRepository,
  businessRepository: BusinessRepository,
) : ViewModel() {

  private val feedback = MutableStateFlow<Pair<String, Boolean>?>(null)

  val uiState: StateFlow<ProductDetailUiState> = combine(
    inventoryRepository.observeProductDetail(productId),
    inventoryRepository.observeProductMovements(productId),
    businessRepository.observeWorkspaceSnapshot(),
    feedback,
  ) { detail, movements, workspace, msg ->
    val bizId = workspace.business?.id ?: detail?.product?.businessId ?: 0L
    val cats = if (bizId > 0L) emptyList<CategoryEntity>() else emptyList()
    ProductDetailUiState(
      isLoading = false,
      businessId = bizId,
      detail = detail,
      movements = movements,
      categories = cats,
      gramsPerPound = workspace.settings?.gramsPerPound ?: UnitConverter.DEFAULT_GRAMS_PER_POUND,
      primaryCurrency = workspace.business?.primaryCurrency ?: "CUP",
      feedbackMessage = msg?.first,
      isError = msg?.second ?: false,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = ProductDetailUiState(),
  )

  fun clearFeedback() { feedback.value = null }

  fun recordAdjustment(request: StockAdjustmentRequest, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      try {
        inventoryRepository.recordStockAdjustment(request)
        feedback.value = "Ajuste guardado en el kárdex." to false
        onSuccess()
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al ajustar existencias.") to true
      }
    }
  }

  fun savePresentation(
    businessId: Long,
    name: String,
    baseUnitsPerPresentation: BigDecimal,
    salePrice: BigDecimal?,
    isBuy: Boolean,
    isSell: Boolean,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      try {
        inventoryRepository.savePresentation(
          businessId = businessId,
          productId = productId,
          name = name,
          baseUnitsPerPresentation = baseUnitsPerPresentation,
          salePrice = salePrice,
          isBuyPresentation = isBuy,
          isSellPresentation = isSell,
        )
        feedback.value = "Presentación guardada." to false
        onSuccess()
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al guardar presentación.") to true
      }
    }
  }

  fun deactivatePresentation(businessId: Long, presentationId: Long) {
    viewModelScope.launch {
      try {
        inventoryRepository.deactivatePresentation(businessId, presentationId)
        feedback.value = "Presentación eliminada." to false
      } catch (e: Exception) {
        feedback.value = (e.message ?: "No se pudo eliminar.") to true
      }
    }
  }

  fun updateProduct(request: SaveProductRequest, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      try {
        inventoryRepository.saveProduct(request)
        feedback.value = "Producto actualizado." to false
        onSuccess()
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al actualizar producto.") to true
      }
    }
  }

  fun archiveProduct(businessId: Long, onArchived: () -> Unit) {
    viewModelScope.launch {
      try {
        inventoryRepository.archiveProduct(businessId, productId)
        onArchived()
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al archivar producto.") to true
      }
    }
  }

  class Factory(
    private val productId: Long,
    private val inventoryRepository: InventoryRepository,
    private val businessRepository: BusinessRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ProductDetailViewModel(productId, inventoryRepository, businessRepository) as T
  }
}
