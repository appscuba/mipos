package com.example.ui.backups

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.security.BackupFileEnvelope
import com.example.data.preferences.AppPreferencesDataStore
import com.example.data.repository.BackupRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupsUiState(
  val businessId: Long = 0L,
  val businessName: String = "Gestor Comercial",
  val lastBackupTimestamp: Long = 0L,
  val pendingRestoreBytes: ByteArray? = null,
  val pendingEnvelope: BackupFileEnvelope? = null,
  val lastRestoreSummary: RestoreSummary? = null,
  val isBusy: Boolean = false,
  val feedbackMessage: String? = null,
)

class BackupsViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val backupRepository: BackupRepository,
  businessRepository: BusinessRepository,
  preferences: AppPreferencesDataStore,
  private val currentUserIdProvider: () -> Long? = { null },
) : ViewModel() {

  private val pendingRestoreBytes = MutableStateFlow<ByteArray?>(null)
  private val pendingEnvelope = MutableStateFlow<BackupFileEnvelope?>(null)
  private val lastRestoreSummary = MutableStateFlow<RestoreSummary?>(null)
  private val isBusy = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<BackupsUiState> = combine(
    businessIdFlow,
    businessRepository.observeWorkspaceSnapshot(),
    preferences.preferencesFlow,
    pendingRestoreBytes,
    pendingEnvelope,
    lastRestoreSummary,
    isBusy,
    feedbackMessage,
  ) { values ->
    val bizId = values[0] as Long
    val workspace = values[1] as com.example.data.repository.BusinessWorkspaceSnapshot
    val prefs = values[2] as com.example.data.preferences.UserPreferences
    val pendingBytes = values[3] as ByteArray?
    val envelope = values[4] as BackupFileEnvelope?
    val summary = values[5] as RestoreSummary?
    val busy = values[6] as Boolean
    val msg = values[7] as String?

    BackupsUiState(
      businessId = bizId,
      businessName = workspace.business?.name ?: "Gestor Comercial",
      lastBackupTimestamp = prefs.lastBackupTimestamp,
      pendingRestoreBytes = pendingBytes,
      pendingEnvelope = envelope,
      lastRestoreSummary = summary,
      isBusy = busy,
      feedbackMessage = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = BackupsUiState(),
  )

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun clearPendingRestore() {
    pendingRestoreBytes.value = null
    pendingEnvelope.value = null
  }

  fun exportToUri(context: Context, uri: Uri, passphrase: String?) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isBusy.value = true
      try {
        val bytes = withContext(Dispatchers.IO) {
          backupRepository.exportEncryptedBackup(
            businessId = bizId,
            passphrase = passphrase,
            actorUserId = currentUserIdProvider(),
          )
        }
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
          } ?: error("No se pudo abrir el destino seleccionado para guardar el respaldo.")
        }
        feedbackMessage.value = "Respaldo cifrado exportado correctamente (${bytes.size / 1024} KB)"
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "Error al exportar el respaldo."
      } finally {
        isBusy.value = false
      }
    }
  }

  fun exportInternalLocalCopy(context: Context, passphrase: String?) {
    val bizId = uiState.value.businessId
    if (bizId <= 0L) return
    viewModelScope.launch {
      isBusy.value = true
      try {
        val bytes = withContext(Dispatchers.IO) {
          backupRepository.exportEncryptedBackup(
            businessId = bizId,
            passphrase = passphrase,
            actorUserId = currentUserIdProvider(),
          )
        }
        val destFile = withContext(Dispatchers.IO) {
          val dir = File(context.filesDir, "manual_backups").apply { mkdirs() }
          File(dir, "respaldo_${System.currentTimeMillis()}.gcbk").apply {
            writeBytes(bytes)
          }
        }
        feedbackMessage.value = "Copia local guardada (${destFile.name})"
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "Error al generar copia local."
      } finally {
        isBusy.value = false
      }
    }
  }

  fun loadBackupFromUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      isBusy.value = true
      try {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer el archivo seleccionado.")
        }
        val envelope = backupRepository.inspectBackup(bytes)
        pendingRestoreBytes.value = bytes
        pendingEnvelope.value = envelope
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "Archivo de respaldo inválido."
      } finally {
        isBusy.value = false
      }
    }
  }

  fun confirmRestore(context: Context, passphrase: String?) {
    val bytes = pendingRestoreBytes.value ?: return
    val bizId = uiState.value.businessId
    viewModelScope.launch {
      isBusy.value = true
      try {
        val summary = withContext(Dispatchers.IO) {
          backupRepository.restoreEncryptedBackup(
            context = context,
            fileBytes = bytes,
            passphrase = passphrase,
            currentBusinessId = bizId,
            actorUserId = currentUserIdProvider(),
          )
        }
        lastRestoreSummary.value = summary
        pendingRestoreBytes.value = null
        pendingEnvelope.value = null
        feedbackMessage.value = "Respaldo restaurado: ${summary.businessName} (${summary.productsCount} productos)"
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo restaurar el respaldo."
      } finally {
        isBusy.value = false
      }
    }
  }

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val backupRepository: BackupRepository,
    private val businessRepository: BusinessRepository,
    private val preferences: AppPreferencesDataStore,
    private val currentUserIdProvider: () -> Long? = { null },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      BackupsViewModel(
        businessIdFlow,
        backupRepository,
        businessRepository,
        preferences,
        currentUserIdProvider,
      ) as T
  }
}
