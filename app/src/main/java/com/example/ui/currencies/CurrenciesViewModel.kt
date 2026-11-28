package com.example.ui.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.license.DefaultLicenseGate
import com.example.core.license.LicenseGate
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.rates.RateSyncRepository
import com.example.data.rates.RateSyncResult
import com.example.data.rates.RateSyncSource
import com.example.data.rates.RatesSyncScheduler
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
  // Tasas automáticas desde elTOQUE (función con licencia).
  val licenseActive: Boolean = false,
  val autoUpdateEnabled: Boolean = false,
  val isSyncing: Boolean = false,
  val lastSyncAt: Long = 0L,
  val lastSyncSource: String? = null,
  val lastSyncError: String? = null,
  val lastSyncCodes: List<String> = emptyList(),
)

class CurrenciesViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val businessRepository: BusinessRepository,
  private val currentUserIdProvider: () -> Long? = { null },
  private val preferences: AppPreferencesDataStore? = null,
  private val rateSyncRepository: RateSyncRepository? = null,
  private val licenseGate: LicenseGate = DefaultLicenseGate(),
  private val ratesSyncScheduler: RatesSyncScheduler? = null,
) : ViewModel() {

  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)
  private val isSyncing = MutableStateFlow(false)
  private val licenseActive = MutableStateFlow(false)

  init {
    viewModelScope.launch {
      licenseActive.value = licenseGate.isRatesAutoUpdateLicensed()
    }
  }

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

  private val prefsFlow = preferences?.preferencesFlow ?: flowOf(null)

  val uiState: StateFlow<CurrenciesUiState> = combine(
    domainFlow,
    isSaving,
    feedbackMessage,
    isSyncing,
    licenseActive,
    prefsFlow,
  ) { triple, saving, msg, syncing, licensed, prefs ->
    if (triple == null) {
      CurrenciesUiState(
        isSaving = saving,
        feedbackMessage = msg,
        isSyncing = syncing,
        licenseActive = licensed,
        autoUpdateEnabled = prefs?.ratesAutoUpdateEnabled ?: false,
        lastSyncAt = prefs?.lastRatesSyncAt ?: 0L,
        lastSyncSource = prefs?.lastRatesSyncSource,
        lastSyncError = prefs?.lastRatesSyncError,
        lastSyncCodes = prefs?.lastRatesSyncCodes ?: emptyList(),
      )
    } else {
      CurrenciesUiState(
        businessId = triple.first,
        primaryCurrency = triple.second,
        rates = triple.third,
        isSaving = saving,
        feedbackMessage = msg,
        isSyncing = syncing,
        licenseActive = licensed,
        autoUpdateEnabled = prefs?.ratesAutoUpdateEnabled ?: false,
        lastSyncAt = prefs?.lastRatesSyncAt ?: 0L,
        lastSyncSource = prefs?.lastRatesSyncSource,
        lastSyncError = prefs?.lastRatesSyncError,
        lastSyncCodes = prefs?.lastRatesSyncCodes ?: emptyList(),
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

  /** Actualización manual desde elTOQUE. Solo con licencia activa. */
  fun refreshRatesNow() {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    if (!uiState.value.licenseActive) {
      feedbackMessage.value =
        "La actualización desde elTOQUE requiere licencia activa."
      return
    }
    val repo = rateSyncRepository ?: run {
      feedbackMessage.value = "Actualización desde elTOQUE no disponible."
      return
    }
    viewModelScope.launch {
      isSyncing.value = true
      try {
        when (val result = repo.sync(bizId, currentUserIdProvider(), RateSyncSource.MANUAL)) {
          is RateSyncResult.Success -> {
            feedbackMessage.value = if (result.updatedCodes.isEmpty()) {
              "elTOQUE respondió, pero no había monedas activas para actualizar."
            } else {
              "Tasas actualizadas desde elTOQUE: ${result.updatedCodes.joinToString(", ")}"
            }
          }
          RateSyncResult.LicenseRequired ->
            feedbackMessage.value = "La actualización desde elTOQUE requiere licencia activa."
          RateSyncResult.NoNetwork ->
            feedbackMessage.value = "Sin conexión a internet. Se conservan las tasas manuales."
          RateSyncResult.UnsupportedBaseCurrency ->
            feedbackMessage.value = "La moneda base no permite derivar tasas desde elTOQUE."
          is RateSyncResult.Failed ->
            feedbackMessage.value = result.reason
        }
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudieron actualizar las tasas."
      } finally {
        isSyncing.value = false
      }
    }
  }

  /** Activa/desactiva la actualización automática cada hora. Solo con licencia. */
  fun setAutoUpdateEnabled(enabled: Boolean) {
    if (enabled && !uiState.value.licenseActive) {
      feedbackMessage.value =
        "La actualización automática desde elTOQUE requiere licencia activa."
      return
    }
    viewModelScope.launch {
      preferences?.setRatesAutoUpdateEnabled(enabled)
      if (enabled) {
        ratesSyncScheduler?.schedule()
        feedbackMessage.value = "Actualización automática cada hora activada (elTOQUE)."
      } else {
        ratesSyncScheduler?.cancel()
        feedbackMessage.value = "Actualización automática desactivada."
      }
    }
  }

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val businessRepository: BusinessRepository,
    private val currentUserIdProvider: () -> Long? = { null },
    private val preferences: AppPreferencesDataStore? = null,
    private val rateSyncRepository: RateSyncRepository? = null,
    private val licenseGate: LicenseGate = DefaultLicenseGate(),
    private val ratesSyncScheduler: RatesSyncScheduler? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      CurrenciesViewModel(
        businessIdFlow,
        businessRepository,
        currentUserIdProvider,
        preferences,
        rateSyncRepository,
        licenseGate,
        ratesSyncScheduler,
      ) as T
  }
}
