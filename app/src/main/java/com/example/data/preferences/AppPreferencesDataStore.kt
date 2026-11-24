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

  suspend fun resetAllWorkspaceData() {
    dataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = false
      prefs[Keys.ACTIVE_BUSINESS_ID] = 0L
      prefs[Keys.ACTIVE_USER_ID] = 0L
      prefs[Keys.LAST_BACKUP_TIMESTAMP] = 0L
    }
  }
}
