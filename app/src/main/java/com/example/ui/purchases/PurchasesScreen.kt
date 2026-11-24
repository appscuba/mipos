package com.example.ui.purchases

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.money.MoneyMath
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.PurchaseWithSupplier
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurchasesScreen(
  viewModel: PurchasesViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var showCreateDialog by remember { mutableStateOf(false) }
  var selectedPurchase by remember { mutableStateOf<PurchaseWithSupplier?>(null) }

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
        onClick = { showCreateDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("purchases_add_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Nueva compra")
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
          .testTag("purchases_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
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
                    text = "Compras Confirmadas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = MoneyMath.format(state.totalConfirmedBase, state.primaryCurrency),
                    style = MaterialTheme.typography.headlineSmall.merge(MonoMetricMedium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("purchases_total_confirmed"),
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "Por pagar a proveedores",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = MoneyMath.format(state.totalPendingDebtBase, state.primaryCurrency),
                    style = MonoMetricMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.totalPendingDebtBase > BigDecimal.ZERO) {
                      MaterialTheme.colorScheme.error
                    } else {
                      MaterialTheme.colorScheme.tertiary
                    },
                  )
                }
              }

              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                  text = "${state.purchases.count { it.purchase.status == "CONFIRMED" }} confirmadas",
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
                StatusPill(
                  text = "${state.suppliers.size} proveedores",
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
              }
            }
          }
        }

        // Search
        item {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearch,
            placeholder = { Text("Buscar por factura, proveedor o producto...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("purchases_search_input"),
          )
        }

        // Status filters
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PurchaseStatusFilter.entries) { filter ->
              FilterChip(
                selected = state.statusFilter == filter,
                onClick = { viewModel.selectStatusFilter(filter) },
                label = { Text(filter.label) },
              )
            }
          }
        }

        if (state.filteredPurchases.isEmpty()) {
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
                  Icons.Filled.ShoppingBag,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text = if (state.purchases.isEmpty()) {
                    "No hay compras registradas"
                  } else {
                    "Sin compras para los filtros actuales"
                  },
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Toca + para registrar entradas de mercancía, distribuir transporte y actualizar costos promedio.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(state.filteredPurchases, key = { it.purchase.id }) { item ->
            PurchaseCard(
              item = item,
              products = state.products,
              primaryCurrency = state.primaryCurrency,
              onClick = { selectedPurchase = item },
            )
          }
        }
      }
    }
  }

  if (showCreateDialog && state.businessId > 0L) {
    PurchaseFormDialog(
      businessId = state.businessId,
      products = state.products,
      suppliers = state.suppliers,
      exchangeRates = state.exchangeRates,
      primaryCurrency = state.primaryCurrency,
      gramsPerPound = state.gramsPerPound,
      isSaving = state.isSaving,
      onQuickCreateSupplier = viewModel::quickCreateSupplier,
      onConfirm = { req ->
        viewModel.confirmPurchase(req) {
          showCreateDialog = false
        }
      },
      onDismiss = { showCreateDialog = false },
    )
  }

  selectedPurchase?.let { item ->
    PurchaseDetailDialog(
      item = item,
      products = state.products,
      primaryCurrency = state.primaryCurrency,
      isSaving = state.isSaving,
      onAnnul = { reason ->
        viewModel.annulPurchase(item.purchase.id, reason) {
          selectedPurchase = null
        }
      },
      onDismiss = { selectedPurchase = null },
    )
  }
}

@Composable
private fun PurchaseCard(
  item: PurchaseWithSupplier,
  products: List<ProductWithDetails>,
  primaryCurrency: String,
  onClick: () -> Unit,
) {
  val p = item.purchase
  val isAnnulled = p.status == "ANNULLED"
  val dateStr = remember(p.purchaseDate) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(p.purchaseDate))
  }
  val productMap = remember(products) { products.associateBy { it.product.id } }
  val itemsSummary = item.details.take(3).joinToString(", ") { d ->
    productMap[d.productId]?.product?.name ?: "#${d.productId}"
  } + if (item.details.size > 3) " (+${item.details.size - 3})" else ""

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("purchase_card_${p.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          val title = if (p.invoiceNumber.isNotBlank()) {
            "Factura ${p.invoiceNumber} (#${p.id})"
          } else {
            "Compra #${p.id}"
          }
          Text(title, style = MaterialTheme.typography.titleMedium)
          Text(
            text = "${item.supplierName ?: "Sin proveedor (Contado)"} • $dateStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          text = if (isAnnulled) "Anulada" else "Confirmada",
          containerColor = if (isAnnulled) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        )
      }

      if (itemsSummary.isNotBlank()) {
        Text(
          text = "${item.details.size} línea(s): $itemsSummary",
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
            text = MoneyMath.format(p.totalOriginal, p.currencyCode),
            style = MonoMetricMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          if (p.currencyCode != primaryCurrency) {
            Text(
              text = "Base: ${MoneyMath.format(p.totalConvertedBase, primaryCurrency)} (Tasa ${MoneyMath.formatQuantity(p.exchangeRateToBase)})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (p.pendingDebtBase > BigDecimal.ZERO && !isAnnulled) {
          StatusPill(
            text = "Deuda: ${MoneyMath.format(p.pendingDebtBase, primaryCurrency)}",
            containerColor = MaterialTheme.colorScheme.errorContainer,
          )
        }
      }
    }
  }
}

@Composable
private fun PurchaseDetailDialog(
  item: PurchaseWithSupplier,
  products: List<ProductWithDetails>,
  primaryCurrency: String,
  isSaving: Boolean,
  onAnnul: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val p = item.purchase
  val productMap = remember(products) { products.associateBy { it.product.id } }
  val isAnnulled = p.status == "ANNULLED"
  var confirmAnnul by remember { mutableStateOf(false) }
  var annulReason by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (p.invoiceNumber.isNotBlank()) "Compra #${p.id} (${p.invoiceNumber})" else "Compra #${p.id}",
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
          Text(item.supplierName ?: "Sin proveedor (Contado)", style = MaterialTheme.typography.titleSmall)
        }
        Text(
          text = "Estado: ${if (isAnnulled) "Anulada" else "Confirmada"} • Método: ${p.paymentMethod}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Moneda: ${p.currencyCode} (1 ${p.currencyCode} = ${MoneyMath.formatQuantity(p.exchangeRateToBase)} $primaryCurrency)",
          style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text("Líneas y Prorrateo de Costos", style = MaterialTheme.typography.titleSmall)
        item.details.forEach { d ->
          val prodName = productMap[d.productId]?.product?.name ?: "Producto #${d.productId}"
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = MaterialTheme.shapes.small,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(prodName, style = MaterialTheme.typography.labelLarge)
              Text(
                text = "${MoneyMath.formatQuantity(d.enteredQuantity)} ${d.presentationLabel} (×${MoneyMath.formatQuantity(d.appliedEquivalence)}) -> ${MoneyMath.formatQuantity(d.baseQuantityAdded)} base",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Prorrateo: ${MoneyMath.format(d.allocatedExtraCostBase, primaryCurrency)} • Costo real/${d.presentationLabel}: ${MoneyMath.format(d.realUnitCostBase, primaryCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Total línea: ${MoneyMath.format(d.lineTotalBase, primaryCurrency)}",
                style = MonoMetricMedium,
              )
            }
          }
        }

        HorizontalDivider()
        Text("Subtotal (${p.currencyCode}): ${MoneyMath.format(p.subtotalOriginal, p.currencyCode)}", style = MaterialTheme.typography.bodySmall)
        if (p.discountOriginal > BigDecimal.ZERO) {
          Text("Descuento: -${MoneyMath.format(p.discountOriginal, p.currencyCode)}", style = MaterialTheme.typography.bodySmall)
        }
        if (p.transportCostOriginal > BigDecimal.ZERO) {
          Text("Transporte prorrateado: +${MoneyMath.format(p.transportCostOriginal, p.currencyCode)}", style = MaterialTheme.typography.bodySmall)
        }
        if (p.extraExpensesOriginal > BigDecimal.ZERO) {
          Text("Gastos adicionales: +${MoneyMath.format(p.extraExpensesOriginal, p.currencyCode)}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
          text = "Total base: ${MoneyMath.format(p.totalConvertedBase, primaryCurrency)}",
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Pagado: ${MoneyMath.format(p.paidAmountBase, primaryCurrency)} • Deuda: ${MoneyMath.format(p.pendingDebtBase, primaryCurrency)}",
          style = MaterialTheme.typography.bodySmall,
        )

        if (confirmAnnul) {
          HorizontalDivider()
          OutlinedTextField(
            value = annulReason,
            onValueChange = { annulReason = it },
            label = { Text("Motivo de anulación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("purchase_annul_reason_input"),
          )
          Button(
            onClick = { onAnnul(annulReason) },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("purchase_annul_confirm_button"),
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
          modifier = Modifier.minimumInteractiveComponentSize().testTag("purchase_annul_button"),
        ) {
          Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Anular Compra")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@Composable
private fun StatusPill(
  text: String,
  containerColor: androidx.compose.ui.graphics.Color,
) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(999.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}
