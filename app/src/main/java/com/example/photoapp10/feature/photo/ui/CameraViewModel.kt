package com.example.photoapp10.feature.photo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    
    private val albumRepo: AlbumRepository = Modules.provideAlbumRepository(app)
    private val photoRepo: PhotoRepository = Modules.providePhotoRepository(app)
    private val storage = AppStorage(app)
    
    // Camera state
    private val _isFlashEnabled = MutableStateFlow(false)
    val isFlashEnabled: StateFlow<Boolean> = _isFlashEnabled.asStateFlow()
    
    private val _currentZoomRatio = MutableStateFlow(1.0f)
    val currentZoomRatio: StateFlow<Float> = _currentZoomRatio.asStateFlow()
    
    private val _showSavedOverlay = MutableStateFlow(false)
    val showSavedOverlay: StateFlow<Boolean> = _showSavedOverlay.asStateFlow()
    
    private val _lastCapturedPhotoPath = MutableStateFlow<String?>(null)
    val lastCapturedPhotoPath: StateFlow<String?> = _lastCapturedPhotoPath.asStateFlow()
    
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
    
    /**
     * Get the target album ID for photo capture
     * @param explicitAlbumId Album ID passed from navigation, or 0 for default album
     * @return The album ID to save photos to
     */
    suspend fun getTargetAlbumId(explicitAlbumId: Long): Long {
        return if (explicitAlbumId > 0) {
            explicitAlbumId
        } else {
            // Ensure default album exists
            albumRepo.findOrCreateDefaultAlbum()
        }
    }
    
    /**
     * Create a temporary photo file for capture
     * @param albumId The album to save the photo to
     * @return Temporary file for photo capture
     */
    fun createTempPhotoFile(albumId: Long): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val filename = "IMG_${timestamp}.jpg"
        
        val albumDir = storage.getPhotosDir(albumId)
        return File(albumDir, filename)
    }
    
    /**
     * Save captured photo to album and repository
     * @param photoFile The captured photo file
     * @param albumId The album to save to
     */
    fun savePhotoToAlbum(photoFile: File, albumId: Long) = viewModelScope.launch {
        try {
            Timber.d("CameraViewModel: Saving photo to album $albumId: ${photoFile.absolutePath}")
            
            // Validate photo file
            if (!photoFile.exists() || photoFile.length() == 0L) {
                Timber.e("CameraViewModel: Invalid photo file - exists: ${photoFile.exists()}, size: ${photoFile.length()}")
                _snackbarMessage.value = "Invalid photo file"
                return@launch
            }
            
            // Validate album ID
            if (albumId <= 0) {
                Timber.e("CameraViewModel: Invalid album ID: $albumId")
                _snackbarMessage.value = "Invalid album ID"
                return@launch
            }
            
            // Add photo to repository
            val (photoId, finalPath) = photoRepo.addPhotoFromPath(
                albumId = albumId,
                originalPath = photoFile.absolutePath,
                filename = photoFile.name,
                width = 0, // Will be updated by ImageMetadata
                height = 0, // Will be updated by ImageMetadata
                sizeBytes = photoFile.length(),
                takenAt = System.currentTimeMillis()
            )
            
            if (photoId > 0) {
                Timber.d("CameraViewModel: Successfully saved photo with ID: $photoId")
                
                // Store the final photo path for thumbnail (after file move)
                _lastCapturedPhotoPath.value = finalPath
                _snackbarMessage.value = "Photo saved successfully"
            } else {
                Timber.e("CameraViewModel: Failed to save photo to repository")
                _snackbarMessage.value = "Failed to save photo"
            }
            
        } catch (e: Exception) {
            Timber.e(e, "CameraViewModel: Error saving photo to album")
            _snackbarMessage.value = "Error saving photo: ${e.message}"
        }
    }
    
    /**
     * Toggle flash state
     */
    fun toggleFlash() {
        _isFlashEnabled.value = !_isFlashEnabled.value
        Timber.d("CameraViewModel: Flash toggled to ${_isFlashEnabled.value}")
    }
    
    /**
     * Update zoom ratio
     * @param ratio The new zoom ratio
     */
    fun updateZoomRatio(ratio: Float) {
        _currentZoomRatio.value = ratio.coerceIn(1.0f, 2.0f)
    }
    
    /**
     * Set discrete zoom level
     * @param level 1 or 2
     */
    fun setZoomLevel(level: Int) {
        when (level) {
            1 -> _currentZoomRatio.value = 1.0f
            2 -> _currentZoomRatio.value = 2.0f
        }
        Timber.d("CameraViewModel: Zoom level set to $level")
    }
    
    companion object {
        fun factory(app: Application): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return CameraViewModel(app) as T
                }
            }
        }
    }
}

