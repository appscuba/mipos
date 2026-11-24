package com.example.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.example.core.cost.CostEngine
import com.example.core.cost.PurchaseCostLineInput
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.entity.ExchangeRateEntity
import com.example.data.local.db.entity.SupplierEntity
import com.example.data.repository.ConfirmPurchaseRequest
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.PurchaseLineInput
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.math.RoundingMode

private data class UnitOption(
  val label: String,
  val factorToBase: BigDecimal,
)

private enum class PaymentQuickMode(val label: String) {
  FULL("Pago total"),
  CREDIT("Crédito (0)"),
  PARTIAL("Pago parcial"),
}

private val PURCHASE_PAYMENT_METHODS = listOf(
  "CASH" to "Efectivo",
  "TRANSFER" to "Transferencia",
  "USD" to "USD",
  "OTHER" to "Otro",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PurchaseFormDialog(
  businessId: Long,
  products: List<ProductWithDetails>,
  suppliers: List<SupplierEntity>,
  exchangeRates: List<ExchangeRateEntity>,
  primaryCurrency: String,
  gramsPerPound: BigDecimal,
  isSaving: Boolean,
  onQuickCreateSupplier: (name: String, phone: String, onCreated: (Long) -> Unit) -> Unit,
  onConfirm: (ConfirmPurchaseRequest) -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedSupplierId by remember { mutableStateOf<Long?>(suppliers.firstOrNull()?.id) }
  var supplierMenuExpanded by remember { mutableStateOf(false) }
  var showQuickSupplierDialog by remember { mutableStateOf(false) }

  var invoiceNumber by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  val activeRates = remember(exchangeRates, primaryCurrency) {
    exchangeRates.ifEmpty {
      listOf(
        ExchangeRateEntity(
          businessId = businessId,
          currencyCode = primaryCurrency,
          currencyName = primaryCurrency,
          symbol = "$",
          rateToBase = BigDecimal("1.0000"),
          isBaseCurrency = true,
        ),
      )
    }
  }
  var selectedCurrencyCode by remember { mutableStateOf(primaryCurrency) }
  var exchangeRateText by remember { mutableStateOf("1.0000") }

  var discountText by remember { mutableStateOf("") }
  var transportText by remember { mutableStateOf("") }
  var extraExpensesText by remember { mutableStateOf("") }

  var paymentMethod by remember { mutableStateOf("CASH") }
  var paymentQuickMode by remember { mutableStateOf(PaymentQuickMode.FULL) }
  var paidAmountText by remember { mutableStateOf("") }

  // Line builder state
  var selectedProduct by remember(products) { mutableStateOf(products.firstOrNull()) }
  var productMenuExpanded by remember { mutableStateOf(false) }

  val availableUnitOptions = remember(selectedProduct, gramsPerPound) {
    buildUnitOptionsForProduct(selectedProduct, gramsPerPound)
  }
  var selectedUnit by remember(selectedProduct, availableUnitOptions) {
    mutableStateOf(
      defaultUnitForProduct(selectedProduct, availableUnitOptions),
    )
  }
  var lineQtyText by remember { mutableStateOf("") }
  var lineUnitPriceText by remember { mutableStateOf("") }

  val lines = remember { mutableStateListOf<PurchaseLineInput>() }
  var errorText by remember { mutableStateOf<String?>(null) }

  val parsedRate = MoneyMath.parseDecimalOrNull(exchangeRateText)
    ?.takeIf { it > BigDecimal.ZERO }
    ?: BigDecimal.ONE
  val parsedDiscount = MoneyMath.parseMoneyOrNull(discountText)?.max(BigDecimal.ZERO) ?: MoneyMath.ZERO
  val parsedTransport = MoneyMath.parseMoneyOrNull(transportText)?.max(BigDecimal.ZERO) ?: MoneyMath.ZERO
  val parsedExtra = MoneyMath.parseMoneyOrNull(extraExpensesText)?.max(BigDecimal.ZERO) ?: MoneyMath.ZERO

  val subtotalOriginal = lines.fold(MoneyMath.ZERO) { acc, l ->
    MoneyMath.add(acc, MoneyMath.multiply(l.unitPriceOriginal, l.enteredQuantity))
  }
  val totalOriginal = subtotalOriginal
    .subtract(parsedDiscount)
    .add(parsedTransport)
    .add(parsedExtra)
    .max(BigDecimal.ZERO)
    .setScale(MoneyMath.MONEY_SCALE, RoundingMode.HALF_UP)

  val paidOriginal = when (paymentQuickMode) {
    PaymentQuickMode.FULL -> totalOriginal
    PaymentQuickMode.CREDIT -> MoneyMath.ZERO
    PaymentQuickMode.PARTIAL -> MoneyMath.parseMoneyOrNull(paidAmountText)?.max(BigDecimal.ZERO) ?: MoneyMath.ZERO
  }
  val pendingOriginal = totalOriginal.subtract(paidOriginal).max(BigDecimal.ZERO)
  val totalBase = MoneyMath.convertToBase(totalOriginal, parsedRate)
  val pendingBase = MoneyMath.convertToBase(pendingOriginal, parsedRate)

  val costPreview = remember(
    lines.toList(),
    parsedRate,
    parsedDiscount,
    parsedTransport,
    parsedExtra,
  ) {
    if (lines.isEmpty()) emptyList()
    else CostEngine.allocatePurchaseCosts(
      lines = lines.map {
        PurchaseCostLineInput(
          productId = it.productId,
          baseQuantityAdded = UnitConverter.toBaseUnits(it.enteredQuantity, it.conversionFactorToBase),
          lineSubtotalOriginal = MoneyMath.multiply(it.unitPriceOriginal, it.enteredQuantity),
        )
      },
      exchangeRateToBase = parsedRate,
      discountOriginal = parsedDiscount,
      transportOriginal = parsedTransport,
      extraExpensesOriginal = parsedExtra,
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.92f)
        .padding(12.dp)
        .widthIn(max = 760.dp)
        .heightIn(max = 860.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Nueva Compra de Mercancía", style = MaterialTheme.typography.titleLarge)
            Text(
              text = "Actualiza inventario y costo promedio ponderado",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar formulario de compra")
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // 1. Proveedor y Factura
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ExposedDropdownMenuBox(
              expanded = supplierMenuExpanded,
              onExpandedChange = { supplierMenuExpanded = !supplierMenuExpanded },
              modifier = Modifier.weight(1f),
            ) {
              val currentSupplierName = suppliers.firstOrNull { it.id == selectedSupplierId }?.name
                ?: "Sin proveedor (Contado)"
              OutlinedTextField(
                value = currentSupplierName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Proveedor") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierMenuExpanded) },
                modifier = Modifier
                  .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                  .fillMaxWidth()
                  .testTag("purchase_supplier_dropdown"),
              )
              ExposedDropdownMenu(
                expanded = supplierMenuExpanded,
                onDismissRequest = { supplierMenuExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text("Sin proveedor (Contado)") },
                  onClick = {
                    selectedSupplierId = null
                    supplierMenuExpanded = false
                  },
                )
                suppliers.forEach { sup ->
                  DropdownMenuItem(
                    text = { Text(sup.name) },
                    onClick = {
                      selectedSupplierId = sup.id
                      supplierMenuExpanded = false
                    },
                  )
                }
              }
            }

            OutlinedButton(
              onClick = { showQuickSupplierDialog = true },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .height(56.dp)
                .testTag("purchase_quick_supplier_btn"),
            ) {
              Icon(Icons.Filled.PersonAdd, contentDescription = "Crear proveedor rápido", modifier = Modifier.size(18.dp))
            }
          }

          BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 440.dp) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = invoiceNumber,
                  onValueChange = { invoiceNumber = it },
                  label = { Text("Nro. factura / nota") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("purchase_invoice_input"),
                )
                OutlinedTextField(
                  value = exchangeRateText,
                  onValueChange = { exchangeRateText = it },
                  label = { Text("Tasa a $primaryCurrency") },
                  singleLine = true,
                  enabled = selectedCurrencyCode != primaryCurrency,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.fillMaxWidth().testTag("purchase_exchange_rate_input"),
                )
              }
            } else {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                  value = invoiceNumber,
                  onValueChange = { invoiceNumber = it },
                  label = { Text("Nro. factura / nota") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("purchase_invoice_input"),
                )
                OutlinedTextField(
                  value = exchangeRateText,
                  onValueChange = { exchangeRateText = it },
                  label = { Text("Tasa a $primaryCurrency") },
                  singleLine = true,
                  enabled = selectedCurrencyCode != primaryCurrency,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f).testTag("purchase_exchange_rate_input"),
                )
              }
            }
          }

          // Moneda selector
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Moneda de compra", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              activeRates.forEach { rate ->
                FilterChip(
                  selected = selectedCurrencyCode == rate.currencyCode,
                  onClick = {
                    selectedCurrencyCode = rate.currencyCode
                    exchangeRateText = if (rate.isBaseCurrency) {
                      "1.0000"
                    } else {
                      MoneyMath.formatQuantity(rate.rateToBase)
                    }
                  },
                  label = { Text("${rate.currencyCode} (${MoneyMath.formatQuantity(rate.rateToBase)})") },
                  modifier = Modifier.testTag("purchase_currency_${rate.currencyCode}"),
                )
              }
            }
          }

          // 2. Agregar líneas de productos
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = MaterialTheme.shapes.medium,
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("Agregar Productos", style = MaterialTheme.typography.titleSmall)

              if (products.isEmpty()) {
                Text(
                  text = "No hay productos creados en el inventario. Crea al menos un producto primero.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              } else {
                ExposedDropdownMenuBox(
                  expanded = productMenuExpanded,
                  onExpandedChange = { productMenuExpanded = !productMenuExpanded },
                ) {
                  OutlinedTextField(
                    value = selectedProduct?.product?.name ?: "Seleccionar producto",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Producto") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productMenuExpanded) },
                    modifier = Modifier
                      .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                      .fillMaxWidth()
                      .testTag("purchase_product_dropdown"),
                  )
                  ExposedDropdownMenu(
                    expanded = productMenuExpanded,
                    onDismissRequest = { productMenuExpanded = false },
                  ) {
                    products.forEach { item ->
                      DropdownMenuItem(
                        text = {
                          Text("${item.product.name} (${ProductModality.fromCode(item.product.modality).label})")
                        },
                        onClick = {
                          selectedProduct = item
                          productMenuExpanded = false
                        },
                      )
                    }
                  }
                }

                // Unidad / Presentación chips
                if (availableUnitOptions.isNotEmpty()) {
                  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableUnitOptions.forEach { option ->
                      FilterChip(
                        selected = selectedUnit.label == option.label,
                        onClick = { selectedUnit = option },
                        label = {
                          val baseSym = if (selectedProduct?.product?.modality == ProductModality.WEIGHT.code) "g" else "u"
                          Text("${option.label} (${MoneyMath.formatQuantity(option.factorToBase)} $baseSym)")
                        },
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedTextField(
                    value = lineQtyText,
                    onValueChange = { lineQtyText = it },
                    label = { Text("Cantidad (${selectedUnit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("purchase_line_qty_input"),
                  )
                  OutlinedTextField(
                    value = lineUnitPriceText,
                    onValueChange = { lineUnitPriceText = it },
                    label = { Text("Precio x ${selectedUnit.label} ($selectedCurrencyCode)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("purchase_line_price_input"),
                  )
                }

                Button(
                  onClick = {
                    val prod = selectedProduct
                    val qty = MoneyMath.parseDecimalOrNull(lineQtyText)
                    val unitPrice = MoneyMath.parseMoneyOrNull(lineUnitPriceText)
                    when {
                      prod == null -> errorText = "Selecciona un producto."
                      qty == null || qty <= BigDecimal.ZERO -> errorText = "Ingresa una cantidad válida mayor que cero."
                      unitPrice == null || unitPrice < BigDecimal.ZERO -> errorText = "Ingresa un precio de compra válido."
                      else -> {
                        errorText = null
                        lines.add(
                          PurchaseLineInput(
                            productId = prod.product.id,
                            productName = prod.product.name,
                            unitLabel = selectedUnit.label,
                            enteredQuantity = qty,
                            conversionFactorToBase = selectedUnit.factorToBase,
                            unitPriceOriginal = unitPrice,
                          ),
                        )
                        lineQtyText = ""
                        lineUnitPriceText = ""
                      }
                    }
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("purchase_add_line_button"),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Agregar Producto a la Compra")
                }
              }
            }
          }

          // 3. Líneas agregadas + vista previa de costo prorrateado
          if (lines.isNotEmpty()) {
            Text("Detalle de Compra (${lines.size})", style = MaterialTheme.typography.titleSmall)
            lines.forEachIndexed { idx, line ->
              val preview = costPreview.getOrNull(idx)
              val lineSubtotalOrig = MoneyMath.multiply(line.unitPriceOriginal, line.enteredQuantity)
              val baseAdded = UnitConverter.toBaseUnits(line.enteredQuantity, line.conversionFactorToBase)
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
                    Text(line.productName, style = MaterialTheme.typography.titleSmall)
                    Text(
                      text = "${MoneyMath.formatQuantity(line.enteredQuantity)} ${line.unitLabel} × ${MoneyMath.format(line.unitPriceOriginal, selectedCurrencyCode)} = ${MoneyMath.format(lineSubtotalOrig, selectedCurrencyCode)}",
                      style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                      text = "Ingresa: ${MoneyMath.formatQuantity(baseAdded)} base" + (preview?.let {
                        " • Costo real: ${MoneyMath.format(it.lineTotalRealCostBase, primaryCurrency)}"
                      } ?: ""),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  IconButton(
                    onClick = { lines.removeAt(idx) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Icon(
                      Icons.Filled.DeleteOutline,
                      contentDescription = "Quitar ${line.productName}",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
          }

          // 4. Descuentos, Transporte y Gastos Adicionales (prorrateo)
          Text("Costos Adicionales y Descuentos ($selectedCurrencyCode)", style = MaterialTheme.typography.titleSmall)
          Text(
            text = "El transporte y gastos adicionales se prorratean en el costo de los productos (no se registran como gastos operativos).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = discountText,
              onValueChange = { discountText = it },
              label = { Text("Descuento") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f).testTag("purchase_discount_input"),
            )
            OutlinedTextField(
              value = transportText,
              onValueChange = { transportText = it },
              label = { Text("Transporte") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f).testTag("purchase_transport_input"),
            )
            OutlinedTextField(
              value = extraExpensesText,
              onValueChange = { extraExpensesText = it },
              label = { Text("Otros") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.weight(1f).testTag("purchase_extra_input"),
            )
          }

          // 5. Forma de pago y deuda
          Text("Pago y Crédito", style = MaterialTheme.typography.titleSmall)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PURCHASE_PAYMENT_METHODS.forEach { (code, label) ->
              FilterChip(
                selected = paymentMethod == code,
                onClick = { paymentMethod = code },
                label = { Text(label) },
              )
            }
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentQuickMode.entries.forEach { mode ->
              FilterChip(
                selected = paymentQuickMode == mode,
                onClick = {
                  paymentQuickMode = mode
                  if (mode == PaymentQuickMode.PARTIAL && paidAmountText.isBlank()) {
                    paidAmountText = MoneyMath.formatQuantity(totalOriginal.divide(BigDecimal("2"), 2, RoundingMode.HALF_UP))
                  }
                },
                label = { Text(mode.label) },
                modifier = Modifier.testTag("purchase_pay_mode_${mode.name}"),
              )
            }
          }

          if (paymentQuickMode == PaymentQuickMode.PARTIAL) {
            OutlinedTextField(
              value = paidAmountText,
              onValueChange = { paidAmountText = it },
              label = { Text("Monto pagado ($selectedCurrencyCode)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.fillMaxWidth().testTag("purchase_paid_amount_input"),
            )
          }

          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notas / Observaciones (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          // Resumen Financiero de la Compra
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            shape = MaterialTheme.shapes.medium,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              SummaryLine("Total factura ($selectedCurrencyCode)", MoneyMath.format(totalOriginal, selectedCurrencyCode))
              if (selectedCurrencyCode != primaryCurrency) {
                SummaryLine("Equivalente base ($primaryCurrency)", MoneyMath.format(totalBase, primaryCurrency))
              }
              SummaryLine("Pagado ahora", MoneyMath.format(paidOriginal, selectedCurrencyCode))
              SummaryLine(
                "Deuda pendiente",
                MoneyMath.format(pendingBase, primaryCurrency),
                highlight = pendingBase > BigDecimal.ZERO,
              )
              if (pendingBase > BigDecimal.ZERO && selectedSupplierId == null) {
                Text(
                  text = "⚠ Selecciona un proveedor para poder registrar deuda pendiente.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }

          if (errorText != null) {
            Text(
              text = errorText!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }

        Spacer(Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Text("Cancelar")
          }
          Button(
            onClick = {
              when {
                lines.isEmpty() -> errorText = "Agrega al menos un producto a la compra."
                parsedRate <= BigDecimal.ZERO -> errorText = "La tasa de cambio debe ser mayor que cero."
                paidOriginal > totalOriginal -> errorText = "El monto pagado no puede superar el total de la compra."
                pendingBase > BigDecimal.ZERO && selectedSupplierId == null ->
                  errorText = "Selecciona un proveedor para registrar la deuda pendiente."
                else -> {
                  errorText = null
                  onConfirm(
                    ConfirmPurchaseRequest(
                      businessId = businessId,
                      supplierId = selectedSupplierId,
                      invoiceNumber = invoiceNumber,
                      currencyCode = selectedCurrencyCode,
                      exchangeRateToBase = parsedRate,
                      discountOriginal = parsedDiscount,
                      transportCostOriginal = parsedTransport,
                      extraExpensesOriginal = parsedExtra,
                      paidAmountOriginal = paidOriginal,
                      paymentMethod = paymentMethod,
                      notes = notes,
                      lines = lines.toList(),
                    ),
                  )
                }
              }
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1.4f)
              .height(48.dp)
              .testTag("purchase_confirm_button"),
          ) {
            Text("Confirmar Compra", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }

  if (showQuickSupplierDialog) {
    QuickSupplierDialog(
      onCreate = { name, phone ->
        onQuickCreateSupplier(name, phone) { newId ->
          selectedSupplierId = newId
          showQuickSupplierDialog = false
        }
      },
      onDismiss = { showQuickSupplierDialog = false },
    )
  }
}

@Composable
private fun SummaryLine(
  label: String,
  value: String,
  highlight: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Text(
      text = value,
      style = MonoMetricMedium,
      fontWeight = FontWeight.Bold,
      color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun QuickSupplierDialog(
  onCreate: (String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuevo Proveedor Rápido") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("quick_supplier_name_input"),
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
        modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_supplier_save_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

private fun buildUnitOptionsForProduct(
  item: ProductWithDetails?,
  gramsPerPound: BigDecimal,
): List<UnitOption> {
  if (item == null) return emptyList()
  val isWeight = item.product.modality == ProductModality.WEIGHT.code
  val baseOptions = if (isWeight) {
    listOf(
      UnitOption("lb", gramsPerPound),
      UnitOption("g", BigDecimal.ONE),
      UnitOption("kg", UnitConverter.GRAMS_PER_KILOGRAM),
    )
  } else {
    listOf(UnitOption("u", BigDecimal.ONE))
  }
  val presOptions = item.presentations
    .filter { it.isBuyPresentation && it.baseUnitsPerPresentation > BigDecimal.ZERO }
    .map { UnitOption(it.name, it.baseUnitsPerPresentation) }
  return baseOptions + presOptions
}

private fun defaultUnitForProduct(
  item: ProductWithDetails?,
  options: List<UnitOption>,
): UnitOption {
  if (item == null || options.isEmpty()) return UnitOption("u", BigDecimal.ONE)
  val isWeight = item.product.modality == ProductModality.WEIGHT.code
  if (isWeight) {
    val preferred = when (item.product.defaultSaleUnit) {
      "GRAM" -> "g"
      "KG" -> "kg"
      else -> "lb"
    }
    return options.firstOrNull { it.label == preferred } ?: options.first()
  }
  return options.firstOrNull { it.label != "u" } ?: options.first()
}
