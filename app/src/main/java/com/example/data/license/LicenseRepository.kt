package com.example.data.license

import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

class LicenseRepository(
  private val preferences: AppPreferencesDataStore,
  private val validator: LicenseValidator = LicenseValidator(),
) {
  val snapshot: Flow<LicenseSnapshot> = preferences.preferencesFlow.map { prefs ->
    LicenseSnapshot(prefs.installationId, prefs.licenseKey, prefs.licenseStatus,
      prefs.licenseExpiresAt, prefs.lastLicenseValidation, prefs.firstLaunchAt)
  }

  suspend fun initialize() {
    val now = System.currentTimeMillis()
    preferences.ensureLicenseIdentity(
      installationId = generateInstallationId(preferences.deviceIdentityFingerprint()),
      firstLaunchAt = now,
    )
  }

  private fun generateInstallationId(fingerprint: String): String {
    // Formato oficial del plugin: STP-XXXX-XXXX-XXXX (12 caracteres hexadecimales).
    // Los IDs ya guardados no se modifican para no desvincular licencias existentes.
    val hex = if (fingerprint.length == 64 && fingerprint.all { it in "0123456789abcdefABCDEF" }) {
      fingerprint.take(12).uppercase()
    } else {
      ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0').uppercase()
      }
    }
    return "STP-" + hex.chunked(4).joinToString("-")
  }

  suspend fun activate(licenseKey: String): LicenseValidationResult {
    val id = preferences.currentInstallationId()
    val result = validator.validate(id, licenseKey)
    if (result.status != "unavailable") {
      preferences.saveLicenseResult(licenseKey, result.status, result.expiresAt,
        if (result.status == "active") System.currentTimeMillis() else 0L)
    }
    return result
  }

  suspend fun revalidateIfDue() {
    val prefs = preferences.currentLicenseValues()
    if (prefs.installationId.isBlank() || prefs.licenseKey.isBlank() || prefs.licenseStatus != "active") return
    val now = System.currentTimeMillis()
    if (prefs.lastLicenseValidation > 0L &&
      now - prefs.lastLicenseValidation < PlanLimits.REVALIDATE_AFTER_HOURS * 3_600_000L) return
    val result = validator.validate(prefs.installationId, prefs.licenseKey)
    if (result.status == "active") {
      preferences.saveLicenseResult(prefs.licenseKey, result.status, result.expiresAt, now)
    } else if (result.status == "revoked" || result.status == "expired" || result.status == "invalid") {
      preferences.saveLicenseResult(prefs.licenseKey, result.status, result.expiresAt, 0L)
    }
  }
}
