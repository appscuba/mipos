package com.example.data.license

import com.example.data.preferences.AppPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LicenseRepository(
  private val preferences: AppPreferencesDataStore,
  private val validator: LicenseValidator = LicenseValidator(),
) {
  val snapshot: Flow<LicenseSnapshot> = preferences.preferencesFlow.map { prefs ->
    LicenseSnapshot(
      deviceId = prefs.installationId,
      phone = prefs.licensePhone,
      serverStatus = prefs.licenseStatus,
      expiresAt = prefs.licenseExpiresAt,
      lastSuccessfulValidation = prefs.lastLicenseValidation,
      firstLaunchAt = prefs.firstLaunchAt,
    )
  }

  suspend fun initialize() {
    val now = System.currentTimeMillis()
    preferences.ensureLicenseIdentity(
      installationId = "STP-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase().chunked(4).joinToString("-"),
      firstLaunchAt = now,
    )
  }

  suspend fun activate(phone: String): LicenseValidationResult {
    val id = preferences.currentInstallationId()
    val result = validator.validate(id, phone)
    preferences.saveLicenseResult(phone, result.status, result.expiresAt, if (result.status == "active") System.currentTimeMillis() else 0L)
    return result
  }

  suspend fun revalidateIfDue() {
    val prefs = preferences.currentLicenseValues()
    if (prefs.first <= 0L || prefs.second.isBlank() || prefs.third != "active") return
    val now = System.currentTimeMillis()
    if (prefs.fourth > 0L && now - prefs.fourth < PlanLimits.REVALIDATE_AFTER_HOURS * 3_600_000L) return
    val result = validator.validate(prefs.first.toString(), prefs.second)
    if (result.status == "active") {
      preferences.saveLicenseResult(prefs.second, result.status, result.expiresAt, now)
    } else if (result.status == "revoked" || result.status == "expired" || result.status == "invalid") {
      preferences.saveLicenseResult(prefs.second, result.status, result.expiresAt, 0L)
    }
  }
}
