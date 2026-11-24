package com.example.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.money.MoneyMath
import com.example.core.units.UnitConverter
import com.example.data.repository.ProductModality
import com.example.data.repository.ProductWithDetails
import com.example.data.repository.StockAdjustmentMode
import com.example.data.repository.StockAdjustmentRequest
import java.math.BigDecimal

private data class UnitOption(
  val label: String,
  val factorToBase: BigDecimal,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockAdjustmentDialog(
  businessId: Long,
  item: ProductWithDetails,
  gramsPerPound: BigDecimal,
  onConfirm: (StockAdjustmentRequest) -> Unit,
  onDismiss: () -> Unit,
) {
  val isWeight = item.product.modality == ProductModality.WEIGHT.code
  val unitOptions = remember(item, gramsPerPound) {
    buildList {
      if (isWeight) {
        add(UnitOption("lb", gramsPerPound))
        add(UnitOption("g", BigDecimal.ONE))
        add(UnitOption("kg", UnitConverter.GRAMS_PER_KILOGRAM))
      } else {
        add(UnitOption("u", BigDecimal.ONE))
      }
      item.presentations.forEach { pres ->
        add(UnitOption(pres.name, pres.baseUnitsPerPresentation))
      }
    }
  }

  var mode by remember { mutableStateOf(StockAdjustmentMode.ADD) }
  var selectedUnit by remember { mutableStateOf(unitOptions.first()) }
  var quantityText by remember { mutableStateOf("") }
  var reasonText by remember { mutableStateOf("") }
  var localError by remember { mutableStateOf<String?>(null) }

  val parsedQty = MoneyMath.parseDecimalOrNull(quantityText)
  val convertedBase = if (parsedQty != null && parsedQty >= BigDecimal.ZERO) {
    UnitConverter.toBaseUnits(parsedQty, selectedUnit.factorToBase)
  } else null

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ajustar Stock: ${item.product.name}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = if (isWeight) {
            "Actual: ${UnitConverter.formatWeightStock(item.product.stockBaseQty, gramsPerPound)}"
          } else {
            "Actual: ${MoneyMath.formatQuantity(item.product.stockBaseQty)} u"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StockAdjustmentMode.entries.forEach { entry ->
            FilterChip(
              selected = mode == entry,
              onClick = { mode = entry },
              label = { Text(entry.label) },
            )
          }
        }

        Text("Unidad / Presentación", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          unitOptions.forEach { option ->
            FilterChip(
              selected = selectedUnit == option,
              onClick = { selectedUnit = option },
              label = { Text(option.label) },
            )
          }
        }

        if (isWeight && selectedUnit.label == "lb") {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UnitConverter.WEIGHT_PRESET_FRACTIONS.forEach { (label, fraction) ->
              FilterChip(
                selected = parsedQty?.compareTo(fraction) == 0,
                onClick = {
                  quantityText = MoneyMath.formatQuantity(fraction)
                  localError = null
                },
                label = { Text(label) },
              )
            }
          }
        }

        OutlinedTextField(
          value = quantityText,
          onValueChange = {
            quantityText = it
            localError = null
          },
          label = { Text("Cantidad en ${selectedUnit.label}") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("adjust_qty_input"),
        )

        if (convertedBase != null) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = "Equivalencia aplicada: 1 ${selectedUnit.label} = ${MoneyMath.formatQuantity(selectedUnit.factorToBase)} ${item.product.baseUnit}  •  Total: ${MoneyMath.formatQuantity(convertedBase)} ${item.product.baseUnit}",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        OutlinedTextField(
          value = reasonText,
          onValueChange = {
            reasonText = it
            localError = null
          },
          label = { Text("Motivo del ajuste") },
          placeholder = { Text("Ej. Conteo físico, merma, reposición") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("adjust_reason_input"),
        )

        if (localError != null) {
          Text(
            text = localError.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val qty = MoneyMath.parseDecimalOrNull(quantityText)
          if (qty == null || qty < BigDecimal.ZERO || (mode != StockAdjustmentMode.SET_EXACT && qty <= BigDecimal.ZERO)) {
            localError = "Ingresa una cantidad válida."
            return@Button
          }
          onConfirm(
            StockAdjustmentRequest(
              businessId = businessId,
              productId = item.product.id,
              mode = mode,
              enteredQuantity = qty,
              unitLabel = selectedUnit.label,
              conversionFactorToBase = selectedUnit.factorToBase,
              reason = reasonText.ifBlank { mode.label },
            ),
          )
        },
        modifier = Modifier.testTag("adjust_confirm_button"),
      ) {
        Text("Aplicar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar")
      }
    },
  )
}
