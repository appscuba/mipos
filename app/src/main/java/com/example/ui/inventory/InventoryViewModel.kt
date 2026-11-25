package com.example.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.license.PlanLimits
import com.example.data.license.PlanRules
import com.example.data.license.PlanState
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.InventoryOverview
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.SaveProductRequest
import com.example.data.repository.StockAdjustmentRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class StockStatusFilter(val label: String) {
  ALL("Todos"),
  LOW_STOCK("Stock bajo"),
  OUT_OF_STOCK("Agotados"),
}

data class InventoryUiState(
  val businessId: Long = 0L,
  val isLoading: Boolean = true,
  val searchQuery: String = "",
  val selectedCategoryId: Long? = null,
  val selectedModality: ProductModality? = null,
  val stockFilter: StockStatusFilter = StockStatusFilter.ALL,
  val items: List<ProductWithDetails> = emptyList(),
  val filteredItems: List<ProductWithDetails> = emptyList(),
  val categories: List<CategoryEntity> = emptyList(),
  val productCountsByCategory: Map<Long, Int> = emptyMap(),
  val gramsPerPound: BigDecimal = UnitConverter.DEFAULT_GRAMS_PER_POUND,
  val primaryCurrency: String = "CUP",
  val totalInventoryValueBase: BigDecimal = MoneyMath.ZERO,
  val lowStockCount: Int = 0,
  val outOfStockCount: Int = 0,
  val feedbackMessage: String? = null,
  val isError: Boolean = false,
  val planState: PlanState = PlanState.TRIAL,
)

private data class InventoryFilters(
  val searchQuery: String = "",
  val selectedCategoryId: Long? = null,
  val selectedModality: ProductModality? = null,
  val stockFilter: StockStatusFilter = StockStatusFilter.ALL,
)

class InventoryViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val inventoryRepository: InventoryRepository,
  private val preferences: AppPreferencesDataStore,
  private val currentUserIdProvider: () -> Long? = { null },
) : ViewModel() {

  private val filters = MutableStateFlow(InventoryFilters())
  private val feedback = MutableStateFlow<Pair<String, Boolean>?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val overviewFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(
        InventoryOverview(
          items = emptyList(),
          categories = emptyList(),
          gramsPerPound = UnitConverter.DEFAULT_GRAMS_PER_POUND,
          primaryCurrency = "CUP",
          totalInventoryValueBase = MoneyMath.ZERO,
          lowStockCount = 0,
          outOfStockCount = 0,
        ),
      )
    } else {
      inventoryRepository.observeInventoryOverview(bizId)
    }
  }

  val uiState: StateFlow<InventoryUiState> = combine(
    businessIdFlow,
    overviewFlow,
    filters,
    feedback,
    preferences.preferencesFlow,
  ) { bizId, overview, currentFilters, msg, prefs ->
    val q = currentFilters.searchQuery.trim().lowercase()
    val filtered = overview.items.filter { item ->
      val matchesSearch = q.isEmpty() ||
        item.product.name.lowercase().contains(q) ||
        item.product.sku.lowercase().contains(q)
      val matchesCategory = currentFilters.selectedCategoryId == null ||
        item.product.categoryId == currentFilters.selectedCategoryId
      val matchesModality = currentFilters.selectedModality == null ||
        item.product.modality == currentFilters.selectedModality.code
      val matchesStock = when (currentFilters.stockFilter) {
        StockStatusFilter.ALL -> true
        StockStatusFilter.LOW_STOCK -> item.isLowStock
        StockStatusFilter.OUT_OF_STOCK -> item.isOutOfStock
      }
      matchesSearch && matchesCategory && matchesModality && matchesStock
    }

    val countsByCat = overview.items
      .mapNotNull { it.product.categoryId }
      .groupingBy { it }
      .eachCount()

    InventoryUiState(
      businessId = bizId,
      isLoading = bizId <= 0L,
      searchQuery = currentFilters.searchQuery,
      selectedCategoryId = currentFilters.selectedCategoryId,
      selectedModality = currentFilters.selectedModality,
      stockFilter = currentFilters.stockFilter,
      items = overview.items,
      filteredItems = filtered,
      categories = overview.categories,
      productCountsByCategory = countsByCat,
      gramsPerPound = overview.gramsPerPound,
      primaryCurrency = overview.primaryCurrency,
      totalInventoryValueBase = overview.totalInventoryValueBase,
      lowStockCount = overview.lowStockCount,
      outOfStockCount = overview.outOfStockCount,
      feedbackMessage = msg?.first,
      isError = msg?.second ?: false,
      planState = PlanRules.resolve(prefs, System.currentTimeMillis()),
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = InventoryUiState(),
  )

  fun updateSearch(query: String) = filters.update { it.copy(searchQuery = query) }
  fun selectCategory(categoryId: Long?) = filters.update { it.copy(selectedCategoryId = categoryId) }
  fun selectModality(modality: ProductModality?) = filters.update { it.copy(selectedModality = modality) }
  fun selectStockFilter(filter: StockStatusFilter) = filters.update { it.copy(stockFilter = filter) }
  fun clearFeedback() { feedback.value = null }

  fun saveProduct(request: SaveProductRequest, onSaved: (Long) -> Unit = {}) {
    val current = uiState.value
    // Límite del plan gratuito: solo bloquea productos NUEVOS (las
    // ediciones de los ya creados siguen permitidas).
    if (request.productId == null &&
      current.planState == PlanState.FREE &&
      current.items.size >= PlanLimits.MAX_FREE_PRODUCTS
    ) {
      feedback.value =
        "Plan gratuito: llegaste a los ${PlanLimits.MAX_FREE_PRODUCTS} productos. " +
          "Activa una licencia para añadir más." to true
      return
    }
    viewModelScope.launch {
      try {
        val id = inventoryRepository.saveProduct(
          request.copy(userId = request.userId ?: currentUserIdProvider()),
        )
        // Aviso suave al cruzar el 80 % de los productos del plan.
        val newCount = current.items.size + 1
        feedback.value = if (
          request.productId == null &&
          current.planState == PlanState.FREE &&
          newCount == PlanLimits.warnThreshold(PlanLimits.MAX_FREE_PRODUCTS)
        ) {
          "Producto guardado. Ya tienes $newCount de ${PlanLimits.MAX_FREE_PRODUCTS} " +
            "productos del plan gratuito: activa una licencia para añadir sin límites." to false
        } else {
          "Producto guardado correctamente." to false
        }
        onSaved(id)
      } catch (e: Exception) {
        feedback.value = (e.message ?: "No se pudo guardar el producto.") to true
      }
    }
  }

  fun saveCategory(name: String, description: String = "", colorHex: String = "#FFA510", categoryId: Long? = null) {
    val bizId = businessIdFlow.value
    if (bizId <= 0L) return
    viewModelScope.launch {
      try {
        inventoryRepository.saveCategory(
          businessId = bizId,
          categoryId = categoryId,
          name = name,
          description = description,
          colorHex = colorHex,
        )
        feedback.value = "Categoría guardada." to false
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al guardar categoría.") to true
      }
    }
  }

  fun deleteCategory(categoryId: Long) {
    val bizId = businessIdFlow.value
    if (bizId <= 0L) return
    viewModelScope.launch {
      try {
        inventoryRepository.deleteCategory(bizId, categoryId)
        if (filters.value.selectedCategoryId == categoryId) {
          filters.update { it.copy(selectedCategoryId = null) }
        }
        feedback.value = "Categoría eliminada." to false
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al eliminar categoría.") to true
      }
    }
  }

  fun archiveCategory(categoryId: Long) {
    val bizId = businessIdFlow.value
    if (bizId <= 0L) return
    viewModelScope.launch {
      try {
        inventoryRepository.archiveCategory(bizId, categoryId)
        if (filters.value.selectedCategoryId == categoryId) {
          filters.update { it.copy(selectedCategoryId = null) }
        }
        feedback.value = "Categoría desactivada." to false
      } catch (e: Exception) {
        feedback.value = (e.message ?: "Error al desactivar categoría.") to true
      }
    }
  }

  fun recordStockAdjustment(request: StockAdjustmentRequest, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      try {
        inventoryRepository.recordStockAdjustment(
          request.copy(userId = request.userId ?: currentUserIdProvider()),
        )
        feedback.value = "Movimiento de inventario registrado." to false
        onSuccess()
      } catch (e: Exception) {
        feedback.value = (e.message ?: "No se pudo ajustar el inventario.") to true
      }
    }
  }

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val inventoryRepository: InventoryRepository,
    private val preferences: AppPreferencesDataStore,
    private val currentUserIdProvider: () -> Long? = { null },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      InventoryViewModel(businessIdFlow, inventoryRepository, preferences, currentUserIdProvider) as T
  }
}
