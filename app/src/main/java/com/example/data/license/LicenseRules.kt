package com.example.data.license

import com.example.data.preferences.UserPreferences

/**
 * Reglas de uso local de la licencia, compartidas por la puerta de
 * entrada (MainActivity), el LicenseViewModel y PlanRules.
 *
 * Sustituye a las reglas del sistema anterior (gracia local de 7 días
 * sin firma): ahora el uso sin conexión lo autoriza el GRANT OFFLINE
 * FIRMADO que emite LicenseHub (48 h). Su vencimiento ya verificado
 * criptográficamente se persiste en `licenseGrantExpiresAt`.
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
    // Vencida por fecha: ni el grant la salva.
    if (prefs.licenseExpiresAt > 0L && prefs.licenseExpiresAt <= nowMs) return false
    // Sin conexión manda el grant firmado: solo vale hasta su vencimiento.
    return prefs.licenseGrantExpiresAt > nowMs
  }

  fun needsRevalidation(prefs: UserPreferences, nowMs: Long): Boolean {
    if (prefs.licenseDeviceId.isBlank() || prefs.licenseKey.isBlank()) return false
    // Hay clave guardada pero nunca se validó (p. ej. se activó sin
    // conexión): se reintenta al arrancar.
    if (prefs.licenseLastValidatedAt <= 0L) return true
    return nowMs - prefs.licenseLastValidatedAt > REVALIDATE_AFTER_MS
  }
}
