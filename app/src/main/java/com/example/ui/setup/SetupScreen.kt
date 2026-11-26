package com.example.ui.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BUSINESS_TYPES = listOf(
  "Tienda / Bodega",
  "Cafetería / Restaurante",
  "Alimentos y Víveres",
  "Comercio General",
  "Otro",
)

private val CURRENCY_PRESETS = listOf("CUP", "USD", "EUR", "MLC")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
  viewModel: SetupViewModel,
  onSetupComplete: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var restorePassphrase by remember { mutableStateOf("") }
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    viewModel.updateLogoUri(uri?.toString())
  }
  val restoreFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri != null) {
      restorePassphrase = ""
      viewModel.loadBackupForRestore(context, uri)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 640.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Hero Banner
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
          Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
            Image(
              painter = painterResource(id = R.drawable.img_store_onboarding_1795445355086),
              contentDescription = "Ilustración de gestión comercial",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xEE0B1020)),
                  ),
                ),
            )
            Column(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            ) {
              Text(
                text = "Configura tu Negocio",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFE5EAF5),
              )
              Text(
                text = "100% offline • Peso, unidades, costos y finanzas",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFA510),
              )
            }
          }
        }

        // Quick restore from existing backup
        OutlinedButton(
          onClick = { restoreFileLauncher.launch(arrayOf("*/*")) },
          enabled = !state.isSaving && !state.isRestoringBackup,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("setup_restore_backup_button"),
        ) {
          Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("¿Ya tienes copia de seguridad? Restaurar (.gcbk)")
        }

        // Theme quick choice
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val compact = maxWidth < 440.dp
            val themeDescription: @Composable () -> Unit = {
              Column(modifier = if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f)) {
                Text("Apariencia", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Elige tema oscuro o claro (editable luego)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            val themeOptions: @Composable () -> Unit = {
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                FilterChip(
                  selected = state.themeMode == ThemeMode.DARK,
                  onClick = { viewModel.selectThemeMode(ThemeMode.DARK) },
                  label = { Text("Oscuro", maxLines = 1) },
                  leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                  modifier = Modifier.testTag("setup_theme_dark"),
                )
                FilterChip(
                  selected = state.themeMode == ThemeMode.LIGHT,
                  onClick = { viewModel.selectThemeMode(ThemeMode.LIGHT) },
                  label = { Text("Claro", maxLines = 1) },
                  leadingIcon = { Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                  modifier = Modifier.testTag("setup_theme_light"),
                )
              }
            }
            if (compact) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                themeDescription()
                themeOptions()
              }
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                themeDescription()
                themeOptions()
              }
            }
          }
        }

        // Business Identity Card
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
                  .size(64.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer)
                  .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                  .clickable {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                  }
                  .testTag("setup_logo_picker"),
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
                  Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Seleccionar logotipo opcional",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text("Datos del Negocio", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Toca el círculo para añadir un logotipo opcional",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            OutlinedTextField(
              value = state.businessName,
              onValueChange = viewModel::updateBusinessName,
              label = { Text("Nombre del negocio *") },
              placeholder = { Text("Ej. Mercado El Sol") },
              leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_business_name_input"),
            )

            Text("Actividad comercial", style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              BUSINESS_TYPES.forEach { type ->
                FilterChip(
                  selected = state.businessType == type,
                  onClick = { viewModel.updateBusinessType(type) },
                  label = { Text(type) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
                )
              }
            }

            OutlinedTextField(
              value = state.phone,
              onValueChange = viewModel::updatePhone,
              label = { Text("Teléfono (opcional)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = state.address,
              onValueChange = viewModel::updateAddress,
              label = { Text("Dirección (opcional)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        // Currency Card
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Monedas y Tasa Manual", style = MaterialTheme.typography.titleMedium)
            Text("Moneda principal (contable)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CURRENCY_PRESETS.forEach { code ->
                FilterChip(
                  selected = state.primaryCurrency == code,
                  onClick = { viewModel.updatePrimaryCurrency(code) },
                  label = { Text(code) },
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Habilitar moneda adicional (ej. USD)", style = MaterialTheme.typography.bodyMedium)
                Text(
                  "Permite cobrar y comprar en otra divisa con tasa manual",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.enableSecondaryCurrency,
                onCheckedChange = viewModel::updateEnableSecondaryCurrency,
              )
            }

            if (state.enableSecondaryCurrency) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                OutlinedTextField(
                  value = state.secondaryCurrencyCode,
                  onValueChange = viewModel::updateSecondaryCurrencyCode,
                  label = { Text("Código") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                  value = state.secondaryCurrencyRate,
                  onValueChange = viewModel::updateSecondaryCurrencyRate,
                  label = { Text("1 ${state.secondaryCurrencyCode} = ? ${state.primaryCurrency}") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(2f),
                )
              }
            }
          }
        }

        // Units & Inventory Card
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.Scale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(Modifier.width(8.dp))
              Text("Unidades e Inventario", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedTextField(
              value = state.gramsPerPoundInput,
              onValueChange = viewModel::updateGramsPerPound,
              label = { Text("Equivalencia: gramos por 1 libra") },
              supportingText = { Text("Por defecto 1 libra = 500 g (¼ lb=125g, ½ lb=250g)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_grams_per_pound_input"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Permitir vender con stock negativo", style = MaterialTheme.typography.bodyMedium)
                Text(
                  "Desactivado protege contra ventas sin existencias",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = state.allowNegativeStock,
                onCheckedChange = viewModel::updateAllowNegativeStock,
              )
            }
          }
        }

        if (state.errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          ) {
            Text(
              text = state.errorMessage.orEmpty(),
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Button(
          onClick = { viewModel.submitSetup(onSetupComplete) },
          enabled = !state.isSaving,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("setup_submit_button"),
        ) {
          if (state.isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              strokeWidth = 2.5.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Guardar y Comenzar", fontWeight = FontWeight.Bold)
          }
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }

  state.pendingRestoreEnvelope?.let { envelope ->
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    AlertDialog(
      onDismissRequest = { if (!state.isRestoringBackup) viewModel.clearPendingRestore() },
      title = { Text("Restaurar Copia de Seguridad") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Negocio en respaldo: ${envelope.businessName}", style = MaterialTheme.typography.titleSmall)
          Text(
            "Fecha: ${dateFmt.format(Date(envelope.createdAt))} • Versión ${envelope.schemaVersion}",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            "Se restaurarán todos los usuarios, productos, inventario, ventas, compras, clientes, deudas, caja, gastos y configuración.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (envelope.requiresPassphrase) {
            OutlinedTextField(
              value = restorePassphrase,
              onValueChange = { restorePassphrase = it },
              label = { Text("Contraseña del respaldo *") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("setup_restore_passphrase_input"),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.confirmRestoreBackup(context, restorePassphrase.ifBlank { null }, onSetupComplete)
          },
          enabled = !state.isRestoringBackup && (!envelope.requiresPassphrase || restorePassphrase.isNotBlank()),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("setup_restore_confirm_button"),
        ) {
          Text("Restaurar Todo")
        }
      },
      dismissButton = {
        TextButton(
          onClick = viewModel::clearPendingRestore,
          enabled = !state.isRestoringBackup,
        ) {
          Text("Cancelar")
        }
      },
    )
  }
}
