package com.example.data.license

import android.content.Context
import android.os.SystemClock
import com.example.BuildConfig
import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * LicenseManager — punto único de licencias de Stock+ contra
 * LicenseHub (https://appscuba.vercel.app).
 *
 * Sustituye al cliente del sistema anterior (pedritotech.cu +
 * teléfono): aquí el cliente escribe su CLAVE DE LICENCIA y la app
 * envía {application: "mipos", license_key, installation_id}. El
 * teléfono nunca es identidad en este sistema.
 *
 * Flujo:
 * 1) La clave introducida se guarda en las preferencias (DataStore).
 * 2) Al arrancar: si hay conexión, se valida contra LicenseHub y se
 *    VERIFICA LA FIRMA Ed25519 de la respuesta antes de aceptarla.
 * 3) Sin conexión: manda la CUENTA REGRESIVA local — al validar se
 *    ancla el tiempo que le queda a la licencia según el servidor
 *    (vencimiento − hora del servidor, ambos firmados) y desde ahí
 *    corre con el reloj monotónico del equipo, inmune a cambios de
 *    hora del sistema. Ver LicenseCountdown.
 * 4) El servidor es la autoridad: al reconsultar (cada 24 h si hay
 *    conexión), suspendida, revocada o vencida bloquean y la
 *    renovación re-ancla la cuenta con el tiempo nuevo.
 */
class LicenseManager(
    context: Context,
    private val preferences: AppPreferencesDataStore
) {

    private val appContext = context.applicationContext
    private val installationIds = InstallationIdProvider(preferences)
    private val grants = OfflineGrantStore(appContext)
    private val client = LicenseApiClient(
        baseUrl = BuildConfig.LICENSEHUB_URL,
        applicationSlug = APPLICATION_SLUG
    )

    enum class State { ACTIVE, INACTIVE, BLOCKED, OFFLINE_ACTIVE, ERROR }

    data class CheckResult(
        val state: State,
        val message: String,
        val expiresAtMs: Long = 0L,
        val errorCode: String? = null
    )

    suspend fun installationId(): String = installationIds.getOrCreate()

    suspend fun savedLicenseKey(): String? =
        preferences.preferencesFlow.first().licenseKey.takeIf { it.isNotBlank() }
            ?: grants.savedLicenseKey()

    /** Primera activación (vincula este dispositivo a la licencia). */
    suspend fun activate(licenseKey: String): CheckResult {
        val key = licenseKey.trim()
        if (key.isBlank()) {
            return CheckResult(State.INACTIVE, "Introduce tu clave de licencia.")
        }
        preferences.saveLicenseKey(key)
        val id = installationId()
        return client.activate(key, id, appVersion()).fold(
            onSuccess = { response -> handleResponse(response, key) },
            onFailure = { offlineFallback(key, "Sin conexión con el servidor de licencias.") }
        )
    }

    /** Validación al arrancar la app y al abrir la pantalla de activación. */
    suspend fun check(): CheckResult {
        val key = savedLicenseKey()
            ?: return CheckResult(State.INACTIVE, "La app aún no está activada.")
        val id = installationId()
        return client.validate(key, id, appVersion()).fold(
            onSuccess = { response -> handleResponse(response, key) },
            onFailure = { offlineFallback(key, "Sin conexión. Se usa la cuenta regresiva local.") }
        )
    }

    /**
     * Al abrir la app: si la última validación tiene más de 24 h (o
     * nunca se validó habiendo clave), reconsulta en silencio. Sin
     * conexión no pasa nada: manda el grant firmado vigente.
     */
    suspend fun refreshIfStale() {
        reanchorCountdown()
        val prefs = preferences.preferencesFlow.first()
        if (!LicenseRules.needsRevalidation(prefs, System.currentTimeMillis())) return
        check()
    }

    /**
     * Re-ancla la cuenta regresiva al abrir la app: el tiempo ya
     * transcurrido queda descontado del restante guardado y las
     * marcas de ambos relojes se renuevan. Así, tras un reinicio (el
     * reloj monotónico vuelve a cero), el tiempo apagado también
     * queda contado y no se puede recuperar cambiando la hora.
     */
    private suspend fun reanchorCountdown() {
        val prefs = preferences.preferencesFlow.first()
        if (prefs.licenseStatus != LicenseRules.STATUS_ACTIVE) return
        if (prefs.licenseExpiresAt <= 0L) return
        if (prefs.licenseAnchorRemainingMs < 0L) return
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val remaining = LicenseCountdown.remainingMs(
            anchorRemainingMs = prefs.licenseAnchorRemainingMs,
            anchorElapsedMs = prefs.licenseAnchorElapsedMs,
            anchorWallMs = prefs.licenseAnchorWallMs,
            nowElapsedMs = nowElapsed,
            nowWallMs = nowWall
        )
        preferences.saveLicenseCountdownAnchor(remaining, nowElapsed, nowWall)
    }

    private suspend fun handleResponse(response: LicenseResponse, key: String): CheckResult {
        // 1) La firma manda: una respuesta sin firma válida se rechaza.
        if (!Ed25519Verifier.verifyResponse(response)) {
            return CheckResult(
                State.ERROR,
                "La respuesta del servidor no superó la verificación de seguridad.",
                errorCode = LicenseErrorCodes.SERVER_ERROR
            )
        }
        // 2) El ID de instalación de la respuesta debe ser el nuestro.
        val id = installationId()
        if (response.installationId != null && response.installationId != id) {
            return CheckResult(State.BLOCKED, "La respuesta no corresponde a este dispositivo.")
        }
        val now = System.currentTimeMillis()
        val expiresMs = parseIsoToMs(response.expiresAt)
        if (response.valid) {
            // Grant offline firmado: se guarda y se persiste su
            // vencimiento YA verificado. Si una respuesta válida no
            // trajera grant, la ventana local es la gracia de 48 h.
            val grantExpiresMs = if (response.offlineGrant != null) {
                grants.save(response.offlineGrant, key)
                grants.verifiedGrantExpiresAtMs(id)
            } else {
                now + LicenseRules.GRACE_MS
            }
            // Cuenta regresiva: el restante nace de DOS datos del
            // servidor (su hora y el vencimiento, ambos dentro de la
            // respuesta firmada), nunca del reloj del teléfono. Sin
            // fecha de fin = licencia de por vida (sin ancla).
            val serverNowMs = parseIsoToMs(response.serverTime)
            val anchorRemainingMs = when {
                expiresMs <= 0L -> LicenseCountdown.NO_ANCHOR
                serverNowMs > 0L -> maxOf(0L, expiresMs - serverNowMs)
                else -> maxOf(0L, expiresMs - now)
            }
            preferences.saveLicenseValidation(
                status = LicenseRules.STATUS_ACTIVE,
                expiresAt = expiresMs,
                validatedAt = now,
                grantExpiresAt = grantExpiresMs,
                anchorRemainingMs = anchorRemainingMs,
                anchorElapsedMs = SystemClock.elapsedRealtime(),
                anchorWallMs = now
            )
            return CheckResult(
                State.ACTIVE,
                response.message.ifBlank { "Licencia activa." },
                expiresAtMs = expiresMs
            )
        }
        // Errores transitorios del servidor: no se toca el estado guardado.
        if (response.code == LicenseErrorCodes.RATE_LIMITED ||
            response.code == LicenseErrorCodes.SERVER_ERROR
        ) {
            return CheckResult(
                State.ERROR,
                LicenseMessages.forCode(response.code, response.message),
                errorCode = response.code
            )
        }
        // El servidor decidió: la licencia no es válida en este equipo.
        if (response.code in HARD_BLOCK_CODES) grants.clear()
        preferences.saveLicenseValidation(
            status = statusToStore(response),
            expiresAt = expiresMs,
            validatedAt = 0L,
            grantExpiresAt = 0L
        )
        return CheckResult(
            State.BLOCKED,
            LicenseMessages.forCode(response.code, response.message),
            expiresAtMs = expiresMs,
            errorCode = response.code
        )
    }

    private suspend fun offlineFallback(key: String, message: String): CheckResult {
        // Sin conexión manda la cuenta regresiva: si la licencia ya
        // se validó una vez y aún le queda tiempo, sigue activa.
        val prefs = preferences.preferencesFlow.first()
        val keyMatches = prefs.licenseKey == key || grants.savedLicenseKey() == key
        return if (keyMatches && LicenseRules.isUsable(prefs, System.currentTimeMillis())) {
            CheckResult(State.OFFLINE_ACTIVE, message)
        } else {
            CheckResult(
                State.BLOCKED,
                "Sin conexión y sin una licencia vigente en este equipo. Conéctate para validar tu licencia."
            )
        }
    }

    /** Estado corto que se guarda en preferencias (compatible con la UI). */
    private fun statusToStore(response: LicenseResponse): String = when (response.code) {
        LicenseErrorCodes.LICENSE_EXPIRED -> "expired"
        LicenseErrorCodes.LICENSE_REVOKED -> "revoked"
        LicenseErrorCodes.LICENSE_SUSPENDED -> "suspended"
        LicenseErrorCodes.DEVICE_MISMATCH -> "device_mismatch"
        LicenseErrorCodes.DEVICE_LIMIT_REACHED -> "device_limit"
        LicenseErrorCodes.LICENSE_NOT_FOUND -> "not_found"
        LicenseErrorCodes.APPLICATION_NOT_FOUND, LicenseErrorCodes.APPLICATION_DISABLED -> "unavailable"
        else -> response.status.takeIf { it.isNotBlank() && it != "unknown" } ?: "invalid"
    }

    private fun parseIsoToMs(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L // 0 = licencia de por vida
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
    }

    private fun appVersion(): String =
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")

    companion object {
        /** Slug registrado en LicenseHub para esta aplicación. */
        const val APPLICATION_SLUG = "mipos"
        private val HARD_BLOCK_CODES = setOf(
            LicenseErrorCodes.LICENSE_REVOKED,
            LicenseErrorCodes.LICENSE_SUSPENDED,
            LicenseErrorCodes.LICENSE_EXPIRED,
            LicenseErrorCodes.DEVICE_MISMATCH
        )
    }
}
