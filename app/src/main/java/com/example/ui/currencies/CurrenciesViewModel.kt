package com.example.ui.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.repository.BusinessRepository
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

data class CurrenciesUiState(
  val businessId: Long = 0L,
  val primaryCurrency: String = "CUP",
  val rates: List<ExchangeRateEntity> = emptyList(),
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class CurrenciesViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val businessRepository: BusinessRepository,
  private val currentUserIdProvider: () -> Long? = { null },
) : ViewModel() {

  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val domainFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        businessRepository.observeWorkspaceSnapshot(),
        businessRepository.observeAllExchangeRates(bizId),
      ) { workspace, rates ->
        Triple(bizId, workspace.business?.primaryCurrency ?: "CUP", rates)
      }
    }
  }

  val uiState: StateFlow<CurrenciesUiState> = combine(
    domainFlow,
    isSaving,
    feedbackMessage,
  ) { triple, saving, msg ->
    if (triple == null) {
      CurrenciesUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      CurrenciesUiState(
        businessId = triple.first,
        primaryCurrency = triple.second,
        rates = triple.third,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = CurrenciesUiState(),
  )

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun saveCurrencyRate(
    code: String,
    name: String,
    symbol: String,
    rateToBase: BigDecimal,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        businessRepository.saveCustomExchangeRate(
          businessId = bizId,
          currencyCode = code,
          currencyName = name,
          symbol = symbol,
          rateToBase = rateToBase,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "Tasa de $code guardada (historial anterior protegido)"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo guardar la moneda."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun toggleCurrencyActive(code: String, isActive: Boolean) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        businessRepository.setExchangeRateActive(
          businessId = bizId,
          currencyCode = code,
          isActive = isActive,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = if (isActive) "Moneda $code activada" else "Moneda $code desactivada"
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "Operación no permitida."
      } finally {
        isSaving.value = false
      }
    }
  }

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val businessRepository: BusinessRepository,
    private val currentUserIdProvider: () -> Long? = { null },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CurrenciesViewModel(businessIdFlow, businessRepository, currentUserIdProvider) as T
  }
}
