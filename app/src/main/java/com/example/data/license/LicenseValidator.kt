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
  /** POST /wp-json/ptls/v1/app-validate { "device_id": "..." } */
  suspend fun validate(deviceId: String): LicenseValidationResult = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 12_000
        readTimeout = 12_000
        doOutput = true
        instanceFollowRedirects = false
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-store")
      }
      val payload = JSONObject().put("device_id", deviceId).toString()
      connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      parseLicenseResponse(stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(), code)
    } catch (_: Exception) {
      unavailableLicenseResult()
    } finally {
      connection?.disconnect()
    }
  }
}

internal fun parseLicenseResponse(body: String, responseCode: Int): LicenseValidationResult {
  if (responseCode == 429 || responseCode >= 500 || responseCode in 300..399 || body.isBlank()) return unavailableLicenseResult()
  return try {
    val json = JSONObject(body)
    val status = json.optString("status", "unavailable").trim().lowercase()
    if (status !in setOf("active", "pending", "revoked", "expired", "not_found")) return unavailableLicenseResult()
    if (status == "active" && responseCode !in 200..299) return unavailableLicenseResult()
    val seconds = json.optLong("expira_ts", 0L)
    LicenseValidationResult(status, if (seconds > 0L) seconds * 1_000L else 0L,
      json.optString("message").takeIf { it.isNotBlank() })
  } catch (_: Exception) { unavailableLicenseResult() }
}

private fun unavailableLicenseResult() = LicenseValidationResult(
  status = "unavailable",
  message = "No se pudo consultar el servidor. Comprueba tu conexión e inténtalo de nuevo.",
)
