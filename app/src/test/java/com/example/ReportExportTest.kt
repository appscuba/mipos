package com.example

import com.example.core.export.XlsxCell
import com.example.core.export.XlsxWriter
import com.example.data.repository.BusinessReportSnapshot
import com.example.data.repository.ProductProfitabilityRow
import com.example.ui.reports.ReportExportBuilder
import com.example.ui.reports.toXlsxRows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportTest {

  private fun bd(value: String) = BigDecimal(value)

  private fun sampleSnapshot() = BusinessReportSnapshot(
    primaryCurrency = "CUP",
    salesCount = 3,
    purchasesCount = 1,
    expensesCount = 2,
    netSalesBase = bd("1500.00"),
    cogsBase = bd("1000.00"),
    grossProfitBase = bd("500.00"),
    grossMarginPercent = bd("33.3"),
    operatingExpensesBase = bd("100.00"),
    netProfitBase = bd("400.00"),
    netMarginPercent = bd("26.7"),
    totalPurchasesBase = bd("900.00"),
    extraIncomeBase = bd("0.00"),
    investedCapitalBase = bd("2000.00"),
    ownerWithdrawalsBase = bd("0.00"),
    inventoryValueBase = bd("5000.00"),
    currentCashDrawerBase = bd("750.00"),
    customerReceivableBase = bd("120.00"),
    supplierPayableBase = bd("300.00"),
    productProfitability = listOf(
      ProductProfitabilityRow(
        productId = 1L,
        productName = "Arroz 1kg",
        unitsSummary = "10 u",
        revenueBase = bd("1500.00"),
        costBase = bd("1000.00"),
        grossProfitBase = bd("500.00"),
        marginPercent = bd("33.3"),
      ),
    ),
    paymentMethodTotals = linkedMapOf("Efectivo" to bd("1500.00")),
  )

  private fun sampleModel(businessName: String = "Maná Shop") = ReportExportBuilder.build(
    snapshot = sampleSnapshot(),
    businessName = businessName,
    periodLabel = "Mes",
    periodRangeText = "01/11/2026 – 30/11/2026",
    generatedAtMillis = 1764000000000L,
  )

  @Test
  fun `el modelo formatea el resumen y los productos desde el snapshot`() {
    val model = sampleModel()

    assertEquals("Maná Shop", model.businessName)
    assertEquals("CUP", model.currency)
    assertTrue(model.generatedAtText.isNotBlank())

    val netSales = model.summaryEntries.first { it.label.startsWith("Ventas netas") }
    assertEquals("1,500.00 CUP", netSales.displayValue)
    assertEquals(0, bd("1500.00").compareTo(netSales.numericValue!!))

    val netProfit = model.summaryEntries.first { it.label.startsWith("Ganancia neta") }
    assertTrue(netProfit.emphasize)
    assertEquals("400.00 CUP", netProfit.displayValue)

    assertEquals(1, model.paymentEntries.size)
    assertEquals("Efectivo", model.paymentEntries.first().label)

    assertEquals(1, model.productRows.size)
    assertEquals("Arroz 1kg", model.productRows.first().productName)
    assertEquals(0, bd("1500.00").compareTo(model.totalRevenue))
    assertEquals(0, bd("500.00").compareTo(model.totalProfit))
  }

  @Test
  fun `las filas xlsx llevan encabezados de producto y fila de totales`() {
    val rows = sampleModel().toXlsxRows()

    val headerIndex = rows.indexOfFirst { row ->
      row.any { it is XlsxCell.Text && it.value == "Producto" } &&
        row.any { it is XlsxCell.Text && it.value == "Margen %" }
    }
    assertTrue("Debe existir la fila de encabezados de producto", headerIndex >= 0)

    val productRow = rows[headerIndex + 1]
    assertEquals(XlsxCell.Text("Arroz 1kg"), productRow[0])
    assertEquals("1500.00", (productRow[2] as XlsxCell.Number).value.toPlainString())
    assertEquals("33.3", (productRow[5] as XlsxCell.Number).value.toPlainString())

    val totalRow = rows[headerIndex + 2]
    assertEquals(XlsxCell.Text("TOTAL"), totalRow[0])
    assertEquals("1500.00", (totalRow[2] as XlsxCell.Number).value.toPlainString())
    assertEquals("500.00", (totalRow[4] as XlsxCell.Number).value.toPlainString())
  }

  @Test
  fun `el escritor xlsx genera un zip con las partes ooxml y los valores clave`() {
    val output = ByteArrayOutputStream()
    XlsxWriter.write(output, "Reporte", sampleModel().toXlsxRows())

    val parts = unzip(output.toByteArray())
    assertTrue(parts.containsKey("[Content_Types].xml"))
    assertTrue(parts.containsKey("_rels/.rels"))
    assertTrue(parts.containsKey("xl/workbook.xml"))
    assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"))
    assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))

    val workbook = parts.getValue("xl/workbook.xml").toString(Charsets.UTF_8)
    assertTrue(workbook.contains("""name="Reporte""""))

    val sheet = parts.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
    assertTrue(sheet.contains("Maná Shop"))
    assertTrue(sheet.contains("Arroz 1kg"))
    assertTrue(sheet.contains("ESTADO DE RESULTADOS"))
    assertTrue(sheet.contains("RENTABILIDAD POR PRODUCTO"))
    assertTrue(sheet.contains("<v>1500.00</v>"))
    assertTrue(sheet.contains("<v>400.00</v>"))
    assertTrue(sheet.contains("<v>33.3</v>"))
  }

  @Test
  fun `el escritor xlsx escapa los caracteres especiales del texto`() {
    val output = ByteArrayOutputStream()
    XlsxWriter.write(
      output,
      "Reporte",
      listOf(listOf(XlsxCell.Text("A & B <Tienda>"))),
    )
    val sheet = unzip(output.toByteArray())
      .getValue("xl/worksheets/sheet1.xml")
      .toString(Charsets.UTF_8)
    assertTrue(sheet.contains("A &amp; B &lt;Tienda&gt;"))
  }

  private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val parts = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        parts[entry.name] = zip.readBytes()
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }
    return parts
  }
}
