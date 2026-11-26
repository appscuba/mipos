package com.example.ui.finance

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.local.db.entity.ExpenseCategoryEntity
import com.example.data.repository.CashAdjustmentType
import com.example.data.repository.ExpenseWithCategory
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EXPENSE_PAYMENT_METHODS = listOf(
  "CASH" to "Efectivo",
  "TRANSFER" to "Transferencia",
  "USD" to "USD",
  "OTHER" to "Otro",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanceScreen(
  viewModel: FinanceViewModel,
  onOpenCashRegister: () -> Unit,
  onOpenCustomers: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var showExpenseForm by remember { mutableStateOf(false) }
  var showCategoriesDialog by remember { mutableStateOf(false) }
  var showAdjustmentDialog by remember { mutableStateOf(false) }
  var selectedExpense by remember { mutableStateOf<ExpenseWithCategory?>(null) }

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
      FloatingActionButton(
        onClick = { showExpenseForm = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("finance_add_expense_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Registrar gasto")
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
          .testTag("finance_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Financial KPIs
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("Resumen Financiero", style = MaterialTheme.typography.titleMedium)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                FinanceMetricBox(
                  label = "Ganancia bruta",
                  value = MoneyMath.format(state.grossProfitBase, state.primaryCurrency),
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
                FinanceMetricBox(
                  label = "Gastos operativos",
                  value = MoneyMath.format(state.operatingExpensesBase, state.primaryCurrency),
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.weight(1f),
                  valueTag = "finance_operating_expenses",
                )
              }
              HorizontalDivider()
              // La ganancia neta va a ancho completo: compartir renglón con
              // «Por cobrar / Por pagar» montaba un importe sobre el otro.
              Column {
                Text(
                  "Ganancia neta",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = MoneyMath.format(state.netProfitBase, state.primaryCurrency),
                  style = MonoMetricLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                  color = if (state.netProfitBase >= BigDecimal.ZERO) {
                    MaterialTheme.colorScheme.tertiary
                  } else {
                    MaterialTheme.colorScheme.error
                  },
                  modifier = Modifier.testTag("finance_net_profit"),
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                FinanceMetricBox(
                  label = "Por cobrar",
                  value = MoneyMath.format(state.customerReceivableBase, state.primaryCurrency),
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
                FinanceMetricBox(
                  label = "Por pagar",
                  value = MoneyMath.format(state.supplierPayableBase, state.primaryCurrency),
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
              }
              Text(
                text = "Capital aportado: ${MoneyMath.format(state.totalInvestmentsBase, state.primaryCurrency)} • Ingresos extra: ${MoneyMath.format(state.totalExtraIncomeBase, state.primaryCurrency)} • Retiros: ${MoneyMath.format(state.totalOwnerWithdrawalsBase, state.primaryCurrency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Cash drawer quick status + shortcut buttons
        item {
          Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Icon(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Column {
                    Text(
                      text = if (state.openCashSession != null) "Caja diaria ABIERTA" else "Caja diaria CERRADA",
                      style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                      text = state.openCashSession?.let {
                        "Efectivo esperado: ${MoneyMath.format(it.expectedCashBase, state.primaryCurrency)}"
                      } ?: "Abre la caja para controlar entradas y salidas de efectivo",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                OutlinedButton(
                  onClick = onOpenCashRegister,
                  modifier = Modifier.height(48.dp).testTag("finance_open_cash_button"),
                ) {
                  Text("Ir a Caja")
                }
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = { showAdjustmentDialog = true },
                  modifier = Modifier.height(48.dp).testTag("finance_cash_adjustment_button"),
                ) {
                  Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Ingreso / Retiro")
                }
                OutlinedButton(
                  onClick = { showCategoriesDialog = true },
                  modifier = Modifier.height(48.dp).testTag("finance_categories_button"),
                ) {
                  Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Categorías")
                }
                OutlinedButton(
                  onClick = onOpenCustomers,
                  modifier = Modifier.height(48.dp).testTag("finance_open_customers_button"),
                ) {
                  Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Clientes y Fiados")
                }
              }
            }
          }
        }

        // Expense Filters
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Gastos Operativos (${state.filteredExpenses.size})", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(Modifier.height(6.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseStatusFilter.entries.forEach { filter ->
              FilterChip(
                selected = state.statusFilter == filter,
                onClick = { viewModel.setStatusFilter(filter) },
                label = { Text(filter.label) },
              )
            }
          }
        }

        if (state.filteredExpenses.isEmpty()) {
          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Filled.AccountBalanceWallet,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(40.dp),
                )
                Text("Sin gastos registrados", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Los gastos de operación se descuentan de la ganancia neta, nunca se mezclan con las compras de inventario.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(state.filteredExpenses, key = { it.expense.id }) { item ->
            ExpenseCard(
              item = item,
              primaryCurrency = state.primaryCurrency,
              onClick = { selectedExpense = item },
            )
          }
        }
      }
    }
  }

  if (showExpenseForm) {
    ExpenseFormDialog(
      categories = state.expenseCategories,
      exchangeRates = state.exchangeRates,
      primaryCurrency = state.primaryCurrency,
      hasOpenCashSession = state.openCashSession != null,
      isSaving = state.isSaving,
      onSave = { catId, desc, currency, amount, rate, method, fromDrawer ->
        viewModel.recordExpense(
          expenseCategoryId = catId,
          description = desc,
          currencyCode = currency,
          originalAmount = amount,
          exchangeRateToBase = rate,
          paymentMethod = method,
          paidFromCashDrawer = fromDrawer,
        ) {
          showExpenseForm = false
        }
      },
      onDismiss = { showExpenseForm = false },
    )
  }

  if (showCategoriesDialog) {
    ExpenseCategoriesDialog(
      categories = state.expenseCategories,
      isSaving = state.isSaving,
      onAddCategory = { name -> viewModel.saveExpenseCategory(name) {} },
      onArchiveCategory = { catId -> viewModel.archiveExpenseCategory(catId) },
      onDismiss = { showCategoriesDialog = false },
    )
  }

  if (showAdjustmentDialog) {
    CashAdjustmentDialog(
      exchangeRates = state.exchangeRates,
      primaryCurrency = state.primaryCurrency,
      hasOpenCashSession = state.openCashSession != null,
      isSaving = state.isSaving,
      onConfirm = { type, amount, currency, rate, affectsCash, desc ->
        viewModel.recordCashAdjustment(
          type = type,
          amountOriginal = amount,
          currencyCode = currency,
          exchangeRateToBase = rate,
          affectsPhysicalCash = affectsCash,
          description = desc,
        ) {
          showAdjustmentDialog = false
        }
      },
      onDismiss = { showAdjustmentDialog = false },
    )
  }

  selectedExpense?.let { current ->
    ExpenseDetailDialog(
      item = current,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onAnnul = { reason ->
        viewModel.annulExpense(current.expense.id, reason) {
          selectedExpense = null
        }
      },
      onDismiss = { selectedExpense = null },
    )
  }
}

@Composable
private fun FinanceMetricBox(
  label: String,
  value: String,
  color: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
  valueTag: String? = null,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = MonoMetricMedium,
      fontWeight = FontWeight.Bold,
      color = color,
      modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier,
    )
  }
}

@Composable
private fun ExpenseCard(
  item: ExpenseWithCategory,
  primaryCurrency: String,
  onClick: () -> Unit,
) {
  val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
  val isAnnulled = item.expense.status == "ANNULLED"

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("expense_card_${item.expense.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(item.expense.description, style = MaterialTheme.typography.titleMedium)
          Surface(
            color = if (isAnnulled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = if (isAnnulled) "ANULADO" else item.categoryName,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        Text(
          text = "${dateFmt.format(Date(item.expense.expenseDate))} • ${item.expense.paymentMethod}" +
            if (item.expense.paidFromCashDrawer) " (Caja)" else "",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = MoneyMath.format(item.expense.convertedAmountBase, primaryCurrency),
        style = MonoMetricMedium,
        fontWeight = FontWeight.Bold,
        color = if (isAnnulled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseFormDialog(
  categories: List<ExpenseCategoryEntity>,
  exchangeRates: List<ExchangeRateEntity>,
  primaryCurrency: String,
  hasOpenCashSession: Boolean,
  isSaving: Boolean,
  onSave: (
    categoryId: Long,
    description: String,
    currencyCode: String,
    originalAmount: BigDecimal,
    exchangeRateToBase: BigDecimal,
    paymentMethod: String,
    paidFromCashDrawer: Boolean,
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
  var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
  var description by remember { mutableStateOf("") }
  var selectedCurrency by remember { mutableStateOf(primaryCurrency) }
  var rateText by remember { mutableStateOf("1.0000") }
  var amountText by remember { mutableStateOf("") }
  var paymentMethod by remember { mutableStateOf("CASH") }
  var paidFromDrawer by remember { mutableStateOf(hasOpenCashSession) }
  var drawerToggledByUser by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(categories) {
    if (selectedCatId <= 0L && categories.isNotEmpty()) {
      selectedCatId = categories.first().id
    }
  }
  LaunchedEffect(hasOpenCashSession) {
    if (!drawerToggledByUser && paymentMethod == "CASH") {
      paidFromDrawer = hasOpenCashSession
    }
  }

  val parsedRate = MoneyMath.parseDecimalOrNull(rateText)?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
  val parsedAmount = MoneyMath.parseMoneyOrNull(amountText) ?: MoneyMath.ZERO
  val convertedBase = MoneyMath.convertToBase(parsedAmount.max(BigDecimal.ZERO), parsedRate)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Registrar Gasto Operativo") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Categoría", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          categories.forEach { cat ->
            FilterChip(
              selected = selectedCatId == cat.id,
              onClick = { selectedCatId = cat.id },
              label = { Text(cat.name) },
            )
          }
        }

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Descripción del gasto *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("expense_description_input"),
        )

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
            label = { Text("Monto ($selectedCurrency) *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).testTag("expense_amount_input"),
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
          text = "Equivalente: ${MoneyMath.format(convertedBase, primaryCurrency)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          EXPENSE_PAYMENT_METHODS.forEach { (code, label) ->
            FilterChip(
              selected = paymentMethod == code,
              onClick = {
                paymentMethod = code
                if (code != "CASH") paidFromDrawer = false
              },
              label = { Text(label) },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Descontar de la caja física", style = MaterialTheme.typography.bodyMedium)
            Text(
              text = if (hasOpenCashSession) "Registrará salida en efectivo en caja" else "Abre caja primero para pagar desde caja",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = paidFromDrawer,
            onCheckedChange = {
              drawerToggledByUser = true
              paidFromDrawer = it
            },
            enabled = hasOpenCashSession,
            modifier = Modifier.testTag("expense_from_drawer_switch"),
          )
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
            selectedCatId <= 0L -> error = "Selecciona una categoría."
            description.trim().isEmpty() -> error = "La descripción es obligatoria."
            parsedAmount <= BigDecimal.ZERO -> error = "El monto debe ser mayor que cero."
            parsedRate <= BigDecimal.ZERO -> error = "La tasa debe ser positiva."
            else -> onSave(
              selectedCatId,
              description,
              selectedCurrency,
              parsedAmount,
              parsedRate,
              paymentMethod,
              paidFromDrawer,
            )
          }
        },
        enabled = !isSaving,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("expense_save_button"),
      ) {
        Text("Guardar Gasto")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
private fun ExpenseCategoriesDialog(
  categories: List<ExpenseCategoryEntity>,
  isSaving: Boolean,
  onAddCategory: (String) -> Unit,
  onArchiveCategory: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  var newName by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Categorías de Gastos") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Nueva categoría") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Button(
            onClick = {
              if (newName.trim().isNotEmpty()) {
                onAddCategory(newName)
                newName = ""
              }
            },
            enabled = !isSaving && newName.trim().isNotEmpty(),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text("Crear")
          }
        }
        HorizontalDivider()
        categories.forEach { cat ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
            if (!cat.isSystemDefault) {
              IconButton(onClick = { onArchiveCategory(cat.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Desactivar ${cat.name}")
              }
            }
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
private fun CashAdjustmentDialog(
  exchangeRates: List<ExchangeRateEntity>,
  primaryCurrency: String,
  hasOpenCashSession: Boolean,
  isSaving: Boolean,
  onConfirm: (
    type: CashAdjustmentType,
    amountOriginal: BigDecimal,
    currencyCode: String,
    exchangeRateToBase: BigDecimal,
    affectsPhysicalCash: Boolean,
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
  var affectsCash by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  val parsedAmount = MoneyMath.parseMoneyOrNull(amountText) ?: MoneyMath.ZERO
  val parsedRate = MoneyMath.parseDecimalOrNull(rateText)?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Movimiento de Caja y Capital") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!hasOpenCashSession) {
          Text(
            "Debes abrir la caja diaria primero para registrar ingresos adicionales, inversiones o retiros.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          CashAdjustmentType.entries.forEach { t ->
            FilterChip(
              selected = selectedType == t,
              onClick = { selectedType = t },
              label = { Text(t.label) },
              leadingIcon = {
                Icon(
                  if (t.isInflow) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
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
            modifier = Modifier.weight(1f).testTag("cash_adj_amount_input"),
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
          label = { Text("Motivo o descripción") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("cash_adj_desc_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Afecta efectivo físico en caja", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = affectsCash, onCheckedChange = { affectsCash = it })
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
            !hasOpenCashSession -> error = "Abre la caja diaria antes de registrar este movimiento."
            parsedAmount <= BigDecimal.ZERO -> error = "El monto debe ser mayor que cero."
            parsedRate <= BigDecimal.ZERO -> error = "La tasa debe ser positiva."
            else -> onConfirm(selectedType, parsedAmount, selectedCurrency, parsedRate, affectsCash, description)
          }
        },
        enabled = !isSaving && hasOpenCashSession,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("cash_adj_confirm_button"),
      ) {
        Text("Registrar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
private fun ExpenseDetailDialog(
  item: ExpenseWithCategory,
  primaryCurrency: String,
  isSaving: Boolean,
  onAnnul: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var reason by remember { mutableStateOf("") }
  val isAnnulled = item.expense.status == "ANNULLED"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Detalle de Gasto #${item.expense.id}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.expense.description, style = MaterialTheme.typography.titleMedium)
        Text("Categoría: ${item.categoryName}", style = MaterialTheme.typography.bodySmall)
        Text(
          "Monto: ${MoneyMath.format(item.expense.convertedAmountBase, primaryCurrency)} (${MoneyMath.format(item.expense.originalAmount, item.expense.currencyCode)})",
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
        )
        Text("Forma de pago: ${item.expense.paymentMethod}${if (item.expense.paidFromCashDrawer) " (desde caja)" else ""}", style = MaterialTheme.typography.bodySmall)
        Text("Estado: ${if (isAnnulled) "ANULADO" else "CONFIRMADO"}", style = MaterialTheme.typography.bodySmall)

        if (!isAnnulled) {
          HorizontalDivider()
          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Motivo de anulación (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      if (!isAnnulled) {
        Button(
          onClick = { onAnnul(reason.ifBlank { "Anulación manual" }) },
          enabled = !isSaving,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("expense_annul_button"),
        ) {
          Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Anular Gasto")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}
