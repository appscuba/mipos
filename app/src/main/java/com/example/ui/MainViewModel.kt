package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.preferences.UserPreferences
import com.example.data.repository.ActiveUserSession
import com.example.data.repository.BusinessRepository
import com.example.data.repository.BusinessWorkspaceSnapshot
import com.example.data.repository.SecurityRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainAppState(
  val isLoaded: Boolean = false,
  val preferences: UserPreferences = UserPreferences(),
  val workspace: BusinessWorkspaceSnapshot = BusinessWorkspaceSnapshot(
    business = null,
    settings = null,
    exchangeRates = emptyList(),
    activeProductCount = 0,
    confirmedSaleCount = 0,
    categoriesCount = 0,
    expenseCategoriesCount = 0,
  ),
  val activeSession: ActiveUserSession = ActiveUserSession(),
)

class MainViewModel(
  private val preferences: AppPreferencesDataStore,
  private val businessRepository: BusinessRepository,
  private val securityRepository: SecurityRepository,
) : ViewModel() {

  init {
    // Siembra de roles y permisos también en instalaciones existentes:
    // los perfiles nuevos (Administrador, Almacenero) aparecen sin reinstalar.
    viewModelScope.launch {
      businessRepository.ensureSystemRolesAndPermissions()
    }
  }

  private val workspaceFlow = businessRepository.observeWorkspaceSnapshot()
  private val businessIdFlow = workspaceFlow.map { it.business?.id ?: 0L }

  val appState: StateFlow<MainAppState> = combine(
    preferences.preferencesFlow,
    workspaceFlow,
    securityRepository.observeActiveSession(businessIdFlow),
  ) { prefs, workspace, session ->
    MainAppState(
      isLoaded = true,
      preferences = prefs,
      workspace = workspace,
      activeSession = session,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = MainAppState(),
  )

  val activeBusinessId: StateFlow<Long> = appState
    .map { it.workspace.business?.id ?: 0L }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = 0L,
    )

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      preferences.setThemeMode(mode)
    }
  }

  fun toggleDarkLight() {
    val current = appState.value.preferences.themeMode
    val next = if (current == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
    setThemeMode(next)
  }

  fun unlockWithPin(pin: String, onResult: (Boolean) -> Unit) {
    val userId = appState.value.activeSession.user?.id ?: return onResult(false)
    viewModelScope.launch {
      val ok = securityRepository.unlockSession(userId, pin)
      onResult(ok)
    }
  }

  fun lockSession() {
    securityRepository.lockSession()
  }

  class Factory(
    private val preferences: AppPreferencesDataStore,
    private val businessRepository: BusinessRepository,
    private val securityRepository: SecurityRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      MainViewModel(preferences, businessRepository, securityRepository) as T
  }
}
