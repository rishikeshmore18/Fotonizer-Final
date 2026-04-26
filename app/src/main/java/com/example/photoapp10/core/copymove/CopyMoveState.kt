package com.example.photoapp10.core.copymove

import androidx.compose.runtime.*
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.photo.data.PhotoEntity
import timber.log.Timber

/**
 * Manages copy/move operation state
 * Similar to clipboard in Windows File Explorer
 */
class CopyMoveState {
    private val _operationType = mutableStateOf<OperationType?>(null)
    val operationType: State<OperationType?> = _operationType
    
    private val _selectedAlbums = mutableStateOf<List<AlbumEntity>>(emptyList())
    val selectedAlbums: State<List<AlbumEntity>> = _selectedAlbums
    
    private val _selectedPhotos = mutableStateOf<List<PhotoEntity>>(emptyList())
    val selectedPhotos: State<List<PhotoEntity>> = _selectedPhotos
    
    enum class OperationType {
        COPY,
        MOVE
    }
    
    /**
     * Check if there's a pending copy/move operation
     */
    fun hasPendingOperation(): Boolean {
        return _operationType.value != null && 
               (_selectedAlbums.value.isNotEmpty() || _selectedPhotos.value.isNotEmpty())
    }
    
    /**
     * Start a copy operation
     */
    fun startCopy(albums: List<AlbumEntity>, photos: List<PhotoEntity>) {
        Timber.d("CopyMoveState: Starting COPY operation - ${albums.size} albums, ${photos.size} photos")
        _operationType.value = OperationType.COPY
        _selectedAlbums.value = albums.toList()
        _selectedPhotos.value = photos.toList()
    }
    
    /**
     * Start a move operation
     */
    fun startMove(albums: List<AlbumEntity>, photos: List<PhotoEntity>) {
        Timber.d("CopyMoveState: Starting MOVE operation - ${albums.size} albums, ${photos.size} photos")
        _operationType.value = OperationType.MOVE
        _selectedAlbums.value = albums.toList()
        _selectedPhotos.value = photos.toList()
    }
    
    /**
     * Clear the operation (after paste or cancel)
     */
    fun clear() {
        Timber.d("CopyMoveState: Clearing operation")
        _operationType.value = null
        _selectedAlbums.value = emptyList()
        _selectedPhotos.value = emptyList()
    }
    
    /**
     * Get all selected album IDs
     */
    fun getSelectedAlbumIds(): List<Long> {
        return _selectedAlbums.value.map { it.id }
    }
    
    /**
     * Get all selected photo IDs
     */
    fun getSelectedPhotoIds(): List<Long> {
        return _selectedPhotos.value.map { it.id }
    }
    
    /**
     * Get operation description for UI
     */
    fun getOperationDescription(): String {
        val type = _operationType.value ?: return ""
        val albumCount = _selectedAlbums.value.size
        val photoCount = _selectedPhotos.value.size
        
        val parts = mutableListOf<String>()
        if (albumCount > 0) parts.add("$albumCount album${if (albumCount > 1) "s" else ""}")
        if (photoCount > 0) parts.add("$photoCount item${if (photoCount > 1) "s" else ""}")
        
        val items = parts.joinToString(" and ")
        return when (type) {
            OperationType.COPY -> "Copy $items"
            OperationType.MOVE -> "Move $items"
        }
    }
}

/**
 * Shared CopyMoveState instance that persists across screens
 * This allows copy/move operations to persist when navigating between albums
 */
object SharedCopyMoveState {
    private val instance = CopyMoveState()
    
    fun getInstance(): CopyMoveState = instance
}

/**
 * Creates a CopyMoveState that will be remembered across recompositions
 * Uses the shared instance to persist state across navigation
 */
@Composable
fun rememberCopyMoveState(): CopyMoveState {
    return remember { SharedCopyMoveState.getInstance() }
}
