package com.example.ui.reports

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Genera el PDF del reporte con la API nativa [PdfDocument]:
 * cabecera con negocio/período/fecha, bloques de resumen en pares
 * concepto–valor y la tabla de rentabilidad por producto con paginación.
 */
object ReportPdfExporter {

  private const val PAGE_WIDTH = 595 // A4 a 72 dpi
  private const val PAGE_HEIGHT = 842
  private const val MARGIN = 40f
  private const val CONTENT_BOTTOM = PAGE_HEIGHT - 56f

  fun write(model: ReportExportModel, file: File) {
    val document = PdfDocument()
    try {
      Renderer(document, model).render()
      file.outputStream().use { output -> document.writeTo(output) }
    } finally {
      document.close()
    }
  }

  private class Renderer(
    private val document: PdfDocument,
    private val model: ReportExportModel,
  ) {
    private val titlePaint = paint(19f, bold = true)
    private val subtitlePaint = paint(13f, bold = true, color = Color.rgb(66, 66, 66))
    private val metaPaint = paint(10f, color = Color.rgb(97, 97, 97))
    private val sectionPaint = paint(12.5f, bold = true)
    private val bodyPaint = paint(10.5f)
    private val bodyBoldPaint = paint(10.5f, bold = true)
    private val tableHeaderPaint = paint(9.5f, bold = true)
    private val tablePaint = paint(9.5f)
    private val tableBoldPaint = paint(9.5f, bold = true)
    private val footerPaint = paint(8.5f, color = Color.rgb(117, 117, 117))
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(189, 189, 189)
      strokeWidth = 0.7f
    }

    private val numberFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US).apply {
      groupingSeparator = ','
      decimalSeparator = '.'
    })

    // Extremos derechos de las columnas de la tabla de productos.
    private val colUnitsEnd = 292f
    private val colRevenueEnd = 372f
    private val colCostEnd = 442f
    private val colProfitEnd = 512f
    private val colMarginEnd = PAGE_WIDTH - MARGIN

    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = MARGIN

    fun render() {
      startPage()
      drawHeader()
      drawSummarySection("Estado de resultados", model.summaryEntries)
      drawSummarySection("Balance actual", model.balanceEntries)
      if (model.paymentEntries.isNotEmpty()) {
        drawSummarySection("Ingresos por forma de pago", model.paymentEntries)
      }
      drawProductTable()
      finishPage()
    }

    private fun drawHeader() {
      val canvas = canvas()
      canvas.drawText(model.businessName, MARGIN, y + 15f, titlePaint)
      y += 26f
      canvas.drawText("Reporte financiero", MARGIN, y + 11f, subtitlePaint)
      y += 20f
      val meta = buildString {
        append("Período: ${model.periodLabel}")
        model.periodRangeText?.let { append("  ·  $it") }
        append("  ·  Generado: ${model.generatedAtText}")
        append("  ·  Moneda: ${model.currency}")
      }
      canvas.drawText(meta, MARGIN, y + 8f, metaPaint)
      y += 14f
      drawLine()
      y += 10f
    }

    private fun drawSummarySection(title: String, entries: List<ReportSummaryEntry>) {
      ensureSpace(30f)
      canvas().drawText(title, MARGIN, y + 10f, sectionPaint)
      y += 18f
      entries.forEach { entry ->
        ensureSpace(15f)
        val labelPaint = if (entry.emphasize) bodyBoldPaint else bodyPaint
        val valuePaint = if (entry.emphasize) bodyBoldPaint else bodyPaint
        canvas().drawText(entry.label, MARGIN, y + 8f, labelPaint)
        valuePaint.textAlign = Paint.Align.RIGHT
        canvas().drawText(entry.displayValue, colMarginEnd, y + 8f, valuePaint)
        valuePaint.textAlign = Paint.Align.LEFT
        y += 15f
      }
      y += 6f
      drawLine()
      y += 12f
    }

    private fun drawProductTable() {
      ensureSpace(46f)
      canvas().drawText("Rentabilidad por producto", MARGIN, y + 10f, sectionPaint)
      y += 18f
      drawProductTableHeader()
      if (model.productRows.isEmpty()) {
        ensureSpace(15f)
        canvas().drawText("Sin ventas registradas en este período.", MARGIN, y + 8f, bodyPaint)
        y += 15f
        return
      }
      model.productRows.forEach { row ->
        if (ensureSpace(14f)) drawProductTableHeader()
        drawProductRow(
          name = row.productName,
          units = row.unitsSummary,
          revenue = row.revenue,
          cost = row.cost,
          profit = row.grossProfit,
          margin = "${row.marginPercent}%",
          bold = false,
        )
        y += 14f
      }
      if (ensureSpace(16f)) drawProductTableHeader()
      drawLine()
      y += 3f
      drawProductRow(
        name = "TOTAL",
        units = "",
        revenue = model.totalRevenue,
        cost = model.totalCost,
        profit = model.totalProfit,
        margin = "",
        bold = true,
      )
      y += 14f
    }

    private fun drawProductTableHeader() {
      val canvas = canvas()
      val baseline = y + 8f
      canvas.drawText("Producto", MARGIN, baseline, tableHeaderPaint)
      drawRight("Unidades", colUnitsEnd, baseline, tableHeaderPaint)
      drawRight("Ingresos", colRevenueEnd, baseline, tableHeaderPaint)
      drawRight("Costo", colCostEnd, baseline, tableHeaderPaint)
      drawRight("Ganancia", colProfitEnd, baseline, tableHeaderPaint)
      drawRight("Margen %", colMarginEnd, baseline, tableHeaderPaint)
      y += 12f
      drawLine()
      y += 2f
    }

    private fun drawProductRow(
      name: String,
      units: String,
      revenue: BigDecimal,
      cost: BigDecimal,
      profit: BigDecimal,
      margin: String,
      bold: Boolean,
    ) {
      val canvas = canvas()
      val textPaint = if (bold) tableBoldPaint else tablePaint
      val baseline = y + 8f
      val maxNameWidth = colUnitsEnd - MARGIN - 52f
      canvas.drawText(ellipsize(name, textPaint, maxNameWidth), MARGIN, baseline, textPaint)
      if (units.isNotEmpty()) drawRight(units, colUnitsEnd, baseline, textPaint)
      drawRight(numberFormat.format(revenue), colRevenueEnd, baseline, textPaint)
      drawRight(numberFormat.format(cost), colCostEnd, baseline, textPaint)
      drawRight(numberFormat.format(profit), colProfitEnd, baseline, textPaint)
      if (margin.isNotEmpty()) drawRight(margin, colMarginEnd, baseline, textPaint)
    }

    private fun drawRight(text: String, rightX: Float, baseline: Float, paint: Paint) {
      paint.textAlign = Paint.Align.RIGHT
      canvas().drawText(text, rightX, baseline, paint)
      paint.textAlign = Paint.Align.LEFT
    }

    private fun drawLine() {
      canvas().drawLine(MARGIN, y, colMarginEnd, y, linePaint)
    }

    /** Si no caben [needed] puntos, cierra la página y abre otra. Devuelve true si cambió de página. */
    private fun ensureSpace(needed: Float): Boolean {
      if (y + needed <= CONTENT_BOTTOM) return false
      finishPage()
      startPage()
      return true
    }

    private fun startPage() {
      pageNumber += 1
      val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
      page = document.startPage(info)
      y = MARGIN
    }

    private fun finishPage() {
      val current = page ?: return
      footerPaint.textAlign = Paint.Align.RIGHT
      current.canvas.drawText("Página $pageNumber", colMarginEnd, PAGE_HEIGHT - 24f, footerPaint)
      footerPaint.textAlign = Paint.Align.LEFT
      document.finishPage(current)
      page = null
    }

    private fun canvas() = (page ?: error("Página no iniciada")).canvas

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
      if (paint.measureText(text) <= maxWidth) return text
      var end = text.length
      while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
      return if (end == 0) "…" else text.substring(0, end) + "…"
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK): Paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
  }
}
