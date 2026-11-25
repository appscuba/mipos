package com.example.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.PermissionEntity
import com.example.data.local.db.entity.RoleEntity
import com.example.data.local.db.entity.RolePermissionCrossRef
import com.example.data.local.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityDao {
  @Query("SELECT * FROM roles ORDER BY id ASC")
  fun observeRoles(): Flow<List<RoleEntity>>

  @Query("SELECT * FROM roles WHERE code = :code LIMIT 1")
  suspend fun getRoleByCode(code: String): RoleEntity?

  @Query("SELECT * FROM roles WHERE id = :roleId LIMIT 1")
  suspend fun getRoleById(roleId: Long): RoleEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertRole(role: RoleEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertPermission(permission: PermissionEntity): Long

  @Query("SELECT * FROM permissions ORDER BY module, code")
  suspend fun getAllPermissions(): List<PermissionEntity>

  @Query("SELECT * FROM permissions WHERE code = :code LIMIT 1")
  suspend fun getPermissionByCode(code: String): PermissionEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertRolePermission(crossRef: RolePermissionCrossRef)

  @Query(
    """
    SELECT p.* FROM permissions p
    INNER JOIN role_permissions rp ON p.id = rp.permissionId
    WHERE rp.roleId = :roleId
    ORDER BY p.module, p.code
    """
  )
  suspend fun getPermissionsForRole(roleId: Long): List<PermissionEntity>

  @Query("SELECT * FROM users WHERE businessId = :businessId AND isActive = 1 ORDER BY id ASC")
  fun observeUsers(businessId: Long): Flow<List<UserEntity>>

  @Query("SELECT * FROM users WHERE businessId = :businessId ORDER BY isActive DESC, id ASC")
  fun observeAllUsers(businessId: Long): Flow<List<UserEntity>>

  @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
  fun observeUserById(userId: Long): Flow<UserEntity?>

  @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
  suspend fun getUserById(userId: Long): UserEntity?

  @Query("SELECT * FROM users WHERE businessId = :businessId AND LOWER(username) = LOWER(:username) LIMIT 1")
  suspend fun getUserByUsername(businessId: Long, username: String): UserEntity?

  @Query("SELECT COUNT(*) FROM users WHERE businessId = :businessId")
  suspend fun countUsersForBusiness(businessId: Long): Int

  @Query("SELECT COUNT(*) FROM users WHERE businessId = :businessId AND roleId = :roleId AND isActive = 1")
  suspend fun countActiveUsersByRole(businessId: Long, roleId: Long): Int

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertUser(user: UserEntity): Long

  @androidx.room.Update
  suspend fun updateUser(user: UserEntity)

  @Query("DELETE FROM users WHERE id = :userId")
  suspend fun deleteUserById(userId: Long)

  @Insert
  suspend fun insertAuditLog(log: AuditLogEntity): Long

  @Query("SELECT * FROM audit_logs WHERE businessId = :businessId ORDER BY timestamp DESC, id DESC LIMIT :limit")
  fun observeRecentAuditLogs(businessId: Long, limit: Int = 200): Flow<List<AuditLogEntity>>
}
