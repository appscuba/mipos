package com.example.core.export

import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Celda de una hoja de cálculo: texto, número o vacía. */
sealed interface XlsxCell {
  data class Text(val value: String) : XlsxCell
  data class Number(val value: BigDecimal) : XlsxCell
  data object Empty : XlsxCell
}

/**
 * Escritor mínimo de archivos .xlsx (OOXML) sin dependencias externas.
 *
 * Genera un libro válido con una sola hoja: cadenas en línea (`inlineStr`)
 * y celdas numéricas reales, empaquetado con [ZipOutputStream]. Suficiente
 * para exportar tablas que Excel/LibreOffice/Sheets abren sin reparos.
 */
object XlsxWriter {

  fun write(output: OutputStream, sheetName: String, rows: List<List<XlsxCell>>) {
    val safeSheetName = sanitizeSheetName(sheetName)
    ZipOutputStream(output.buffered()).use { zip ->
      zip.putEntry("[Content_Types].xml", CONTENT_TYPES)
      zip.putEntry("_rels/.rels", ROOT_RELS)
      zip.putEntry("xl/workbook.xml", workbookXml(safeSheetName))
      zip.putEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
      zip.putEntry("xl/worksheets/sheet1.xml", sheetXml(rows))
    }
  }

  private fun ZipOutputStream.putEntry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
  }

  private fun workbookXml(sheetName: String): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
      """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
      """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
      """<sheets><sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

  private fun sheetXml(rows: List<List<XlsxCell>>): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
    append("<sheetData>")
    rows.forEachIndexed { rowIndex, cells ->
      val rowNumber = rowIndex + 1
      append("""<row r="$rowNumber">""")
      cells.forEachIndexed { colIndex, cell ->
        val ref = "${columnLetters(colIndex)}$rowNumber"
        when (cell) {
          is XlsxCell.Text -> append(
            """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escapeXml(cell.value)}</t></is></c>""",
          )
          is XlsxCell.Number -> append("""<c r="$ref"><v>${cell.value.toPlainString()}</v></c>""")
          XlsxCell.Empty -> Unit // Sin elemento: la referencia explícita de las demás celdas mantiene la posición.
        }
      }
      append("</row>")
    }
    append("</sheetData></worksheet>")
  }

  /** Convierte un índice de columna 0-based en letras de Excel (A, B, …, Z, AA, AB…). */
  private fun columnLetters(zeroBasedIndex: Int): String {
    var n = zeroBasedIndex + 1
    val sb = StringBuilder()
    while (n > 0) {
      val rem = (n - 1) % 26
      sb.append('A' + rem)
      n = (n - 1) / 26
    }
    return sb.reverse().toString()
  }

  /** Los nombres de hoja no admiten \ / ? * [ ] : y tienen un máximo de 31 caracteres. */
  private fun sanitizeSheetName(name: String): String {
    val cleaned = name.replace(Regex("""[\\/?*\[\]:]"""), " ").trim()
    return cleaned.take(31).ifEmpty { "Hoja1" }
  }

  /** Escapa el contenido XML y descarta caracteres de control no válidos en XML 1.0. */
  private fun escapeXml(value: String): String = buildString(value.length) {
    for (ch in value) {
      when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        '\t', '\n', '\r' -> append(ch)
        else -> if (ch >= ' ') append(ch)
      }
    }
  }

  private const val CONTENT_TYPES =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
      """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
      """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
      """<Default Extension="xml" ContentType="application/xml"/>""" +
      """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
      """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
      """</Types>"""

  private const val ROOT_RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
      """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
      """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
      """</Relationships>"""

  private const val WORKBOOK_RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
      """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
      """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
      """</Relationships>"""
}
