package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.license.PlanLimits
import com.example.data.license.PlanState
import com.example.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessSettingsScreen(
  viewModel: BusinessSettingsViewModel,
  onNavigateToBackups: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenLicense: () -> Unit = {},
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showResetWarningDialog by remember { mutableStateOf(false) }
  val picker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    viewModel.updateLogoUri(uri?.toString())
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier
        .widthIn(max = 640.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 168.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.testTag("settings_plan_card"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.Key,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Tu plan",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          when (state.planState) {
            PlanState.LICENSED -> {
              Text(
                text = "Licencia activa",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (state.planLicenseExpiresAt > 0L) {
                  "Válida hasta el ${formatPlanDate(state.planLicenseExpiresAt)}. " +
                    "Sin límites de productos, ventas ni clientes."
                } else {
                  "Licencia de por vida. Sin límites de productos, ventas ni clientes."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            PlanState.TRIAL -> {
              Text(
                text = "Prueba gratuita",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Te quedan ${state.planTrialDaysRemaining} días de prueba completa, sin límites. " +
                  "Al terminar pasarás al plan gratuito; activa una licencia para no perder funciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            PlanState.FREE -> {
              Text(
                text = "Plan gratuito",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Estás usando Stock+ con límites: ${PlanLimits.MAX_FREE_PRODUCTS} productos, " +
                  "${PlanLimits.MAX_FREE_SALES_PER_MONTH} ventas al mes y ${PlanLimits.MAX_FREE_CUSTOMERS} clientes. " +
                  "Reportes, monedas y respaldos requieren licencia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          OutlinedButton(
            onClick = onOpenLicense,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_open_license_button"),
          ) {
            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Activar licencia", fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Tema de la aplicación", style = MaterialTheme.typography.titleMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = state.themeMode == ThemeMode.DARK,
              onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
              label = { Text("Oscuro") },
              modifier = Modifier.testTag("settings_theme_dark"),
            )
            FilterChip(
              selected = state.themeMode == ThemeMode.LIGHT,
              onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
              label = { Text("Claro") },
              modifier = Modifier.testTag("settings_theme_light"),
            )
            FilterChip(
              selected = state.themeMode == ThemeMode.SYSTEM,
              onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
              label = { Text("Sistema") },
            )
          }
        }
      }

      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onNavigateToBackups).testTag("settings_backups_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
              Text("Respaldos y Seguridad de Datos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text("Exportar / Restaurar respaldo cifrado (.gcbk)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Box(
              modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                  picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
              contentAlignment = Alignment.Center,
            ) {
              if (state.logoUri != null) {
                AsyncImage(
                  model = state.logoUri,
                  contentDescription = "Logotipo del negocio",
                  modifier = Modifier.fillMaxSize(),
                  contentScale = ContentScale.Crop,
                )
              } else {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Cambiar logotipo")
              }
            }
            Text("Identidad y Contacto", style = MaterialTheme.typography.titleMedium)
          }

          OutlinedTextField(
            value = state.businessName,
            onValueChange = viewModel::updateBusinessName,
            label = { Text("Nombre del negocio") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_business_name"),
          )
          OutlinedTextField(
            value = state.businessType,
            onValueChange = viewModel::updateBusinessType,
            label = { Text("Actividad comercial") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::updatePhone,
            label = { Text("Teléfono") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::updateEmail,
            label = { Text("Correo electrónico") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::updateAddress,
            label = { Text("Dirección") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Parámetros Operativos", style = MaterialTheme.typography.titleMedium)
          OutlinedTextField(
            value = state.primaryCurrency,
            onValueChange = viewModel::updatePrimaryCurrency,
            label = { Text("Moneda principal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = state.gramsPerPoundInput,
            onValueChange = viewModel::updateGramsPerPound,
            label = { Text("Gramos por 1 libra") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_grams_per_pound"),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Permitir stock negativo", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = state.allowNegativeStock,
              onCheckedChange = viewModel::updateAllowNegativeStock,
            )
          }
        }
      }

      if (state.statusMessage != null) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (state.isError) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.tertiaryContainer
            },
          ),
        ) {
          Text(
            text = state.statusMessage.orEmpty(),
            color = if (state.isError) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onTertiaryContainer
            },
            modifier = Modifier.padding(14.dp),
          )
        }
      }

      Button(
        onClick = viewModel::saveSettings,
        enabled = !state.isSaving && !state.isResetting,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("settings_save_button"),
      ) {
        if (state.isSaving) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.Filled.Save, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Guardar Configuración", fontWeight = FontWeight.Bold)
        }
      }

      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.testTag("settings_support_card"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.SupportAgent,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Soporte y Acerca de Stock+",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }

          SupportContactRow(
            icon = Icons.Filled.Language,
            label = "Sitio web",
            value = "PedritoTech.cu",
            onClick = { openExternalUri(context, "https://pedritotech.cu") },
            modifier = Modifier.testTag("support_website_row"),
          )
          SupportContactRow(
            icon = Icons.Filled.Email,
            label = "Correo electrónico",
            value = "appscuba@gmail.com",
            onClick = {
              openExternalUri(
                context,
                "mailto:appscuba@gmail.com?subject=${Uri.encode("Soporte Stock+")}",
              )
            },
            modifier = Modifier.testTag("support_email_row"),
          )
          SupportContactRow(
            icon = Icons.AutoMirrored.Filled.Send,
            label = "Telegram",
            value = "@pedritotech",
            onClick = { openExternalUri(context, "https://t.me/pedritotech") },
            modifier = Modifier.testTag("support_telegram_row"),
          )
          SupportContactRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "WhatsApp",
            value = "+5353274074",
            onClick = { openExternalUri(context, "https://wa.me/5353274074") },
            modifier = Modifier.testTag("support_whatsapp_row"),
          )

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = "© 2026 Pedro Martínez Figueredo. Todos los derechos reservados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("support_copyright_text"),
          )
        }
      }

      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        ),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.WarningAmber,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
            )
            Text(
              text = "Zona de Peligro • Restablecer sistema",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text = "Deja todo Stock+ en cero. Elimina permanentemente productos, categorías, ventas, compras, clientes, deudas, movimientos de caja, gastos, usuarios y configuración.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = { showResetWarningDialog = true },
            enabled = !state.isResetting && !state.isSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_reset_system_button"),
          ) {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Restablecer todo (Dejar en cero)", fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }

  if (showResetWarningDialog) {
    AlertDialog(
      onDismissRequest = { if (!state.isResetting) showResetWarningDialog = false },
      modifier = Modifier.testTag("settings_reset_warning_dialog"),
      icon = {
        Icon(
          Icons.Filled.WarningAmber,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = {
        Text(
          "Advertencia: Dejar todo en cero",
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
          "¿Estás seguro de que deseas restablecer todo el sistema?\n\n" +
            "Esta acción borrará por completo:\n" +
            "• Todos los productos, precios y categorías\n" +
            "• Historial de ventas, compras y movimientos\n" +
            "• Clientes, proveedores y deudas pendientes\n" +
            "• Cajas diarias, gastos, usuarios y configuración\n\n" +
            "No podrás recuperar estos datos a menos que tengas un respaldo exportado previamente.",
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.resetAllSystemData {
              showResetWarningDialog = false
            }
          },
          enabled = !state.isResetting,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier.testTag("settings_reset_confirm_button"),
        ) {
          if (state.isResetting) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onError,
            )
          } else {
            Text("Sí, borrar todo")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showResetWarningDialog = false },
          enabled = !state.isResetting,
          modifier = Modifier.testTag("settings_reset_cancel_button"),
        ) {
          Text("Cancelar")
        }
      },
    )
  }
}

@Composable
private fun SupportContactRow(
  icon: ImageVector,
  label: String,
  value: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f),
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
      Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      }
    }
    Icon(
      Icons.AutoMirrored.Filled.OpenInNew,
      contentDescription = "Abrir $label",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
  }
}

private fun openExternalUri(context: android.content.Context, url: String) {
  runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}

private fun formatPlanDate(epochMs: Long): String =
  SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(epochMs))
