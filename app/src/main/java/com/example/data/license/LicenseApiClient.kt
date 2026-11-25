package com.example.data.license

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP de la API v1 de LicenseHub.
 * Usa HttpURLConnection: sin dependencias y sin Google Play Services
 * (el mismo enfoque que usaba el cliente del sistema anterior).
 * Las llamadas se ejecutan en Dispatchers.IO.
 */
class LicenseApiClient(
    private val baseUrl: String, // https://appscuba.vercel.app
    private val applicationSlug: String // "mipos"
) {

    suspend fun activate(
        licenseKey: String,
        installationId: String,
        appVersion: String
    ): Result<LicenseResponse> = post("/api/v1/licenses/activate", licenseKey, installationId, appVersion)

    suspend fun validate(
        licenseKey: String,
        installationId: String,
        appVersion: String
    ): Result<LicenseResponse> = post("/api/v1/licenses/validate", licenseKey, installationId, appVersion)

    suspend fun status(
        licenseKey: String,
        installationId: String,
        appVersion: String
    ): Result<LicenseResponse> = post("/api/v1/licenses/status", licenseKey, installationId, appVersion)

    private suspend fun post(
        path: String,
        licenseKey: String,
        installationId: String,
        appVersion: String
    ): Result<LicenseResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("application_slug", applicationSlug)
                .put("license_key", licenseKey)
                .put("installation_id", installationId)
                .put("app_version", appVersion)
                .put("platform", "android")
            val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                // La API responde con el cuerpo firmado también en errores de
                // negocio (404/403): el modelo se parsea igual y la decisión
                // se toma por "valid" + "code", no por el HTTP status.
                LicenseResponse.fromJson(JSONObject(text))
            } finally {
                conn.disconnect()
            }
        }
    }
}
