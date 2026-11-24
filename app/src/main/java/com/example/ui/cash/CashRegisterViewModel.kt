package com.example.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.repository.BusinessRepository
import com.example.data.repository.CashAdjustmentType
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

data class CashRegisterUiState(
  val businessId: Long = 0L,
  val primaryCurrency: String = "CUP",
  val openSession: CashSessionEntity? = null,
  val sessionsHistory: List<CashSessionEntity> = emptyList(),
  val openSessionMovements: List<CashMovementEntity> = emptyList(),
  val exchangeRates: List<ExchangeRateEntity> = emptyList(),
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class CashRegisterViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val financeRepository: FinanceRepository,
  private val businessRepository: BusinessRepository,
  private val currentUserIdProvider: () -> Long? = { null },
) : ViewModel() {

  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val baseFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        financeRepository.observeOpenCashSession(bizId),
        financeRepository.observeCashSessions(bizId),
        financeRepository.observeExchangeRates(bizId),
        businessRepository.observeWorkspaceSnapshot(),
      ) { openSession, allSessions, rates, workspace ->
        CashDomainBundle(
          businessId = bizId,
          primaryCurrency = workspace.business?.primaryCurrency ?: "CUP",
          openSession = openSession,
          sessions = allSessions,
          rates = rates,
        )
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val movementsFlow = baseFlow.flatMapLatest { bundle ->
    val sessionId = bundle?.openSession?.id
    if (sessionId == null) {
      flowOf(emptyList())
    } else {
      financeRepository.observeCashMovementsForSession(sessionId)
    }
  }

  val uiState: StateFlow<CashRegisterUiState> = combine(
    baseFlow,
    movementsFlow,
    isSaving,
    feedbackMessage,
  ) { bundle, movements, saving, msg ->
    if (bundle == null) {
      CashRegisterUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      CashRegisterUiState(
        businessId = bundle.businessId,
        primaryCurrency = bundle.primaryCurrency,
        openSession = bundle.openSession,
        sessionsHistory = bundle.sessions.filter { it.status == "CLOSED" },
        openSessionMovements = movements,
        exchangeRates = bundle.rates,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = CashRegisterUiState(),
  )

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun openSession(openingCashBase: BigDecimal, notes: String, onSuccess: () -> Unit = {}) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.openCashSession(
          businessId = bizId,
          openingCashBase = openingCashBase,
          notes = notes,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "Caja diaria abierta correctamente"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo abrir la caja."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun recordCashAdjustment(
    type: CashAdjustmentType,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    description: String,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        financeRepository.recordCashAdjustment(
          businessId = bizId,
          type = type,
          amountOriginal = amountOriginal,
          currencyCode = currencyCode,
          exchangeRateToBase = exchangeRateToBase,
          affectsPhysicalCash = true,
          description = description,
          userId = currentUserIdProvider(),
        )
        feedbackMessage.value = "${type.label} registrado en caja"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo registrar el movimiento."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun closeSession(
    sessionId: Long,
    countedCashBase: BigDecimal,
    notes: String,
    onSuccess: () -> Unit,
  ) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        val closed = financeRepository.closeCashSession(
          businessId = bizId,
          sessionId = sessionId,
          countedCashBase = countedCashBase,
          notes = notes,
          userId = currentUserIdProvider(),
        )
        val diff = closed.differenceBase ?: BigDecimal.ZERO
        feedbackMessage.value = "Caja cerrada • Diferencia: $diff ${uiState.value.primaryCurrency}"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo cerrar la caja."
      } finally {
        isSaving.value = false
      }
    }
  }

  private data class CashDomainBundle(
    val businessId: Long,
    val primaryCurrency: String,
    val openSession: CashSessionEntity?,
    val sessions: List<CashSessionEntity>,
    val rates: List<ExchangeRateEntity>,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val financeRepository: FinanceRepository,
    private val businessRepository: BusinessRepository,
    private val currentUserIdProvider: () -> Long? = { null },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CashRegisterViewModel(businessIdFlow, financeRepository, businessRepository, currentUserIdProvider) as T
  }
}
