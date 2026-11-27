package com.example.core.common

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies picker images into app-private storage so the logo survives app restarts. */
object PersistentLogoStorage {
  suspend fun copyIntoAppStorage(context: Context, source: Uri): String =
    withContext(Dispatchers.IO) {
      val resolver = context.contentResolver
      val extension = when (resolver.getType(source)?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
      }
      val directory = File(context.filesDir, "business")
      check(directory.exists() || directory.mkdirs()) { "No se pudo crear el almacenamiento del logotipo." }
      val destination = File(directory, "store_logo.$extension")
      val temporary = File(directory, "store_logo.tmp")
      resolver.openInputStream(source)?.use { input ->
        temporary.outputStream().use { output -> input.copyTo(output) }
      } ?: error("No se pudo leer la imagen seleccionada.")
      check(temporary.length() > 0L) { "La imagen seleccionada está vacía." }
      if (!temporary.renameTo(destination)) {
        temporary.copyTo(destination, overwrite = true)
        temporary.delete()
      }
      directory.listFiles()?.filter {
        it.name.startsWith("store_logo.") && it != destination && it != temporary
      }?.forEach(File::delete)
      Uri.fromFile(destination).toString()
    }
}
