package com.example.ui.backups

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupsScreen(
  viewModel: BackupsViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

  var exportPassphrase by remember { mutableStateOf("") }
  var restorePassphrase by remember { mutableStateOf("") }

  val createDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
  ) { uri ->
    if (uri != null) {
      viewModel.exportToUri(context, uri, exportPassphrase.ifBlank { null })
    }
  }

  val openDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      restorePassphrase = ""
      viewModel.loadBackupFromUri(context, uri)
    }
  }

  LaunchedEffect(state.feedbackMessage) {
    val msg = state.feedbackMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearFeedback()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
          .testTag("backups_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 168.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Respaldo Cifrado AES-256 Offline", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "Último respaldo: " + if (state.lastBackupTimestamp > 0L) {
                  dateFmt.format(Date(state.lastBackupTimestamp))
                } else {
                  "Aún no realizado"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("backups_last_timestamp"),
              )
              Text(
                text = "Respaldo integral 100% de toda la app: usuarios, roles y PIN, inventario (productos, categorías, presentaciones, movimientos y ajustes), ventas, compras, clientes, proveedores, deudas, cajas y movimientos, gastos, tasas de cambio, auditoría, apariencia y configuración, protegidos con AES-256-GCM y SHA-256.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Export card
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Exportar Respaldo", style = MaterialTheme.typography.titleMedium)
              }
              OutlinedTextField(
                value = exportPassphrase,
                onValueChange = { exportPassphrase = it },
                label = { Text("Contraseña de cifrado (opcional)") },
                placeholder = { Text("Déjala vacía para cifrado portátil automático") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("backup_export_passphrase_input"),
              )
              Button(
                onClick = {
                  val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                  createDocumentLauncher.launch("stock_plus_$fileStamp.gcbk")
                },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("backup_export_saf_button"),
              ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar Archivo (.gcbk)")
              }
              OutlinedButton(
                onClick = { viewModel.exportInternalLocalCopy(context, exportPassphrase.ifBlank { null }) },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("backup_export_local_button"),
              ) {
                Text("Crear Copia Rápida en el Dispositivo")
              }
            }
          }
        }

        // Restore card
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Restaurar desde Archivo", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "Antes de restaurar se valida la versión, integridad SHA-256 y se guarda automáticamente una copia de seguridad del estado actual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedButton(
                onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("backup_import_saf_button"),
              ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Seleccionar Archivo (.gcbk)")
              }
            }
          }
        }

        state.lastRestoreSummary?.let { summary ->
          item {
            Card(
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Column {
                  Text("Última restauración exitosa", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "${summary.businessName}: ${summary.usersCount} usuarios, ${summary.productsCount} productos (${summary.categoriesCount} categorías), ${summary.salesCount} ventas, ${summary.purchasesCount} compras, ${summary.customersCount} clientes, ${summary.suppliersCount} proveedores, ${summary.cashSessionsCount} cajas, ${summary.expensesCount} gastos y ${summary.debtsCount} deudas.",
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  state.pendingEnvelope?.let { envelope ->
    AlertDialog(
      onDismissRequest = viewModel::clearPendingRestore,
      title = { Text("Confirmar Restauración") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Negocio en respaldo: ${envelope.businessName}", style = MaterialTheme.typography.titleSmall)
          Text(
            "Fecha: ${dateFmt.format(Date(envelope.createdAt))} • Versión ${envelope.schemaVersion}",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            "Se creará un respaldo preventivo automático antes de reemplazar los datos actuales.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
          if (envelope.requiresPassphrase) {
            OutlinedTextField(
              value = restorePassphrase,
              onValueChange = { restorePassphrase = it },
              label = { Text("Contraseña del respaldo *") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("backup_restore_passphrase_input"),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { viewModel.confirmRestore(context, restorePassphrase.ifBlank { null }) },
          enabled = !state.isBusy && (!envelope.requiresPassphrase || restorePassphrase.isNotBlank()),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("backup_restore_confirm_button"),
        ) {
          Text("Restaurar Ahora")
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::clearPendingRestore) { Text("Cancelar") }
      },
    )
  }
}
