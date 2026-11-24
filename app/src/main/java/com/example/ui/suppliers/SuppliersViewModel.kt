package com.example.ui.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.local.db.entity.SupplierEntity
import com.example.data.repository.BusinessRepository
import com.example.data.repository.PurchaseRepository
import com.example.data.repository.SupplierDetailSnapshot
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

data class SuppliersUiState(
  val businessId: Long = 0L,
  val suppliers: List<SupplierEntity> = emptyList(),
  val filteredSuppliers: List<SupplierEntity> = emptyList(),
  val exchangeRates: List<ExchangeRateEntity> = emptyList(),
  val primaryCurrency: String = "CUP",
  val searchQuery: String = "",
  val totalOwedBase: BigDecimal = MoneyMath.ZERO,
  val selectedDetail: SupplierDetailSnapshot? = null,
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class SuppliersViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val purchaseRepository: PurchaseRepository,
  private val businessRepository: BusinessRepository,
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedSupplierId = MutableStateFlow<Long?>(null)
  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val baseDataFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        purchaseRepository.observeSuppliers(bizId),
        purchaseRepository.observeExchangeRates(bizId),
        businessRepository.observeWorkspaceSnapshot(),
      ) { suppliers, rates, workspace ->
        SuppliersDomainBundle(
          businessId = bizId,
          suppliers = suppliers,
          rates = rates,
          primaryCurrency = workspace.business?.primaryCurrency ?: "CUP",
        )
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val selectedDetailFlow = combine(businessIdFlow, selectedSupplierId) { bizId, supId ->
    bizId to supId
  }.flatMapLatest { (bizId, supId) ->
    if (bizId <= 0L || supId == null || supId <= 0L) {
      flowOf(null)
    } else {
      purchaseRepository.observeSupplierDetail(bizId, supId)
    }
  }

  val uiState: StateFlow<SuppliersUiState> = combine(
    baseDataFlow,
    selectedDetailFlow,
    searchQuery,
    isSaving,
    feedbackMessage,
  ) { bundle, detail, query, saving, msg ->
    if (bundle == null) {
      SuppliersUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      val q = query.trim().lowercase()
      val filtered = bundle.suppliers.filter { s ->
        q.isEmpty() ||
          s.name.lowercase().contains(q) ||
          s.phone.lowercase().contains(q) ||
          s.address.lowercase().contains(q)
      }
      val totalOwed = bundle.suppliers.fold(MoneyMath.ZERO) { acc, s ->
        MoneyMath.add(acc, s.balanceOwedBase)
      }
      SuppliersUiState(
        businessId = bundle.businessId,
        suppliers = bundle.suppliers,
        filteredSuppliers = filtered,
        exchangeRates = bundle.rates,
        primaryCurrency = bundle.primaryCurrency,
        searchQuery = query,
        totalOwedBase = totalOwed,
        selectedDetail = detail,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SuppliersUiState(),
  )

  fun updateSearch(query: String) {
    searchQuery.value = query
  }

  fun selectSupplier(supplierId: Long?) {
    selectedSupplierId.value = supplierId
  }

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun saveSupplier(
    supplierId: Long? = null,
    name: String,
    phone: String,
    address: String,
    notes: String,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        purchaseRepository.saveSupplier(
          businessId = bizId,
          supplierId = supplierId,
          name = name,
          phone = phone,
          address = address,
          notes = notes,
        )
        feedbackMessage.value = if (supplierId == null) "Proveedor creado" else "Proveedor actualizado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo guardar el proveedor."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun archiveSupplier(supplierId: Long, onSuccess: () -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        purchaseRepository.archiveSupplier(bizId, supplierId)
        selectedSupplierId.value = null
        feedbackMessage.value = "Proveedor desactivado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo desactivar el proveedor."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun payDebt(
    debtId: Long,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    paymentMethod: String,
    note: String,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        purchaseRepository.paySupplierDebt(
          businessId = bizId,
          debtId = debtId,
          amountOriginal = amountOriginal,
          currencyCode = currencyCode,
          exchangeRateToBase = exchangeRateToBase,
          paymentMethod = paymentMethod,
          note = note,
        )
        feedbackMessage.value = "Abono registrado correctamente"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo registrar el abono."
      } finally {
        isSaving.value = false
      }
    }
  }

  private data class SuppliersDomainBundle(
    val businessId: Long,
    val suppliers: List<SupplierEntity>,
    val rates: List<ExchangeRateEntity>,
    val primaryCurrency: String,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val purchaseRepository: PurchaseRepository,
    private val businessRepository: BusinessRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      SuppliersViewModel(businessIdFlow, purchaseRepository, businessRepository) as T
  }
}
