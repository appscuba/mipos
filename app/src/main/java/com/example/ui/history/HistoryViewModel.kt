package com.example.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.BusinessEntity
import com.example.data.repository.BusinessRepository
import com.example.data.repository.BusinessWorkspaceSnapshot
import com.example.data.repository.SaleRepository
import com.example.data.repository.SaleWithDetails
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

enum class SaleHistoryFilter(val label: String) {
  ALL("Todas"),
  CONFIRMED("Confirmadas"),
  CREDIT("Con deuda"),
  RETURNED("Dev. parcial"),
  ANNULLED("Anuladas"),
}

data class HistoryUiState(
  val businessId: Long = 0L,
  val business: BusinessEntity? = null,
  val primaryCurrency: String = "CUP",
  val sales: List<SaleWithDetails> = emptyList(),
  val filteredSales: List<SaleWithDetails> = emptyList(),
  val searchQuery: String = "",
  val statusFilter: SaleHistoryFilter = SaleHistoryFilter.ALL,
  val totalSalesNetBase: BigDecimal = MoneyMath.ZERO,
  val totalGrossProfitBase: BigDecimal = MoneyMath.ZERO,
  val activeSalesCount: Int = 0,
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class HistoryViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val saleRepository: SaleRepository,
  private val businessRepository: BusinessRepository,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val statusFilter = MutableStateFlow(SaleHistoryFilter.ALL)
  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val dataFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        saleRepository.observeSales(bizId),
        businessRepository.observeWorkspaceSnapshot(),
      ) { sales, workspace ->
        HistoryDomainBundle(
          businessId = bizId,
          sales = sales,
          workspace = workspace,
        )
      }
    }
  }

  val uiState: StateFlow<HistoryUiState> = combine(
    dataFlow,
    searchQuery,
    statusFilter,
    isSaving,
    feedbackMessage,
  ) { bundle, query, filter, saving, msg ->
    if (bundle == null) {
      HistoryUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      val q = query.trim().lowercase()
      val filtered = bundle.sales.filter { item ->
        val s = item.sale
        val matchesStatus = when (filter) {
          SaleHistoryFilter.ALL -> true
          SaleHistoryFilter.CONFIRMED -> s.status == "CONFIRMED"
          SaleHistoryFilter.CREDIT -> s.status != "ANNULLED" && s.pendingDebtBase > BigDecimal.ZERO
          SaleHistoryFilter.RETURNED -> s.status == "PARTIALLY_RETURNED"
          SaleHistoryFilter.ANNULLED -> s.status == "ANNULLED"
        }
        val matchesQuery = q.isEmpty() ||
          s.receiptNumber.lowercase().contains(q) ||
          (item.customerName?.lowercase()?.contains(q) == true) ||
          item.details.any { it.productNameSnapshot.lowercase().contains(q) }
        matchesStatus && matchesQuery
      }

      val activeSales = bundle.sales.filter { it.sale.status != "ANNULLED" }
      val totalNet = activeSales.fold(MoneyMath.ZERO) { acc, it ->
        MoneyMath.add(acc, it.sale.totalBase)
      }
      val totalProfit = activeSales.fold(MoneyMath.ZERO) { acc, it ->
        MoneyMath.add(acc, it.sale.grossProfitBase)
      }

      HistoryUiState(
        businessId = bundle.businessId,
        business = bundle.workspace.business,
        primaryCurrency = bundle.workspace.business?.primaryCurrency ?: "CUP",
        sales = bundle.sales,
        filteredSales = filtered,
        searchQuery = query,
        statusFilter = filter,
        totalSalesNetBase = totalNet,
        totalGrossProfitBase = totalProfit,
        activeSalesCount = activeSales.size,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = HistoryUiState(),
  )

  fun updateSearch(query: String) {
    searchQuery.value = query
  }

  fun selectFilter(filter: SaleHistoryFilter) {
    statusFilter.value = filter
  }

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun returnSaleItem(
    saleId: Long,
    detailId: Long,
    returnEnteredQty: BigDecimal,
    reason: String,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        saleRepository.returnSaleItem(
          businessId = bizId,
          saleId = saleId,
          saleDetailId = detailId,
          returnEnteredQty = returnEnteredQty,
          reason = reason.ifBlank { "Devolución parcial" },
        )
        feedbackMessage.value = "Devolución registrada e inventario restaurado."
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo registrar la devolución."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun annulSale(saleId: Long, reason: String, onSuccess: () -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        saleRepository.annulSale(
          businessId = bizId,
          saleId = saleId,
          reason = reason.ifBlank { "Anulación de venta" },
        )
        feedbackMessage.value = "Venta anulada conservando el registro histórico."
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo anular la venta."
      } finally {
        isSaving.value = false
      }
    }
  }

  private data class HistoryDomainBundle(
    val businessId: Long,
    val sales: List<SaleWithDetails>,
    val workspace: BusinessWorkspaceSnapshot,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val saleRepository: SaleRepository,
    private val businessRepository: BusinessRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      HistoryViewModel(businessIdFlow, saleRepository, businessRepository) as T
  }
}
