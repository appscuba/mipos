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
import com.example.data.local.db.entity.CustomerEntity
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
  var mixedAmountText by remember { mutableStateOf("") }
  var mixedMethod by remember { mutableStateOf("CASH") }
  var mixedCurrency by remember { mutableStateOf(state.primaryCurrency) }
  val mixedPayments = remember { mutableStateListOf<SalePaymentInput>() }
  var notes by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val usdRate = remember(state.exchangeRates) {
    state.exchangeRates.firstOrNull { it.currencyCode == "USD" }?.rateToBase ?: BigDecimal("1.0000")
  }

  // Live payment calculation
  val computedPaymentsResult: Pair<List<SalePaymentInput>, BigDecimal> = when (paymentMode) {
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
          ),
        )
      } else {
        emptyList()
      }
      list to change
    }
    CheckoutPaymentMode.TRANSFER -> {
      val list = if (state.totalBase > BigDecimal.ZERO) {
        listOf(
          SalePaymentInput(
            paymentMethod = "TRANSFER",
            currencyCode = state.primaryCurrency,
            originalAmount = state.totalBase,
            exchangeRateToBase = BigDecimal("1.0000"),
            isPhysicalCash = false,
          ),
        )
      } else {
        emptyList()
      }
      list to MoneyMath.ZERO
    }
    CheckoutPaymentMode.USD -> {
      val usdAmount = if (usdRate > BigDecimal.ZERO) {
        state.totalBase.divide(usdRate, MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)
      } else {
        state.totalBase
      }
      val list = if (state.totalBase > BigDecimal.ZERO) {
        listOf(
          SalePaymentInput(
            paymentMethod = "USD",
            currencyCode = "USD",
            originalAmount = usdAmount,
            exchangeRateToBase = usdRate,
            isPhysicalCash = false,
          ),
        )
      } else {
        emptyList()
      }
      list to MoneyMath.ZERO
    }
    CheckoutPaymentMode.CREDIT -> emptyList<SalePaymentInput>() to MoneyMath.ZERO
    CheckoutPaymentMode.MIXED -> mixedPayments.toList() to MoneyMath.ZERO
  }

  val paidBasePreview = computedPaymentsResult.first.fold(MoneyMath.ZERO) { acc, p ->
    MoneyMath.add(acc, MoneyMath.convertToBase(p.originalAmount, p.exchangeRateToBase))
  }
  val changePreview = computedPaymentsResult.second
  val pendingDebtPreview = MoneyMath.subtract(state.totalBase, paidBasePreview).max(MoneyMath.ZERO)

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
          text = "El carrito está vacío. Toca un producto del catálogo para agregar peso, unidades o presentaciones.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp),
        )
      }
    } else {
      state.cartItems.forEach { line ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
          shape = MaterialTheme.shapes.small,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(line.productWithDetails.product.name, style = MaterialTheme.typography.titleSmall)
              Text(
                text = "${MoneyMath.formatQuantity(line.enteredQuantity)} ${line.unitLabel} (${MoneyMath.formatQuantity(line.baseQuantityDeducted)} ${line.productWithDetails.product.baseUnit})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = MoneyMath.format(line.lineTotalBase, state.primaryCurrency),
                style = MonoMetricMedium,
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

      // Descuento
      OutlinedTextField(
        value = discountText,
        onValueChange = { text ->
          discountText = text
          val parsed = MoneyMath.parseMoneyOrNull(text) ?: MoneyMath.ZERO
          onDiscountChange(parsed.max(MoneyMath.ZERO))
        },
        label = { Text("Descuento (${state.primaryCurrency})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("pos_discount_input"),
      )

      // Cliente selector
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
          val currentCustomer = state.customers.firstOrNull { it.id == state.selectedCustomerId }?.name
            ?: "Cliente al contado"
          OutlinedTextField(
            value = currentCustomer,
            onValueChange = {},
            readOnly = true,
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

      // Forma de pago
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

      if (paymentMode == CheckoutPaymentMode.CASH) {
        OutlinedTextField(
          value = cashTenderedText,
          onValueChange = { cashTenderedText = it },
          label = { Text("Efectivo recibido (${state.primaryCurrency})") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("pos_cash_tendered_input"),
        )
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
                    val newPaymentBase = MoneyMath.convertToBase(amt, rate)
                    if (MoneyMath.add(paidBasePreview, newPaymentBase) > state.totalBase) {
                      errorText = "La suma de pagos supera el total de la venta."
                    } else {
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

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Nota de venta (opcional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      // Totals Summary
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          CheckoutSummaryRow("Subtotal", MoneyMath.format(state.subtotalBase, state.primaryCurrency))
          if (state.discountBase > BigDecimal.ZERO) {
            CheckoutSummaryRow("Descuento", "-${MoneyMath.format(state.discountBase, state.primaryCurrency)}")
          }
          CheckoutSummaryRow("Total a cobrar", MoneyMath.format(state.totalBase, state.primaryCurrency), bold = true)
          CheckoutSummaryRow("Pagado", MoneyMath.format(paidBasePreview, state.primaryCurrency))
          if (changePreview > BigDecimal.ZERO) {
            CheckoutSummaryRow("Cambio / Vuelto", MoneyMath.format(changePreview, state.primaryCurrency))
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
          text = "⚠ Selecciona un cliente para ventas a crédito o con saldo pendiente.",
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
              onConfirmSale(computedPaymentsResult.first, notes)
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
private fun QuickCustomerDialog(
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
