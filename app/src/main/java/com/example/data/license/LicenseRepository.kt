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
    LicenseSnapshot(prefs.installationId, prefs.licensePhone, prefs.licenseStatus,
      prefs.licenseExpiresAt, prefs.lastLicenseValidation, prefs.firstLaunchAt)
  }

  suspend fun initialize() {
    val now = System.currentTimeMillis()
    preferences.ensureLicenseIdentity(generateInstallationId(preferences.deviceIdentityFingerprint()), now)
  }

  private fun generateInstallationId(fingerprint: String): String {
    val hex = if (fingerprint.length == 64 && fingerprint.all { it in "0123456789abcdefABCDEF" }) fingerprint.take(12).uppercase()
    else ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0').uppercase() }
    return "STP-" + hex.chunked(4).joinToString("-")
  }

  suspend fun requestApproval(phone: String): LicenseValidationResult {
    val normalizedPhone = phone.filter { it.isDigit() }
    if (normalizedPhone.length < 8) return LicenseValidationResult("invalid", message = "Introduce el número de teléfono asociado a tu compra (al menos 8 dígitos).")
    preferences.saveLicensePhone(phone.trim())
    val result = validator.validate(preferences.currentInstallationId(), phone.trim())
    if (result.status != "unavailable") {
      preferences.saveLicenseResult(phone.trim(), result.status, result.expiresAt,
        if (result.status == "active") System.currentTimeMillis() else 0L)
    }
    return result
  }

  suspend fun revalidateIfDue() {
    if (PlanLimits.FREE_ACCESS_MODE) return
    val prefs = preferences.currentLicenseValues()
    if (prefs.installationId.isBlank() || prefs.licensePhone.isBlank() || prefs.licenseStatus != "active") return
    val now = System.currentTimeMillis()
    if (prefs.lastLicenseValidation > 0L && now - prefs.lastLicenseValidation < PlanLimits.REVALIDATE_AFTER_HOURS * 3_600_000L) return
    val result = validator.validate(prefs.installationId, prefs.licensePhone)
    if (result.status != "unavailable") {
      preferences.saveLicenseResult(prefs.licensePhone, result.status, result.expiresAt,
        if (result.status == "active") now else 0L)
    }
  }
}
