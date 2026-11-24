package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.security.PinLockOverlay
import com.example.ui.setup.SetupScreen
import com.example.ui.setup.SetupViewModel
import com.example.ui.shell.MainAppShell
import com.example.ui.theme.GestorComercialTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val container = (application as GestorComercialApp).container

    setContent {
      val mainVm: MainViewModel = viewModel(
        factory = MainViewModel.Factory(
          preferences = container.preferences,
          businessRepository = container.businessRepository,
          securityRepository = container.securityRepository,
        ),
      )
      val appState by mainVm.appState.collectAsStateWithLifecycle()

      GestorComercialTheme(themeMode = appState.preferences.themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          when {
            !appState.isLoaded -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            }
            !appState.preferences.isOnboardingCompleted || appState.workspace.business == null -> {
              val setupVm: SetupViewModel = viewModel(
                factory = SetupViewModel.Factory(
                  businessRepository = container.businessRepository,
                  preferences = container.preferences,
                  backupRepository = container.backupRepository,
                ),
              )
              SetupScreen(
                viewModel = setupVm,
                onSetupComplete = {
                  // StateFlow updates automatically when DataStore + Room emit
                },
              )
            }
            appState.activeSession.isPinLocked -> {
              PinLockOverlay(
                businessName = appState.workspace.business?.name ?: "Stock+",
                session = appState.activeSession,
                onUnlock = mainVm::unlockWithPin,
              )
            }
            else -> {
              MainAppShell(
                appState = appState,
                activeBusinessIdFlow = mainVm.activeBusinessId,
                container = container,
                onToggleTheme = mainVm::toggleDarkLight,
              )
            }
          }
        }
      }
    }
  }
}
