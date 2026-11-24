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
  /**
   * Contrato público del plugin PTLS:
   * POST /wp-json/ptls/v1/app-validate
   * { "device_id": "...", "phone": "..." }
   */
  suspend fun validate(deviceId: String, phone: String): LicenseValidationResult =
    withContext(Dispatchers.IO) {
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

        val payload = JSONObject()
          .put("device_id", deviceId)
          .put("phone", phone.trim())
          .toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        parseLicenseResponse(body, responseCode)
      } catch (_: Exception) {
        unavailableLicenseResult()
      } finally {
        connection?.disconnect()
      }
    }
}

/** Parser aislado para poder verificar el contrato del plugin sin conexión de red. */
internal fun parseLicenseResponse(body: String, responseCode: Int): LicenseValidationResult {
  if (responseCode == 429 || responseCode >= 500 || responseCode in 300..399 || body.isBlank()) {
    return unavailableLicenseResult()
  }

  return try {
    val json = JSONObject(body)
    val rawStatus = json.optString("status", json.optString("code", "unavailable"))
      .trim()
      .lowercase()
    val status = when (rawStatus) {
      "active", "revoked", "expired", "invalid" -> rawStatus
      else -> "unavailable"
    }

    // Una respuesta HTTP de error nunca puede conceder una licencia activa.
    if (status == "active" && responseCode !in 200..299) return unavailableLicenseResult()

    // El plugin entrega expira_ts como timestamp Unix en segundos.
    val expiresAtSeconds = json.optLong("expira_ts", 0L)
    val expiresAt = if (expiresAtSeconds > 0L) expiresAtSeconds * 1_000L else 0L

    LicenseValidationResult(
      status = status,
      expiresAt = expiresAt,
      message = json.optString("message").takeIf { it.isNotBlank() },
    )
  } catch (_: Exception) {
    unavailableLicenseResult()
  }
}

private fun unavailableLicenseResult() = LicenseValidationResult(
  status = "unavailable",
  message = "No se pudo validar la licencia. Comprueba tu conexión e inténtalo de nuevo.",
)
