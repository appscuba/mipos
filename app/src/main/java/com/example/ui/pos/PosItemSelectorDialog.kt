package com.example.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.cost.CostEngine
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal

private data class PosSellUnitOption(
  val label: String,
  val factorToBase: BigDecimal,
  val overridePriceBase: BigDecimal? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosItemSelectorDialog(
  item: ProductWithDetails,
  gramsPerPound: BigDecimal,
  primaryCurrency: String,
  allowNegativeStock: Boolean,
  onAddToCart: (
    unitLabel: String,
    enteredQty: BigDecimal,
    factorToBase: BigDecimal,
    overridePriceBase: BigDecimal?,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  val product = item.product
  val modality = ProductModality.fromCode(product.modality)
  val isWeight = modality == ProductModality.WEIGHT

  val unitOptions = remember(item, gramsPerPound) {
    val baseList = if (isWeight) {
      listOf(
        PosSellUnitOption("lb", gramsPerPound),
        PosSellUnitOption("g", BigDecimal.ONE),
        PosSellUnitOption("kg", UnitConverter.GRAMS_PER_KILOGRAM),
      )
    } else {
      listOf(PosSellUnitOption("u", BigDecimal.ONE))
    }
    val presList = item.presentations
      .filter { it.isSellPresentation && it.baseUnitsPerPresentation > BigDecimal.ZERO }
      .map {
        PosSellUnitOption(
          label = it.name,
          factorToBase = it.baseUnitsPerPresentation,
          overridePriceBase = it.salePrice,
        )
      }
    baseList + presList
  }

  val defaultOption = remember(unitOptions, product.defaultSaleUnit) {
    if (isWeight) {
      val preferred = when (product.defaultSaleUnit) {
        "GRAM" -> "g"
        "KG" -> "kg"
        else -> "lb"
      }
      unitOptions.firstOrNull { it.label == preferred } ?: unitOptions.first()
    } else {
      unitOptions.first()
    }
  }

  var selectedUnit by remember(defaultOption) { mutableStateOf(defaultOption) }
  var qtyText by remember { mutableStateOf(if (isWeight) "0.5" else "1") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val parsedQty = MoneyMath.parseDecimalOrNull(qtyText) ?: BigDecimal.ZERO
  val baseDeducted = if (parsedQty > BigDecimal.ZERO) {
    UnitConverter.toBaseUnits(parsedQty, selectedUnit.factorToBase)
  } else {
    BigDecimal.ZERO
  }

  val lineTotalPreview = remember(parsedQty, selectedUnit, item, gramsPerPound) {
    if (parsedQty <= BigDecimal.ZERO) {
      MoneyMath.ZERO
    } else if (selectedUnit.overridePriceBase != null) {
      MoneyMath.multiply(selectedUnit.overridePriceBase!!, parsedQty)
    } else {
      val saleUnits = CostEngine.toSaleUnits(
        baseQuantity = baseDeducted,
        modality = modality,
        defaultSaleUnit = product.defaultSaleUnit,
        gramsPerPound = gramsPerPound,
      )
      MoneyMath.multiply(product.salePrice, saleUnits)
    }
  }

  val stockDisplay = if (isWeight) {
    UnitConverter.formatWeightStock(product.stockBaseQty, gramsPerPound)
  } else {
    "${MoneyMath.formatQuantity(product.stockBaseQty)} u"
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(product.name) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Disponible: $stockDisplay • Precio base: ${MoneyMath.format(product.salePrice, primaryCurrency)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Unit / Presentation selector
        Text("Unidad o presentación", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          unitOptions.forEach { option ->
            FilterChip(
              selected = selectedUnit.label == option.label,
              onClick = { selectedUnit = option },
              label = {
                val priceTag = option.overridePriceBase?.let { " (${MoneyMath.format(it, primaryCurrency)})" } ?: ""
                Text("${option.label}$priceTag")
              },
              modifier = Modifier.testTag("pos_unit_chip_${option.label}"),
            )
          }
        }

        // Quick preset buttons
        if (isWeight && selectedUnit.label == "lb") {
          Text("Fracciones rápidas (libras)", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UnitConverter.WEIGHT_PRESET_FRACTIONS.forEach { (label, amount) ->
              OutlinedButton(
                onClick = { qtyText = MoneyMath.formatQuantity(amount) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp).testTag("pos_fraction_${MoneyMath.formatQuantity(amount)}"),
              ) {
                Text(label)
              }
            }
          }
        } else if (isWeight && selectedUnit.label == "kg") {
          Text("Fracciones rápidas (kilogramos)", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              "¼ kg" to "0.25",
              "½ kg" to "0.5",
              "¾ kg" to "0.75",
              "1 kg" to "1",
              "1½ kg" to "1.5",
              "2 kg" to "2",
            ).forEach { (label, amount) ->
              OutlinedButton(
                onClick = { qtyText = amount },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp).testTag("pos_kg_fraction_$amount"),
              ) {
                Text(label)
              }
            }
          }
        } else if (isWeight && selectedUnit.label == "g") {
          Text("Gramos rápidos", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("100", "250", "350", "500", "750", "1000").forEach { gramsStr ->
              OutlinedButton(
                onClick = { qtyText = gramsStr },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp).testTag("pos_grams_$gramsStr"),
              ) {
                Text("$gramsStr g")
              }
            }
          }
        } else {
          Text("Cantidad rápida", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1", "2", "3", "5", "10").forEach { countStr ->
              OutlinedButton(
                onClick = { qtyText = countStr },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
              ) {
                Text(countStr)
              }
            }
          }
        }

        OutlinedTextField(
          value = qtyText,
          onValueChange = { qtyText = it },
          label = { Text("Cantidad (${selectedUnit.label})") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("pos_item_qty_input"),
        )

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
          shape = MaterialTheme.shapes.small,
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Equivale a:", style = MaterialTheme.typography.bodySmall)
              Text(
                "${MoneyMath.formatQuantity(baseDeducted)} ${product.baseUnit}",
                style = MonoMetricMedium,
              )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Subtotal:", style = MaterialTheme.typography.titleSmall)
              Text(
                text = MoneyMath.format(lineTotalPreview, primaryCurrency),
                style = MonoMetricMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("pos_item_subtotal_preview"),
              )
            }
          }
        }

        if (product.trackInventory && !allowNegativeStock && baseDeducted > product.stockBaseQty) {
          Text(
            text = "Supera el stock disponible ($stockDisplay).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (errorText != null) {
          Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          when {
            parsedQty <= BigDecimal.ZERO -> errorText = "Ingresa una cantidad mayor que cero."
            product.trackInventory && !allowNegativeStock && baseDeducted > product.stockBaseQty ->
              errorText = "Stock insuficiente ($stockDisplay)."
            else -> onAddToCart(
              selectedUnit.label,
              parsedQty,
              selectedUnit.factorToBase,
              selectedUnit.overridePriceBase,
            )
          }
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("pos_add_to_cart_button"),
      ) {
        Text("Agregar al Carrito")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
