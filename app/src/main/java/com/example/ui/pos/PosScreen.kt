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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.license.PlanState
import com.example.data.local.db.entity.CustomerEntity
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
  var showCustomerPicker by remember { mutableStateOf(false) }
  var showQuickCustomerDialog by remember { mutableStateOf(false) }

  LaunchedEffect(state.feedbackMessage) {
    val msg = state.feedbackMessage
    // Con el diálogo de cobro abierto, un error se muestra DENTRO del
    // diálogo (encima de todo): el snackbar quedaría detrás y solo se
    // vería al salir. En ese caso el aviso lo pinta el propio diálogo
    // y se limpia al cerrarlo.
    if (msg != null && !(showCheckoutDialog && state.feedbackIsError)) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearFeedback()
    }
  }

  val selectedCustomer = remember(state.customers, state.selectedCustomerId) {
    state.customers.firstOrNull { it.id == state.selectedCustomerId }
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
      val isCompact = maxWidth < 360.dp

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          PosCatalogPane(
            state = state,
            selectedCustomer = selectedCustomer,
            onSearchChange = viewModel::updateSearch,
            onSelectCategory = viewModel::selectCategory,
            onOpenCustomerPicker = { showCustomerPicker = true },
            onOpenQuickCustomer = { showQuickCustomerDialog = true },
            onProductClick = { selectingProduct = it },
            onQuickAdd = viewModel::quickAddDefaultUnit,
            onQuickDecrement = viewModel::quickDecrementDefaultUnit,
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
            selectedCustomer = selectedCustomer,
            onSearchChange = viewModel::updateSearch,
            onSelectCategory = viewModel::selectCategory,
            onOpenCustomerPicker = { showCustomerPicker = true },
            onOpenQuickCustomer = { showQuickCustomerDialog = true },
            onProductClick = { selectingProduct = it },
            onQuickAdd = viewModel::quickAddDefaultUnit,
            onQuickDecrement = viewModel::quickDecrementDefaultUnit,
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
                .padding(horizontal = if (isCompact) 10.dp else 16.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                val customerTag = selectedCustomer?.name ?: "Al contado"
                Text(
                  text = if (state.cartItems.isEmpty()) "Carrito vacío • $customerTag"
                  else "${state.cartItems.size} línea(s) • $customerTag",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = MoneyMath.format(state.totalBase, state.primaryCurrency),
                  style = MonoMetricMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.testTag("pos_cart_bar_total"),
                )
              }
              Spacer(Modifier.width(8.dp))
              Button(
                onClick = { showCheckoutDialog = true },
                enabled = state.cartItems.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp).testTag("pos_open_cart_button"),
              ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isCompact) "Cobrar" else "Cobrar (${state.cartItems.size})", maxLines = 1, softWrap = false)
              }
            }
          }
        }
      }
    }
  }

  if (showCustomerPicker) {
    PosCustomerPickerDialog(
      customers = state.customers,
      selectedCustomerId = state.selectedCustomerId,
      primaryCurrency = state.primaryCurrency,
      onSelectCustomer = { custId ->
        viewModel.selectCustomer(custId)
        showCustomerPicker = false
      },
      onCreateNewClick = {
        showCustomerPicker = false
        showQuickCustomerDialog = true
      },
      onDismiss = { showCustomerPicker = false },
    )
  }

  if (showQuickCustomerDialog) {
    QuickCustomerDialog(
      onCreate = { name, phone ->
        viewModel.quickCreateCustomer(name, phone) {
          showQuickCustomerDialog = false
        }
      },
      onDismiss = { showQuickCustomerDialog = false },
    )
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
      onDismiss = {
        showCheckoutDialog = false
        viewModel.clearFeedback()
      },
    )
  }

  state.completedSale?.let { saleWithDetails ->
    SaleReceiptDialog(
      business = state.business,
      saleWithDetails = saleWithDetails,
      onDismiss = viewModel::dismissReceipt,
      freeVersionFooter = state.planState == PlanState.FREE,
    )
  }
}

@Composable
private fun PosCatalogPane(
  state: PosUiState,
  selectedCustomer: CustomerEntity?,
  onSearchChange: (String) -> Unit,
  onSelectCategory: (Long?) -> Unit,
  onOpenCustomerPicker: () -> Unit,
  onOpenQuickCustomer: () -> Unit,
  onProductClick: (ProductWithDetails) -> Unit,
  onQuickAdd: (ProductWithDetails) -> Unit,
  onQuickDecrement: (ProductWithDetails) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.testTag("pos_catalog_list"),
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 168.dp),
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

    // Barra rápida de Cliente para asignar ventas sin buscar en el checkout
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (selectedCustomer != null) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
          } else {
            MaterialTheme.colorScheme.surface
          },
          tonalElevation = 2.dp,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clickable(onClick = onOpenCustomerPicker)
            .testTag("pos_bar_customer_selector"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = selectedCustomer?.let {
                  if (it.debtBalanceBase > BigDecimal.ZERO) {
                    "${it.name} • Debe ${MoneyMath.format(it.debtBalanceBase, state.primaryCurrency)}"
                  } else {
                    it.name
                  }
                } ?: "Cliente al contado",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text = "Cambiar",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        OutlinedButton(
          onClick = onOpenQuickCustomer,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp),
          modifier = Modifier.height(48.dp).testTag("pos_bar_quick_customer_btn"),
        ) {
          Icon(Icons.Filled.PersonAdd, contentDescription = "Nuevo cliente rápido", modifier = Modifier.size(18.dp))
        }
      }
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
        val defaultUnitLabel = when {
          item.product.modality != ProductModality.WEIGHT.code -> "u"
          item.product.defaultSaleUnit == "KG" -> "kg"
          item.product.defaultSaleUnit == "GRAM" -> "g"
          else -> "lb"
        }
        val inCartQty = state.cartItems
          .filter {
            it.productWithDetails.product.id == item.product.id &&
              it.unitLabel == defaultUnitLabel &&
              it.overrideUnitSalePriceBase == null
          }
          .fold(BigDecimal.ZERO) { acc, line -> acc.add(line.enteredQuantity) }

        PosProductCard(
          item = item,
          defaultUnitLabel = defaultUnitLabel,
          inCartDefaultQty = inCartQty,
          gramsPerPound = state.gramsPerPound,
          primaryCurrency = state.primaryCurrency,
          onClick = { onProductClick(item) },
          onQuickAdd = { onQuickAdd(item) },
          onQuickDecrement = { onQuickDecrement(item) },
        )
      }
    }
  }
}

@Composable
private fun PosProductCard(
  item: ProductWithDetails,
  defaultUnitLabel: String,
  inCartDefaultQty: BigDecimal,
  gramsPerPound: BigDecimal,
  primaryCurrency: String,
  onClick: () -> Unit,
  onQuickAdd: () -> Unit,
  onQuickDecrement: () -> Unit,
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
  val quickButtonLabel = when (defaultUnitLabel) {
    "g" -> "+100 g"
    "kg" -> "+1 kg"
    "lb" -> "+1 lb"
    else -> "+1"
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
      modifier = Modifier.fillMaxWidth().padding(12.dp),
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
          Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            text = "${MoneyMath.format(product.salePrice, primaryCurrency)}$unitSuffix • $stockText",
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Spacer(Modifier.width(8.dp))

      if (inCartDefaultQty > BigDecimal.ZERO) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onQuickDecrement,
              modifier = Modifier.size(42.dp).testTag("pos_quick_dec_${product.id}"),
            ) {
              Icon(Icons.Filled.Remove, contentDescription = "Quitar uno", modifier = Modifier.size(18.dp))
            }
            Text(
              text = "${MoneyMath.formatQuantity(inCartDefaultQty)} $defaultUnitLabel",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(
              onClick = onQuickAdd,
              modifier = Modifier.size(42.dp).testTag("pos_quick_inc_${product.id}"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = "Agregar otro", modifier = Modifier.size(18.dp))
            }
          }
        }
      } else {
        FilledTonalButton(
          onClick = onQuickAdd,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
          ),
          modifier = Modifier.height(42.dp).testTag("pos_quick_add_${product.id}"),
        ) {
          Text(quickButtonLabel, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
      }
    }
  }
}

@Composable
private fun PosCustomerPickerDialog(
  customers: List<CustomerEntity>,
  selectedCustomerId: Long?,
  primaryCurrency: String,
  onSelectCustomer: (Long?) -> Unit,
  onCreateNewClick: () -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(customers, query) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) customers
    else customers.filter {
      it.name.lowercase().contains(q) || it.phone.lowercase().contains(q)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
        .widthIn(max = 560.dp)
        .heightIn(max = 760.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Elegir Cliente", style = MaterialTheme.typography.titleLarge)
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar selector")
          }
        }

        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Buscar por nombre o teléfono...") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("pos_customer_picker_search"),
        )

        OutlinedButton(
          onClick = { onSelectCustomer(null) },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pos_customer_walkin_btn"),
        ) {
          Text(
            text = if (selectedCustomerId == null) "✓ Cliente al contado (Activo)" else "Cliente al contado",
            fontWeight = FontWeight.Medium,
          )
        }

        HorizontalDivider()

        LazyColumn(
          modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(filtered, key = { it.id }) { customer ->
            val isSelected = customer.id == selectedCustomerId
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
              },
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectCustomer(customer.id) }
                .testTag("pos_customer_pick_${customer.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(customer.name, style = MaterialTheme.typography.titleSmall)
                  if (customer.phone.isNotBlank()) {
                    Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                if (customer.debtBalanceBase > BigDecimal.ZERO) {
                  Text(
                    text = "Debe ${MoneyMath.format(customer.debtBalanceBase, primaryCurrency)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          TextButton(onClick = onDismiss) { Text("Cerrar") }
          Button(
            onClick = onCreateNewClick,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(48.dp).testTag("pos_picker_new_customer_btn"),
          ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Nuevo Cliente")
          }
        }
      }
    }
  }
}

