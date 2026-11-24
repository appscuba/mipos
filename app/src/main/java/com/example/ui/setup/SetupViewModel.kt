package com.example.ui.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.core.security.BackupFileEnvelope
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BackupRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InitialBusinessSetupRequest
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

data class SetupUiState(
  val businessName: String = "",
  val businessType: String = "Tienda / Bodega",
  val logoUri: String? = null,
  val primaryCurrency: String = "CUP",
  val enableSecondaryCurrency: Boolean = true,
  val secondaryCurrencyCode: String = "USD",
  val secondaryCurrencyRate: String = "320.00",
  val phone: String = "",
  val email: String = "",
  val address: String = "",
  val gramsPerPoundInput: String = "500",
  val allowNegativeStock: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val errorMessage: String? = null,
  val isSaving: Boolean = false,
  val pendingRestoreBytes: ByteArray? = null,
  val pendingRestoreEnvelope: BackupFileEnvelope? = null,
  val isRestoringBackup: Boolean = false,
)

class SetupViewModel(
  private val businessRepository: BusinessRepository,
  private val preferences: AppPreferencesDataStore,
  private val backupRepository: BackupRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SetupUiState())
  val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

  fun updateBusinessName(value: String) = _uiState.update { it.copy(businessName = value, errorMessage = null) }
  fun updateBusinessType(value: String) = _uiState.update { it.copy(businessType = value) }
  fun updateLogoUri(uri: String?) = _uiState.update { it.copy(logoUri = uri) }
  fun updatePrimaryCurrency(value: String) = _uiState.update { it.copy(primaryCurrency = value.uppercase(), errorMessage = null) }
  fun updateEnableSecondaryCurrency(enabled: Boolean) = _uiState.update { it.copy(enableSecondaryCurrency = enabled) }
  fun updateSecondaryCurrencyCode(value: String) = _uiState.update { it.copy(secondaryCurrencyCode = value.uppercase(), errorMessage = null) }
  fun updateSecondaryCurrencyRate(value: String) = _uiState.update { it.copy(secondaryCurrencyRate = value, errorMessage = null) }
  fun updatePhone(value: String) = _uiState.update { it.copy(phone = value) }
  fun updateEmail(value: String) = _uiState.update { it.copy(email = value) }
  fun updateAddress(value: String) = _uiState.update { it.copy(address = value) }
  fun updateGramsPerPound(value: String) = _uiState.update { it.copy(gramsPerPoundInput = value, errorMessage = null) }
  fun updateAllowNegativeStock(allowed: Boolean) = _uiState.update { it.copy(allowNegativeStock = allowed) }

  fun selectThemeMode(mode: ThemeMode) {
    _uiState.update { it.copy(themeMode = mode) }
    viewModelScope.launch {
      preferences.setThemeMode(mode)
    }
  }

  fun loadBackupForRestore(context: Context, uri: Uri) {
    val repo = backupRepository ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isRestoringBackup = true, errorMessage = null) }
      try {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer el archivo seleccionado.")
        }
        val envelope = repo.inspectBackup(bytes)
        _uiState.update {
          it.copy(
            isRestoringBackup = false,
            pendingRestoreBytes = bytes,
            pendingRestoreEnvelope = envelope,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isRestoringBackup = false,
            errorMessage = e.message ?: "Archivo de respaldo inválido.",
          )
        }
      }
    }
  }

  fun clearPendingRestore() {
    _uiState.update {
      it.copy(pendingRestoreBytes = null, pendingRestoreEnvelope = null, isRestoringBackup = false)
    }
  }

  fun confirmRestoreBackup(context: Context, passphrase: String?, onCompleted: () -> Unit) {
    val repo = backupRepository ?: return
    val bytes = _uiState.value.pendingRestoreBytes ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isRestoringBackup = true, errorMessage = null) }
      try {
        withContext(Dispatchers.IO) {
          repo.restoreEncryptedBackup(
            context = context,
            fileBytes = bytes,
            passphrase = passphrase,
            currentBusinessId = 0L,
            actorUserId = null,
          )
        }
        _uiState.value = SetupUiState(themeMode = _uiState.value.themeMode)
        onCompleted()
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isRestoringBackup = false,
            pendingRestoreBytes = null,
            pendingRestoreEnvelope = null,
            errorMessage = e.message ?: "No se pudo restaurar el respaldo.",
          )
        }
      }
    }
  }

  fun submitSetup(onCompleted: () -> Unit) {
    val state = _uiState.value
    val name = state.businessName.trim()
    if (name.isEmpty()) {
      _uiState.update { it.copy(errorMessage = "Ingresa el nombre de tu negocio.") }
      return
    }
    val baseCurrency = state.primaryCurrency.trim().uppercase()
    if (baseCurrency.length < 3) {
      _uiState.update { it.copy(errorMessage = "La moneda principal debe tener al menos 3 letras (ej. CUP).") }
      return
    }
    val gramsPerPound = MoneyMath.parseDecimalOrNull(state.gramsPerPoundInput)
    if (gramsPerPound == null || gramsPerPound <= BigDecimal.ZERO) {
      _uiState.update { it.copy(errorMessage = "La equivalencia de gramos por libra debe ser mayor que cero.") }
      return
    }
    val secRate = if (state.enableSecondaryCurrency) {
      val parsed = MoneyMath.parseDecimalOrNull(state.secondaryCurrencyRate)
      if (parsed == null || parsed <= BigDecimal.ZERO) {
        _uiState.update { it.copy(errorMessage = "Ingresa una tasa de cambio válida mayor que cero.") }
        return
      }
      parsed
    } else null

    _uiState.update { it.copy(isSaving = true, errorMessage = null) }
    viewModelScope.launch {
      try {
        businessRepository.completeInitialSetup(
          InitialBusinessSetupRequest(
            businessName = name,
            businessType = state.businessType,
            logoUri = state.logoUri,
            phone = state.phone,
            email = state.email,
            address = state.address,
            primaryCurrency = baseCurrency,
            secondaryCurrencyCode = if (state.enableSecondaryCurrency) state.secondaryCurrencyCode else null,
            secondaryCurrencyRate = secRate,
            gramsPerPound = gramsPerPound,
            allowNegativeStock = state.allowNegativeStock,
          ),
        )
        _uiState.value = SetupUiState(themeMode = state.themeMode)
        onCompleted()
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSaving = false,
            errorMessage = e.message ?: "No se pudo guardar la configuración inicial.",
          )
        }
      }
    }
  }

  class Factory(
    private val businessRepository: BusinessRepository,
    private val preferences: AppPreferencesDataStore,
    private val backupRepository: BackupRepository? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      SetupViewModel(businessRepository, preferences, backupRepository) as T
  }
}
