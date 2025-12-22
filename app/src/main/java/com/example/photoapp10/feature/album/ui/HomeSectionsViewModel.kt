package com.example.photoapp10.feature.album.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.settings.data.UserPrefs
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeSectionsViewModel(app: Application) : AndroidViewModel(app) {
    private val albumRepo = Modules.provideAlbumRepository(app)
    private val photoRepo = Modules.providePhotoRepository(app)
    private val prefs = UserPrefs(app)

    val albums = albumRepo.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<PhotoEntity>> =
        photoRepo.observeFavorites(limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<PhotoEntity>> =
        photoRepo.observeRecents(limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val theme = prefs.themeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val sort  = prefs.sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.DATE_NEW)
    val lastQ = prefs.lastSearchFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // convenience setters (use in a settings screen)
    suspend fun setTheme(mode: ThemeMode) = prefs.setTheme(mode)
    suspend fun setSort(mode: SortMode)   = prefs.setSort(mode)
    suspend fun setLastSearch(q: String)  = prefs.setLastSearch(q)
}














