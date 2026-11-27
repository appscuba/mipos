package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.security.PinHasher
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.PermissionEntity
import com.example.data.local.db.entity.RoleEntity
import com.example.data.local.db.entity.UserEntity
import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

object AppPermissions {
  const val POS_SELL = "POS_SELL"
  const val POS_DISCOUNT = "POS_DISCOUNT"
  const val POS_ANNUL = "POS_ANNUL"
  const val PRODUCTS_MANAGE = "PRODUCTS_MANAGE"
  const val STOCK_ADJUST = "STOCK_ADJUST"
  const val PURCHASES_MANAGE = "PURCHASES_MANAGE"
  const val FINANCE_VIEW = "FINANCE_VIEW"
  const val CASH_CLOSE = "CASH_CLOSE"
  const val REPORTS_COST_PROFIT = "REPORTS_COST_PROFIT"
  const val SETTINGS_MANAGE = "SETTINGS_MANAGE"
  const val USERS_MANAGE = "USERS_MANAGE"
  const val BACKUPS_MANAGE = "BACKUPS_MANAGE"
}

data class UserWithRole(
  val user: UserEntity,
  val role: RoleEntity?,
  val permissions: List<PermissionEntity>,
)

data class ActiveUserSession(
  val user: UserEntity? = null,
  val role: RoleEntity? = null,
  val permissionCodes: Set<String> = emptySet(),
  val isPinLocked: Boolean = false,
) {
  val isOwner: Boolean get() = role?.code == "OWNER"
  val hasPinConfigured: Boolean get() = !user?.pinHash.isNullOrBlank()
  fun hasPermission(code: String): Boolean {
    if (user == null) return true // Before onboarding finishes or uninitialized
    if (isOwner) return true
    return code in permissionCodes
  }
}

class SecurityRepository(
  private val db: AppDatabase,
  private val preferences: AppPreferencesDataStore,
) {
  private val securityDao = db.securityDao()
  private val lockOverride = MutableStateFlow<Boolean?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeActiveSession(businessIdFlow: Flow<Long>): Flow<ActiveUserSession> = combine(
    businessIdFlow,
    preferences.preferencesFlow,
    lockOverride,
  ) { bizId, prefs, manualLock ->
    Triple(bizId, prefs.activeUserId, manualLock)
  }.flatMapLatest { (bizId, activeUserId, manualLock) ->
    if (bizId <= 0L) {
      lockOverride.value = null
      flowOf(ActiveUserSession())
    } else {
      combine(
        securityDao.observeUsers(bizId),
        securityDao.observeRoles(),
      ) { users, roles ->
        val selectedUser = users.firstOrNull { it.id == activeUserId } ?: users.firstOrNull()
        val role = selectedUser?.let { u -> roles.firstOrNull { it.id == u.roleId } }
        val perms = if (role != null) {
          securityDao.getPermissionsForRole(role.id).map { it.code }.toSet()
        } else {
          emptySet()
        }
        val hasPin = !selectedUser?.pinHash.isNullOrBlank()
        val isLocked = when {
          !hasPin -> false
          manualLock != null -> manualLock
          else -> true // Cold start with PIN-protected user requires PIN unlock
        }
        ActiveUserSession(
          user = selectedUser,
          role = role,
          permissionCodes = perms,
          isPinLocked = isLocked,
        )
      }
    }
  }

  fun observeUsersWithRoles(businessId: Long): Flow<List<UserWithRole>> = combine(
    securityDao.observeAllUsers(businessId),
    securityDao.observeRoles(),
  ) { users, roles ->
    val roleMap = roles.associateBy { it.id }
    val permCache = mutableMapOf<Long, List<PermissionEntity>>()
    users.map { user ->
      val role = roleMap[user.roleId]
      val perms = permCache.getOrPut(user.roleId) {
        securityDao.getPermissionsForRole(user.roleId)
      }
      UserWithRole(user = user, role = role, permissions = perms)
    }
  }

  fun observeRoles(): Flow<List<RoleEntity>> = securityDao.observeRoles()

  fun observeAuditLogs(businessId: Long, limit: Int = 200): Flow<List<AuditLogEntity>> =
    securityDao.observeRecentAuditLogs(businessId, limit)

  suspend fun getCurrentActorId(businessId: Long): Long? {
    val active = securityDao.getUserById(0L)
    if (active != null) return active.id
    return null
  }

  suspend fun requirePermission(userId: Long?, permissionCode: String) {
    if (userId == null || userId <= 0L) return
    val user = securityDao.getUserById(userId) ?: return
    val role = securityDao.getRoleById(user.roleId) ?: return
    if (role.code == "OWNER") return
    val perms = securityDao.getPermissionsForRole(role.id).map { it.code }.toSet()
    require(permissionCode in perms) {
      "Tu perfil (${role.name}) no tiene permiso para realizar esta operación ($permissionCode)."
    }
  }

  suspend fun createUser(
    businessId: Long,
    fullName: String,
    username: String,
    roleCode: String,
    pin: String?,
    actorUserId: Long? = null,
  ): Long {
    requirePermission(actorUserId, AppPermissions.USERS_MANAGE)
    val cleanName = fullName.trim()
    val cleanUser = username.trim().lowercase()
    require(cleanName.isNotEmpty()) { "El nombre completo es obligatorio." }
    require(cleanUser.length >= 3) { "El usuario debe tener al menos 3 caracteres." }

    val pinHash = pin?.trim()?.takeIf { it.isNotEmpty() }?.let { PinHasher.hashPin(it) }

    return db.withTransaction {
      val role = securityDao.getRoleByCode(roleCode) ?: error("Rol '$roleCode' no encontrado.")
      val duplicate = securityDao.getUserByUsername(businessId, cleanUser)
      require(duplicate == null) { "Ya existe un usuario con el identificador '$cleanUser'." }

      val newId = securityDao.insertUser(
        UserEntity(
          businessId = businessId,
          roleId = role.id,
          fullName = cleanName,
          username = cleanUser,
          pinHash = pinHash,
          isActive = true,
        ),
      )

      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = actorUserId,
          action = "USER_CREATED",
          entityType = "USER",
          entityId = newId,
          details = "Usuario '$cleanName' (@$cleanUser) creado con rol ${role.name}",
        ),
      )
      newId
    }
  }

  suspend fun updateUserPin(
    businessId: Long,
    targetUserId: Long,
    newPin: String?,
    actorUserId: Long? = null,
  ) {
    val cleanPin = newPin?.trim().orEmpty()
    val newHash = if (cleanPin.isEmpty()) null else PinHasher.hashPin(cleanPin)

    db.withTransaction {
      val target = securityDao.getUserById(targetUserId) ?: error("Usuario no encontrado.")
      securityDao.updateUser(target.copy(pinHash = newHash))
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = actorUserId ?: targetUserId,
          action = if (newHash == null) "USER_PIN_REMOVED" else "USER_PIN_UPDATED",
          entityType = "USER",
          entityId = targetUserId,
          details = "PIN ${if (newHash == null) "eliminado" else "actualizado"} para ${target.fullName}",
        ),
      )
    }
    if (newHash == null) {
      lockOverride.value = false
    }
  }

  suspend fun setUserActive(
    businessId: Long,
    targetUserId: Long,
    isActive: Boolean,
    actorUserId: Long? = null,
  ) {
    requirePermission(actorUserId, AppPermissions.USERS_MANAGE)
    db.withTransaction {
      val target = securityDao.getUserById(targetUserId) ?: error("Usuario no encontrado.")
      if (!isActive) {
        require(targetUserId != actorUserId) { "No puedes desactivar el usuario activo en sesión." }
        val ownerRole = securityDao.getRoleByCode("OWNER")
        if (ownerRole != null && target.roleId == ownerRole.id) {
          val activeOwners = securityDao.countActiveUsersByRole(businessId, ownerRole.id)
          require(activeOwners > 1) { "Debe permanecer al menos un Propietario activo." }
        }
      }
      securityDao.updateUser(target.copy(isActive = isActive))
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = actorUserId,
          action = if (isActive) "USER_ENABLED" else "USER_DISABLED",
          entityType = "USER",
          entityId = targetUserId,
          details = "Usuario ${target.fullName} ${if (isActive) "activado" else "desactivado"}",
        ),
      )
    }
  }

  suspend fun updateUserProfile(
    businessId: Long,
    targetUserId: Long,
    fullName: String,
    username: String,
    roleCode: String,
    actorUserId: Long? = null,
  ) {
    requirePermission(actorUserId, AppPermissions.USERS_MANAGE)
    val cleanName = fullName.trim()
    val cleanUser = username.trim().lowercase()
    require(cleanName.isNotEmpty()) { "El nombre completo es obligatorio." }
    require(cleanUser.length >= 3) { "El usuario debe tener al menos 3 caracteres." }

    db.withTransaction {
      val target = securityDao.getUserById(targetUserId) ?: error("Usuario no encontrado.")
      val newRole = securityDao.getRoleByCode(roleCode) ?: error("Rol '$roleCode' no encontrado.")
      val duplicate = securityDao.getUserByUsername(businessId, cleanUser)
      require(duplicate == null || duplicate.id == targetUserId) {
        "Ya existe un usuario con el identificador '$cleanUser'."
      }
      val ownerRole = securityDao.getRoleByCode("OWNER")
      if (ownerRole != null && target.roleId == ownerRole.id && newRole.id != ownerRole.id && target.isActive) {
        val activeOwners = securityDao.countActiveUsersByRole(businessId, ownerRole.id)
        require(activeOwners > 1) { "Debe permanecer al menos un Propietario activo." }
      }
      securityDao.updateUser(target.copy(fullName = cleanName, username = cleanUser, roleId = newRole.id))
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = actorUserId,
          action = "USER_UPDATED",
          entityType = "USER",
          entityId = targetUserId,
          details = "Usuario actualizado: '$cleanName' (@$cleanUser) con rol ${newRole.name}",
        ),
      )
    }
  }

  suspend fun deleteUser(
    businessId: Long,
    targetUserId: Long,
    actorUserId: Long? = null,
  ) {
    requirePermission(actorUserId, AppPermissions.USERS_MANAGE)
    db.withTransaction {
      val target = securityDao.getUserById(targetUserId) ?: error("Usuario no encontrado.")
      require(targetUserId != actorUserId) { "No puedes eliminar el usuario activo en sesión." }
      val ownerRole = securityDao.getRoleByCode("OWNER")
      if (ownerRole != null && target.roleId == ownerRole.id && target.isActive) {
        val activeOwners = securityDao.countActiveUsersByRole(businessId, ownerRole.id)
        require(activeOwners > 1) { "Debe permanecer al menos un Propietario activo." }
      }
      securityDao.deleteUserById(targetUserId)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = actorUserId,
          action = "USER_DELETED",
          entityType = "USER",
          entityId = targetUserId,
          details = "Usuario '${target.fullName}' (@${target.username}) eliminado",
        ),
      )
    }
  }

  suspend fun clearAuditLogs(
    businessId: Long,
    actorUserId: Long?,
  ): Int {
    requirePermission(actorUserId, AppPermissions.USERS_MANAGE)
    return db.withTransaction {
      val actor = actorUserId?.let { securityDao.getUserById(it) }
      val deletedCount = securityDao.deleteAuditLogs(businessId)
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = businessId,
          userId = actorUserId,
          action = "AUDIT_LOG_CLEARED",
          entityType = "AUDIT_LOG",
          details = "Bitácora vaciada por ${actor?.fullName ?: "Propietario"}; ${deletedCount} registros anteriores eliminados",
        ),
      )
      deletedCount
    }
  }

  suspend fun switchUser(
    businessId: Long,
    targetUserId: Long,
    pinInput: String?,
  ) {
    val target = securityDao.getUserById(targetUserId) ?: error("Usuario no encontrado.")
    require(target.isActive) { "Este usuario está desactivado." }
    if (!target.pinHash.isNullOrBlank()) {
      require(PinHasher.verifyPin(pinInput.orEmpty(), target.pinHash)) {
        "PIN incorrecto para ${target.fullName}."
      }
    }
    preferences.setActiveUser(target.id)
    lockOverride.value = false
    securityDao.insertAuditLog(
      AuditLogEntity(
        businessId = businessId,
        userId = target.id,
        action = "USER_SWITCHED",
        entityType = "USER",
        entityId = target.id,
        details = "Inicio de sesión como ${target.fullName}",
      ),
    )
  }

  fun lockSession() {
    lockOverride.value = true
  }

  suspend fun unlockSession(userId: Long, pinInput: String): Boolean {
    val user = securityDao.getUserById(userId) ?: return false
    val ok = PinHasher.verifyPin(pinInput, user.pinHash)
    if (ok) {
      lockOverride.value = false
      securityDao.insertAuditLog(
        AuditLogEntity(
          businessId = user.businessId,
          userId = user.id,
          action = "SESSION_UNLOCKED",
          entityType = "USER",
          entityId = user.id,
          details = "Sesión desbloqueada por ${user.fullName}",
        ),
      )
    }
    return ok
  }
}
