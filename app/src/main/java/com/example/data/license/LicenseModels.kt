package com.example.data.license

import org.json.JSONObject

/** Respuesta de la API de LicenseHub (validate / activate / status). */
data class LicenseResponse(
    val valid: Boolean,
    val status: String,
    val application: String?,
    val plan: String?,
    val expiresAt: String?,
    val installationId: String?,
    val serverTime: String,
    val message: String,
    val code: String?,
    val kid: String?,
    val signature: String?,
    val offlineGrant: String?,
    /** JSON original completo, necesario para verificar la firma. */
    val raw: JSONObject
) {
    companion object {
        fun fromJson(json: JSONObject): LicenseResponse = LicenseResponse(
            valid = json.optBoolean("valid", false),
            status = json.optString("status", "unknown"),
            application = json.optStringOrNull("application"),
            plan = json.optStringOrNull("plan"),
            expiresAt = json.optStringOrNull("expires_at"),
            installationId = json.optStringOrNull("installation_id"),
            serverTime = json.optString("server_time", ""),
            message = json.optString("message", ""),
            code = json.optStringOrNull("code"),
            kid = json.optStringOrNull("kid"),
            signature = json.optStringOrNull("signature"),
            offlineGrant = json.optStringOrNull("offline_grant"),
            raw = json
        )
    }
}

fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

/** Códigos de error estables de la API (ver docs/API.md de LicenseHub). */
object LicenseErrorCodes {
    const val LICENSE_NOT_FOUND = "LICENSE_NOT_FOUND"
    const val APPLICATION_NOT_FOUND = "APPLICATION_NOT_FOUND"
    const val APPLICATION_DISABLED = "APPLICATION_DISABLED"
    const val LICENSE_EXPIRED = "LICENSE_EXPIRED"
    const val LICENSE_SUSPENDED = "LICENSE_SUSPENDED"
    const val LICENSE_REVOKED = "LICENSE_REVOKED"
    const val DEVICE_MISMATCH = "DEVICE_MISMATCH"
    const val DEVICE_LIMIT_REACHED = "DEVICE_LIMIT_REACHED"
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val SERVER_ERROR = "SERVER_ERROR"
}

/**
 * Mensajes en español para el usuario. La API ya responde con su propio
 * mensaje en español: se usa ese cuando viene; el mapa es el respaldo
 * para códigos conocidos y estados locales.
 */
object LicenseMessages {
    fun forCode(code: String?, serverMessage: String?): String =
        serverMessage?.takeIf { it.isNotBlank() } ?: when (code) {
            LicenseErrorCodes.LICENSE_NOT_FOUND ->
                "No se encontró esa clave de licencia. Revísala, o pide la tuya a PedritoTech por WhatsApp."
            LicenseErrorCodes.LICENSE_EXPIRED ->
                "Tu licencia venció. Contacta a PedritoTech para renovarla."
            LicenseErrorCodes.LICENSE_SUSPENDED ->
                "Tu licencia está suspendida. Contacta a PedritoTech."
            LicenseErrorCodes.LICENSE_REVOKED ->
                "Esta licencia fue revocada. Contacta a PedritoTech."
            LicenseErrorCodes.DEVICE_MISMATCH ->
                "Esta licencia ya está vinculada a otro dispositivo. " +
                    "Pide a PedritoTech que desvincule el anterior desde su panel."
            LicenseErrorCodes.DEVICE_LIMIT_REACHED ->
                "Se alcanzó el límite de dispositivos de esta licencia. Contacta a PedritoTech."
            LicenseErrorCodes.APPLICATION_NOT_FOUND, LicenseErrorCodes.APPLICATION_DISABLED ->
                "La activación no está disponible en este momento. Contacta a PedritoTech."
            LicenseErrorCodes.RATE_LIMITED ->
                "Demasiados intentos seguidos. Espera unos minutos e inténtalo de nuevo."
            LicenseErrorCodes.INVALID_REQUEST ->
                "La solicitud no es válida. Revisa la clave escrita."
            else ->
                "No se pudo validar la licencia. Inténtalo de nuevo."
        }
}
