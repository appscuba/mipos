package com.example.ui.license

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.license.LicenseManager
import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LicenseUiState(
  val deviceId: String = "",
  val licenseKey: String = "",
  val status: String = "",
  val expiresAt: Long = 0L,
  val isChecking: Boolean = false,
  val message: String? = null,
  val isError: Boolean = false,
)

class LicenseViewModel(
  private val preferences: AppPreferencesDataStore,
  private val licenseManager: LicenseManager,
) : ViewModel() {

  private data class Draft(
    val keyInput: String? = null,
    val isChecking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
  )

  private val draft = MutableStateFlow(Draft())

  val uiState: StateFlow<LicenseUiState> =
    combine(preferences.preferencesFlow, draft) { prefs, d ->
      LicenseUiState(
        deviceId = prefs.licenseDeviceId,
        licenseKey = d.keyInput ?: prefs.licenseKey,
        status = prefs.licenseStatus,
        expiresAt = prefs.licenseExpiresAt,
        isChecking = d.isChecking,
        message = d.message,
        isError = d.isError,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LicenseUiState())

  init {
    viewModelScope.launch {
      preferences.ensureLicenseDeviceId()
      // La marca del primer arranque enciende la prueba de 15 días.
      preferences.ensureFirstLaunchAt()
    }
  }

  fun onLicenseKeyChange(value: String) {
    draft.update { it.copy(keyInput = value, message = null) }
  }

  /**
   * Activa con la clave escrita: LicenseHub vincula ESTE dispositivo
   * a la licencia de forma atómica. Si es válida, el plan pasa a
   * LICENSED solo (toda la app deriva el plan de las preferencias).
   */
  fun activate() {
    val key = uiState.value.licenseKey.trim()
    if (key.isBlank()) {
      draft.update { it.copy(message = "Escribe tu clave de licencia.", isError = true) }
      return
    }
    if (uiState.value.isChecking) return
    draft.update { it.copy(isChecking = true, message = null) }
    viewModelScope.launch {
      val result = licenseManager.activate(key)
      draft.update {
        it.copy(
          isChecking = false,
          message = if (result.state == LicenseManager.State.ACTIVE) {
            "Licencia activada. ¡Bienvenido a Stock+!"
          } else {
            result.message
          },
          isError = result.state != LicenseManager.State.ACTIVE &&
            result.state != LicenseManager.State.OFFLINE_ACTIVE,
        )
      }
    }
  }

  /** Al abrir la pantalla de activación: revalida en silencio si hay clave. */
  fun checkOnOpen() {
    viewModelScope.launch {
      if (licenseManager.savedLicenseKey() != null) {
        licenseManager.check()
      }
    }
  }

  /**
   * Al abrir la app: si la última validación tiene más de 24 h, se
   * reconsulta en silencio. Sin conexión no pasa nada: manda el
   * grant offline firmado (48 h) de LicenseRules.
   */
  fun refreshIfStale() {
    viewModelScope.launch { licenseManager.refreshIfStale() }
  }

  class Factory(
    private val context: Context,
    private val preferences: AppPreferencesDataStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      LicenseViewModel(
        preferences = preferences,
        licenseManager = LicenseManager(context.applicationContext, preferences),
      ) as T
  }
}
