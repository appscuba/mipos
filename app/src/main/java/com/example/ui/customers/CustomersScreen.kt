package com.example.ui.customers

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import com.example.data.local.db.entity.CustomerEntity
import com.example.data.local.db.entity.DebtEntity
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.repository.CustomerDetailSnapshot
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val COLLECTION_METHODS = listOf(
  "CASH" to "Efectivo",
  "TRANSFER" to "Transferencia",
  "USD" to "USD",
  "OTHER" to "Otro",
)

@Composable
fun CustomersScreen(
  viewModel: CustomersViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
  var showCreateForm by remember { mutableStateOf(false) }
  var collectingDebt by remember { mutableStateOf<DebtEntity?>(null) }

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
        onClick = { showCreateForm = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("customers_add_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Nuevo cliente")
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
          .testTag("customers_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
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
              Column {
                Text(
                  text = "Cuentas por Cobrar (Fiados)",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = MoneyMath.format(state.totalReceivableBase, state.primaryCurrency),
                  style = MaterialTheme.typography.headlineSmall.merge(MonoMetricMedium),
                  color = if (state.totalReceivableBase > BigDecimal.ZERO) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.tertiary
                  },
                  modifier = Modifier.testTag("customers_total_receivable"),
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
              ) {
                Text(
                  text = "${state.customers.size} activos",
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }
          }
        }

        item {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearch,
            placeholder = { Text("Buscar cliente por nombre o teléfono...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("customers_search_input"),
          )
        }

        if (state.filteredCustomers.isEmpty()) {
          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  Icons.Filled.Groups,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text = if (state.customers.isEmpty()) {
                    "Sin clientes registrados"
                  } else {
                    "No se encontraron clientes"
                  },
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Toca + para registrar clientes frecuentes y controlar ventas a crédito y abonos.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(state.filteredCustomers, key = { it.id }) { customer ->
            CustomerCard(
              customer = customer,
              primaryCurrency = state.primaryCurrency,
              onClick = { viewModel.selectCustomer(customer.id) },
            )
          }
        }
      }
    }
  }

  if (showCreateForm || editingCustomer != null) {
    CustomerFormDialog(
      editing = editingCustomer,
      isSaving = state.isSaving,
      onSave = { name, phone, address, notes ->
        viewModel.saveCustomer(
          customerId = editingCustomer?.id,
          name = name,
          phone = phone,
          address = address,
          notes = notes,
        ) {
          showCreateForm = false
          editingCustomer = null
        }
      },
      onDismiss = {
        showCreateForm = false
        editingCustomer = null
      },
    )
  }

  state.selectedDetail?.customer?.let { currentCustomer ->
    CustomerDetailDialog(
      detail = state.selectedDetail!!,
      primaryCurrency = state.primaryCurrency,
      onEdit = {
        viewModel.selectCustomer(null)
        editingCustomer = currentCustomer
      },
      onArchive = {
        viewModel.archiveCustomer(currentCustomer.id) {}
      },
      onCollectDebt = { debt -> collectingDebt = debt },
      onDismiss = { viewModel.selectCustomer(null) },
    )
  }

  collectingDebt?.let { debt ->
    CollectCustomerDebtDialog(
      debt = debt,
      exchangeRates = state.exchangeRates,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onConfirmCollection = { amount, currency, rate, method, note ->
        viewModel.collectDebt(
          debtId = debt.id,
          amountOriginal = amount,
          currencyCode = currency,
          exchangeRateToBase = rate,
          paymentMethod = method,
          note = note,
        ) {
          collectingDebt = null
        }
      },
      onDismiss = { collectingDebt = null },
    )
  }
}

@Composable
private fun CustomerCard(
  customer: CustomerEntity,
  primaryCurrency: String,
  onClick: () -> Unit,
) {
  val hasDebt = customer.debtBalanceBase > BigDecimal.ZERO
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("customer_card_${customer.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(customer.name, style = MaterialTheme.typography.titleMedium)
        if (customer.phone.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (customer.address.isNotBlank()) {
          Text(
            customer.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = if (hasDebt) "Debe" else "Al día",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = MoneyMath.format(customer.debtBalanceBase, primaryCurrency),
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
          color = if (hasDebt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}

@Composable
private fun CustomerFormDialog(
  editing: CustomerEntity?,
  isSaving: Boolean,
  onSave: (name: String, phone: String, address: String, notes: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(editing?.name.orEmpty()) }
  var phone by remember { mutableStateOf(editing?.phone.orEmpty()) }
  var address by remember { mutableStateOf(editing?.address.orEmpty()) }
  var notes by remember { mutableStateOf(editing?.notes.orEmpty()) }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (editing == null) "Nuevo Cliente" else "Editar Cliente") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre del cliente *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("customer_name_input"),
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Teléfono") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("customer_phone_input"),
        )
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Dirección") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notas") },
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
          if (name.trim().isEmpty()) {
            error = "El nombre del cliente es obligatorio."
          } else {
            onSave(name, phone, address, notes)
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("customer_save_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
private fun CustomerDetailDialog(
  detail: CustomerDetailSnapshot,
  primaryCurrency: String,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
  onCollectDebt: (DebtEntity) -> Unit,
  onDismiss: () -> Unit,
) {
  val customer = detail.customer ?: return
  val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(customer.name) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (customer.phone.isNotBlank()) {
          Text("Teléfono: ${customer.phone}", style = MaterialTheme.typography.bodySmall)
        }
        if (customer.address.isNotBlank()) {
          Text("Dirección: ${customer.address}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
          text = "Saldo por cobrar: ${MoneyMath.format(customer.debtBalanceBase, primaryCurrency)}",
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
          color = if (customer.debtBalanceBase > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Editar")
          }
          OutlinedButton(
            onClick = onArchive,
            enabled = customer.debtBalanceBase <= BigDecimal.ZERO,
            modifier = Modifier.weight(1f).height(48.dp).testTag("customer_archive_button"),
          ) {
            Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Archivar")
          }
        }

        HorizontalDivider()
        Text("Cuentas por Cobrar (${detail.debts.size})", style = MaterialTheme.typography.titleSmall)
        if (detail.debts.isEmpty()) {
          Text("Sin créditos pendientes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          detail.debts.forEach { debt ->
            val canCollect = debt.status == "OPEN" || debt.status == "PARTIAL"
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
              shape = MaterialTheme.shapes.small,
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Venta #${debt.referenceId} • ${debt.status}", style = MaterialTheme.typography.labelLarge)
                  Text(dateFmt.format(Date(debt.createdAt)), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                  text = "Total: ${MoneyMath.format(debt.totalBase, primaryCurrency)} • Abonado: ${MoneyMath.format(debt.paidBase, primaryCurrency)}",
                  style = MaterialTheme.typography.bodySmall,
                )
                Text(
                  text = "Restante: ${MoneyMath.format(debt.remainingBase, primaryCurrency)}",
                  style = MonoMetricMedium,
                  color = if (canCollect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
                if (canCollect) {
                  Button(
                    onClick = { onCollectDebt(debt) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("customer_debt_collect_btn_${debt.id}"),
                  ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Registrar Abono")
                  }
                }
              }
            }
          }
        }

        HorizontalDivider()
        Text("Historial de Compras (${detail.sales.size})", style = MaterialTheme.typography.titleSmall)
        if (detail.sales.isEmpty()) {
          Text("Sin ventas asociadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          detail.sales.take(10).forEach { sale ->
            Text(
              text = "${sale.receiptNumber} (${sale.status}) • ${dateFmt.format(Date(sale.saleDate))} • ${MoneyMath.format(sale.totalBase, primaryCurrency)}",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectCustomerDebtDialog(
  debt: DebtEntity,
  exchangeRates: List<ExchangeRateEntity>,
  primaryCurrency: String,
  isSaving: Boolean,
  onConfirmCollection: (
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    paymentMethod: String,
    note: String,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  val activeRates = remember(exchangeRates, primaryCurrency) {
    exchangeRates.ifEmpty {
      listOf(
        ExchangeRateEntity(
          businessId = debt.businessId,
          currencyCode = primaryCurrency,
          currencyName = primaryCurrency,
          symbol = "$",
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
        ),
      )
    }
  }
  var selectedCurrency by remember { mutableStateOf(primaryCurrency) }
  var rateText by remember { mutableStateOf("1.0000") }
  var amountText by remember { mutableStateOf(MoneyMath.formatQuantity(debt.remainingBase)) }
  var paymentMethod by remember { mutableStateOf("CASH") }
  var note by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  val parsedRate = MoneyMath.parseDecimalOrNull(rateText)?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
  val parsedAmount = MoneyMath.parseMoneyOrNull(amountText) ?: MoneyMath.ZERO
  val convertedBase = MoneyMath.convertToBase(parsedAmount.max(BigDecimal.ZERO), parsedRate)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Cobrar Crédito #${debt.id}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Saldo pendiente: ${MoneyMath.format(debt.remainingBase, primaryCurrency)}",
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          activeRates.forEach { rate ->
            FilterChip(
              selected = selectedCurrency == rate.currencyCode,
              onClick = {
                selectedCurrency = rate.currencyCode
                val newRate = if (rate.isBaseCurrency) BigDecimal.ONE else rate.rateToBase
                rateText = MoneyMath.formatQuantity(newRate)
                val equivFull = debt.remainingBase.divide(newRate, 2, RoundingMode.HALF_UP)
                amountText = MoneyMath.formatQuantity(equivFull)
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
            modifier = Modifier.weight(1f).testTag("customer_debt_amount_input"),
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

        Text(
          text = "Equivalente en $primaryCurrency: ${MoneyMath.format(convertedBase, primaryCurrency)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          COLLECTION_METHODS.forEach { (code, label) ->
            FilterChip(
              selected = paymentMethod == code,
              onClick = { paymentMethod = code },
              label = { Text(label) },
            )
          }
        }

        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Nota (opcional)") },
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
            parsedAmount <= BigDecimal.ZERO -> error = "El abono debe ser mayor que cero."
            parsedRate <= BigDecimal.ZERO -> error = "La tasa debe ser positiva."
            convertedBase > debt.remainingBase -> error = "El monto convertido ($convertedBase) supera el saldo (${debt.remainingBase})."
            else -> onConfirmCollection(parsedAmount, selectedCurrency, parsedRate, paymentMethod, note)
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("customer_debt_confirm_button"),
      ) {
        Text("Confirmar Cobro")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
