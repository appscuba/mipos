package com.example.data.license

import android.os.SystemClock
import com.example.data.preferences.UserPreferences

/**
 * Reglas de uso local de la licencia, compartidas por la puerta de
 * entrada (MainActivity), el LicenseViewModel y PlanRules.
 *
 * Modelo de VALIDACIÓN ÚNICA con cuenta regresiva (sustituye a la
 * ventana offline de 48 h): la app valida contra LicenseHub y, de la
 * respuesta firmada, ancla el tiempo que le queda a la licencia
 * (vencimiento − hora del servidor). Desde ese momento manda
 * [LicenseCountdown]: el restante corre con el reloj monotónico del
 * equipo, así que cambiar la hora del sistema no alarga la licencia.
 * Sin conexión la licencia sigue usable hasta que su propio tiempo
 * se agota; al conectarse, la app reconsulta y el servidor vuelve a
 * mandar (renovaciones, suspensiones y revocaciones).
 */
object LicenseRules {
  const val STATUS_ACTIVE = "active"

  /** Ventana del grant offline firmado que emite el servidor. */
  const val GRACE_MS = 48L * 60 * 60 * 1000

  /** Pasadas 24 h desde la última validación, se vuelve a consultar. */
  const val REVALIDATE_AFTER_MS = 24L * 60 * 60 * 1000

  fun isUsable(prefs: UserPreferences, nowMs: Long): Boolean {
    if (prefs.licenseStatus != STATUS_ACTIVE) return false
    if (prefs.licenseLastValidatedAt <= 0L) return false
    // Sin fecha de fin en el servidor: licencia de por vida.
    if (prefs.licenseExpiresAt <= 0L) return true
    if (prefs.licenseAnchorRemainingMs >= 0L) {
      // Con ancla manda la cuenta regresiva (reloj monotónico):
      // la hora del sistema no interviene.
      return LicenseCountdown.remainingMs(
        anchorRemainingMs = prefs.licenseAnchorRemainingMs,
        anchorElapsedMs = prefs.licenseAnchorElapsedMs,
        anchorWallMs = prefs.licenseAnchorWallMs,
        nowElapsedMs = SystemClock.elapsedRealtime(),
        nowWallMs = nowMs,
      ) > 0L
    }
    // Instalación anterior a la cuenta regresiva, aún sin ancla:
    // vale la fecha del servidor contra el reloj de pared hasta la
    // próxima validación, que crea el ancla.
    return prefs.licenseExpiresAt > nowMs
  }

  fun needsRevalidation(prefs: UserPreferences, nowMs: Long): Boolean {
    if (prefs.licenseDeviceId.isBlank() || prefs.licenseKey.isBlank()) return false
    // Hay clave guardada pero nunca se validó (p. ej. se activó sin
    // conexión): se reintenta al arrancar.
    if (prefs.licenseLastValidatedAt <= 0L) return true
    // Licencia activa con fecha de fin pero sin ancla (instalación
    // anterior): validar cuanto antes para crear la cuenta regresiva.
    if (prefs.licenseStatus == STATUS_ACTIVE &&
      prefs.licenseExpiresAt > 0L &&
      prefs.licenseAnchorRemainingMs < 0L
    ) {
      return true
    }
    return nowMs - prefs.licenseLastValidatedAt > REVALIDATE_AFTER_MS
  }
}
