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
    preferences.ensureLicenseIdentity(
      installationId = generateInstallationId(),
      firstLaunchAt = now,
    )
  }

  private fun generateInstallationId(): String {
    // 128 bits aleatorios: evita los IDs cortos de 48 bits de versiones previas.
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    val hex = bytes.joinToString("") { byte ->
      (byte.toInt() and 0xff).toString(16).padStart(2, '0').uppercase()
    }
    return "STP-" + hex.chunked(4).joinToString("-")
  }

  suspend fun activate(phone: String): LicenseValidationResult {
    val id = preferences.currentInstallationId()
    val result = validator.validate(id, phone)
    if (result.status != "unavailable") {
      preferences.saveLicenseResult(phone, result.status, result.expiresAt,
        if (result.status == "active") System.currentTimeMillis() else 0L)
    }
    return result
  }

  suspend fun revalidateIfDue() {
    val prefs = preferences.currentLicenseValues()
    if (prefs.installationId.isBlank() || prefs.licensePhone.isBlank() || prefs.licenseStatus != "active") return
    val now = System.currentTimeMillis()
    if (prefs.lastLicenseValidation > 0L &&
      now - prefs.lastLicenseValidation < PlanLimits.REVALIDATE_AFTER_HOURS * 3_600_000L) return
    val result = validator.validate(prefs.installationId, prefs.licensePhone)
    if (result.status == "active") {
      preferences.saveLicenseResult(prefs.licensePhone, result.status, result.expiresAt, now)
    } else if (result.status == "revoked" || result.status == "expired" || result.status == "invalid") {
      preferences.saveLicenseResult(prefs.licensePhone, result.status, result.expiresAt, 0L)
    }
  }
}
