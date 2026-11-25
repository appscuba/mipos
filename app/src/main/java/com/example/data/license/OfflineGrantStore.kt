package com.example.data.license

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.time.Instant

/**
 * Caché de validación y política SIN CONEXIÓN.
 *
 * Cuando /validate responde válido, el servidor incluye un
 * "offline_grant": una autorización temporal FIRMADA (Ed25519) con
 * expiración (48 h según la configuración del servidor). Sin conexión,
 * la app acepta el grant solo si:
 *   1) la firma verifica con la clave pública,
 *   2) el installation_id coincide con el de este equipo,
 *   3) no ha expirado.
 * El grant NUNCA se renueva solo: al expirar hay que validar en línea.
 */
class OfflineGrantStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(grantToken: String, licenseKey: String) {
        prefs.edit()
            .putString(KEY_GRANT, grantToken)
            .putString(KEY_LICENSE, licenseKey)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun savedLicenseKey(): String? = prefs.getString(KEY_LICENSE, null)

    fun clear() {
        prefs.edit().remove(KEY_GRANT).remove(KEY_LICENSE).remove(KEY_SAVED_AT).apply()
    }

    /** ¿Hay un grant offline vigente y auténtico para este dispositivo? */
    fun hasValidGrant(installationId: String): Boolean =
        verifiedGrantExpiresAtMs(installationId) > System.currentTimeMillis()

    /**
     * Vencimiento (epoch ms) del grant guardado SI su firma verifica y
     * corresponde a esta instalación; 0 en cualquier otro caso. Sirve
     * tanto para decidir sin conexión como para persistir la ventana
     * de uso en las preferencias de la app.
     */
    fun verifiedGrantExpiresAtMs(installationId: String): Long {
        val token = prefs.getString(KEY_GRANT, null) ?: return 0L
        return try {
            val json = JSONObject(
                String(Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            )
            if (!Ed25519Verifier.verifyOfflineGrant(json)) return 0L
            if (json.optString("installation_id") != installationId) return 0L
            Instant.parse(json.optString("expires_at")).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        private const val PREFS_NAME = "licensehub_prefs"
        private const val KEY_GRANT = "offline_grant"
        private const val KEY_LICENSE = "license_key"
        private const val KEY_SAVED_AT = "grant_saved_at"
    }
}
