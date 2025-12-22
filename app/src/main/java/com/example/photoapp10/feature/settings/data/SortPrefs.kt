package com.example.photoapp10.feature.settings.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.photoapp10.feature.photo.domain.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sortDataStore by preferencesDataStore("sort_prefs")

class SortPrefs(private val context: Context) {
    private val KEY_SORT = intPreferencesKey("default_sort")

    val flow: Flow<SortMode> = context.sortDataStore.data.map { prefs ->
        when (prefs[KEY_SORT] ?: 2 /* DATE_NEW default */) {
            0 -> SortMode.NAME_ASC
            1 -> SortMode.NAME_DESC
            2 -> SortMode.DATE_NEW
            3 -> SortMode.DATE_OLD
            4 -> SortMode.FAV_FIRST
            else -> SortMode.DATE_NEW
        }
    }

    suspend fun set(mode: SortMode) {
        context.sortDataStore.edit { prefs ->
            prefs[KEY_SORT] = when (mode) {
                SortMode.NAME_ASC -> 0
                SortMode.NAME_DESC -> 1
                SortMode.DATE_NEW -> 2
                SortMode.DATE_OLD -> 3
                SortMode.FAV_FIRST -> 4
            }
        }
    }
}














