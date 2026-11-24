package com.example.ui.reports

import android.content.Intent
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
  viewModel: ReportsViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val report = state.report

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
              OutlinedButton(
                onClick = {
                  val summary = viewModel.buildShareText()
                  if (summary.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "Reporte ${state.businessName}")
                      putExtra(Intent.EXTRA_TEXT, summary)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
                  }
                },
                modifier = Modifier.height(48.dp).testTag("reports_share_button"),
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar")
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

          if (report.expenseBreakdown.isNotEmpty()) {
            item {
              Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("report_expense_breakdown"),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text("Gastos por categoría", style = MaterialTheme.typography.titleMedium)
                  report.expenseBreakdown.forEach { expense ->
                    ReportLine("${expense.categoryName} (${expense.expenseCount})", MoneyMath.format(expense.totalBase, cur))
                  }
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
                ReportLine("Capital invertido / Retiros", "${MoneyMath.format(report.investedCapitalBase, cur)} / ${MoneyMath.format(report.ownerWithdrawalsBase, cur)}")
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
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = MonoMetricMedium,
      fontWeight = FontWeight.SemiBold,
      color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
      modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    )
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
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(row.productName, style = MaterialTheme.typography.titleSmall)
        Text(
          text = "Margen: ${row.marginPercent}%",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
        text = "Volumen vendido: ${row.unitsSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Ing: ${MoneyMath.format(row.revenueBase, currency)}", style = MaterialTheme.typography.bodySmall)
        Text("Costo: ${MoneyMath.format(row.costBase, currency)}", style = MaterialTheme.typography.bodySmall)
        Text(
          text = "+${MoneyMath.format(row.grossProfitBase, currency)}",
          style = MonoMetricMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}
