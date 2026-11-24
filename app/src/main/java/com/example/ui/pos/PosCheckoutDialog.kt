package com.example.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.money.MoneyMath
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.repository.SalePaymentInput
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.math.RoundingMode

private enum class CheckoutPaymentMode(val label: String) {
  CASH("Efectivo"),
  TRANSFER("Transferencia"),
  USD("USD"),
  MIXED("Mixto / Parcial"),
  CREDIT("Crédito (Fiado)"),
}

private val MIXED_METHOD_OPTIONS = listOf(
  "CASH" to "Efectivo",
  "TRANSFER" to "Transferencia",
  "USD" to "USD",
  "OTHER" to "Otro",
)

private val BASE_QUICK_BILLS = listOf("50", "100", "200", "500", "1000")
private val USD_QUICK_BILLS = listOf("5", "10", "20", "50", "100")

private data class PaymentComputationResult(
  val appliedPayments: List<SalePaymentInput>,
  val enteredTotalBase: BigDecimal,
  val changeBase: BigDecimal,
)

@Composable
fun PosCheckoutDialog(
  state: PosUiState,
  onUpdateQty: (String, BigDecimal) -> Unit,
  onRemoveItem: (String) -> Unit,
  onDiscountChange: (BigDecimal) -> Unit,
  onSelectCustomer: (Long?) -> Unit,
  onQuickCreateCustomer: (String, String, (Long) -> Unit) -> Unit,
  onConfirmSale: (List<SalePaymentInput>, String) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
        .widthIn(max = 720.dp)
        .heightIn(max = 860.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Carrito y Cobro (${state.cartItems.size})", style = MaterialTheme.typography.titleLarge)
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar carrito")
          }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        PosCartCheckoutContent(
          state = state,
          onUpdateQty = onUpdateQty,
          onRemoveItem = onRemoveItem,
          onDiscountChange = onDiscountChange,
          onSelectCustomer = onSelectCustomer,
          onQuickCreateCustomer = onQuickCreateCustomer,
          onConfirmSale = onConfirmSale,
          modifier = Modifier.weight(1f, fill = false),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PosCartCheckoutContent(
  state: PosUiState,
  onUpdateQty: (String, BigDecimal) -> Unit,
  onRemoveItem: (String) -> Unit,
  onDiscountChange: (BigDecimal) -> Unit,
  onSelectCustomer: (Long?) -> Unit,
  onQuickCreateCustomer: (String, String, (Long) -> Unit) -> Unit,
  onConfirmSale: (List<SalePaymentInput>, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val usdRate = remember(state.exchangeRates) {
    state.exchangeRates.firstOrNull { it.currencyCode == "USD" }?.rateToBase ?: BigDecimal("1.0000")
  }
  val exactUsdAmount = remember(state.totalBase, usdRate) {
    if (usdRate > BigDecimal.ZERO) {
      state.totalBase.divide(usdRate, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
    } else {
      state.totalBase
    }
  }

  var discountText by remember(state.discountBase) {
    mutableStateOf(
      if (state.discountBase > BigDecimal.ZERO) MoneyMath.formatQuantity(state.discountBase) else "",
    )
  }
  var customerMenuExpanded by remember { mutableStateOf(false) }
  var showQuickCustomerDialog by remember { mutableStateOf(false) }

  var paymentMode by remember { mutableStateOf(CheckoutPaymentMode.CASH) }
  var cashTenderedText by remember(state.totalBase) {
    mutableStateOf(MoneyMath.formatQuantity(state.totalBase))
  }
  var transferTenderedText by remember(state.totalBase) {
    mutableStateOf(MoneyMath.formatQuantity(state.totalBase))
  }
  var usdTenderedText by remember(exactUsdAmount) {
    mutableStateOf(MoneyMath.formatQuantity(exactUsdAmount))
  }

  var mixedAmountText by remember { mutableStateOf("") }
  var mixedMethod by remember { mutableStateOf("CASH") }
  var mixedCurrency by remember { mutableStateOf(state.primaryCurrency) }
  val mixedPayments = remember { mutableStateListOf<SalePaymentInput>() }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  // Live payment calculation across all modes
  val paymentCalc: PaymentComputationResult = when (paymentMode) {
    CheckoutPaymentMode.CASH -> {
      val tendered = MoneyMath.parseMoneyOrNull(cashTenderedText) ?: MoneyMath.ZERO
      val applied = tendered.min(state.totalBase).max(MoneyMath.ZERO)
      val change = tendered.subtract(state.totalBase).max(MoneyMath.ZERO)
      val list = if (applied > BigDecimal.ZERO) {
        listOf(
          SalePaymentInput(
            paymentMethod = "CASH",
            currencyCode = state.primaryCurrency,
            originalAmount = applied,
            exchangeRateToBase = BigDecimal("1.0000"),
            isPhysicalCash = true,
            note = if (change > BigDecimal.ZERO) {
              "Recibido ${MoneyMath.format(tendered, state.primaryCurrency)} • Vuelto ${MoneyMath.format(change, state.primaryCurrency)}"
            } else {
              ""
            },
          ),
        )
      } else {
        emptyList()
      }
      PaymentComputationResult(list, tendered, change)
    }
    CheckoutPaymentMode.TRANSFER -> {
      val tendered = MoneyMath.parseMoneyOrNull(transferTenderedText) ?: MoneyMath.ZERO
      val applied = tendered.min(state.totalBase).max(MoneyMath.ZERO)
      val change = tendered.subtract(state.totalBase).max(MoneyMath.ZERO)
      val list = if (applied > BigDecimal.ZERO) {
        listOf(
          SalePaymentInput(
            paymentMethod = "TRANSFER",
            currencyCode = state.primaryCurrency,
            originalAmount = applied,
            exchangeRateToBase = BigDecimal("1.0000"),
            isPhysicalCash = false,
            note = if (change > BigDecimal.ZERO) {
              "Transferido ${MoneyMath.format(tendered, state.primaryCurrency)} • Devolver ${MoneyMath.format(change, state.primaryCurrency)}"
            } else {
              ""
            },
          ),
        )
      } else {
        emptyList()
      }
      PaymentComputationResult(list, tendered, change)
    }
    CheckoutPaymentMode.USD -> {
      val tenderedUsd = MoneyMath.parseMoneyOrNull(usdTenderedText) ?: MoneyMath.ZERO
      val tenderedBase = MoneyMath.convertToBase(tenderedUsd, usdRate)
      val appliedUsd = if (tenderedBase > state.totalBase && usdRate > BigDecimal.ZERO) {
        state.totalBase.divide(usdRate, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
      } else {
        tenderedUsd
      }
      val changeBase = tenderedBase.subtract(state.totalBase).max(MoneyMath.ZERO)
      val list = if (appliedUsd > BigDecimal.ZERO) {
        listOf(
          SalePaymentInput(
            paymentMethod = "USD",
            currencyCode = "USD",
            originalAmount = appliedUsd,
            exchangeRateToBase = usdRate,
            isPhysicalCash = false,
            note = if (changeBase > BigDecimal.ZERO) {
              "Recibido ${MoneyMath.format(tenderedUsd, "USD")} • Devolver ${MoneyMath.format(changeBase, state.primaryCurrency)}"
            } else {
              ""
            },
          ),
        )
      } else {
        emptyList()
      }
      PaymentComputationResult(list, tenderedBase, changeBase)
    }
    CheckoutPaymentMode.CREDIT -> {
      PaymentComputationResult(emptyList(), MoneyMath.ZERO, MoneyMath.ZERO)
    }
    CheckoutPaymentMode.MIXED -> {
      var remainingToCover = state.totalBase
      var totalEnteredBase = MoneyMath.ZERO
      val applied = mutableListOf<SalePaymentInput>()
      for (entry in mixedPayments) {
        val entryBase = MoneyMath.convertToBase(entry.originalAmount, entry.exchangeRateToBase)
        totalEnteredBase = MoneyMath.add(totalEnteredBase, entryBase)
        if (remainingToCover > BigDecimal.ZERO) {
          if (entryBase <= remainingToCover) {
            applied.add(entry)
            remainingToCover = MoneyMath.subtract(remainingToCover, entryBase)
          } else {
            val cappedOriginal = if (entry.exchangeRateToBase > BigDecimal.ZERO) {
              remainingToCover.divide(entry.exchangeRateToBase, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
            } else {
              remainingToCover
            }
            if (cappedOriginal > BigDecimal.ZERO) {
              applied.add(entry.copy(originalAmount = cappedOriginal))
            }
            remainingToCover = MoneyMath.ZERO
          }
        }
      }
      val change = totalEnteredBase.subtract(state.totalBase).max(MoneyMath.ZERO)
      PaymentComputationResult(applied, totalEnteredBase, change)
    }
  }

  val paidBasePreview = paymentCalc.appliedPayments.fold(MoneyMath.ZERO) { acc, p ->
    MoneyMath.add(acc, MoneyMath.convertToBase(p.originalAmount, p.exchangeRateToBase))
  }
  val changePreview = paymentCalc.changeBase
  val pendingDebtPreview = MoneyMath.subtract(state.totalBase, paidBasePreview).max(MoneyMath.ZERO)
  val selectedCustomer = state.customers.firstOrNull { it.id == state.selectedCustomerId }

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (state.cartItems.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.medium,
      ) {
        Text(
          text = "El carrito está vacío. Usa el botón + en cualquier producto o tócalo para elegir peso o presentación.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp),
        )
      }
    } else {
      // 1. Cliente (siempre visible arriba para vender rápido)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ExposedDropdownMenuBox(
          expanded = customerMenuExpanded,
          onExpandedChange = { customerMenuExpanded = !customerMenuExpanded },
          modifier = Modifier.weight(1f),
        ) {
          val currentCustomerLabel = selectedCustomer?.let {
            if (it.debtBalanceBase > BigDecimal.ZERO) "${it.name} (Debe ${MoneyMath.format(it.debtBalanceBase, state.primaryCurrency)})"
            else it.name
          } ?: "Cliente al contado"
          OutlinedTextField(
            value = currentCustomerLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Cliente") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerMenuExpanded) },
            modifier = Modifier
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
              .fillMaxWidth()
              .testTag("pos_customer_dropdown"),
          )
          ExposedDropdownMenu(
            expanded = customerMenuExpanded,
            onDismissRequest = { customerMenuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text("Cliente al contado") },
              onClick = {
                onSelectCustomer(null)
                customerMenuExpanded = false
              },
            )
            state.customers.forEach { customer ->
              DropdownMenuItem(
                text = { Text(customer.name) },
                onClick = {
                  onSelectCustomer(customer.id)
                  customerMenuExpanded = false
                },
              )
            }
          }
        }

        OutlinedButton(
          onClick = { showQuickCustomerDialog = true },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.height(56.dp).testTag("pos_quick_customer_btn"),
        ) {
          Icon(Icons.Filled.PersonAdd, contentDescription = "Nuevo cliente rápido", modifier = Modifier.size(18.dp))
        }
      }

      // 2. Forma de pago (compacta y directa)
      Text("Forma de Pago", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CheckoutPaymentMode.entries.forEach { mode ->
          FilterChip(
            selected = paymentMode == mode,
            onClick = {
              paymentMode = mode
              errorText = null
            },
            label = { Text(mode.label) },
            modifier = Modifier.testTag("pos_pay_mode_${mode.name}"),
          )
        }
      }

      if (paymentMode == CheckoutPaymentMode.CREDIT && state.selectedCustomerId == null && state.customers.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Elige rápido a quién fiar:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.customers.take(5).forEach { c ->
              FilterChip(
                selected = false,
                onClick = { onSelectCustomer(c.id) },
                label = { Text(c.name) },
              )
            }
          }
        }
      }

      if (paymentMode == CheckoutPaymentMode.CASH || paymentMode == CheckoutPaymentMode.TRANSFER || paymentMode == CheckoutPaymentMode.USD) {
        val isUsdMode = paymentMode == CheckoutPaymentMode.USD
        val activeCurrency = if (isUsdMode) "USD" else state.primaryCurrency
        val currentTenderedText = when (paymentMode) {
          CheckoutPaymentMode.CASH -> cashTenderedText
          CheckoutPaymentMode.TRANSFER -> transferTenderedText
          CheckoutPaymentMode.USD -> usdTenderedText
          else -> ""
        }
        val onTenderedUpdate: (String) -> Unit = { newValue ->
          when (paymentMode) {
            CheckoutPaymentMode.CASH -> cashTenderedText = newValue
            CheckoutPaymentMode.TRANSFER -> transferTenderedText = newValue
            CheckoutPaymentMode.USD -> usdTenderedText = newValue
            else -> {}
          }
          errorText = null
        }

        val fieldLabel = when (paymentMode) {
          CheckoutPaymentMode.CASH -> "¿Con cuánto paga? — Efectivo recibido (${state.primaryCurrency})"
          CheckoutPaymentMode.TRANSFER -> "¿Cuánto transfirió / paga? — Transferencia (${state.primaryCurrency})"
          CheckoutPaymentMode.USD -> "¿Cuánto entrega en USD? (Tasa: ${MoneyMath.formatQuantity(usdRate)} ${state.primaryCurrency})"
          else -> ""
        }

        OutlinedTextField(
          value = currentTenderedText,
          onValueChange = onTenderedUpdate,
          label = { Text(fieldLabel) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("pos_cash_tendered_input"),
        )

        // Quick denomination chips (100, 200, 500, 1000, Exacto)
        val quickBills = if (isUsdMode) USD_QUICK_BILLS else BASE_QUICK_BILLS
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SuggestionChip(
            onClick = {
              val exact = if (isUsdMode) exactUsdAmount else state.totalBase
              onTenderedUpdate(MoneyMath.formatQuantity(exact))
            },
            label = { Text("Exacto") },
            modifier = Modifier.testTag("pos_tendered_chip_exact"),
          )
          quickBills.forEach { bill ->
            SuggestionChip(
              onClick = { onTenderedUpdate(bill) },
              label = { Text("$bill $activeCurrency") },
              modifier = Modifier.testTag("pos_tendered_chip_$bill"),
            )
          }
        }
      }

      if (paymentMode == CheckoutPaymentMode.MIXED) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
          shape = MaterialTheme.shapes.small,
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              MIXED_METHOD_OPTIONS.forEach { (code, label) ->
                FilterChip(
                  selected = mixedMethod == code,
                  onClick = {
                    mixedMethod = code
                    if (code == "USD") mixedCurrency = "USD" else if (mixedCurrency == "USD" && code == "CASH") mixedCurrency = state.primaryCurrency
                  },
                  label = { Text(label) },
                )
              }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              val rates = state.exchangeRates.ifEmpty {
                listOf(
                  ExchangeRateEntity(
                    businessId = state.businessId,
                    currencyCode = state.primaryCurrency,
                    currencyName = state.primaryCurrency,
                    symbol = "$",
                    rateToBase = BigDecimal.ONE,
                    isBaseCurrency = true,
                  ),
                )
              }
              rates.forEach { r ->
                FilterChip(
                  selected = mixedCurrency == r.currencyCode,
                  onClick = { mixedCurrency = r.currencyCode },
                  label = { Text("${r.currencyCode} (${MoneyMath.formatQuantity(r.rateToBase)})") },
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = mixedAmountText,
                onValueChange = { mixedAmountText = it },
                label = { Text("Monto ($mixedCurrency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("pos_mixed_amount_input"),
              )
              Button(
                onClick = {
                  val amt = MoneyMath.parseMoneyOrNull(mixedAmountText)
                  if (amt != null && amt > BigDecimal.ZERO) {
                    val rate = state.exchangeRates.firstOrNull { it.currencyCode == mixedCurrency }?.rateToBase ?: BigDecimal.ONE
                    errorText = null
                    mixedPayments.add(
                      SalePaymentInput(
                        paymentMethod = mixedMethod,
                        currencyCode = mixedCurrency,
                        originalAmount = amt,
                        exchangeRateToBase = rate,
                        isPhysicalCash = mixedMethod == "CASH",
                      ),
                    )
                    mixedAmountText = ""
                  }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(52.dp).testTag("pos_mixed_add_button"),
              ) {
                Text("Agregar")
              }
            }
            mixedPayments.forEachIndexed { idx, p ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val baseVal = MoneyMath.convertToBase(p.originalAmount, p.exchangeRateToBase)
                Text(
                  text = "${p.paymentMethod}: ${MoneyMath.format(p.originalAmount, p.currencyCode)} (${MoneyMath.format(baseVal, state.primaryCurrency)})",
                  style = MaterialTheme.typography.bodySmall,
                )
                IconButton(
                  onClick = { mixedPayments.removeAt(idx) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Quitar pago", tint = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }

      // Highlighted Change / Remaining banner (visible immediately for Cash, Transfer, USD, Mixed)
      if (paymentMode != CheckoutPaymentMode.CREDIT) {
        if (changePreview > BigDecimal.ZERO) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("pos_change_to_return_banner"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Filled.Payments,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column {
                  Text(
                    text = if (paymentMode == CheckoutPaymentMode.TRANSFER) {
                      "A devolver al cliente"
                    } else {
                      "Cambio / Vuelto a devolver"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                  Text(
                    text = "Paga con ${MoneyMath.format(paymentCalc.enteredTotalBase, state.primaryCurrency)} de ${MoneyMath.format(state.totalBase, state.primaryCurrency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                  )
                }
              }
              Text(
                text = MoneyMath.format(changePreview, state.primaryCurrency),
                style = MaterialTheme.typography.titleLarge.merge(MonoMetricMedium),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.testTag("pos_change_to_return_text"),
              )
            }
          }
        } else if (pendingDebtPreview > BigDecimal.ZERO && paymentCalc.enteredTotalBase > BigDecimal.ZERO) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Falta por cubrir (pasa a crédito)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Text(
                text = MoneyMath.format(pendingDebtPreview, state.primaryCurrency),
                style = MonoMetricMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
      }

      // 3. Productos en el carrito (compacto)
      Text("Productos (${state.cartItems.size})", style = MaterialTheme.typography.titleSmall)
      state.cartItems.forEach { line ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
          shape = MaterialTheme.shapes.small,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(line.productWithDetails.product.name, style = MaterialTheme.typography.titleSmall)
              Text(
                text = "${MoneyMath.formatQuantity(line.enteredQuantity)} ${line.unitLabel} • ${MoneyMath.format(line.lineTotalBase, state.primaryCurrency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              val step = if (line.unitLabel == "lb") BigDecimal("0.25") else BigDecimal.ONE
              IconButton(
                onClick = { onUpdateQty(line.lineId, line.enteredQuantity.subtract(step)) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Filled.Remove, contentDescription = "Disminuir cantidad")
              }
              IconButton(
                onClick = { onUpdateQty(line.lineId, line.enteredQuantity.add(step)) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Filled.Add, contentDescription = "Aumentar cantidad")
              }
              IconButton(
                onClick = { onRemoveItem(line.lineId) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  Icons.Filled.DeleteOutline,
                  contentDescription = "Quitar línea",
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }

      // 4. Descuento y Nota opcional en una fila compacta
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = discountText,
          onValueChange = { text ->
            discountText = text
            val parsed = MoneyMath.parseMoneyOrNull(text) ?: MoneyMath.ZERO
            onDiscountChange(parsed.max(MoneyMath.ZERO))
          },
          label = { Text("Descuento") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.weight(0.45f).testTag("pos_discount_input"),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Nota (opcional)") },
          singleLine = true,
          modifier = Modifier.weight(0.55f),
        )
      }

      // 5. Resumen y Confirmar Venta
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (state.discountBase > BigDecimal.ZERO) {
            CheckoutSummaryRow("Subtotal", MoneyMath.format(state.subtotalBase, state.primaryCurrency))
            CheckoutSummaryRow("Descuento", "-${MoneyMath.format(state.discountBase, state.primaryCurrency)}")
          }
          CheckoutSummaryRow("Total a cobrar", MoneyMath.format(state.totalBase, state.primaryCurrency), bold = true)
          if (paymentCalc.enteredTotalBase > BigDecimal.ZERO && paymentMode != CheckoutPaymentMode.CREDIT) {
            CheckoutSummaryRow("Recibido", MoneyMath.format(paymentCalc.enteredTotalBase, state.primaryCurrency))
          }
          CheckoutSummaryRow("Aplicado a venta", MoneyMath.format(paidBasePreview, state.primaryCurrency))
          if (changePreview > BigDecimal.ZERO) {
            CheckoutSummaryRow("Devolver (Cambio)", MoneyMath.format(changePreview, state.primaryCurrency), bold = true)
          }
          if (pendingDebtPreview > BigDecimal.ZERO) {
            CheckoutSummaryRow(
              "Saldo a crédito",
              MoneyMath.format(pendingDebtPreview, state.primaryCurrency),
              isError = true,
            )
          }
        }
      }

      if (pendingDebtPreview > BigDecimal.ZERO && state.selectedCustomerId == null) {
        Text(
          text = "⚠ Selecciona un cliente arriba para ventas a crédito o con saldo pendiente.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      if (errorText != null) {
        Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }

      Button(
        onClick = {
          when {
            state.cartItems.isEmpty() -> errorText = "El carrito está vacío."
            paidBasePreview > state.totalBase -> errorText = "El total pagado supera el total de la venta."
            pendingDebtPreview > BigDecimal.ZERO && state.selectedCustomerId == null ->
              errorText = "Selecciona un cliente para registrar el saldo a crédito."
            else -> {
              errorText = null
              onConfirmSale(paymentCalc.appliedPayments, notes)
            }
          }
        },
        enabled = !state.isSaving,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("pos_confirm_sale_button"),
      ) {
        Text("Confirmar Venta", fontWeight = FontWeight.Bold)
      }
    }
  }

  if (showQuickCustomerDialog) {
    QuickCustomerDialog(
      onCreate = { name, phone ->
        onQuickCreateCustomer(name, phone) {
          showQuickCustomerDialog = false
        }
      },
      onDismiss = { showQuickCustomerDialog = false },
    )
  }
}

@Composable
private fun CheckoutSummaryRow(
  label: String,
  value: String,
  bold: Boolean = false,
  isError: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall)
    Text(
      text = value,
      style = MonoMetricMedium,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
internal fun QuickCustomerDialog(
  onCreate: (String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuevo Cliente Rápido") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("quick_customer_name_input"),
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Teléfono (opcional)") },
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
            error = "El nombre es obligatorio."
          } else {
            onCreate(name, phone)
          }
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_customer_save_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
