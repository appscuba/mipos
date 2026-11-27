package com.example.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.RoleEntity
import com.example.data.repository.ActiveUserSession
import com.example.data.repository.SecurityRepository
import com.example.data.repository.UserWithRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UsersAuditUiState(
  val businessId: Long = 0L,
  val activeSession: ActiveUserSession = ActiveUserSession(),
  val users: List<UserWithRole> = emptyList(),
  val roles: List<RoleEntity> = emptyList(),
  val auditLogs: List<AuditLogEntity> = emptyList(),
  val isSaving: Boolean = false,
  val feedbackMessage: String? = null,
)

class UsersAuditViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val securityRepository: SecurityRepository,
) : ViewModel() {

  private val isSaving = MutableStateFlow(false)
  private val feedbackMessage = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val domainFlow = businessIdFlow.flatMapLatest { bizId ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      combine(
        securityRepository.observeActiveSession(businessIdFlow),
        securityRepository.observeUsersWithRoles(bizId),
        securityRepository.observeRoles(),
        securityRepository.observeAuditLogs(bizId, 200),
      ) { session, users, roles, logs ->
        SecurityDomainBundle(
          businessId = bizId,
          activeSession = session,
          users = users,
          roles = roles,
          auditLogs = logs,
        )
      }
    }
  }

  val uiState: StateFlow<UsersAuditUiState> = combine(
    domainFlow,
    isSaving,
    feedbackMessage,
  ) { bundle, saving, msg ->
    if (bundle == null) {
      UsersAuditUiState(isSaving = saving, feedbackMessage = msg)
    } else {
      UsersAuditUiState(
        businessId = bundle.businessId,
        activeSession = bundle.activeSession,
        users = bundle.users,
        roles = bundle.roles,
        auditLogs = bundle.auditLogs,
        isSaving = saving,
        feedbackMessage = msg,
      )
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = UsersAuditUiState(),
  )

  fun clearFeedback() {
    feedbackMessage.value = null
  }

  fun createUser(
    fullName: String,
    username: String,
    roleCode: String,
    pin: String?,
    onSuccess: () -> Unit,
  ) {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        securityRepository.createUser(
          businessId = state.businessId,
          fullName = fullName,
          username = username,
          roleCode = roleCode,
          pin = pin,
          actorUserId = state.activeSession.user?.id,
        )
        feedbackMessage.value = "Usuario '$fullName' creado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo crear el usuario."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun updateUserPin(targetUserId: Long, newPin: String?, onSuccess: () -> Unit) {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        securityRepository.updateUserPin(
          businessId = state.businessId,
          targetUserId = targetUserId,
          newPin = newPin,
          actorUserId = state.activeSession.user?.id,
        )
        feedbackMessage.value = if (newPin.isNullOrBlank()) "PIN eliminado" else "PIN actualizado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo actualizar el PIN."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun updateUserProfile(
    targetUserId: Long,
    fullName: String,
    username: String,
    roleCode: String,
    onSuccess: () -> Unit,
  ) {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        securityRepository.updateUserProfile(
          businessId = state.businessId,
          targetUserId = targetUserId,
          fullName = fullName,
          username = username,
          roleCode = roleCode,
          actorUserId = state.activeSession.user?.id,
        )
        feedbackMessage.value = "Usuario actualizado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo actualizar el usuario."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun deleteUser(targetUserId: Long, onSuccess: () -> Unit) {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        securityRepository.deleteUser(
          businessId = state.businessId,
          targetUserId = targetUserId,
          actorUserId = state.activeSession.user?.id,
        )
        feedbackMessage.value = "Usuario eliminado"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo eliminar el usuario."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun toggleUserActive(targetUserId: Long, isActive: Boolean) {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        securityRepository.setUserActive(
          businessId = state.businessId,
          targetUserId = targetUserId,
          isActive = isActive,
          actorUserId = state.activeSession.user?.id,
        )
        feedbackMessage.value = if (isActive) "Usuario activado" else "Usuario desactivado"
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "Operación no permitida."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun clearAuditLogs() {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        val deleted = securityRepository.clearAuditLogs(
          businessId = state.businessId,
          actorUserId = state.activeSession.user?.id,
        )
        feedbackMessage.value = "Bitácora vaciada. ${deleted} registros eliminados; la operación quedó registrada."
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "No se pudo vaciar la bitácora."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun switchUser(targetUserId: Long, pinInput: String?, onSuccess: () -> Unit) {
    val state = uiState.value
    if (state.businessId <= 0L) return
    viewModelScope.launch {
      isSaving.value = true
      try {
        securityRepository.switchUser(
          businessId = state.businessId,
          targetUserId = targetUserId,
          pinInput = pinInput,
        )
        feedbackMessage.value = "Sesión cambiada correctamente"
        onSuccess()
      } catch (e: Exception) {
        feedbackMessage.value = e.message ?: "PIN incorrecto."
      } finally {
        isSaving.value = false
      }
    }
  }

  fun lockNow() {
    securityRepository.lockSession()
  }

  private data class SecurityDomainBundle(
    val businessId: Long,
    val activeSession: ActiveUserSession,
    val users: List<UserWithRole>,
    val roles: List<RoleEntity>,
    val auditLogs: List<AuditLogEntity>,
  )

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val securityRepository: SecurityRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      UsersAuditViewModel(businessIdFlow, securityRepository) as T
  }
}
