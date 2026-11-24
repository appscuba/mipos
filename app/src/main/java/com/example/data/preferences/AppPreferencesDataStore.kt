package com.example.data.preferences

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.DARK,
  val isOnboardingCompleted: Boolean = false,
  val activeBusinessId: Long = 0L,
  val activeUserId: Long = 0L,
  val lastBackupTimestamp: Long = 0L,
  val installationId: String = "",
  val licensePhone: String = "",
  val licenseStatus: String = "",
  val licenseExpiresAt: Long = 0L,
  val lastLicenseValidation: Long = 0L,
  val firstLaunchAt: Long = 0L,
)

class AppPreferencesDataStore(context: Context) {
  private val appContext = context.applicationContext
  private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    produceFile = { appContext.preferencesDataStoreFile("gestor_comercial_prefs") },
  )

  /**
   * Fingerprint estable de Android para esta app, firma, usuario y dispositivo.
   * Se mantiene tras reinstalar la app en el mismo dispositivo (Android 8+).
   */
  fun deviceIdentityFingerprint(): String {
    val androidId = Settings.Secure.getString(
      appContext.contentResolver,
      Settings.Secure.ANDROID_ID,
    ).orEmpty()
    if (androidId.isBlank() || androidId == "9774d56d682e549c") return ""
    val seed = "${appContext.packageName}:$androidId"
    return MessageDigest.getInstance("SHA-256")
      .digest(seed.toByteArray(Charsets.UTF_8))
      .joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
      }
  }

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val ACTIVE_BUSINESS_ID = longPreferencesKey("active_business_id")
    val ACTIVE_USER_ID = longPreferencesKey("active_user_id")
    val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    val INSTALLATION_ID = stringPreferencesKey("license_installation_id")
    val LICENSE_PHONE = stringPreferencesKey("license_phone")
    val LICENSE_STATUS = stringPreferencesKey("license_status")
    val LICENSE_EXPIRES_AT = longPreferencesKey("license_expires_at")
    val LAST_LICENSE_VALIDATION = longPreferencesKey("last_license_validation")
    val FIRST_LAUNCH_AT = longPreferencesKey("first_launch_at")
  }

  val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
    val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
    val theme = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.DARK
    UserPreferences(
      themeMode = theme,
      isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
      activeBusinessId = prefs[Keys.ACTIVE_BUSINESS_ID] ?: 0L,
      activeUserId = prefs[Keys.ACTIVE_USER_ID] ?: 0L,
      lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TIMESTAMP] ?: 0L,
      installationId = prefs[Keys.INSTALLATION_ID] ?: "",
      licensePhone = prefs[Keys.LICENSE_PHONE] ?: "",
      licenseStatus = prefs[Keys.LICENSE_STATUS] ?: "",
      licenseExpiresAt = prefs[Keys.LICENSE_EXPIRES_AT] ?: 0L,
      lastLicenseValidation = prefs[Keys.LAST_LICENSE_VALIDATION] ?: 0L,
      firstLaunchAt = prefs[Keys.FIRST_LAUNCH_AT] ?: 0L,
    )
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
  }

  suspend fun markOnboardingCompleted(businessId: Long, userId: Long) {
    dataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = true
      prefs[Keys.ACTIVE_BUSINESS_ID] = businessId
      prefs[Keys.ACTIVE_USER_ID] = userId
    }
  }

  suspend fun setActiveUser(userId: Long) {
    dataStore.edit { prefs -> prefs[Keys.ACTIVE_USER_ID] = userId }
  }

  suspend fun updateLastBackupTimestamp(timestamp: Long) {
    dataStore.edit { prefs -> prefs[Keys.LAST_BACKUP_TIMESTAMP] = timestamp }
  }

  suspend fun ensureLicenseIdentity(installationId: String, firstLaunchAt: Long) {
    dataStore.edit { prefs ->
      if (prefs[Keys.INSTALLATION_ID].isNullOrBlank()) prefs[Keys.INSTALLATION_ID] = installationId
      if ((prefs[Keys.FIRST_LAUNCH_AT] ?: 0L) <= 0L) prefs[Keys.FIRST_LAUNCH_AT] = firstLaunchAt
    }
  }

  suspend fun currentInstallationId(): String =
    dataStore.data.map { it[Keys.INSTALLATION_ID] ?: "" }.first()

  suspend fun saveLicenseResult(phone: String, status: String, expiresAt: Long, validatedAt: Long) {
    dataStore.edit { prefs ->
      prefs[Keys.LICENSE_PHONE] = phone
      prefs[Keys.LICENSE_STATUS] = status
      prefs[Keys.LICENSE_EXPIRES_AT] = expiresAt
      prefs[Keys.LAST_LICENSE_VALIDATION] = validatedAt
    }
  }

  suspend fun currentLicenseValues(): UserPreferences = dataStore.data.map { prefs ->
    UserPreferences(
      installationId = prefs[Keys.INSTALLATION_ID] ?: "",
      licensePhone = prefs[Keys.LICENSE_PHONE] ?: "",
      licenseStatus = prefs[Keys.LICENSE_STATUS] ?: "",
      licenseExpiresAt = prefs[Keys.LICENSE_EXPIRES_AT] ?: 0L,
      lastLicenseValidation = prefs[Keys.LAST_LICENSE_VALIDATION] ?: 0L,
      firstLaunchAt = prefs[Keys.FIRST_LAUNCH_AT] ?: 0L,
    )
  }.first()

  suspend fun resetAllWorkspaceData() {
    dataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = false
      prefs[Keys.ACTIVE_BUSINESS_ID] = 0L
      prefs[Keys.ACTIVE_USER_ID] = 0L
      prefs[Keys.LAST_BACKUP_TIMESTAMP] = 0L
      // No borrar INSTALLATION_ID, licencia ni FIRST_LAUNCH_AT.
    }
  }
}
