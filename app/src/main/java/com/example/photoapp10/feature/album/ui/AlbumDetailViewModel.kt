package com.example.photoapp10.feature.album.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.util.MediaPreviewResolver
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File

class AlbumDetailViewModel(app: Application, albumId: Long) : AndroidViewModel(app) {

    private val currentAlbumId: Long = albumId
    private val photosRepo: PhotoRepository = Modules.providePhotoRepository(app)
    private val albumRepo: AlbumRepository = Modules.provideAlbumRepository(app)
    private val userPrefs = UserPrefs(app)
    
    // Add mutex for thread safety
    private val mutex = Mutex()
    
    // Success message state
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    private val _sort = userPrefs.sortFlow.stateIn(
        scope = viewModelScope, 
        started = SharingStarted.Eagerly, 
        initialValue = SortMode.DATE_NEW
    )
    val sort: StateFlow<SortMode> = _sort

    // Album data for the title - observe changes
    val album: StateFlow<AlbumEntity?> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(albumRepo.getAlbumById(currentAlbumId))
            kotlinx.coroutines.delay(1000) // Poll every second for changes
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L),
        initialValue = null
    )
    
    // Breadcrumb path for navigation
    val breadcrumbPath: StateFlow<List<AlbumEntity>> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(albumRepo.getBreadcrumbPath(currentAlbumId))
            kotlinx.coroutines.delay(1000) // Poll every second for changes
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L),
        initialValue = emptyList()
    )
    
    // Sub-albums (nested folders) - observe by parentId
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val subAlbums = sort.flatMapLatest { s ->
        try {
            albumRepo.observeAlbumsByParent(currentAlbumId, s)
        } catch (e: Exception) {
            Timber.e(e, "Error observing sub-albums")
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photos = sort.flatMapLatest { s ->
        try {
            photosRepo.observePhotos(currentAlbumId, s)
        } catch (e: Exception) {
            Timber.e(e, "Error observing photos")
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope, 
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), 
        initialValue = emptyList()
    )

    fun setSort(mode: SortMode) {
        viewModelScope.launch { 
            try {
                userPrefs.setSort(mode)
            } catch (e: Exception) {
                Timber.e(e, "Error setting sort mode")
            }
        }
    }

    fun deletePhoto(photoId: Long) = viewModelScope.launch {
        try {
            if (photoId > 0) {
                photosRepo.deletePhoto(photoId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting photo: $photoId")
        }
    }

    fun deleteAllPhotos(photoIds: List<Long>?) = viewModelScope.launch {
        try {
            if (photoIds != null && photoIds.isNotEmpty()) {
                photoIds.forEach { photoId ->
                    try {
                        if (photoId > 0) {
                            photosRepo.deletePhoto(photoId)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error deleting individual photo: $photoId")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting photos")
        }
    }

    fun toggleFavorite(photoId: Long) = viewModelScope.launch {
        try {
            if (photoId > 0) {
                photosRepo.toggleFavorite(photoId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite: $photoId")
        }
    }

    fun toggleFavorites(photoIds: List<Long>?) = viewModelScope.launch {
        try {
            if (photoIds != null && photoIds.isNotEmpty()) {
                var addedCount = 0
                var removedCount = 0
                
                photoIds.forEach { photoId ->
                    try {
                        if (photoId > 0) {
                            // Get current photo state before toggling
                            val photo = photosRepo.getPhoto(photoId)
                            if (photo != null) {
                                val wasFavorite = photo.favorite
                                photosRepo.toggleFavorite(photoId)
                                
                                // Count based on the toggle result
                                if (wasFavorite) {
                                    removedCount++
                                } else {
                                    addedCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error toggling individual favorite: $photoId")
                    }
                }
                
                // Show appropriate success message
                when {
                    addedCount > 0 && removedCount > 0 -> {
                        _successMessage.value = "$addedCount photos added to favorites, $removedCount photos removed from favorites."
                    }
                    addedCount > 0 -> {
                        _successMessage.value = "$addedCount photos were added to favorites."
                    }
                    removedCount > 0 -> {
                        _successMessage.value = "$removedCount photos were removed from favorites."
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error toggling favorites")
        }
    }
    
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun sharePhotos(photoIds: List<Long>?) = viewModelScope.launch {
        try {
            if (photoIds != null && photoIds.isNotEmpty()) {
                Timber.i("Sharing photos: $photoIds")
                
                // Get photo entities for the selected IDs
                val photosToShare = mutableListOf<PhotoEntity>()
                photoIds.forEach { photoId ->
                    try {
                        val photo = photosRepo.getPhoto(photoId)
                        if (photo != null) {
                            photosToShare.add(photo)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error getting photo for sharing: $photoId")
                    }
                }
                
                if (photosToShare.isNotEmpty()) {
                    // Create share intent based on number of photos
                    val context = getApplication<Application>()
                    val authority = context.packageName + ".fileprovider"
                    
                    val shareIntent = if (photosToShare.size == 1) {
                        // Single photo sharing (reuse existing logic)
                        val photo = photosToShare.first()
                        val uri = FileProvider.getUriForFile(context, authority, File(photo.path))
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        // Multiple photos sharing
                        val uris = photosToShare.mapNotNull { photo ->
                            try {
                                FileProvider.getUriForFile(context, authority, File(photo.path))
                            } catch (e: Exception) {
                                Timber.e(e, "Error creating URI for photo: ${photo.path}")
                                null
                            }
                        }
                        
                        if (uris.isNotEmpty()) {
                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        } else {
                            null
                        }
                    }
                    
                    if (shareIntent != null) {
                        val chooserIntent = Intent.createChooser(shareIntent, "Share photos")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooserIntent)
                        Timber.i("Share intent launched successfully")
                    } else {
                        Timber.e("Failed to create share intent")
                    }
                } else {
                    Timber.w("No valid photos found to share")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sharing photos")
        }
    }

    // convenience for future actions (delete/move/favorite etc.)
    suspend fun photoById(id: Long): PhotoEntity? = try {
        if (id > 0) {
            photosRepo.getPhoto(id)
        } else {
            null
        }
    } catch (e: Exception) {
        Timber.e(e, "Error getting photo by ID: $id")
        null
    }
    
    /**
     * Process a captured photo from the native camera
     */
    fun processCapturedPhoto(photoFile: File, filename: String) = viewModelScope.launch {
        try {
            Timber.d("AlbumDetailViewModel: Processing captured photo: ${photoFile.absolutePath}")

            // Validate the photo file
            if (!photoFile.exists() || photoFile.length() == 0L) {
                Timber.e("AlbumDetailViewModel: Invalid photo file")
                return@launch
            }

            // Extract album ID from the file path or use the current album ID
            val targetAlbumId = extractAlbumIdFromPath(photoFile.absolutePath)
            if (targetAlbumId <= 0) {
                Timber.e("AlbumDetailViewModel: Could not determine album ID from path")
                return@launch
            }

            // Add photo to repository
            val (photoId, _) = photosRepo.addPhotoFromPath(
                albumId = targetAlbumId,
                originalPath = photoFile.absolutePath,
                filename = filename,
                width = 0, // Will be updated by ImageMetadata
                height = 0, // Will be updated by ImageMetadata
                sizeBytes = photoFile.length(),
                takenAt = System.currentTimeMillis()
            )

            if (photoId > 0) {
                Timber.d("AlbumDetailViewModel: Successfully added photo with ID: $photoId")
                // Show success message
                _successMessage.value = "Photo added successfully"
                // Clear message after delay
                kotlinx.coroutines.delay(3000)
                _successMessage.value = null
            } else {
                Timber.e("AlbumDetailViewModel: Failed to add photo to repository")
            }

        } catch (e: Exception) {
            Timber.e(e, "AlbumDetailViewModel: Error processing captured photo")
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
            Timber.e(e, "AlbumDetailViewModel: Error extracting album ID from path: $path")
            0L
        }
    }
    
    // Create nested album
    suspend fun createAlbum(name: String): Long {
        return try {
            albumRepo.createAlbum(name, currentAlbumId)
        } catch (e: Exception) {
            Timber.e(e, "Error creating nested album")
            -1L
        }
    }
    
    // Rename album (for current album)
    fun renameAlbum(newName: String) = viewModelScope.launch {
        try {
            albumRepo.renameAlbum(currentAlbumId, newName)
        } catch (e: Exception) {
            Timber.e(e, "Error renaming album")
        }
    }
    
    // Rename any album by ID (for sub-folders)
    fun renameAlbumById(albumId: Long, newName: String) = viewModelScope.launch {
        try {
            if (albumId > 0) {
                albumRepo.renameAlbum(albumId, newName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error renaming album $albumId")
        }
    }
    
    // Set emoji for current album
    fun setEmoji(emoji: String?) = viewModelScope.launch {
        try {
            albumRepo.setEmoji(currentAlbumId, emoji)
        } catch (e: Exception) {
            Timber.e(e, "Error setting emoji")
        }
    }
    
    // Set emoji for any album by ID (for sub-folders)
    fun setEmojiForAlbum(albumId: Long, emoji: String?) = viewModelScope.launch {
        try {
            if (albumId > 0) {
                albumRepo.setEmoji(albumId, emoji)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting emoji for album $albumId")
        }
    }
    
    // Toggle favorite for album by ID
    fun toggleFavoriteAlbum(albumId: Long) = viewModelScope.launch {
        try {
            if (albumId > 0) {
                albumRepo.toggleFavorite(albumId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite for album $albumId")
        }
    }
    
    // Delete album by ID
    fun deleteAlbum(album: AlbumEntity) = viewModelScope.launch {
        try {
            albumRepo.deleteAlbum(album)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting album ${album.id}")
        }
    }

    fun updateSubAlbumCovers() = viewModelScope.launch {
        try {
            subAlbums.value.forEach { album ->
                if (album.id > 0 && (album.coverPhotoId == null || album.coverPhotoId <= 0L)) {
                    albumRepo.updateCoverFromFirstPhoto(album.id)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating child album covers")
        }
    }

    suspend fun getCoverImagePath(album: AlbumEntity?): String? {
        return try {
            if (album == null) return null

            val coverPhotoId = album.coverPhotoId ?: return null
            if (coverPhotoId <= 0L) return null

            val photo = photosRepo.getPhoto(coverPhotoId) ?: return null
            MediaPreviewResolver.resolvePreviewPath(photo.thumbPath, photo.path)
        } catch (e: kotlinx.coroutines.CancellationException) {
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting child album cover image path for album ${album?.id}")
            null
        }
    }
}
