package com.example.ui.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.local.db.entity.SupplierEntity
import com.example.data.repository.ConfirmPurchaseRequest
import com.example.data.repository.InventoryOverview
import com.example.data.repository.InventoryRepository
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.PurchaseWithSupplier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class PurchaseStatusFilter(val label: String) {
  ALL("Todas"),
  CONFIRMED("Confirmadas"),
  WITH_DEBT("Con deuda"),
  ANNULLED("Anuladas"),
}

data class PurchasesUiState(
  val businessId: Long = 0L,
  val purchases: List<PurchaseWithSupplier> = emptyList(),
  val filteredPurchases: List<PurchaseWithSupplier> = emptyList(),
  val products: List<ProductWithDetails> = emptyList(),
  val suppliers: List<SupplierEntity> = emptyList(),
  val exchangeRates: List<ExchangeRateEntity> = emptyList(),
  val primaryCurrency: String = "CUP",
  val gramsPerPound: BigDecimal = UnitConverter.DEFAULT_GRAMS_PER_POUND,
  val searchQuery: String = "",
  val statusFilter: PurchaseStatusFilter = PurchaseStatusFilter.ALL,
  val totalConfirmedBase: BigDecimal = MoneyMath.ZERO,
  val totalPendingDebtBase: BigDecimal = MoneyMath.ZERO,
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class PurchasesViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val purchaseRepository: PurchaseRepository,
  private val inventoryRepository: InventoryRepository,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val statusFilter = MutableStateFlow(PurchaseStatusFilter.ALL)
  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val dataFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        purchaseRepository.observePurchases(bizId),
        inventoryRepository.observeInventoryOverview(bizId),
        purchaseRepository.observeSuppliers(bizId),
        purchaseRepository.observeExchangeRates(bizId),
      ) { purchases, inventory, suppliers, rates ->
        PurchasesDomainBundle(
          businessId = bizId,
          purchases = purchases,
          inventory = inventory,
          suppliers = suppliers,
          rates = rates,
        )
      }
    }
  }

  val uiState: StateFlow<PurchasesUiState> = combine(
    dataFlow,
    searchQuery,
    statusFilter,
    isSaving,
    feedbackMessage,
  ) { bundle, query, filter, saving, msg ->
    if (bundle == null) {
      PurchasesUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      val q = query.trim().lowercase()
      val productMap = bundle.inventory.items.associateBy { it.product.id }
      val filtered = bundle.purchases.filter { item ->
        val p = item.purchase
        val matchesStatus = when (filter) {
          PurchaseStatusFilter.ALL -> true
          PurchaseStatusFilter.CONFIRMED -> p.status == "CONFIRMED"
          PurchaseStatusFilter.WITH_DEBT -> p.status == "CONFIRMED" && p.pendingDebtBase > BigDecimal.ZERO
          PurchaseStatusFilter.ANNULLED -> p.status == "ANNULLED"
        }
        val matchesQuery = q.isEmpty() ||
          p.invoiceNumber.lowercase().contains(q) ||
          (item.supplierName?.lowercase()?.contains(q) == true) ||
          "compra #${p.id}".contains(q) ||
          item.details.any { d ->
            productMap[d.productId]?.product?.name?.lowercase()?.contains(q) == true
          }
        matchesStatus && matchesQuery
      }

      val confirmed = bundle.purchases.filter { it.purchase.status == "CONFIRMED" }
      val totalConfirmed = confirmed.fold(MoneyMath.ZERO) { acc, it ->
        MoneyMath.add(acc, it.purchase.totalConvertedBase)
      }
      val totalDebt = bundle.suppliers.fold(MoneyMath.ZERO) { acc, s ->
        MoneyMath.add(acc, s.balanceOwedBase)
      }

      PurchasesUiState(
        businessId = bundle.businessId,
        purchases = bundle.purchases,
        filteredPurchases = filtered,
        products = bundle.inventory.items,
        suppliers = bundle.suppliers,
        exchangeRates = bundle.rates,
        primaryCurrency = bundle.inventory.primaryCurrency,
        gramsPerPound = bundle.inventory.gramsPerPound,
        searchQuery = query,
        statusFilter = filter,
        totalConfirmedBase = totalConfirmed,
        totalPendingDebtBase = totalDebt,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = PurchasesUiState(),
  )

  fun updateSearch(query: String) {
    searchQuery.value = query
  }

  fun selectStatusFilter(filter: PurchaseStatusFilter) {
    statusFilter.value = filter
  }

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun confirmPurchase(request: ConfirmPurchaseRequest, onSuccess: (Long) -> Unit) {
    viewModelScope.launch {
      isSaving.value = true
      try {
        val purchaseId = purchaseRepository.confirmPurchase(request)
        feedbackMessage.value = "Compra #$purchaseId confirmada. Inventario y costo promedio actualizados."
        onSuccess(purchaseId)
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo confirmar la compra."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun annulPurchase(purchaseId: Long, reason: String, onSuccess: () -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        purchaseRepository.annulPurchase(
          businessId = bizId,
          purchaseId = purchaseId,
          reason = reason.ifBlank { "Anulación de compra #$purchaseId" },
        )
        feedbackMessage.value = "Compra #$purchaseId anulada con movimiento compensatorio."
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo anular la compra."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun quickCreateSupplier(
    name: String,
    phone: String,
    onCreated: (Long) -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        val id = purchaseRepository.saveSupplier(
          businessId = bizId,
          name = name,
          phone = phone,
        )
        feedbackMessage.value = "Proveedor '$name' agregado"
        onCreated(id)
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "Error al crear proveedor"
      } finally {
        isSaving.value = false
      }
    }
  }

  private data class PurchasesDomainBundle(
    val businessId: Long,
    val purchases: List<PurchaseWithSupplier>,
    val inventory: InventoryOverview,
    val suppliers: List<SupplierEntity>,
    val rates: List<ExchangeRateEntity>,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val purchaseRepository: PurchaseRepository,
    private val inventoryRepository: InventoryRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      PurchasesViewModel(businessIdFlow, purchaseRepository, inventoryRepository) as T
  }
}
