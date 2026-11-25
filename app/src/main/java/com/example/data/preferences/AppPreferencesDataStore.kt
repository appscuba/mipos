package com.example.data.preferences

import android.content.Context
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
import kotlinx.coroutines.flow.map

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.DARK,
  val isOnboardingCompleted: Boolean = false,
  val activeBusinessId: Long = 0L,
  val activeUserId: Long = 0L,
  val lastBackupTimestamp: Long = 0L,
  // Licencia comercial (LicenseHub, https://appscuba.vercel.app).
  // El ID de instalación conserva la MISMA clave y formato (STP-…)
  // que usaba el sistema anterior: un teléfono ya licenciado no
  // pierde su ID al actualizar la app.
  val licenseDeviceId: String = "",
  val licenseKey: String = "",
  val licenseStatus: String = "",
  val licenseExpiresAt: Long = 0L,
  val licenseLastValidatedAt: Long = 0L,
  // Vencimiento (epoch ms) del último grant offline FIRMADO verificado.
  val licenseGrantExpiresAt: Long = 0L,
  // Primer arranque de la app (epoch ms). Arranca la prueba gratuita
  // de 15 días del modo freemium; se escribe una sola vez.
  val firstLaunchAt: Long = 0L,
)

class AppPreferencesDataStore(context: Context) {
  private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
    produceFile = { context.applicationContext.preferencesDataStoreFile("gestor_comercial_prefs") },
  )

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val ACTIVE_BUSINESS_ID = longPreferencesKey("active_business_id")
    val ACTIVE_USER_ID = longPreferencesKey("active_user_id")
    val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    val LICENSE_DEVICE_ID = stringPreferencesKey("license_device_id")
    val LICENSE_KEY = stringPreferencesKey("license_key")
    val LICENSE_STATUS = stringPreferencesKey("license_status")
    val LICENSE_EXPIRES_AT = longPreferencesKey("license_expires_at")
    val LICENSE_LAST_VALIDATED_AT = longPreferencesKey("license_last_validated_at")
    val LICENSE_GRANT_EXPIRES_AT = longPreferencesKey("license_grant_expires_at")
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
      licenseDeviceId = prefs[Keys.LICENSE_DEVICE_ID] ?: "",
      licenseKey = prefs[Keys.LICENSE_KEY] ?: "",
      licenseStatus = prefs[Keys.LICENSE_STATUS] ?: "",
      licenseExpiresAt = prefs[Keys.LICENSE_EXPIRES_AT] ?: 0L,
      licenseLastValidatedAt = prefs[Keys.LICENSE_LAST_VALIDATED_AT] ?: 0L,
      licenseGrantExpiresAt = prefs[Keys.LICENSE_GRANT_EXPIRES_AT] ?: 0L,
      firstLaunchAt = prefs[Keys.FIRST_LAUNCH_AT] ?: 0L,
    )
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { prefs ->
      prefs[Keys.THEME_MODE] = mode.name
    }
  }

  suspend fun markOnboardingCompleted(businessId: Long, userId: Long) {
    dataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = true
      prefs[Keys.ACTIVE_BUSINESS_ID] = businessId
      prefs[Keys.ACTIVE_USER_ID] = userId
    }
  }

  suspend fun setActiveUser(userId: Long) {
    dataStore.edit { prefs ->
      prefs[Keys.ACTIVE_USER_ID] = userId
    }
  }

  suspend fun updateLastBackupTimestamp(timestamp: Long) {
    dataStore.edit { prefs ->
      prefs[Keys.LAST_BACKUP_TIMESTAMP] = timestamp
    }
  }

  /**
   * Devuelve el ID único de esta instalación, generándolo la primera
   * vez (formato STP-XXXX-XXXX-XXXX). Es el identificador que la app
   * envía a LicenseHub junto a la clave de licencia. No cambia nunca,
   * ni siquiera al restablecer los datos del negocio.
   */
  suspend fun ensureLicenseDeviceId(): String {
    var result = ""
    dataStore.edit { prefs ->
      val existing = prefs[Keys.LICENSE_DEVICE_ID].orEmpty()
      result = if (existing.isNotBlank()) {
        existing
      } else {
        val hex = java.util.UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        val generated = "STP-" + hex.chunked(4).joinToString("-")
        prefs[Keys.LICENSE_DEVICE_ID] = generated
        generated
      }
    }
    return result
  }

  /**
   * Devuelve la marca del primer arranque, escribiéndola (ahora) si
   * aún no existe. Nunca se sobrescribe: de ella cuelga la prueba
   * gratuita de 15 días. Sobrevive al «restablecer todo», igual que
   * el ID de instalación.
   */
  suspend fun ensureFirstLaunchAt(): Long {
    var result = 0L
    dataStore.edit { prefs ->
      val existing = prefs[Keys.FIRST_LAUNCH_AT] ?: 0L
      result = if (existing > 0L) {
        existing
      } else {
        val now = System.currentTimeMillis()
        prefs[Keys.FIRST_LAUNCH_AT] = now
        now
      }
    }
    return result
  }

  /** Guarda la clave de licencia escrita por el usuario. */
  suspend fun saveLicenseKey(key: String) {
    dataStore.edit { prefs ->
      prefs[Keys.LICENSE_KEY] = key.trim()
    }
  }

  /**
   * Persiste el resultado de una validación contra LicenseHub.
   * [grantExpiresAt] es el vencimiento del grant offline firmado ya
   * verificado (0 si la respuesta no traía grant).
   */
  suspend fun saveLicenseValidation(
    status: String,
    expiresAt: Long,
    validatedAt: Long,
    grantExpiresAt: Long,
  ) {
    dataStore.edit { prefs ->
      prefs[Keys.LICENSE_STATUS] = status
      prefs[Keys.LICENSE_EXPIRES_AT] = expiresAt
      prefs[Keys.LICENSE_LAST_VALIDATED_AT] = validatedAt
      prefs[Keys.LICENSE_GRANT_EXPIRES_AT] = grantExpiresAt
    }
  }

  suspend fun resetAllWorkspaceData() {
    dataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = false
      prefs[Keys.ACTIVE_BUSINESS_ID] = 0L
      prefs[Keys.ACTIVE_USER_ID] = 0L
      prefs[Keys.LAST_BACKUP_TIMESTAMP] = 0L
    }
  }
}
