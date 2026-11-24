package com.example.data.license

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LicenseValidationResult(
  val status: String,
  val expiresAt: Long = 0L,
  val message: String? = null,
)

class LicenseValidator(
  private val endpoint: String = PlanLimits.VALIDATION_URL,
) {
  suspend fun validate(deviceId: String, phone: String): LicenseValidationResult =
    withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 12_000
          readTimeout = 12_000
          doOutput = true
          setRequestProperty("Content-Type", "application/json; charset=utf-8")
          setRequestProperty("Accept", "application/json")
        }
        val payload = JSONObject()
          .put("device_id", deviceId)
          .put("phone", phone)
          .toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = JSONObject(body)
        val status = json.optString("status", "unavailable")
        val expiresAt = json.optLong("expira_ts", 0L).let { if (it > 0L) it * 1000L else 0L }
        LicenseValidationResult(status = status, expiresAt = expiresAt)
      } catch (_: Exception) {
        LicenseValidationResult(status = "unavailable", message = "No se pudo conectar. Comprueba tu conexión e inténtalo de nuevo.")
      } finally {
        connection?.disconnect()
      }
    }
}
