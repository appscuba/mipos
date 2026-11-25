package com.example.data.license

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject

/**
 * Verificador Ed25519 de las respuestas firmadas de LicenseHub.
 *
 * La app distribuye SOLO la clave pública (abajo). La privada vive
 * únicamente en el servidor. Sin una firma válida, la respuesta se
 * rechaza: así nadie puede falsificar una licencia modificando el
 * tráfico HTTP, las preferencias o la base de datos local.
 *
 * Dependencia (funciona desde API 24, sin Google Play Services):
 *   implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
 * En Android 13+ también existe soporte de plataforma, pero
 * BouncyCastle garantiza el mismo comportamiento en todos los equipos.
 *
 * La firma cubre el JSON CANÓNICO de la respuesta (claves ordenadas,
 * sin "kid", "signature" ni "offline_grant"), igual que en el servidor.
 */
object Ed25519Verifier {

    /**
     * Clave pública de firma de LicenseHub (DER SPKI, base64).
     * kid: lh-2026-01 — al rotar, añade la nueva clave con su kid.
     * Coincide con LICENSE_SIGNING_PUBLIC_KEY del servidor (verificado
     * contra .env.local y la semilla de LicenseHub el 2026-11-25).
     */
    private val PUBLIC_KEYS: Map<String, String> = mapOf(
        "lh-2026-01" to "MCowBQYDK2VwAyEAGTnctpREoEr4UHpuRVOd8LKhmdUvKm/43bB7WbtUwng="
    )

    /** Verifica la firma de una respuesta de validación/activación/estado. */
    fun verifyResponse(response: LicenseResponse): Boolean {
        val kid = response.kid ?: return false
        val signatureB64 = response.signature ?: return false
        val publicKeyB64 = PUBLIC_KEYS[kid] ?: return false

        val payload = JSONObject()
        for (key in SIGNED_FIELDS) {
            if (response.raw.has(key)) payload.put(key, response.raw.get(key))
        }
        val canonical = canonicalJson(payload)
        return verify(publicKeyB64, canonical.toByteArray(Charsets.UTF_8), signatureB64)
    }

    /** Verifica un token de autorización offline (grant firmado). */
    fun verifyOfflineGrant(grantJson: JSONObject): Boolean {
        val kid = grantJson.optStringOrNull("kid") ?: return false
        val signatureB64 = grantJson.optStringOrNull("signature") ?: return false
        val publicKeyB64 = PUBLIC_KEYS[kid] ?: return false
        val payload = JSONObject()
        val keys = grantJson.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k != "kid" && k != "signature") payload.put(k, grantJson.get(k))
        }
        val canonical = canonicalJson(payload)
        return verify(publicKeyB64, canonical.toByteArray(Charsets.UTF_8), signatureB64)
    }

    private fun verify(publicKeyB64: String, data: ByteArray, signatureB64: String): Boolean =
        try {
            val spki = Base64.decode(publicKeyB64, Base64.DEFAULT)
            // SPKI de Ed25519: cabecera DER de 12 bytes + clave cruda de 32 bytes.
            val rawPublic = spki.copyOfRange(spki.size - 32, spki.size)
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(rawPublic, 0))
            signer.update(data, 0, data.size)
            signer.verifySignature(Base64.decode(signatureB64, Base64.DEFAULT))
        } catch (e: Exception) {
            false
        }

    /** JSON canónico: claves ordenadas alfabéticamente, sin espacios. */
    fun canonicalJson(json: JSONObject): String {
        val keys = mutableListOf<String>()
        val it = json.keys()
        while (it.hasNext()) keys.add(it.next())
        keys.sort()
        val sb = StringBuilder("{")
        keys.forEachIndexed { index, key ->
            if (index > 0) sb.append(",")
            sb.append(JSONObject.quote(key)).append(":")
            sb.append(canonicalValue(json.get(key)))
        }
        return sb.append("}").toString()
    }

    private fun canonicalValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> canonicalJson(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    // Campos que firma el servidor en las respuestas de la API v1.
    private val SIGNED_FIELDS = listOf(
        "valid", "status", "application", "plan", "expires_at",
        "installation_id", "server_time", "message", "code"
    )
}
