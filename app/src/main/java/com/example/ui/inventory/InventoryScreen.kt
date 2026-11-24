package com.example.ui.inventory

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
  viewModel: InventoryViewModel,
  onOpenProductDetail: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var showProductDialog by remember { mutableStateOf(false) }
  var showCategoriesDialog by remember { mutableStateOf(false) }
  var adjustingItem by remember { mutableStateOf<ProductWithDetails?>(null) }

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
        onClick = { showProductDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("inventory_add_product_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Nuevo producto")
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
          .testTag("inventory_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Inventory Valuation & Alerts Strip
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = "Valor del Inventario",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = MoneyMath.format(state.totalInventoryValueBase, state.primaryCurrency),
                    style = MaterialTheme.typography.headlineSmall.merge(MonoMetricMedium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("inventory_total_value"),
                  )
                }
                OutlinedButton(
                  onClick = { showCategoriesDialog = true },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.testTag("inventory_categories_button"),
                ) {
                  Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Categorías")
                }
              }

              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryBadge("${state.items.size} productos", MaterialTheme.colorScheme.primaryContainer)
                SummaryBadge(
                  "1 lb = ${MoneyMath.formatQuantity(state.gramsPerPound)} g",
                  MaterialTheme.colorScheme.secondaryContainer,
                )
                if (state.lowStockCount > 0) {
                  SummaryBadge("${state.lowStockCount} stock bajo", MaterialTheme.colorScheme.secondaryContainer)
                }
                if (state.outOfStockCount > 0) {
                  SummaryBadge("${state.outOfStockCount} agotados", MaterialTheme.colorScheme.errorContainer)
                }
              }
            }
          }
        }

        // Search & Filters
        item {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearch,
            placeholder = { Text("Buscar por nombre, SKU o código...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("inventory_search_input"),
          )
        }

        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StockStatusFilter.entries) { filter ->
              FilterChip(
                selected = state.stockFilter == filter,
                onClick = { viewModel.selectStockFilter(filter) },
                label = { Text(filter.label) },
              )
            }
            item {
              FilterChip(
                selected = state.selectedModality == null,
                onClick = { viewModel.selectModality(null) },
                label = { Text("Todas modalidades") },
              )
            }
            items(ProductModality.entries) { mod ->
              FilterChip(
                selected = state.selectedModality == mod,
                onClick = {
                  viewModel.selectModality(if (state.selectedModality == mod) null else mod)
                },
                label = { Text(mod.label) },
              )
            }
          }
        }

        if (state.categories.isNotEmpty()) {
          item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              item {
                FilterChip(
                  selected = state.selectedCategoryId == null,
                  onClick = { viewModel.selectCategory(null) },
                  label = { Text("Todas categorías") },
                )
              }
              items(state.categories, key = { it.id }) { cat ->
                FilterChip(
                  selected = state.selectedCategoryId == cat.id,
                  onClick = {
                    viewModel.selectCategory(if (state.selectedCategoryId == cat.id) null else cat.id)
                  },
                  label = { Text(cat.name) },
                )
              }
            }
          }
        }

        if (state.filteredItems.isEmpty()) {
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
                  Icons.Filled.Inventory2,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text = if (state.items.isEmpty()) {
                    "Tu inventario está vacío"
                  } else {
                    "Sin resultados para estos filtros"
                  },
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Toca + para crear productos por peso (gramos/libras), unidades o cajas/presentaciones.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(state.filteredItems, key = { it.product.id }) { item ->
            ProductInventoryCard(
              item = item,
              gramsPerPound = state.gramsPerPound,
              primaryCurrency = state.primaryCurrency,
              onClick = { onOpenProductDetail(item.product.id) },
              onAdjustClick = { adjustingItem = item },
            )
          }
        }
      }
    }
  }

  if (showProductDialog && state.businessId > 0L) {
    ProductFormDialog(
      businessId = state.businessId,
      categories = state.categories,
      gramsPerPound = state.gramsPerPound,
      primaryCurrency = state.primaryCurrency,
      onSave = { req ->
        viewModel.saveProduct(req) {
          showProductDialog = false
        }
      },
      onDismiss = { showProductDialog = false },
    )
  }

  if (showCategoriesDialog) {
    CategoriesDialog(
      categories = state.categories,
      productCountByCategory = state.productCountsByCategory,
      onSaveCategory = { categoryId, name, desc, color ->
        viewModel.saveCategory(
          name = name,
          description = desc,
          colorHex = color,
          categoryId = categoryId,
        )
      },
      onDeleteCategory = viewModel::deleteCategory,
      onDismiss = { showCategoriesDialog = false },
    )
  }

  adjustingItem?.let { item ->
    StockAdjustmentDialog(
      businessId = state.businessId,
      item = item,
      gramsPerPound = state.gramsPerPound,
      onConfirm = { req ->
        viewModel.recordStockAdjustment(req) {
          adjustingItem = null
        }
      },
      onDismiss = { adjustingItem = null },
    )
  }
}

@Composable
private fun ProductInventoryCard(
  item: ProductWithDetails,
  gramsPerPound: BigDecimal,
  primaryCurrency: String,
  onClick: () -> Unit,
  onAdjustClick: () -> Unit,
) {
  val product = item.product
  val isWeight = product.modality == ProductModality.WEIGHT.code
  val modality = ProductModality.fromCode(product.modality)

  val stockDisplay = if (isWeight) {
    UnitConverter.formatWeightStock(product.stockBaseQty, gramsPerPound)
  } else {
    val firstPres = item.presentations.firstOrNull()
    if (firstPres != null && firstPres.baseUnitsPerPresentation > BigDecimal.ZERO) {
      val packs = product.stockBaseQty.divideToIntegralValue(firstPres.baseUnitsPerPresentation)
      val remainder = product.stockBaseQty.remainder(firstPres.baseUnitsPerPresentation)
      "${MoneyMath.formatQuantity(product.stockBaseQty)} u (${MoneyMath.formatQuantity(packs)} ${firstPres.name} + ${MoneyMath.formatQuantity(remainder)} u)"
    } else {
      "${MoneyMath.formatQuantity(product.stockBaseQty)} u"
    }
  }

  val saleUnitSuffix = when {
    !isWeight -> "/u"
    product.defaultSaleUnit == "POUND" -> "/lb"
    product.defaultSaleUnit == "KG" -> "/kg"
    else -> "/g"
  }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("product_card_${product.id}"),
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(parseHexColor(item.category?.colorHex ?: "#FFA510")),
          )
          Column {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(
              item.category?.name,
              modality.label,
              product.sku.takeIf { it.isNotBlank() }?.let { "SKU: $it" },
            ).joinToString(" • ")
            Text(
              text = meta,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "${MoneyMath.format(product.salePrice, primaryCurrency)}$saleUnitSuffix",
            style = MonoMetricMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Costo: ${MoneyMath.format(product.avgCostPerSaleUnit, primaryCurrency)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = if (isWeight) Icons.Filled.Scale else Icons.Filled.Inventory2,
            contentDescription = null,
            tint = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = stockDisplay,
            style = MonoMetricMedium,
            color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (item.isOutOfStock || item.isLowStock) {
            Surface(
              color = if (item.isOutOfStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(999.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                  text = if (item.isOutOfStock) "Agotado" else "Bajo",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }

          OutlinedButton(
            onClick = onAdjustClick,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp).testTag("product_adjust_btn_${product.id}"),
          ) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Ajustar", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}

@Composable
private fun SummaryBadge(text: String, containerColor: androidx.compose.ui.graphics.Color) {
  Surface(
    color = containerColor,
    shape = RoundedCornerShape(999.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}
