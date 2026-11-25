package com.example.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.core.export.XlsxWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el reporte exportado (PDF o Excel) en la caché de la app
 * (`cacheDir/exports`) y lo comparte con `ACTION_SEND` vía FileProvider.
 */
object ReportExportFiles {

  const val PDF_MIME = "application/pdf"
  const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

  fun writePdf(context: Context, model: ReportExportModel): File {
    val file = exportFile(context, model, "pdf")
    ReportPdfExporter.write(model, file)
    return file
  }

  fun writeExcel(context: Context, model: ReportExportModel): File {
    val file = exportFile(context, model, "xlsx")
    file.outputStream().use { output -> XlsxWriter.write(output, "Reporte", model.toXlsxRows()) }
    return file
  }

  fun shareFile(context: Context, file: File, mimeType: String, subject: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
  }

  private fun exportFile(context: Context, model: ReportExportModel, extension: String): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val slug = model.businessName.lowercase(Locale.US)
      .replace(Regex("[^a-z0-9]+"), "-")
      .trim('-')
      .ifEmpty { "negocio" }
    return File(dir, "reporte-$slug-$stamp.$extension")
  }
}
