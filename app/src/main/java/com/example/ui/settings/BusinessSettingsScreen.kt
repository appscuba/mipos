package com.example.ui.settings

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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessSettingsScreen(
  viewModel: BusinessSettingsViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
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
        enabled = !state.isSaving,
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

      Spacer(Modifier.height(16.dp))
    }
  }
}
