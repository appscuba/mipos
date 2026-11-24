package com.example.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.repository.BusinessRepository
import com.example.data.repository.CustomerDetailSnapshot
import com.example.data.repository.FinanceRepository
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

data class CustomersUiState(
  val businessId: Long = 0L,
  val customers: List<CustomerEntity> = emptyList(),
  val filteredCustomers: List<CustomerEntity> = emptyList(),
  val exchangeRates: List<ExchangeRateEntity> = emptyList(),
  val primaryCurrency: String = "CUP",
  val searchQuery: String = "",
  val totalReceivableBase: BigDecimal = MoneyMath.ZERO,
  val selectedDetail: CustomerDetailSnapshot? = null,
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class CustomersViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val financeRepository: FinanceRepository,
  private val businessRepository: BusinessRepository,
  private val currentUserIdProvider: () -> Long? = { null },
) : ViewModel() {

  private val searchQuery = MutableStateFlow("")
  private val selectedCustomerId = MutableStateFlow<Long?>(null)
  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val baseDataFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        financeRepository.observeCustomers(bizId),
        financeRepository.observeExchangeRates(bizId),
        businessRepository.observeWorkspaceSnapshot(),
      ) { customers, rates, workspace ->
        CustomersDomainBundle(
          businessId = bizId,
          customers = customers,
          rates = rates,
          primaryCurrency = workspace.business?.primaryCurrency ?: "CUP",
        )
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val selectedDetailFlow = combine(businessIdFlow, selectedCustomerId) { bizId, custId ->
    bizId to custId
  }.flatMapLatest { (bizId, custId) ->
    if (bizId <= 0L || custId == null || custId <= 0L) {
      flowOf(null)
    } else {
      financeRepository.observeCustomerDetail(bizId, custId)
    }
  }

  val uiState: StateFlow<CustomersUiState> = combine(
    baseDataFlow,
    selectedDetailFlow,
    searchQuery,
    isSaving,
    feedbackMessage,
  ) { bundle, detail, query, saving, msg ->
    if (bundle == null) {
      CustomersUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      val q = query.trim().lowercase()
      val filtered = bundle.customers.filter { c ->
        q.isEmpty() ||
          c.name.lowercase().contains(q) ||
          c.phone.lowercase().contains(q) ||
          c.address.lowercase().contains(q)
      }
      val totalReceivable = bundle.customers.fold(MoneyMath.ZERO) { acc, c ->
        MoneyMath.add(acc, c.debtBalanceBase)
      }
      CustomersUiState(
        businessId = bundle.businessId,
        customers = bundle.customers,
        filteredCustomers = filtered,
        exchangeRates = bundle.rates,
        primaryCurrency = bundle.primaryCurrency,
        searchQuery = query,
        totalReceivableBase = totalReceivable,
        selectedDetail = detail,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = CustomersUiState(),
  )

  fun updateSearch(query: String) {
    searchQuery.value = query
  }

  fun selectCustomer(customerId: Long?) {
    selectedCustomerId.value = customerId
  }

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun saveCustomer(
    customerId: Long? = null,
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
        financeRepository.saveCustomer(
          businessId = bizId,
          customerId = customerId,
          name = name,
          phone = phone,
          address = address,
          notes = notes,
        )
        feedbackMessage.value = if (customerId == null) "Cliente creado" else "Cliente actualizado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo guardar el cliente."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun archiveCustomer(customerId: Long, onSuccess: () -> Unit) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.archiveCustomer(bizId, customerId)
        selectedCustomerId.value = null
        feedbackMessage.value = "Cliente desactivado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo desactivar el cliente."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun collectDebt(
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
        financeRepository.collectCustomerDebt(
          businessId = bizId,
          debtId = debtId,
          amountOriginal = amountOriginal,
          currencyCode = currencyCode,
          exchangeRateToBase = exchangeRateToBase,
          paymentMethod = paymentMethod,
          note = note,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "Cobro registrado correctamente"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo registrar el cobro."
      } finally {
        isSaving.value = false
      }
    }
  }

  private data class CustomersDomainBundle(
    val businessId: Long,
    val customers: List<CustomerEntity>,
    val rates: List<ExchangeRateEntity>,
    val primaryCurrency: String,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val financeRepository: FinanceRepository,
    private val businessRepository: BusinessRepository,
    private val currentUserIdProvider: () -> Long? = { null },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CustomersViewModel(businessIdFlow, financeRepository, businessRepository, currentUserIdProvider) as T
  }
}
