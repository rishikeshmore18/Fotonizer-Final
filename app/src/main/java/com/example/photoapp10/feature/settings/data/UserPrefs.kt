package com.example.photoapp10.feature.settings.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.preferencesDataStore
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefs by preferencesDataStore("settings_prefs")

class UserPrefs(private val context: Context) {

    private val KEY_THEME = intPreferencesKey("theme_mode")          // 0 system, 1 light, 2 dark
    private val KEY_SORT  = intPreferencesKey("default_sort")        // 0..4 -> SortMode
    private val KEY_LASTQ = stringPreferencesKey("last_search_query")
    private val KEY_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
    private val KEY_LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    private val KEY_LAST_ARCHIVE_AT = longPreferencesKey("last_archive_at")

    val themeFlow: Flow<ThemeMode> = context.userPrefs.data.map { p ->
        when (p[KEY_THEME] ?: 0) { 1 -> ThemeMode.LIGHT; 2 -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
    }
    suspend fun setTheme(mode: ThemeMode) = context.userPrefs.edit { p ->
        p[KEY_THEME] = when (mode) { ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2 }
    }

    val sortFlow: Flow<SortMode> = context.userPrefs.data.map { p ->
        when (p[KEY_SORT] ?: 2) {
            0 -> SortMode.NAME_ASC
            1 -> SortMode.NAME_DESC
            2 -> SortMode.DATE_NEW
            3 -> SortMode.DATE_OLD
            4 -> SortMode.FAV_FIRST
            else -> SortMode.DATE_NEW
        }
    }
    suspend fun setSort(mode: SortMode) = context.userPrefs.edit { p ->
        p[KEY_SORT] = when (mode) {
            SortMode.NAME_ASC -> 0; SortMode.NAME_DESC -> 1; SortMode.DATE_NEW -> 2; SortMode.DATE_OLD -> 3; SortMode.FAV_FIRST -> 4
        }
    }

    val lastSearchFlow: Flow<String> = context.userPrefs.data.map { it[KEY_LASTQ] ?: "" }
    suspend fun setLastSearch(q: String) = context.userPrefs.edit { it[KEY_LASTQ] = q }

    val wifiOnlyFlow: Flow<Boolean> = context.userPrefs.data.map { it[KEY_WIFI_ONLY] ?: true }
    suspend fun setWifiOnly(value: Boolean) = context.userPrefs.edit { it[KEY_WIFI_ONLY] = value }

    val lastSyncedAtFlow: Flow<Long> = context.userPrefs.data.map { it[KEY_LAST_SYNCED_AT] ?: 0L }
    suspend fun setLastSyncedAt(ts: Long) = context.userPrefs.edit { it[KEY_LAST_SYNCED_AT] = ts }

    val lastArchiveAtFlow: Flow<Long> = context.userPrefs.data.map { it[KEY_LAST_ARCHIVE_AT] ?: 0L }
    suspend fun setLastArchiveAt(ts: Long) = context.userPrefs.edit { it[KEY_LAST_ARCHIVE_AT] = ts }

    // Helper to synchronously read wifiOnly (avoid suspend in constraints build)
    // This is acceptable for one-time constraint building in WorkManager
    private var cachedWifiOnly: Boolean? = null
    
    fun wifiOnlyFlowReplay(): Boolean = cachedWifiOnly ?: run {
        // Use a more efficient approach that doesn't block the UI thread
        try {
            val value = kotlinx.coroutines.runBlocking { 
                try {
                    wifiOnlyFlow.first()
                } catch (e: Exception) {
                    false // Default to false if there's an issue
                }
            }
            cachedWifiOnly = value
            value
        } catch (e: Exception) {
            // If runBlocking itself fails, return default value
            false
        }
    }
}










