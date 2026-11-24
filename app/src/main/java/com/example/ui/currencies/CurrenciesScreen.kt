package com.example.ui.currencies

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal

@Composable
fun CurrenciesScreen(
  viewModel: CurrenciesViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var showFormDialog by remember { mutableStateOf(false) }
  var editingRate by remember { mutableStateOf<ExchangeRateEntity?>(null) }

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
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingRate = null
          showFormDialog = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("currencies_add_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Agregar moneda")
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
          .testTag("currencies_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Moneda Base: ${state.primaryCurrency}", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "Actualizar una tasa manual solo aplica a operaciones nuevas. Todas las compras, ventas, pagos y gastos pasados conservan exactamente su tasa histórica.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        items(state.rates, key = { it.currencyCode }) { rate ->
          Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
              .fillMaxWidth()
              .clickable(enabled = !rate.isBaseCurrency) {
                editingRate = rate
                showFormDialog = true
              }
              .testTag("currency_card_${rate.currencyCode}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("${rate.currencyCode} (${rate.symbol})", style = MaterialTheme.typography.titleMedium)
                  if (rate.isBaseCurrency) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                      Text(
                        "BASE",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                  }
                }
                Text(rate.currencyName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = if (rate.isBaseCurrency) {
                    "1 ${rate.currencyCode} = 1.0000 ${state.primaryCurrency}"
                  } else {
                    "1 ${rate.currencyCode} = ${MoneyMath.formatQuantity(rate.rateToBase)} ${state.primaryCurrency}"
                  },
                  style = MonoMetricMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }

              if (!rate.isBaseCurrency) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = {
                      editingRate = rate
                      showFormDialog = true
                    },
                    modifier = Modifier.testTag("currency_edit_${rate.currencyCode}"),
                  ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar tasa ${rate.currencyCode}")
                  }
                  Switch(
                    checked = rate.isActive,
                    onCheckedChange = { active -> viewModel.toggleCurrencyActive(rate.currencyCode, active) },
                    modifier = Modifier.testTag("currency_switch_${rate.currencyCode}"),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showFormDialog) {
    CurrencyRateFormDialog(
      editing = editingRate,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onSave = { code, name, symbol, rate ->
        viewModel.saveCurrencyRate(code, name, symbol, rate) {
          showFormDialog = false
          editingRate = null
        }
      },
      onDismiss = {
        showFormDialog = false
        editingRate = null
      },
    )
  }
}

@Composable
private fun CurrencyRateFormDialog(
  editing: ExchangeRateEntity?,
  primaryCurrency: String,
  isSaving: Boolean,
  onSave: (code: String, name: String, symbol: String, rate: BigDecimal) -> Unit,
  onDismiss: () -> Unit,
) {
  var code by remember { mutableStateOf(editing?.currencyCode.orEmpty()) }
  var name by remember { mutableStateOf(editing?.currencyName.orEmpty()) }
  var symbol by remember { mutableStateOf(editing?.symbol.orEmpty()) }
  var rateText by remember { mutableStateOf(editing?.let { MoneyMath.formatQuantity(it.rateToBase) }.orEmpty()) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (editing == null) "Nueva Moneda" else "Editar ${editing.currencyCode}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = code,
          onValueChange = { code = it.uppercase() },
          label = { Text("Código ISO (ej. USD, EUR, MLC) *") },
          singleLine = true,
          enabled = editing == null,
          modifier = Modifier.fillMaxWidth().testTag("currency_code_input"),
        )
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre de la moneda") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("currency_name_input"),
        )
        OutlinedTextField(
          value = symbol,
          onValueChange = { symbol = it },
          label = { Text("Símbolo (ej. US$, €)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = rateText,
          onValueChange = { rateText = it },
          label = { Text("Tasa (1 moneda = X $primaryCurrency) *") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("currency_rate_input"),
        )
        if (error != null) {
          Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedRate = MoneyMath.parseDecimalOrNull(rateText)
          when {
            code.trim().length !in 2..6 -> error = "El código debe tener entre 2 y 6 letras."
            parsedRate == null || parsedRate <= BigDecimal.ZERO -> error = "Ingresa una tasa mayor que cero."
            else -> onSave(code.trim().uppercase(), name.trim(), symbol.trim(), parsedRate)
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("currency_save_button"),
      ) {
        Text("Guardar Tasa")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
