package com.example.ui.reports

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.money.MoneyMath
import com.example.data.repository.ProductProfitabilityRow
import com.example.data.repository.ReportPeriodPreset
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
  viewModel: ReportsViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val report = state.report
  val scope = rememberCoroutineScope()
  var exportMenuOpen by remember { mutableStateOf(false) }

  fun shareTextReport() {
    val summary = viewModel.buildShareText()
    if (summary.isNotEmpty()) {
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Reporte ${state.businessName}")
        putExtra(Intent.EXTRA_TEXT, summary)
      }
      context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
    }
  }

  fun exportFileReport(asPdf: Boolean) {
    val model = viewModel.buildExportModel() ?: return
    scope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          if (asPdf) {
            ReportExportFiles.writePdf(context, model) to ReportExportFiles.PDF_MIME
          } else {
            ReportExportFiles.writeExcel(context, model) to ReportExportFiles.XLSX_MIME
          }
        }
      }
      result.onSuccess { (file, mime) ->
        ReportExportFiles.shareFile(context, file, mime, "Reporte ${state.businessName}")
      }.onFailure {
        Toast.makeText(context, "No se pudo generar el archivo del reporte", Toast.LENGTH_LONG).show()
      }
    }
  }

  Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
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
          .testTag("reports_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Período de Análisis", style = MaterialTheme.typography.titleMedium)
            if (report != null) {
              Box {
                OutlinedButton(
                  onClick = { exportMenuOpen = true },
                  modifier = Modifier.height(48.dp).testTag("reports_share_button"),
                ) {
                  Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Exportar")
                }
                DropdownMenu(
                  expanded = exportMenuOpen,
                  onDismissRequest = { exportMenuOpen = false },
                ) {
                  DropdownMenuItem(
                    text = { Text("Exportar PDF") },
                    onClick = {
                      exportMenuOpen = false
                      exportFileReport(asPdf = true)
                    },
                    modifier = Modifier.testTag("reports_export_pdf"),
                  )
                  DropdownMenuItem(
                    text = { Text("Exportar Excel (.xlsx)") },
                    onClick = {
                      exportMenuOpen = false
                      exportFileReport(asPdf = false)
                    },
                    modifier = Modifier.testTag("reports_export_excel"),
                  )
                  DropdownMenuItem(
                    text = { Text("Compartir texto") },
                    onClick = {
                      exportMenuOpen = false
                      shareTextReport()
                    },
                    modifier = Modifier.testTag("reports_export_text"),
                  )
                }
              }
            }
          }
          Spacer(Modifier.height(6.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriodPreset.entries.forEach { preset ->
              FilterChip(
                selected = state.selectedPreset == preset && state.customRangeMillis == null,
                onClick = { viewModel.selectPreset(preset) },
                label = { Text(preset.label) },
                modifier = Modifier.testTag("report_preset_${preset.name.lowercase()}"),
              )
            }
          }
        }

        if (report != null) {
          val cur = report.primaryCurrency

          item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetricCard(
                  label = "Ventas netas",
                  value = MoneyMath.format(report.netSalesBase, cur),
                  detail = "${report.salesCount} ventas",
                  modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                  label = "Ganancia bruta",
                  value = MoneyMath.format(report.grossProfitBase, cur),
                  detail = "Margen ${report.grossMarginPercent}%",
                  modifier = Modifier.weight(1f),
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetricCard(
                  label = "Ganancia neta",
                  value = MoneyMath.format(report.netProfitBase, cur),
                  detail = "Margen ${report.netMarginPercent}%",
                  valueColor = if (report.netProfitBase >= BigDecimal.ZERO) {
                    MaterialTheme.colorScheme.tertiary
                  } else {
                    MaterialTheme.colorScheme.error
                  },
                  modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                  label = "Valor inventario",
                  value = MoneyMath.format(report.inventoryValueBase, cur),
                  detail = "Al costo",
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }

          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text("Estado de Resultados", style = MaterialTheme.typography.titleMedium)
                ReportLine("Ventas netas (${report.salesCount})", MoneyMath.format(report.netSalesBase, cur), tag = "report_net_sales")
                ReportLine("- Costo mercancía vendida (COGS)", MoneyMath.format(report.cogsBase, cur), tag = "report_cogs")
                HorizontalDivider()
                ReportLine(
                  label = "Ganancia bruta (${report.grossMarginPercent}%)",
                  value = MoneyMath.format(report.grossProfitBase, cur),
                  highlightColor = MaterialTheme.colorScheme.primary,
                  tag = "report_gross_profit",
                )
                ReportLine("- Gastos operativos (${report.expensesCount})", MoneyMath.format(report.operatingExpensesBase, cur), tag = "report_expenses")
                report.expensesByCategory.forEach { row ->
                  ReportLine(
                    label = "      · ${row.categoryName}",
                    value = MoneyMath.format(row.totalBase, cur),
                    tag = "report_expense_cat_${row.categoryName.lowercase().replace(' ', '_')}",
                  )
                }
                HorizontalDivider()
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text("Ganancia neta (${report.netMarginPercent}%)", style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = MoneyMath.format(report.netProfitBase, cur),
                    style = MonoMetricLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (report.netProfitBase >= BigDecimal.ZERO) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("report_net_profit"),
                  )
                }
              }
            }
          }

          item {
            Card(
              shape = MaterialTheme.shapes.medium,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text("Balance y Patrimonio Actual", style = MaterialTheme.typography.titleMedium)
                ReportLine("Valor inventario (al costo)", MoneyMath.format(report.inventoryValueBase, cur), tag = "report_inventory_value")
                ReportLine("Efectivo en caja abierta", MoneyMath.format(report.currentCashDrawerBase, cur))
                ReportLine("Compras del período (${report.purchasesCount})", MoneyMath.format(report.totalPurchasesBase, cur))
                ReportLine("Cuentas por cobrar (Clientes)", MoneyMath.format(report.customerReceivableBase, cur))
                ReportLine("Cuentas por pagar (Proveedores)", MoneyMath.format(report.supplierPayableBase, cur))
                ReportLine("Capital invertido", MoneyMath.format(report.investedCapitalBase, cur))
                ReportLine("Retiros del propietario", MoneyMath.format(report.ownerWithdrawalsBase, cur))
              }
            }
          }

          if (report.paymentMethodTotals.isNotEmpty()) {
            item {
              Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text("Ingresos por Forma de Pago", style = MaterialTheme.typography.titleMedium)
                  report.paymentMethodTotals.forEach { (method, total) ->
                    ReportLine(method, MoneyMath.format(total, cur))
                  }
                }
              }
            }
          }

          item {
            Text(
              "Rentabilidad por Producto (${report.productProfitability.size})",
              style = MaterialTheme.typography.titleMedium,
            )
          }

          if (report.productProfitability.isEmpty()) {
            item {
              Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              ) {
                Text(
                  text = "Sin ventas registradas en este período.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          } else {
            items(report.productProfitability, key = { it.productId }) { row ->
              ProductProfitCard(row = row, currency = cur)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReportLine(
  label: String,
  value: String,
  highlightColor: androidx.compose.ui.graphics.Color? = null,
  tag: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // La etiqueta cede el espacio y el importe queda siempre en una línea:
    // así ningún importe se parte ni se monta sobre su etiqueta.
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = MonoMetricMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      softWrap = false,
      color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
      modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    )
  }
}

@Composable
private fun SummaryMetricCard(
  label: String,
  value: String,
  detail: String,
  modifier: Modifier = Modifier,
  valueColor: Color = Color.Unspecified,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MonoMetricMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        color = valueColor,
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ProductProfitCard(
  row: ProductProfitabilityRow,
  currency: String,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.testTag("report_product_${row.productId}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(row.productName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Margen: ${row.marginPercent}%",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
        text = "Unidades vendidas: ${row.unitsSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider()
      Row(modifier = Modifier.fillMaxWidth()) {
        ProductAmountColumn(
          label = "Ingresos",
          value = MoneyMath.format(row.revenueBase, currency),
          modifier = Modifier.weight(1f),
        )
        ProductAmountColumn(
          label = "Costo",
          value = MoneyMath.format(row.costBase, currency),
          modifier = Modifier.weight(1f),
        )
        ProductAmountColumn(
          label = "Ganancia",
          value = MoneyMath.format(row.grossProfitBase, currency),
          valueColor = MaterialTheme.colorScheme.tertiary,
          bold = true,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ProductAmountColumn(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueColor: Color = Color.Unspecified,
  bold: Boolean = false,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = if (bold) MonoMetricMedium else MaterialTheme.typography.bodyMedium,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
      color = valueColor,
    )
  }
}
