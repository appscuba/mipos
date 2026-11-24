package com.example.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.local.db.entity.CategoryEntity
import com.example.data.repository.PresentationDraft
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.SaveProductRequest
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormDialog(
  businessId: Long,
  categories: List<CategoryEntity>,
  gramsPerPound: BigDecimal,
  primaryCurrency: String,
  editingItem: ProductWithDetails? = null,
  onSave: (SaveProductRequest) -> Unit,
  onDismiss: () -> Unit,
) {
  val isEditing = editingItem != null
  val initialModality = editingItem?.let { ProductModality.fromCode(it.product.modality) } ?: ProductModality.WEIGHT

  var name by remember { mutableStateOf(editingItem?.product?.name.orEmpty()) }
  var sku by remember { mutableStateOf(editingItem?.product?.sku.orEmpty()) }
  var barcode by remember { mutableStateOf(editingItem?.product?.barcode.orEmpty()) }
  var selectedCategoryId by remember { mutableStateOf(editingItem?.product?.categoryId ?: categories.firstOrNull()?.id) }
  var modality by remember { mutableStateOf(initialModality) }
  var defaultSaleUnit by remember {
    mutableStateOf(
      editingItem?.product?.defaultSaleUnit
        ?: if (initialModality == ProductModality.WEIGHT) "POUND" else "UNIT",
    )
  }
  var salePriceText by remember {
    mutableStateOf(editingItem?.product?.salePrice?.let { MoneyMath.formatQuantity(it) }.orEmpty())
  }
  var avgCostText by remember {
    mutableStateOf(editingItem?.product?.avgCostPerSaleUnit?.let { MoneyMath.formatQuantity(it) }.orEmpty())
  }
  var initialStockText by remember { mutableStateOf("") }
  var initialUnitOption by remember {
    mutableStateOf(if (initialModality == ProductModality.WEIGHT) "lb" else "u")
  }
  var lowStockText by remember {
    mutableStateOf(
      editingItem?.product?.lowStockThresholdBase?.let {
        if (it > BigDecimal.ZERO) MoneyMath.formatQuantity(it) else ""
      }.orEmpty(),
    )
  }
  var trackInventory by remember { mutableStateOf(editingItem?.product?.trackInventory ?: true) }

  LaunchedEffect(categories) {
    if (selectedCategoryId == null && categories.isNotEmpty()) {
      selectedCategoryId = categories.first().id
    }
  }

  val presentations = remember {
    mutableStateListOf<PresentationDraft>().apply {
      editingItem?.presentations?.forEach {
        add(
          PresentationDraft(
            id = it.id,
            name = it.name,
            baseUnitsPerPresentation = it.baseUnitsPerPresentation,
            salePrice = it.salePrice,
            isBuyPresentation = it.isBuyPresentation,
            isSellPresentation = it.isSellPresentation,
          ),
        )
      }
    }
  }

  var presName by remember { mutableStateOf("") }
  var presQtyText by remember { mutableStateOf("") }
  var presPriceText by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val saleUnitLabel = when {
    modality != ProductModality.WEIGHT -> "unidad"
    defaultSaleUnit == "POUND" -> "libra"
    defaultSaleUnit == "KG" -> "kg"
    else -> "gramo"
  }

  val initialFactor = when (initialUnitOption) {
    "lb" -> gramsPerPound
    "kg" -> UnitConverter.GRAMS_PER_KILOGRAM
    "g", "u" -> BigDecimal.ONE
    else -> presentations.firstOrNull { it.name == initialUnitOption }?.baseUnitsPerPresentation ?: BigDecimal.ONE
  }

  val parsedInitialQty = MoneyMath.parseDecimalOrNull(initialStockText)
  val convertedInitialBase = if (parsedInitialQty != null && parsedInitialQty > BigDecimal.ZERO) {
    UnitConverter.toBaseUnits(parsedInitialQty, initialFactor)
  } else null

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
        .widthIn(max = 580.dp)
        .heightIn(max = 840.dp)
        .imePadding(),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isEditing) "Editar Producto" else "Nuevo Producto",
            style = MaterialTheme.typography.titleLarge,
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar formulario de producto")
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Scrollable Body
        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = {
              name = it
              errorText = null
            },
            label = { Text("Nombre del producto *") },
            placeholder = { Text("Ej. Arroz criollo, Jugo caja") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("product_name_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = sku,
              onValueChange = { sku = it },
              label = { Text("SKU", maxLines = 1, overflow = TextOverflow.Ellipsis) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = barcode,
              onValueChange = { barcode = it },
              label = { Text("Cód. Barras", maxLines = 1, overflow = TextOverflow.Ellipsis) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }

          if (categories.isNotEmpty()) {
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              categories.forEach { cat ->
                FilterChip(
                  selected = selectedCategoryId == cat.id,
                  onClick = { selectedCategoryId = cat.id },
                  label = { Text(cat.name) },
                )
              }
            }
          }

          Text("Modalidad de inventario", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            ProductModality.entries.forEach { entry ->
              FilterChip(
                selected = modality == entry,
                onClick = {
                  modality = entry
                  if (entry == ProductModality.WEIGHT) {
                    defaultSaleUnit = "POUND"
                    initialUnitOption = "lb"
                  } else {
                    defaultSaleUnit = "UNIT"
                    initialUnitOption = "u"
                  }
                },
                label = { Text(entry.label) },
                modifier = Modifier.testTag("product_modality_${entry.code}"),
              )
            }
          }

          if (modality == ProductModality.WEIGHT) {
            Text("Precio expresado por", style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf(
                "POUND" to "Libra (${MoneyMath.formatQuantity(gramsPerPound)} g)",
                "KG" to "Kilogramo",
                "GRAM" to "Gramo",
              ).forEach { (code, label) ->
                FilterChip(
                  selected = defaultSaleUnit == code,
                  onClick = { defaultSaleUnit = code },
                  label = { Text(label) },
                )
              }
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Precios en $primaryCurrency (por $saleUnitLabel)",
              style = MaterialTheme.typography.labelLarge,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = salePriceText,
                onValueChange = {
                  salePriceText = it
                  errorText = null
                },
                label = { Text("Precio venta *", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("product_sale_price_input"),
              )
              OutlinedTextField(
                value = avgCostText,
                onValueChange = {
                  avgCostText = it
                  errorText = null
                },
                label = { Text("Costo prom.", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("product_cost_input"),
              )
            }
          }

          // Presentations builder
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.small,
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Presentaciones / Paquetes (opcional)",
                style = MaterialTheme.typography.labelLarge,
              )
              Text(
                text = if (modality == ProductModality.WEIGHT) {
                  "Ej: Saco = 25000 g (50 lb). Permite comprar o vender por saco y fraccionar."
                } else {
                  "Ej: Caja = 12 unidades. Permite comprar 10 cajas (120 u) y vender por unidad."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedTextField(
                value = presName,
                onValueChange = { presName = it },
                label = { Text("Nombre de presentación") },
                placeholder = { Text(if (modality == ProductModality.WEIGHT) "Ej. Saco, Paquete" else "Ej. Caja, Display") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("pres_name_input"),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedTextField(
                  value = presQtyText,
                  onValueChange = { presQtyText = it },
                  label = {
                    Text(
                      text = if (modality == ProductModality.WEIGHT) "Gramos" else "Unidades",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("pres_qty_input"),
                )
                OutlinedTextField(
                  value = presPriceText,
                  onValueChange = { presPriceText = it },
                  label = {
                    Text(
                      text = "Precio ($primaryCurrency)",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }
              OutlinedButton(
                onClick = {
                  val qty = MoneyMath.parseDecimalOrNull(presQtyText)
                  if (presName.isNotBlank() && qty != null && qty > BigDecimal.ZERO) {
                    val draft = PresentationDraft(
                      name = presName.trim(),
                      baseUnitsPerPresentation = qty,
                      salePrice = MoneyMath.parseMoneyOrNull(presPriceText),
                    )
                    presentations.add(draft)
                    if (!isEditing) {
                      initialUnitOption = draft.name
                    }
                    presName = ""
                    presQtyText = ""
                    presPriceText = ""
                  } else {
                    errorText = "Completa nombre y equivalencia (> 0) de la presentación."
                  }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pres_add_button"),
              ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir Presentación")
              }

              presentations.forEachIndexed { idx, draft ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val baseUnitStr = if (modality == ProductModality.WEIGHT) "g" else "u"
                  val priceStr = draft.salePrice?.let { " • ${MoneyMath.format(it, primaryCurrency)}" }.orEmpty()
                  Text(
                    text = "${draft.name} = ${MoneyMath.formatQuantity(draft.baseUnitsPerPresentation)} $baseUnitStr$priceStr",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(onClick = { presentations.removeAt(idx) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar presentación", modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }

          if (!isEditing) {
            Text("Existencia inicial", style = MaterialTheme.typography.labelLarge)
            val initialOptions = buildList {
              if (modality == ProductModality.WEIGHT) {
                add("lb")
                add("g")
                add("kg")
              } else {
                add("u")
              }
              presentations.forEach { add(it.name) }
            }
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              initialOptions.forEach { opt ->
                FilterChip(
                  selected = initialUnitOption == opt,
                  onClick = { initialUnitOption = opt },
                  label = { Text(opt) },
                  modifier = Modifier.testTag("initial_unit_$opt"),
                )
              }
            }
            OutlinedTextField(
              value = initialStockText,
              onValueChange = { initialStockText = it },
              label = { Text("Cantidad inicial en $initialUnitOption (opcional)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("product_initial_stock_input"),
            )
            if (convertedInitialBase != null) {
              val baseUnitStr = if (modality == ProductModality.WEIGHT) "g" else "u"
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
              ) {
                Text(
                  text = "Se registrarán ${MoneyMath.formatQuantity(convertedInitialBase)} $baseUnitStr en inventario",
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(8.dp),
                )
              }
            }
          }

          OutlinedTextField(
            value = lowStockText,
            onValueChange = { lowStockText = it },
            label = {
              Text(
                if (modality == ProductModality.WEIGHT) "Alerta stock bajo (en gramos)" else "Alerta stock bajo (en unidades)",
              )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Controlar existencias", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = trackInventory, onCheckedChange = { trackInventory = it })
          }

          if (errorText != null) {
            Text(
              text = errorText.orEmpty(),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Fixed Footer Actions (always visible & accessible even with keyboard open)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.height(48.dp),
          ) {
            Text("Cancelar")
          }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              if (name.isBlank()) {
                errorText = "El nombre del producto es obligatorio."
                return@Button
              }
              val price = MoneyMath.parseMoneyOrNull(salePriceText) ?: MoneyMath.ZERO
              val cost = MoneyMath.parseMoneyOrNull(avgCostText) ?: MoneyMath.ZERO
              val initQty = MoneyMath.parseDecimalOrNull(initialStockText) ?: BigDecimal.ZERO
              val lowThreshold = MoneyMath.parseDecimalOrNull(lowStockText) ?: BigDecimal.ZERO
              onSave(
                SaveProductRequest(
                  productId = editingItem?.product?.id,
                  businessId = businessId,
                  categoryId = selectedCategoryId,
                  name = name.trim(),
                  sku = sku.trim(),
                  barcode = barcode.trim(),
                  modality = modality,
                  defaultSaleUnit = defaultSaleUnit,
                  salePrice = price,
                  avgCostPerSaleUnit = cost,
                  initialStockEnteredQty = initQty,
                  initialStockUnitLabel = initialUnitOption,
                  initialStockFactorToBase = initialFactor,
                  lowStockThresholdBase = lowThreshold,
                  trackInventory = trackInventory,
                  presentations = presentations.toList(),
                ),
              )
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(48.dp).testTag("product_save_button"),
          ) {
            Text("Guardar")
          }
        }
      }
    }
  }
}
