package com.example.ui.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.ui.history.SaleReceiptDialog
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal

@Composable
fun PosScreen(
  viewModel: PosViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var selectingProduct by remember { mutableStateOf<ProductWithDetails?>(null) }
  var showCheckoutDialog by remember { mutableStateOf(false) }

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
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      val isWide = maxWidth >= 600.dp

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          PosCatalogPane(
            state = state,
            onSearchChange = viewModel::updateSearch,
            onSelectCategory = viewModel::selectCategory,
            onProductClick = { selectingProduct = it },
            modifier = Modifier.weight(1.35f).fillMaxHeight(),
          )
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.weight(1f).fillMaxHeight(),
          ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Carrito y Cobro (${state.cartItems.size})", style = MaterialTheme.typography.titleMedium)
                if (state.cartItems.isNotEmpty()) {
                  Text(
                    text = MoneyMath.format(state.totalBase, state.primaryCurrency),
                    style = MonoMetricMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
              Spacer(Modifier.height(8.dp))
              PosCartCheckoutContent(
                state = state,
                onUpdateQty = viewModel::updateCartItemQuantity,
                onRemoveItem = viewModel::removeCartItem,
                onDiscountChange = viewModel::setDiscount,
                onSelectCustomer = viewModel::selectCustomer,
                onQuickCreateCustomer = viewModel::quickCreateCustomer,
                onConfirmSale = { payments, notes ->
                  viewModel.confirmSale(payments, notes) {}
                },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          PosCatalogPane(
            state = state,
            onSearchChange = viewModel::updateSearch,
            onSelectCategory = viewModel::selectCategory,
            onProductClick = { selectingProduct = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
          )

          // Bottom Cart Bar
          Surface(
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = if (state.cartItems.isEmpty()) "Carrito vacío" else "${state.cartItems.size} línea(s) en carrito",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = MoneyMath.format(state.totalBase, state.primaryCurrency),
                  style = MonoMetricMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.testTag("pos_cart_bar_total"),
                )
              }
              Button(
                onClick = { showCheckoutDialog = true },
                enabled = state.cartItems.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).testTag("pos_open_cart_button"),
              ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cobrar (${state.cartItems.size})")
              }
            }
          }
        }
      }
    }
  }

  selectingProduct?.let { item ->
    PosItemSelectorDialog(
      item = item,
      gramsPerPound = state.gramsPerPound,
      primaryCurrency = state.primaryCurrency,
      allowNegativeStock = state.allowNegativeStock,
      onAddToCart = { unitLabel, qty, factor, overridePrice ->
        viewModel.addItemToCart(
          productWithDetails = item,
          unitLabel = unitLabel,
          enteredQuantity = qty,
          conversionFactorToBase = factor,
          overrideUnitSalePriceBase = overridePrice,
        ) {
          selectingProduct = null
        }
      },
      onDismiss = { selectingProduct = null },
    )
  }

  if (showCheckoutDialog) {
    PosCheckoutDialog(
      state = state,
      onUpdateQty = viewModel::updateCartItemQuantity,
      onRemoveItem = viewModel::removeCartItem,
      onDiscountChange = viewModel::setDiscount,
      onSelectCustomer = viewModel::selectCustomer,
      onQuickCreateCustomer = viewModel::quickCreateCustomer,
      onConfirmSale = { payments, notes ->
        viewModel.confirmSale(payments, notes) {
          showCheckoutDialog = false
        }
      },
      onDismiss = { showCheckoutDialog = false },
    )
  }

  state.completedSale?.let { saleWithDetails ->
    SaleReceiptDialog(
      business = state.business,
      saleWithDetails = saleWithDetails,
      onDismiss = viewModel::dismissReceipt,
    )
  }
}

@Composable
private fun PosCatalogPane(
  state: PosUiState,
  onSearchChange: (String) -> Unit,
  onSelectCategory: (Long?) -> Unit,
  onProductClick: (ProductWithDetails) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.testTag("pos_catalog_list"),
    contentPadding = PaddingValues(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Buscar producto por nombre o SKU...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("pos_search_input"),
      )
    }

    if (state.categories.isNotEmpty()) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = state.selectedCategoryId == null,
              onClick = { onSelectCategory(null) },
              label = { Text("Todas") },
            )
          }
          items(state.categories, key = { it.id }) { cat ->
            FilterChip(
              selected = state.selectedCategoryId == cat.id,
              onClick = { onSelectCategory(if (state.selectedCategoryId == cat.id) null else cat.id) },
              label = { Text(cat.name) },
            )
          }
        }
      }
    }

    if (state.filteredProducts.isEmpty()) {
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.PointOfSale,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(40.dp),
            )
            Text(
              text = if (state.products.isEmpty()) "No hay productos en inventario" else "Sin resultados",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Agrega productos en Inventario o registra una Compra para comenzar a vender.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(state.filteredProducts, key = { it.product.id }) { item ->
        PosProductCard(
          item = item,
          gramsPerPound = state.gramsPerPound,
          primaryCurrency = state.primaryCurrency,
          onClick = { onProductClick(item) },
        )
      }
    }
  }
}

@Composable
private fun PosProductCard(
  item: ProductWithDetails,
  gramsPerPound: BigDecimal,
  primaryCurrency: String,
  onClick: () -> Unit,
) {
  val product = item.product
  val isWeight = product.modality == ProductModality.WEIGHT.code
  val unitSuffix = when {
    !isWeight -> "/u"
    product.defaultSaleUnit == "POUND" -> "/lb"
    product.defaultSaleUnit == "KG" -> "/kg"
    else -> "/g"
  }
  val stockText = if (isWeight) {
    UnitConverter.formatWeightStock(product.stockBaseQty, gramsPerPound)
  } else {
    "${MoneyMath.formatQuantity(product.stockBaseQty)} u"
  }

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("pos_product_card_${product.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.size(40.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isWeight) Icons.Filled.Scale else Icons.Filled.Inventory2,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Column {
          Text(product.name, style = MaterialTheme.typography.titleMedium)
          Text(
            text = listOfNotNull(item.category?.name, stockText).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text = "${MoneyMath.format(product.salePrice, primaryCurrency)}$unitSuffix",
        style = MonoMetricMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
