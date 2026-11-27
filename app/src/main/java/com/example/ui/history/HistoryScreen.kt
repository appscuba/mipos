package com.example.ui.history

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
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
import com.example.data.local.db.entity.SaleDetailEntity
import com.example.data.repository.SaleWithDetails
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
  viewModel: HistoryViewModel,
  modifier: Modifier = Modifier,
  freeVersionFooter: Boolean = false,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var selectedSaleId by remember { mutableStateOf<Long?>(null) }
  var receiptSale by remember { mutableStateOf<SaleWithDetails?>(null) }

  val selectedSale = remember(selectedSaleId, state.sales) {
    selectedSaleId?.let { id -> state.sales.firstOrNull { it.sale.id == id } }
  }

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
          .testTag("history_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Summary Card
        item {
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
                  Text(
                    text = "Ventas Netas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = MoneyMath.format(state.totalSalesNetBase, state.primaryCurrency),
                    style = MaterialTheme.typography.headlineSmall.merge(MonoMetricMedium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("history_total_sales"),
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "Ganancia Bruta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = MoneyMath.format(state.totalGrossProfitBase, state.primaryCurrency),
                    style = MonoMetricMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                  )
                }
              }

              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryStatusBadge(
                  text = "${state.activeSalesCount} activas",
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
                val annulledCount = state.sales.count { it.sale.status == "ANNULLED" }
                if (annulledCount > 0) {
                  HistoryStatusBadge(
                    text = "$annulledCount anuladas",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                  )
                }
              }
            }
          }
        }

        // Search: compact, single-line field with a capped width.
        item {
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            OutlinedTextField(
              value = state.searchQuery,
              onValueChange = viewModel::updateSearch,
              placeholder = {
                Text(
                  "Buscar por ticket, cliente o producto...",
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
              },
              leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .height(58.dp)
                .testTag("history_search_input"),
            )
          }
        }

        // Filters
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SaleHistoryFilter.entries) { filter ->
              FilterChip(
                selected = state.statusFilter == filter,
                onClick = { viewModel.selectFilter(filter) },
                label = { Text(filter.label) },
              )
            }
          }
        }

        if (state.filteredSales.isEmpty()) {
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
                  Icons.Filled.History,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text = if (state.sales.isEmpty()) "No hay ventas registradas" else "Sin resultados para este filtro",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Las ventas confirmadas en Tienda/POS aparecerán aquí con su costo histórico y ganancia bruta.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(state.filteredSales, key = { it.sale.id }) { item ->
            SaleHistoryCard(
              item = item,
              primaryCurrency = state.primaryCurrency,
              onClick = { selectedSaleId = item.sale.id },
            )
          }
        }
      }
    }
  }

  selectedSale?.let { item ->
    SaleDetailDialog(
      item = item,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onShowReceipt = { receiptSale = item },
      onReturnLine = { detailId, qty, reason ->
        viewModel.returnSaleItem(item.sale.id, detailId, qty, reason) {}
      },
      onAnnulSale = { reason ->
        viewModel.annulSale(item.sale.id, reason) {
          selectedSaleId = null
        }
      },
      onDismiss = { selectedSaleId = null },
    )
  }

  receiptSale?.let { item ->
    SaleReceiptDialog(
      business = state.business,
      saleWithDetails = item,
      onDismiss = { receiptSale = null },
      freeVersionFooter = freeVersionFooter,
    )
  }
}

@Composable
private fun SaleHistoryCard(
  item: SaleWithDetails,
  primaryCurrency: String,
  onClick: () -> Unit,
) {
  val s = item.sale
  val dateStr = remember(s.saleDate) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(s.saleDate))
  }
  val (statusLabel, statusContainer) = when (s.status) {
    "ANNULLED" -> "Anulada" to MaterialTheme.colorScheme.errorContainer
    "PARTIALLY_RETURNED" -> "Dev. parcial" to MaterialTheme.colorScheme.secondaryContainer
    else -> "Confirmada" to MaterialTheme.colorScheme.tertiaryContainer
  }

  val itemsSummary = item.details.take(3).joinToString(", ") {
    "${MoneyMath.formatQuantity(it.enteredQuantity)} ${it.unitLabel} ${it.productNameSnapshot}"
  } + if (item.details.size > 3) " (+${item.details.size - 3})" else ""

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("sale_card_${s.id}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(s.receiptNumber, style = MaterialTheme.typography.titleMedium)
          Text(
            text = "${item.customerName ?: "Cliente al contado"} • $dateStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        HistoryStatusBadge(text = statusLabel, containerColor = statusContainer)
      }

      if (itemsSummary.isNotBlank()) {
        Text(
          text = itemsSummary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = MoneyMath.format(s.totalBase, primaryCurrency),
            style = MonoMetricMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          if (s.status != "ANNULLED") {
            Text(
              text = "Ganancia: ${MoneyMath.format(s.grossProfitBase, primaryCurrency)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }
        }
        if (s.pendingDebtBase > BigDecimal.ZERO && s.status != "ANNULLED") {
          HistoryStatusBadge(
            text = "Crédito: ${MoneyMath.format(s.pendingDebtBase, primaryCurrency)}",
            containerColor = MaterialTheme.colorScheme.errorContainer,
          )
        }
      }
    }
  }
}

@Composable
private fun SaleDetailDialog(
  item: SaleWithDetails,
  primaryCurrency: String,
  isSaving: Boolean,
  onShowReceipt: () -> Unit,
  onReturnLine: (detailId: Long, qty: BigDecimal, reason: String) -> Unit,
  onAnnulSale: (reason: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val s = item.sale
  val isAnnulled = s.status == "ANNULLED"
  var returningDetail by remember { mutableStateOf<SaleDetailEntity?>(null) }
  var returnQtyText by remember { mutableStateOf("") }
  var returnReason by remember { mutableStateOf("") }
  var returnError by remember { mutableStateOf<String?>(null) }

  var confirmAnnul by remember { mutableStateOf(false) }
  var annulReason by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Detalle ${s.receiptNumber}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Cliente: ${item.customerName ?: "Al contado"} • Estado: ${s.status}",
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
          onClick = onShowReceipt,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sale_detail_receipt_btn"),
        ) {
          Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Ver / Compartir Comprobante")
        }

        HorizontalDivider()
        Text("Productos", style = MaterialTheme.typography.titleSmall)
        item.details.forEach { d ->
          val remainingBase = d.baseQuantityDeducted.subtract(d.returnedBaseQuantity)
          val canReturn = !isAnnulled && remainingBase > BigDecimal.ZERO
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = MaterialTheme.shapes.small,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(d.productNameSnapshot, style = MaterialTheme.typography.labelLarge)
              Text(
                text = "${MoneyMath.formatQuantity(d.enteredQuantity)} ${d.unitLabel} (×${MoneyMath.formatQuantity(d.appliedEquivalence)}) • Venta: ${MoneyMath.format(d.lineTotalBase, primaryCurrency)}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Costo histórico: ${MoneyMath.format(d.lineCostBase, primaryCurrency)} • Ganancia: ${MoneyMath.format(d.lineGrossProfitBase, primaryCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
              if (d.returnedBaseQuantity > BigDecimal.ZERO) {
                Text(
                  text = "Devuelto base: ${MoneyMath.formatQuantity(d.returnedBaseQuantity)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              if (canReturn) {
                TextButton(
                  onClick = {
                    returningDetail = d
                    returnQtyText = "1"
                    returnError = null
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("sale_return_line_btn_${d.id}"),
                ) {
                  Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Devolver")
                }
              }
            }
          }
        }

        returningDetail?.let { d ->
          HorizontalDivider()
          Text("Devolución de ${d.productNameSnapshot} (${d.unitLabel})", style = MaterialTheme.typography.titleSmall)
          OutlinedTextField(
            value = returnQtyText,
            onValueChange = { returnQtyText = it },
            label = { Text("Cantidad en ${d.unitLabel}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("sale_return_qty_input"),
          )
          OutlinedTextField(
            value = returnReason,
            onValueChange = { returnReason = it },
            label = { Text("Motivo de devolución") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          if (returnError != null) {
            Text(returnError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { returningDetail = null }, modifier = Modifier.weight(1f).height(48.dp)) {
              Text("Cancelar")
            }
            Button(
              onClick = {
                val qty = MoneyMath.parseDecimalOrNull(returnQtyText)
                if (qty == null || qty <= BigDecimal.ZERO) {
                  returnError = "Ingresa una cantidad válida."
                } else {
                  onReturnLine(d.id, qty, returnReason)
                  returningDetail = null
                }
              },
              enabled = !isSaving,
              modifier = Modifier.weight(1f).height(48.dp).testTag("sale_return_confirm_button"),
            ) {
              Text("Confirmar")
            }
          }
        }

        HorizontalDivider()
        Text("Total: ${MoneyMath.format(s.totalBase, primaryCurrency)}", style = MonoMetricMedium, fontWeight = FontWeight.Bold)
        Text(
          text = "Costo CMV: ${MoneyMath.format(s.totalCostOfGoodsBase, primaryCurrency)} • Ganancia bruta: ${MoneyMath.format(s.grossProfitBase, primaryCurrency)}",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          text = "Pagado: ${MoneyMath.format(s.paidBase, primaryCurrency)} • Saldo pendiente: ${MoneyMath.format(s.pendingDebtBase, primaryCurrency)}",
          style = MaterialTheme.typography.bodySmall,
        )

        if (confirmAnnul) {
          HorizontalDivider()
          OutlinedTextField(
            value = annulReason,
            onValueChange = { annulReason = it },
            label = { Text("Motivo de anulación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("sale_annul_reason_input"),
          )
          Button(
            onClick = { onAnnulSale(annulReason) },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sale_annul_confirm_button"),
          ) {
            Text("Confirmar Anulación No Destructiva")
          }
        }
      }
    },
    confirmButton = {
      if (!isAnnulled && !confirmAnnul) {
        OutlinedButton(
          onClick = { confirmAnnul = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("sale_annul_button"),
        ) {
          Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Anular Venta")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@Composable
private fun HistoryStatusBadge(
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
) {
  Surface(color = containerColor, shape = RoundedCornerShape(999.dp)) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}
