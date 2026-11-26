package com.example.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.core.units.UnitConverter
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.local.db.entity.InventoryMovementEntity
import com.example.data.repository.ProductModality
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProductDetailScreen(
  viewModel: ProductDetailViewModel,
  categories: List<CategoryEntity>,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
  canManageProducts: Boolean = true,
  canAdjustStock: Boolean = true,
) {
  BackHandler(onBack = onNavigateBack)

  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var showAdjustDialog by remember { mutableStateOf(false) }
  var showEditDialog by remember { mutableStateOf(false) }
  var showAddPresDialog by remember { mutableStateOf(false) }
  var showArchiveConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(state.feedbackMessage) {
    val msg = state.feedbackMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearFeedback()
    }
  }

  val detail = state.detail

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      if (detail == null) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Producto no disponible", style = MaterialTheme.typography.titleMedium)
          Button(onClick = onNavigateBack) { Text("Volver") }
        }
      } else {
        val product = detail.product
        val isWeight = product.modality == ProductModality.WEIGHT.code
        val stockText = if (isWeight) {
          UnitConverter.formatWeightStock(product.stockBaseQty, state.gramsPerPound)
        } else {
          "${MoneyMath.formatQuantity(product.stockBaseQty)} unidades"
        }

        LazyColumn(
          modifier = Modifier
            .widthIn(max = 920.dp)
            .fillMaxSize()
            .testTag("detail_kardex_list"),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TextButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Volver al Inventario")
              }
              if (canManageProducts) {
                IconButton(onClick = { showArchiveConfirm = true }) {
                  Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Archivar producto",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }

          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = product.name,
                  style = MaterialTheme.typography.headlineSmall,
                  modifier = Modifier.testTag("detail_product_name"),
                )
                Text(
                  text = "${detail.category?.name ?: "Sin categoría"} • ${ProductModality.fromCode(product.modality).label}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = stockText,
                  style = MonoMetricLarge,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.testTag("detail_stock_text"),
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Precio de venta", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                      MoneyMath.format(product.salePrice, state.primaryCurrency),
                      style = MonoMetricMedium,
                      maxLines = 1,
                    )
                  }
                  Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Costo promedio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                      MoneyMath.format(product.avgCostPerSaleUnit, state.primaryCurrency),
                      style = MonoMetricMedium,
                      maxLines = 1,
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  if (canAdjustStock) {
                    Button(
                      onClick = { showAdjustDialog = true },
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail_adjust_button"),
                    ) {
                      Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Ajustar Stock", maxLines = 1)
                    }
                  }
                  if (canManageProducts) {
                    OutlinedButton(
                      onClick = { showEditDialog = true },
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                      Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Editar", maxLines = 1)
                    }
                  }
                }
              }
            }
          }

          // Presentations Card
          item {
            Card(
              shape = MaterialTheme.shapes.medium,
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
                  Text(
                    "Presentaciones y Equivalencias",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                  )
                  if (canManageProducts) {
                    TextButton(
                      onClick = { showAddPresDialog = true },
                      contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Añadir", maxLines = 1, softWrap = false)
                    }
                  }
                }
                if (detail.presentations.isEmpty()) {
                  Text(
                    text = "Sin presentaciones adicionales. Usa 'Añadir' para definir cajas, paquetes o sacos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                } else {
                  detail.presentations.forEach { pres ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(pres.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        val priceLabel = pres.salePrice?.let { " • ${MoneyMath.format(it, state.primaryCurrency)}" }.orEmpty()
                        Text(
                          "1 ${pres.name} = ${MoneyMath.formatQuantity(pres.baseUnitsPerPresentation)} ${product.baseUnit}$priceLabel",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      if (canManageProducts) {
                        IconButton(onClick = { viewModel.deactivatePresentation(state.businessId, pres.id) }) {
                          Icon(Icons.Filled.DeleteOutline, contentDescription = "Quitar ${pres.name}", modifier = Modifier.size(18.dp))
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Text("Kárdex de Movimientos (${state.movements.size})", style = MaterialTheme.typography.titleMedium)
            }
          }

          if (state.movements.isEmpty()) {
            item {
              Text(
                "Aún no hay movimientos registrados para este producto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(state.movements, key = { it.id }) { mov ->
              MovementKardexCard(mov = mov, baseUnit = product.baseUnit)
            }
          }
        }
      }
    }
  }

  if (showAdjustDialog && detail != null) {
    StockAdjustmentDialog(
      businessId = state.businessId,
      item = detail,
      gramsPerPound = state.gramsPerPound,
      onConfirm = { req ->
        viewModel.recordAdjustment(req) { showAdjustDialog = false }
      },
      onDismiss = { showAdjustDialog = false },
    )
  }

  if (showEditDialog && detail != null) {
    ProductFormDialog(
      businessId = state.businessId,
      categories = categories,
      gramsPerPound = state.gramsPerPound,
      primaryCurrency = state.primaryCurrency,
      editingItem = detail,
      onSave = { req ->
        viewModel.updateProduct(req) { showEditDialog = false }
      },
      onDismiss = { showEditDialog = false },
    )
  }

  if (showAddPresDialog && detail != null) {
    AddPresentationDialog(
      baseUnit = detail.product.baseUnit,
      onConfirm = { name, qty, price ->
        viewModel.savePresentation(
          businessId = state.businessId,
          name = name,
          baseUnitsPerPresentation = qty,
          salePrice = price,
          isBuy = true,
          isSell = true,
        ) { showAddPresDialog = false }
      },
      onDismiss = { showAddPresDialog = false },
    )
  }

  if (showArchiveConfirm) {
    AlertDialog(
      onDismissRequest = { showArchiveConfirm = false },
      title = { Text("Archivar producto") },
      text = { Text("El producto dejará de mostrarse en inventario activo, pero su historial de movimientos se conservará intacto.") },
      confirmButton = {
        Button(onClick = {
          showArchiveConfirm = false
          viewModel.archiveProduct(state.businessId, onNavigateBack)
        }) {
          Text("Archivar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancelar") }
      },
    )
  }
}

@Composable
private fun MovementKardexCard(
  mov: InventoryMovementEntity,
  baseUnit: String,
) {
  val dateText = remember(mov.timestamp) {
    SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(mov.timestamp))
  }
  val isPositive = mov.quantityBaseDelta >= BigDecimal.ZERO
  val deltaPrefix = if (isPositive) "+" else ""

  Card(
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = movementTypeLabel(mov.movementType),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text = "$deltaPrefix${MoneyMath.formatQuantity(mov.quantityBaseDelta)} $baseUnit",
          style = MonoMetricMedium,
          color = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${MoneyMath.formatQuantity(mov.enteredQuantity)} ${mov.enteredUnitLabel} (Equiv: ${MoneyMath.formatQuantity(mov.appliedEquivalence)} $baseUnit)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Saldo: ${MoneyMath.formatQuantity(mov.stockAfterBase)} $baseUnit",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
      if (mov.note.isNotBlank()) {
        Text(
          text = "${mov.note} • $dateText",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AddPresentationDialog(
  baseUnit: String,
  onConfirm: (String, BigDecimal, BigDecimal?) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var unitsText by remember { mutableStateOf("") }
  var priceText by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nueva Presentación") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it; error = null },
          label = { Text("Nombre (ej. Caja x 12)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = unitsText,
          onValueChange = { unitsText = it; error = null },
          label = { Text("Equivalencia en $baseUnit") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = priceText,
          onValueChange = { priceText = it },
          label = { Text("Precio de venta por presentación (opcional)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        val qty = MoneyMath.parseDecimalOrNull(unitsText)
        if (name.isBlank() || qty == null || qty <= BigDecimal.ZERO) {
          error = "Ingresa un nombre y una equivalencia válida (> 0)."
        } else {
          onConfirm(name.trim(), qty, MoneyMath.parseMoneyOrNull(priceText))
        }
      }) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

private fun movementTypeLabel(type: String): String = when (type) {
  "INITIAL_STOCK" -> "Inventario Inicial"
  "PURCHASE_IN" -> "Entrada por Compra"
  "SALE_OUT" -> "Salida por Venta"
  "SALE_RETURN_IN" -> "Devolución de Venta"
  "SALE_ANNUL_IN" -> "Anulación de Venta"
  "PURCHASE_ANNUL_OUT" -> "Anulación de Compra"
  "ADJUSTMENT_IN" -> "Ajuste (Entrada)"
  "ADJUSTMENT_OUT" -> "Ajuste (Salida)"
  else -> type
}
