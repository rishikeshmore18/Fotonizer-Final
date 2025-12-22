package com.example.photoapp10.feature.album.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.photo.domain.SortMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import timber.log.Timber
import java.io.File

class AlbumsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: AlbumRepository = Modules.provideAlbumRepository(app)
    private val photoRepo: PhotoRepository = Modules.providePhotoRepository(app)
    
    // Add mutex for thread safety
    private val mutex = Mutex()

    // Sort mode state
    private val _sortMode = MutableStateFlow(SortMode.DATE_NEW)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val albums = _sortMode.flatMapLatest { sort: SortMode ->
        repo.observeAlbums(sort)
    }.stateIn(
        scope = viewModelScope, 
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), 
        initialValue = emptyList<AlbumEntity>()
    )

    fun setSort(mode: SortMode) = viewModelScope.launch {
        try {
            Timber.d("AlbumsViewModel: Setting sort mode to $mode")
            _sortMode.value = mode
            Timber.d("AlbumsViewModel: Sort mode updated successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error setting sort mode")
        }
    }

    fun create(name: String) = viewModelScope.launch {
        try {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                repo.createAlbum(trimmed)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating album")
        }
    }

    suspend fun createAndReturnId(name: String): Long {
        return try {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return -1L
            repo.createAlbum(trimmed)
        } catch (e: Exception) {
            Timber.e(e, "Error creating album and returning ID")
            -1L
        }
    }

    fun rename(id: Long, newName: String) = viewModelScope.launch {
        try {
            if (id > 0 && newName.isNotBlank()) {
                repo.renameAlbum(id, newName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error renaming album")
        }
    }

    fun delete(entity: AlbumEntity?) = viewModelScope.launch {
        try {
            if (entity != null && entity.id > 0) {
                repo.deleteAlbum(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting album")
        }
    }

    fun deleteAlbums(albums: List<AlbumEntity>?) = viewModelScope.launch {
        try {
            if (albums != null && albums.isNotEmpty()) {
                albums.forEach { album ->
                    try {
                        if (album != null && album.id > 0) {
                            repo.deleteAlbum(album)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error deleting individual album: ${album?.id}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting albums")
        }
    }

    fun toggleFavorite(albumId: Long) = viewModelScope.launch {
        try {
            if (albumId > 0) {
                repo.toggleFavorite(albumId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite")
        }
    }

    fun setEmoji(albumId: Long, emoji: String?) = viewModelScope.launch {
        try {
            if (albumId > 0) {
                repo.setEmoji(albumId, emoji)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting emoji")
        }
    }

    fun updateCovers() = viewModelScope.launch {
        try {
            // Safely access albums value
            val currentAlbums = try {
                albums.value
            } catch (e: Exception) {
                Timber.e(e, "Error accessing albums value")
                emptyList<AlbumEntity>()
            }
            
            if (currentAlbums.isNotEmpty()) {
                currentAlbums.forEach { album: AlbumEntity ->
                    try {
                        if (album != null && album.id > 0) {
                            repo.updateCoverFromFirstPhoto(album.id)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating cover for album: ${album?.id}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating covers")
        }
    }

    fun refreshAlbums() = viewModelScope.launch {
        try {
            // Trigger a refresh by updating the sort mode to the current value
            // This will cause the albums flow to re-emit
            val currentSort = _sortMode.value
            _sortMode.value = currentSort
            Timber.d("AlbumsViewModel: Albums refreshed")
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing albums")
        }
    }
    
    fun processCapturedPhoto(photoFile: File, filename: String) = viewModelScope.launch {
        try {
            Timber.d("AlbumsViewModel: Processing captured photo: ${photoFile.absolutePath}")
            
            // Validate the photo file
            if (!photoFile.exists() || photoFile.length() == 0L) {
                Timber.e("AlbumsViewModel: Invalid photo file")
                return@launch
            }
            
            // Determine album ID from file path
            var albumId = extractAlbumIdFromPath(photoFile.absolutePath)
            
            // If albumId is 0 or invalid, ensure default album exists
            if (albumId <= 0) {
                Timber.d("AlbumsViewModel: Album ID is 0 or invalid, ensuring default album exists")
                albumId = ensureDefaultAlbumExists()
                if (albumId <= 0) {
                    Timber.e("AlbumsViewModel: Failed to create or find default album")
                    return@launch
                }
            }
            
            // Add photo to repository
            val (photoId, _) = photoRepo.addPhotoFromPath(
                albumId = albumId,
                originalPath = photoFile.absolutePath,
                filename = filename,
                width = 0, // Will be updated by ImageMetadata
                height = 0, // Will be updated by ImageMetadata
                sizeBytes = photoFile.length(),
                takenAt = System.currentTimeMillis()
            )
            
            if (photoId > 0) {
                Timber.d("AlbumsViewModel: Successfully added photo with ID: $photoId")
                // Refresh albums to show the new photo
                refreshAlbums()
            } else {
                Timber.e("AlbumsViewModel: Failed to add photo to repository")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "AlbumsViewModel: Error processing captured photo")
        }
    }
    
    /**
     * Ensure the default album exists, create it if it doesn't
     * @return The ID of the default album
     */
    private suspend fun ensureDefaultAlbumExists(): Long {
        return try {
            // Use the repository's built-in method to find or create default album
            android.util.Log.d("AlbumsViewModel", "Ensuring default album exists")
            val albumId = repo.findOrCreateDefaultAlbum()
            
            if (albumId > 0) {
                android.util.Log.d("AlbumsViewModel", "Default album ID: $albumId")
                refreshAlbums() // Refresh to show the album
                albumId
            } else {
                android.util.Log.e("AlbumsViewModel", "Failed to create or find default album")
                0L
            }
        } catch (e: Exception) {
            android.util.Log.e("AlbumsViewModel", "Error ensuring default album exists", e)
            0L
        }
    }
    
    /**
     * Extract album ID from photo file path
     * Expected path format: .../photos/{albumId}/{filename}
     */
    private fun extractAlbumIdFromPath(path: String): Long {
        return try {
            val parts = path.split("/")
            val photosIndex = parts.indexOf("photos")
            if (photosIndex >= 0 && photosIndex + 1 < parts.size) {
                parts[photosIndex + 1].toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            Timber.e(e, "AlbumsViewModel: Error extracting album ID from path: $path")
            0L
        }
    }

    suspend fun getCoverImagePath(album: AlbumEntity?): String? {
        return try {
            if (album == null) return null
            
            val coverPhotoId = album.coverPhotoId ?: return null
            if (coverPhotoId <= 0) return null
            
            val photo = photoRepo.getPhoto(coverPhotoId) ?: return null
            
            val thumbPath = photo.thumbPath
            val path = photo.path
            
            if (thumbPath.isNullOrBlank()) {
                if (path.isNullOrBlank()) null else path
            } else {
                thumbPath
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // This is expected when the composition is cancelled
            // Don't log this as an error
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting cover image path")
            null
        }
    }
}
