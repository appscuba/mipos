package com.example.ui.reports

import com.example.core.export.XlsxCell
import com.example.core.money.MoneyMath
import com.example.data.repository.BusinessReportSnapshot
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Par concepto/valor de un bloque de resumen del reporte exportable. */
data class ReportSummaryEntry(
  val label: String,
  val displayValue: String,
  val numericValue: BigDecimal? = null,
  val emphasize: Boolean = false,
)

/** Fila de rentabilidad por producto lista para exportar. */
data class ReportProductExportRow(
  val productName: String,
  val unitsSummary: String,
  val revenue: BigDecimal,
  val cost: BigDecimal,
  val grossProfit: BigDecimal,
  val marginPercent: BigDecimal,
)

/**
 * Datos del reporte ya formateados para compartir fuera de la app
 * (PDF, Excel o texto). Se construye desde el mismo snapshot que pinta
 * la pantalla, sin métricas nuevas.
 */
data class ReportExportModel(
  val businessName: String,
  val periodLabel: String,
  val periodRangeText: String?,
  val generatedAtText: String,
  val currency: String,
  val summaryEntries: List<ReportSummaryEntry>,
  val balanceEntries: List<ReportSummaryEntry>,
  val paymentEntries: List<ReportSummaryEntry>,
  val productRows: List<ReportProductExportRow>,
) {
  val totalRevenue: BigDecimal get() = productRows.fold(MoneyMath.ZERO) { acc, r -> MoneyMath.add(acc, r.revenue) }
  val totalCost: BigDecimal get() = productRows.fold(MoneyMath.ZERO) { acc, r -> MoneyMath.add(acc, r.cost) }
  val totalProfit: BigDecimal get() = productRows.fold(MoneyMath.ZERO) { acc, r -> MoneyMath.add(acc, r.grossProfit) }
}

object ReportExportBuilder {

  fun build(
    snapshot: BusinessReportSnapshot,
    businessName: String,
    periodLabel: String,
    periodRangeText: String?,
    generatedAtMillis: Long,
  ): ReportExportModel {
    val cur = snapshot.primaryCurrency
    fun money(label: String, value: BigDecimal, emphasize: Boolean = false) =
      ReportSummaryEntry(label, MoneyMath.format(value, cur), value, emphasize)

    val summary = listOf(
      money("Ventas netas (${snapshot.salesCount})", snapshot.netSalesBase),
      money("Costo de mercancía vendida (COGS)", snapshot.cogsBase),
      money("Ganancia bruta (${snapshot.grossMarginPercent}%)", snapshot.grossProfitBase),
      money("Gastos operativos (${snapshot.expensesCount})", snapshot.operatingExpensesBase),
      money("Ganancia neta (${snapshot.netMarginPercent}%)", snapshot.netProfitBase, emphasize = true),
    )

    val balance = listOf(
      money("Compras del período (${snapshot.purchasesCount})", snapshot.totalPurchasesBase),
      money("Valor inventario (al costo)", snapshot.inventoryValueBase),
      money("Efectivo en caja abierta", snapshot.currentCashDrawerBase),
      money("Cuentas por cobrar (Clientes)", snapshot.customerReceivableBase),
      money("Cuentas por pagar (Proveedores)", snapshot.supplierPayableBase),
      money("Ingresos extra", snapshot.extraIncomeBase),
      money("Capital invertido", snapshot.investedCapitalBase),
      money("Retiros del dueño", snapshot.ownerWithdrawalsBase),
    )

    val payments = snapshot.paymentMethodTotals.map { (method, total) -> money(method, total) }

    val products = snapshot.productProfitability.map { row ->
      ReportProductExportRow(
        productName = row.productName,
        unitsSummary = row.unitsSummary,
        revenue = row.revenueBase,
        cost = row.costBase,
        grossProfit = row.grossProfitBase,
        marginPercent = row.marginPercent,
      )
    }

    val generatedFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    return ReportExportModel(
      businessName = businessName,
      periodLabel = periodLabel,
      periodRangeText = periodRangeText,
      generatedAtText = generatedFormat.format(Date(generatedAtMillis)),
      currency = cur,
      summaryEntries = summary,
      balanceEntries = balance,
      paymentEntries = payments,
      productRows = products,
    )
  }
}

/** Convierte el modelo en las filas de la hoja «Reporte» del archivo Excel. */
fun ReportExportModel.toXlsxRows(): List<List<XlsxCell>> {
  fun entryRow(entry: ReportSummaryEntry): List<XlsxCell> = listOf(
    XlsxCell.Text(entry.label),
    entry.numericValue?.let { XlsxCell.Number(it) } ?: XlsxCell.Text(entry.displayValue),
  )

  val rows = mutableListOf<List<XlsxCell>>()
  rows += listOf(XlsxCell.Text(businessName))
  rows += listOf(XlsxCell.Text("Reporte financiero"))
  rows += listOf(XlsxCell.Text("Período: $periodLabel"))
  if (periodRangeText != null) rows += listOf(XlsxCell.Text("Rango: $periodRangeText"))
  rows += listOf(XlsxCell.Text("Generado: $generatedAtText"))
  rows += listOf(XlsxCell.Text("Moneda: $currency"))
  rows += emptyList<XlsxCell>()
  rows += listOf(XlsxCell.Text("ESTADO DE RESULTADOS"))
  summaryEntries.forEach { rows += entryRow(it) }
  rows += emptyList<XlsxCell>()
  rows += listOf(XlsxCell.Text("BALANCE ACTUAL"))
  balanceEntries.forEach { rows += entryRow(it) }
  if (paymentEntries.isNotEmpty()) {
    rows += emptyList<XlsxCell>()
    rows += listOf(XlsxCell.Text("INGRESOS POR FORMA DE PAGO"))
    paymentEntries.forEach { rows += entryRow(it) }
  }
  rows += emptyList<XlsxCell>()
  rows += listOf(XlsxCell.Text("RENTABILIDAD POR PRODUCTO"))
  rows += listOf(
    XlsxCell.Text("Producto"),
    XlsxCell.Text("Unidades"),
    XlsxCell.Text("Ingresos"),
    XlsxCell.Text("Costo"),
    XlsxCell.Text("Ganancia"),
    XlsxCell.Text("Margen %"),
  )
  if (productRows.isEmpty()) {
    rows += listOf(XlsxCell.Text("Sin ventas registradas en este período."))
  } else {
    productRows.forEach { row ->
      rows += listOf(
        XlsxCell.Text(row.productName),
        XlsxCell.Text(row.unitsSummary),
        XlsxCell.Number(row.revenue),
        XlsxCell.Number(row.cost),
        XlsxCell.Number(row.grossProfit),
        XlsxCell.Number(row.marginPercent),
      )
    }
    rows += listOf(
      XlsxCell.Text("TOTAL"),
      XlsxCell.Empty,
      XlsxCell.Number(totalRevenue),
      XlsxCell.Number(totalCost),
      XlsxCell.Number(totalProfit),
      XlsxCell.Empty,
    )
  }
  return rows
}
