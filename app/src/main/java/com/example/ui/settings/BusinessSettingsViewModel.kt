package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BusinessRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class BusinessSettingsUiState(
  val businessId: Long = 0L,
  val businessName: String = "",
  val businessType: String = "",
  val logoUri: String? = null,
  val phone: String = "",
  val email: String = "",
  val address: String = "",
  val primaryCurrency: String = "CUP",
  val gramsPerPoundInput: String = "500",
  val allowNegativeStock: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val isSaving: Boolean = false,
  val statusMessage: String? = null,
  val isError: Boolean = false,
)

class BusinessSettingsViewModel(
  private val businessRepository: BusinessRepository,
  private val preferences: AppPreferencesDataStore,
) : ViewModel() {

  private val _uiState = MutableStateFlow(BusinessSettingsUiState())
  val uiState: StateFlow<BusinessSettingsUiState> = _uiState.asStateFlow()

  private var initialized = false

  init {
    viewModelScope.launch {
      combine(
        businessRepository.observeWorkspaceSnapshot(),
        preferences.preferencesFlow,
      ) { workspace, prefs -> workspace to prefs }.collect { (workspace, prefs) ->
        val b = workspace.business
        val s = workspace.settings
        if (b != null && !initialized) {
          initialized = true
          _uiState.update {
            it.copy(
              businessId = b.id,
              businessName = b.name,
              businessType = b.businessType,
              logoUri = b.logoUri,
              phone = b.phone,
              email = b.email,
              address = b.address,
              primaryCurrency = b.primaryCurrency,
              gramsPerPoundInput = s?.gramsPerPound?.stripTrailingZeros()?.toPlainString() ?: "500",
              allowNegativeStock = s?.allowNegativeStock ?: false,
              themeMode = prefs.themeMode,
            )
          }
        } else {
          _uiState.update { it.copy(themeMode = prefs.themeMode) }
        }
      }
    }
  }

  fun updateBusinessName(v: String) = _uiState.update { it.copy(businessName = v, statusMessage = null) }
  fun updateBusinessType(v: String) = _uiState.update { it.copy(businessType = v, statusMessage = null) }
  fun updateLogoUri(v: String?) = _uiState.update { it.copy(logoUri = v, statusMessage = null) }
  fun updatePhone(v: String) = _uiState.update { it.copy(phone = v, statusMessage = null) }
  fun updateEmail(v: String) = _uiState.update { it.copy(email = v, statusMessage = null) }
  fun updateAddress(v: String) = _uiState.update { it.copy(address = v, statusMessage = null) }
  fun updatePrimaryCurrency(v: String) = _uiState.update { it.copy(primaryCurrency = v.uppercase(), statusMessage = null) }
  fun updateGramsPerPound(v: String) = _uiState.update { it.copy(gramsPerPoundInput = v, statusMessage = null) }
  fun updateAllowNegativeStock(v: Boolean) = _uiState.update { it.copy(allowNegativeStock = v, statusMessage = null) }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferences.setThemeMode(mode) }
  }

  fun saveSettings() {
    val current = _uiState.value
    if (current.businessId <= 0L) return
    if (current.businessName.isBlank()) {
      _uiState.update { it.copy(statusMessage = "El nombre del negocio no puede estar vacío.", isError = true) }
      return
    }
    val grams = MoneyMath.parseDecimalOrNull(current.gramsPerPoundInput)
    if (grams == null || grams <= BigDecimal.ZERO) {
      _uiState.update { it.copy(statusMessage = "Ingresa gramos por libra válidos (> 0).", isError = true) }
      return
    }
    _uiState.update { it.copy(isSaving = true, statusMessage = null, isError = false) }
    viewModelScope.launch {
      try {
        businessRepository.updateBusinessSettings(
          businessId = current.businessId,
          businessName = current.businessName,
          businessType = current.businessType,
          logoUri = current.logoUri,
          phone = current.phone,
          email = current.email,
          address = current.address,
          primaryCurrency = current.primaryCurrency,
          gramsPerPound = grams,
          allowNegativeStock = current.allowNegativeStock,
        )
        _uiState.update {
          it.copy(
            isSaving = false,
            statusMessage = "Configuración guardada correctamente.",
            isError = false,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSaving = false,
            statusMessage = e.message ?: "Error al guardar cambios.",
            isError = true,
          )
        }
      }
    }
  }

  class Factory(
    private val businessRepository: BusinessRepository,
    private val preferences: AppPreferencesDataStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      BusinessSettingsViewModel(businessRepository, preferences) as T
  }
}
