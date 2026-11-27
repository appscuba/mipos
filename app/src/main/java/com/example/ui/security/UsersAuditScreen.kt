package com.example.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.db.entity.AuditLogEntity
import com.example.data.local.db.entity.RoleEntity
import com.example.data.repository.AppPermissions
import com.example.data.repository.UserWithRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsersAuditScreen(
  viewModel: UsersAuditViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

  val canManageUsers = state.activeSession.hasPermission(AppPermissions.USERS_MANAGE)
  var showCreateDialog by remember { mutableStateOf(false) }
  var editingPinTarget by remember { mutableStateOf<UserWithRole?>(null) }
  var switchingTarget by remember { mutableStateOf<UserWithRole?>(null) }
  var editingUserTarget by remember { mutableStateOf<UserWithRole?>(null) }
  var deletingUserTarget by remember { mutableStateOf<UserWithRole?>(null) }
  var showClearAuditDialog by remember { mutableStateOf(false) }

  LaunchedEffect(state.feedbackMessage) {
    val msg = state.feedbackMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearFeedback()
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      if (canManageUsers) {
        FloatingActionButton(
          onClick = { showCreateDialog = true },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.testTag("users_add_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Nuevo usuario")
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 920.dp)
          .fillMaxSize()
          .testTag("users_audit_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Active session card
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sesión Activa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = state.activeSession.user?.fullName ?: "Propietario",
                  style = MaterialTheme.typography.titleLarge,
                  modifier = Modifier.testTag("security_active_user_name"),
                )
                Text(
                  text = "Rol: ${state.activeSession.role?.name ?: "Propietario"} • ${if (state.activeSession.hasPinConfigured) "PIN activo" else "Sin PIN"}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (state.activeSession.hasPinConfigured) {
                OutlinedButton(
                  onClick = viewModel::lockNow,
                  modifier = Modifier.height(48.dp).testTag("security_lock_button"),
                ) {
                  Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Bloquear")
                }
              }
            }
          }
        }

        item {
          Text("Perfiles y Usuarios (${state.users.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(state.users, key = { it.user.id }) { item ->
          val isCurrent = state.activeSession.user?.id == item.user.id
          Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.testTag("user_card_${item.user.username}"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                      if (item.role?.code == "OWNER") Icons.Filled.AdminPanelSettings else Icons.Filled.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(item.user.fullName, style = MaterialTheme.typography.titleMedium)
                    if (isCurrent) {
                      Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(
                          "ACTUAL",
                          style = MaterialTheme.typography.labelSmall,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
                  Text(
                    text = "@${item.user.username} • ${item.role?.name ?: "Rol"} • ${if (!item.user.pinHash.isNullOrBlank()) "PIN configurado" else "Sin PIN"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (canManageUsers && !isCurrent) {
                  Switch(
                    checked = item.user.isActive,
                    onCheckedChange = { active -> viewModel.toggleUserActive(item.user.id, active) },
                    // Colores fuertes: con los del tema el interruptor apagado se veía
                    // casi invisible (gris pálido sobre la tarjeta blanca).
                    colors = SwitchDefaults.colors(
                      checkedTrackColor = MaterialTheme.colorScheme.primary,
                      checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                      checkedBorderColor = MaterialTheme.colorScheme.primary,
                      uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                      uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                  )
                }
              }

              // Acciones principales: si hay dos botones se apilan a ancho completo
              // para que el texto nunca se corte en pantallas estrechas.
              val showSwitch = !isCurrent && item.user.isActive
              val showPin = canManageUsers || isCurrent
              val onSwitchClick: () -> Unit = {
                if (item.user.pinHash.isNullOrBlank()) {
                  viewModel.switchUser(item.user.id, null) {}
                } else {
                  switchingTarget = item
                }
              }
              if (showSwitch && showPin) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  SwitchUserButton(item = item, modifier = Modifier.fillMaxWidth().height(48.dp), onClick = onSwitchClick)
                  PinButton(item = item, modifier = Modifier.fillMaxWidth().height(48.dp), onClick = { editingPinTarget = item })
                }
              } else if (showSwitch || showPin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (showSwitch) {
                    SwitchUserButton(item = item, modifier = Modifier.weight(1f).height(48.dp), onClick = onSwitchClick)
                  }
                  if (showPin) {
                    PinButton(item = item, modifier = Modifier.weight(1f).height(48.dp), onClick = { editingPinTarget = item })
                  }
                }
              }

              if (canManageUsers) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { editingUserTarget = item },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("user_edit_btn_${item.user.username}"),
                  ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar")
                  }
                  if (!isCurrent) {
                    OutlinedButton(
                      onClick = { deletingUserTarget = item },
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                      modifier = Modifier.weight(1f).height(48.dp).testTag("user_delete_btn_${item.user.username}"),
                    ) {
                      Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Eliminar")
                    }
                  }
                }
              }
            }
          }
        }

        if (canManageUsers) {
          item {
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Bitácora de Auditoría (${state.auditLogs.size})", style = MaterialTheme.typography.titleMedium)
              }
              OutlinedButton(
                onClick = { showClearAuditDialog = true },
                enabled = state.auditLogs.isNotEmpty() && !state.isSaving,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("audit_clear_button"),
              ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Vaciar registro de auditoría")
              }
            }
          }

          items(state.auditLogs, key = { it.id }) { log ->
            Card(
              shape = MaterialTheme.shapes.small,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                  Text(dateFmt.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(log.details, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }
    }
  }

  if (showClearAuditDialog) {
    AlertDialog(
      onDismissRequest = { if (!state.isSaving) showClearAuditDialog = false },
      title = { Text("Vaciar registro de auditoría") },
      text = {
        Text("Se eliminarán los registros anteriores de esta tienda. Esta acción no se puede deshacer. Se conservará una nueva entrada indicando quién realizó el vaciado y cuándo.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearAuditLogs()
            showClearAuditDialog = false
          },
          enabled = !state.isSaving,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("audit_clear_confirm_button"),
        ) { Text("Vaciar registro") }
      },
      dismissButton = {
        TextButton(onClick = { showClearAuditDialog = false }, enabled = !state.isSaving) { Text("Cancelar") }
      },
    )
  }

  if (showCreateDialog) {
    CreateUserDialog(
      roles = state.roles,
      isSaving = state.isSaving,
      onCreate = { fullName, username, roleCode, pin ->
        viewModel.createUser(fullName, username, roleCode, pin) {
          showCreateDialog = false
        }
      },
      onDismiss = { showCreateDialog = false },
    )
  }

  editingPinTarget?.let { target ->
    EditPinDialog(
      user = target,
      isSaving = state.isSaving,
      onSavePin = { newPin ->
        viewModel.updateUserPin(target.user.id, newPin) {
          editingPinTarget = null
        }
      },
      onDismiss = { editingPinTarget = null },
    )
  }

  switchingTarget?.let { target ->
    SwitchUserPinDialog(
      user = target,
      isSaving = state.isSaving,
      onConfirm = { pin ->
        viewModel.switchUser(target.user.id, pin) {
          switchingTarget = null
        }
      },
      onDismiss = { switchingTarget = null },
    )
  }

  editingUserTarget?.let { target ->
    EditUserDialog(
      user = target,
      roles = state.roles,
      isSaving = state.isSaving,
      onSave = { fullName, username, roleCode ->
        viewModel.updateUserProfile(target.user.id, fullName, username, roleCode) {
          editingUserTarget = null
        }
      },
      onDismiss = { editingUserTarget = null },
    )
  }

  deletingUserTarget?.let { target ->
    DeleteUserDialog(
      user = target,
      isSaving = state.isSaving,
      onConfirm = {
        viewModel.deleteUser(target.user.id) {
          deletingUserTarget = null
        }
      },
      onDismiss = { deletingUserTarget = null },
    )
  }
}

@Composable
private fun SwitchUserButton(
  item: UserWithRole,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.testTag("user_switch_btn_${item.user.username}"),
  ) {
    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text("Cambiar a este usuario")
  }
}

@Composable
private fun PinButton(
  item: UserWithRole,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier.testTag("user_pin_btn_${item.user.username}"),
  ) {
    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(if (item.user.pinHash.isNullOrBlank()) "Asignar PIN" else "Cambiar PIN")
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateUserDialog(
  roles: List<RoleEntity>,
  isSaving: Boolean,
  onCreate: (fullName: String, username: String, roleCode: String, pin: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var fullName by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var roleCode by remember { mutableStateOf("CLERK") }
  var pin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuevo Usuario") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Nombre completo *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("user_fullname_input"),
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("Usuario (min. 3 letras) *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("user_username_input"),
        )
        Text("Rol asignado", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          roles.forEach { role ->
            FilterChip(
              selected = roleCode == role.code,
              onClick = { roleCode = role.code },
              label = { Text(role.name) },
              modifier = Modifier.testTag("user_role_chip_${role.code}"),
            )
          }
        }
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
          label = { Text("PIN numérico (4-8 dígitos, opcional)") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth().testTag("user_pin_input"),
        )
        if (error != null) {
          Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            fullName.trim().isEmpty() -> error = "El nombre completo es obligatorio."
            username.trim().length < 3 -> error = "El usuario debe tener al menos 3 caracteres."
            pin.isNotEmpty() && pin.length < 4 -> error = "El PIN debe tener entre 4 y 8 dígitos."
            else -> onCreate(fullName, username, roleCode, pin.ifEmpty { null })
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("user_save_button"),
      ) {
        Text("Crear Usuario")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
private fun EditPinDialog(
  user: UserWithRole,
  isSaving: Boolean,
  onSavePin: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("PIN de ${user.user.fullName}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
          label = { Text("Nuevo PIN (4-8 dígitos)") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth().testTag("edit_pin_input"),
        )
        if (error != null) {
          Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (pin.length !in 4..8) {
            error = "El PIN debe tener entre 4 y 8 dígitos."
          } else {
            onSavePin(pin)
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_pin_save_button"),
      ) {
        Text("Guardar PIN")
      }
    },
    dismissButton = {
      Row {
        if (!user.user.pinHash.isNullOrBlank()) {
          TextButton(onClick = { onSavePin(null) }, enabled = !isSaving) { Text("Quitar PIN") }
        }
        TextButton(onClick = onDismiss) { Text("Cancelar") }
      }
    },
  )
}

@Composable
private fun SwitchUserPinDialog(
  user: UserWithRole,
  isSaving: Boolean,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var pin by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ingresar como ${user.user.fullName}") },
    text = {
      OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
        label = { Text("Ingresa tu PIN") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().testTag("switch_pin_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(pin) },
        enabled = !isSaving && pin.length >= 4,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("switch_pin_confirm_button"),
      ) {
        Text("Entrar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditUserDialog(
  user: UserWithRole,
  roles: List<RoleEntity>,
  isSaving: Boolean,
  onSave: (fullName: String, username: String, roleCode: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var fullName by remember { mutableStateOf(user.user.fullName) }
  var username by remember { mutableStateOf(user.user.username) }
  var roleCode by remember { mutableStateOf(user.role?.code ?: "CLERK") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Editar Usuario") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Nombre completo *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("edit_user_fullname_input"),
        )
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("Usuario (min. 3 letras) *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("edit_user_username_input"),
        )
        Text("Rol asignado", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          roles.forEach { role ->
            FilterChip(
              selected = roleCode == role.code,
              onClick = { roleCode = role.code },
              label = { Text(role.name) },
              modifier = Modifier.testTag("edit_user_role_chip_${role.code}"),
            )
          }
        }
        if (error != null) {
          Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            fullName.trim().isEmpty() -> error = "El nombre completo es obligatorio."
            username.trim().length < 3 -> error = "El usuario debe tener al menos 3 caracteres."
            else -> onSave(fullName, username, roleCode)
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_user_save_button"),
      ) {
        Text("Guardar Cambios")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
private fun DeleteUserDialog(
  user: UserWithRole,
  isSaving: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Eliminar Usuario") },
    text = {
      Text("Se eliminará el usuario '${user.user.fullName}' (@${user.user.username}) de forma permanente. Esta acción no se puede deshacer.")
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        enabled = !isSaving,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_user_confirm_button"),
      ) {
        Text("Eliminar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
