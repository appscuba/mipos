package com.example.ui.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.data.local.db.entity.CashMovementEntity
import com.example.data.local.db.entity.CashSessionEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.repository.CashAdjustmentType
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CashRegisterScreen(
  viewModel: CashRegisterViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

  var openingCashText by remember { mutableStateOf("0") }
  var openingNotes by remember { mutableStateOf("") }
  var showCloseDialog by remember { mutableStateOf(false) }
  var showAdjustmentDialog by remember { mutableStateOf(false) }

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
          .testTag("cash_movements_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val openSession = state.openSession
        if (openSession == null) {
          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Icon(Icons.Filled.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Text("Abrir Caja Diaria", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                  "Ingresa el efectivo físico inicial con el que arranca la jornada para llevar control exacto del arqueo.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                  value = openingCashText,
                  onValueChange = { openingCashText = it },
                  label = { Text("Efectivo inicial (${state.primaryCurrency})") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.fillMaxWidth().testTag("cash_opening_amount_input"),
                )
                OutlinedTextField(
                  value = openingNotes,
                  onValueChange = { openingNotes = it },
                  label = { Text("Notas de apertura (opcional)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
                Button(
                  onClick = {
                    val parsed = MoneyMath.parseMoneyOrNull(openingCashText) ?: MoneyMath.ZERO
                    viewModel.openSession(parsed, openingNotes) {
                      openingCashText = "0"
                      openingNotes = ""
                    }
                  },
                  enabled = !state.isSaving,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cash_open_button"),
                ) {
                  Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Abrir Caja")
                }
              }
            }
          }
        } else {
          item {
            OpenCashSummaryCard(
              session = openSession,
              primaryCurrency = state.primaryCurrency,
              openedFormatted = dateFmt.format(Date(openSession.openedAt)),
              onAdjustmentClick = { showAdjustmentDialog = true },
              onCloseClick = { showCloseDialog = true },
            )
          }

          item {
            Text(
              text = "Movimientos del Turno (${state.openSessionMovements.size})",
              style = MaterialTheme.typography.titleMedium,
            )
          }

          items(state.openSessionMovements, key = { it.id }) { mov ->
            CashMovementRowCard(movement = mov, primaryCurrency = state.primaryCurrency, dateFmt = dateFmt)
          }
        }

        if (state.sessionsHistory.isNotEmpty()) {
          item {
            Spacer(Modifier.height(4.dp))
            Text("Cierres Anteriores (${state.sessionsHistory.size})", style = MaterialTheme.typography.titleMedium)
          }
          items(state.sessionsHistory, key = { it.id }) { closed ->
            ClosedSessionCard(session = closed, primaryCurrency = state.primaryCurrency, dateFmt = dateFmt)
          }
        }
      }
    }
  }

  val currentOpen = state.openSession
  if (showCloseDialog && currentOpen != null) {
    CloseCashDialog(
      session = currentOpen,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onConfirm = { counted, notes ->
        viewModel.closeSession(currentOpen.id, counted, notes) {
          showCloseDialog = false
        }
      },
      onDismiss = { showCloseDialog = false },
    )
  }

  if (showAdjustmentDialog && currentOpen != null) {
    QuickCashAdjustmentDialog(
      exchangeRates = state.exchangeRates,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onConfirm = { type, amount, currency, rate, desc ->
        viewModel.recordCashAdjustment(type, amount, currency, rate, desc) {
          showAdjustmentDialog = false
        }
      },
      onDismiss = { showAdjustmentDialog = false },
    )
  }
}

@Composable
private fun OpenCashSummaryCard(
  session: CashSessionEntity,
  primaryCurrency: String,
  openedFormatted: String,
  onAdjustmentClick: () -> Unit,
  onCloseClick: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Caja Abierta #${session.id}", style = MaterialTheme.typography.titleLarge)
          Text(
            "Apertura: $openedFormatted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            text = "ABIERTA",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      HorizontalDivider()

      BreakdownLine("Efectivo inicial", MoneyMath.format(session.openingCashBase, primaryCurrency))
      BreakdownLine("+ Ventas en efectivo", MoneyMath.format(session.totalCashSalesBase, primaryCurrency))
      BreakdownLine("+ Ingresos y cobros en efectivo", MoneyMath.format(session.totalCashInflowsBase, primaryCurrency))
      BreakdownLine("- Gastos desde caja", MoneyMath.format(session.totalCashExpensesBase, primaryCurrency))
      BreakdownLine("- Retiros del propietario", MoneyMath.format(session.totalOwnerWithdrawalsBase, primaryCurrency))

      HorizontalDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Efectivo esperado en caja", style = MaterialTheme.typography.titleMedium)
        Text(
          text = MoneyMath.format(session.expectedCashBase, primaryCurrency),
          style = MonoMetricLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("cash_expected_amount"),
        )
      }

      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Transferencias (no suma al cajón)", style = MaterialTheme.typography.bodySmall)
          }
          Text(
            text = MoneyMath.format(session.totalTransferSalesBase, primaryCurrency),
            style = MonoMetricMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("cash_transfer_sales_amount"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onAdjustmentClick,
          modifier = Modifier.weight(1f).height(48.dp).testTag("cash_adjustment_button"),
        ) {
          Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Ingreso / Retiro")
        }
        Button(
          onClick = onCloseClick,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.weight(1f).height(48.dp).testTag("cash_close_button"),
        ) {
          Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Cerrar Caja")
        }
      }
    }
  }
}

@Composable
private fun BreakdownLine(label: String, amount: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(amount, style = MonoMetricMedium)
  }
}

@Composable
private fun CashMovementRowCard(
  movement: CashMovementEntity,
  primaryCurrency: String,
  dateFmt: SimpleDateFormat,
) {
  val isPositive = movement.signedAmountBase >= BigDecimal.ZERO
  Card(
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(movement.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
          text = "${dateFmt.format(Date(movement.timestamp))} • ${movement.movementType}" +
            if (!movement.affectsPhysicalCash) " (No efectivo)" else "",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = MoneyMath.format(movement.signedAmountBase, primaryCurrency),
        style = MonoMetricMedium,
        fontWeight = FontWeight.Bold,
        color = if (!movement.affectsPhysicalCash) {
          MaterialTheme.colorScheme.secondary
        } else if (isPositive) {
          MaterialTheme.colorScheme.tertiary
        } else {
          MaterialTheme.colorScheme.error
        },
      )
    }
  }
}

@Composable
private fun ClosedSessionCard(
  session: CashSessionEntity,
  primaryCurrency: String,
  dateFmt: SimpleDateFormat,
) {
  val diff = session.differenceBase ?: MoneyMath.ZERO
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Turno #${session.id}", style = MaterialTheme.typography.titleSmall)
        Text(
          text = session.closedAt?.let { dateFmt.format(Date(it)) } ?: "",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = "Esperado: ${MoneyMath.format(session.expectedCashBase, primaryCurrency)} • Contado: ${MoneyMath.format(session.countedCashBase ?: MoneyMath.ZERO, primaryCurrency)}",
        style = MaterialTheme.typography.bodySmall,
      )
      Text(
        text = "Diferencia: ${MoneyMath.format(diff, primaryCurrency)}",
        style = MonoMetricMedium,
        fontWeight = FontWeight.Bold,
        color = when {
          diff.compareTo(BigDecimal.ZERO) == 0 -> MaterialTheme.colorScheme.tertiary
          diff < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
          else -> MaterialTheme.colorScheme.primary
        },
      )
    }
  }
}

@Composable
private fun CloseCashDialog(
  session: CashSessionEntity,
  primaryCurrency: String,
  isSaving: Boolean,
  onConfirm: (countedCashBase: BigDecimal, notes: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var countedText by remember { mutableStateOf(MoneyMath.formatQuantity(session.expectedCashBase)) }
  var notes by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  val counted = MoneyMath.parseMoneyOrNull(countedText)
  val difference = counted?.let { MoneyMath.subtract(it, session.expectedCashBase) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Cerrar Caja Diaria #${session.id}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Efectivo esperado: ${MoneyMath.format(session.expectedCashBase, primaryCurrency)}",
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
          value = countedText,
          onValueChange = { countedText = it },
          label = { Text("Efectivo físico contado ($primaryCurrency) *") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("cash_counted_input"),
        )
        if (difference != null) {
          Text(
            text = "Diferencia: ${MoneyMath.format(difference, primaryCurrency)}",
            style = MonoMetricMedium,
            fontWeight = FontWeight.Bold,
            color = when {
              difference.compareTo(BigDecimal.ZERO) == 0 -> MaterialTheme.colorScheme.tertiary
              difference < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.testTag("cash_close_difference"),
          )
        }
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Observaciones del cierre") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (counted == null || counted < BigDecimal.ZERO) {
            error = "Ingresa un monto contado válido (>= 0)."
          } else {
            onConfirm(counted, notes)
          }
        },
        enabled = !isSaving,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("cash_close_confirm_button"),
      ) {
        Text("Confirmar Cierre")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCashAdjustmentDialog(
  exchangeRates: List<ExchangeRateEntity>,
  primaryCurrency: String,
  isSaving: Boolean,
  onConfirm: (
    type: CashAdjustmentType,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    description: String,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  val activeRates = remember(exchangeRates, primaryCurrency) {
    exchangeRates.ifEmpty {
      listOf(
        ExchangeRateEntity(
          businessId = 0L,
          currencyCode = primaryCurrency,
          currencyName = primaryCurrency,
          symbol = "$",
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
        ),
      )
    }
  }
  var selectedType by remember { mutableStateOf(CashAdjustmentType.EXTRA_INCOME) }
  var selectedCurrency by remember { mutableStateOf(primaryCurrency) }
  var rateText by remember { mutableStateOf("1.0000") }
  var amountText by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  val parsedAmount = MoneyMath.parseMoneyOrNull(amountText) ?: MoneyMath.ZERO
  val parsedRate = MoneyMath.parseDecimalOrNull(rateText)?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Movimiento de Efectivo en Caja") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          CashAdjustmentType.entries.forEach { t ->
            FilterChip(
              selected = selectedType == t,
              onClick = { selectedType = t },
              label = { Text(t.label) },
            )
          }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          activeRates.forEach { rate ->
            FilterChip(
              selected = selectedCurrency == rate.currencyCode,
              onClick = {
                selectedCurrency = rate.currencyCode
                val newRate = if (rate.isBaseCurrency) BigDecimal.ONE else rate.rateToBase
                rateText = MoneyMath.formatQuantity(newRate)
              },
              label = { Text(rate.currencyCode) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Monto ($selectedCurrency)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it },
            label = { Text("Tasa") },
            singleLine = true,
            enabled = selectedCurrency != primaryCurrency,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.7f),
          )
        }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Descripción") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
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
            parsedAmount <= BigDecimal.ZERO -> error = "El monto debe ser mayor que cero."
            parsedRate <= BigDecimal.ZERO -> error = "La tasa debe ser positiva."
            else -> onConfirm(selectedType, parsedAmount, selectedCurrency, parsedRate, description)
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
