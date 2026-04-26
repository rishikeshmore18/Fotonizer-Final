package com.example.photoapp10.core.copymove

import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.feature.album.data.AlbumDao
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.photo.data.PhotoDao
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Service for handling copy and move operations for albums and photos
 * Handles recursive operations, name conflicts, and circular move prevention
 */
class CopyMoveService(
    private val albumDao: AlbumDao,
    private val photoDao: PhotoDao,
    private val albumRepo: AlbumRepository,
    private val photoRepo: PhotoRepository,
    private val storage: AppStorage
) {
    
    /**
     * Copy album recursively (including all nested albums and photos)
     * @param sourceAlbumId Album to copy
     * @param targetParentId Destination parent album ID (null for root)
     * @param progressCallback Optional callback for progress updates
     * @return New album ID, or 0 if failed
     */
    suspend fun copyAlbum(
        sourceAlbumId: Long,
        targetParentId: Long?,
        progressCallback: ((String, Int, Int) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val sourceAlbum = albumDao.getById(sourceAlbumId) ?: return@withContext 0L
        
        // Prevent copying album into itself
        if (targetParentId == sourceAlbumId) {
            throw IllegalArgumentException("Cannot copy album into itself")
        }
        
        // Check if targetParentId is a child of sourceAlbum (circular check)
        if (targetParentId != null && isDescendantOf(sourceAlbumId, targetParentId)) {
            throw IllegalArgumentException("Cannot copy album into its own descendant")
        }
        
        // Generate unique name
        val uniqueName = generateUniqueAlbumName(sourceAlbum.name, targetParentId)
        
        // Create new album
        val now = System.currentTimeMillis()
        val newAlbum = sourceAlbum.copy(
            id = 0L, // Will be auto-generated
            name = uniqueName,
            parentId = targetParentId,
            photoCount = 0, // Will be updated after copying photos
            updatedAt = now,
            backedUpAt = 0L // Reset backup status
        )
        val newAlbumId = albumDao.upsert(newAlbum)
        
        progressCallback?.invoke("Copying album: ${sourceAlbum.name}", 0, 1)
        
        // Copy all photos in the album
        val photos = photoDao.getAllInAlbum(sourceAlbumId)
        var copiedPhotos = 0
        photos.forEach { photo ->
            try {
                photoRepo.copyPhoto(photo.id, newAlbumId)
                copiedPhotos++
                progressCallback?.invoke("Copying photos...", copiedPhotos, photos.size)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy photo ${photo.id}")
            }
        }
        
        // Recursively copy child albums
        val childAlbums = albumDao.getChildAlbums(sourceAlbumId)
        childAlbums.forEach { childAlbum ->
            try {
                copyAlbum(childAlbum.id, newAlbumId, progressCallback)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy child album ${childAlbum.id}")
            }
        }
        
        // Update album photo count
        val newCount = photoDao.countInAlbum(newAlbumId)
        albumDao.updateCounts(newAlbumId, newCount, System.currentTimeMillis())
        
        Timber.d("CopyMoveService: Copied album ${sourceAlbum.name} (ID: $sourceAlbumId) to new album (ID: $newAlbumId)")
        newAlbumId
    }
    
    /**
     * Move album recursively (including all nested albums and photos)
     * @param sourceAlbumId Album to move
     * @param targetParentId Destination parent album ID (null for root)
     * @param progressCallback Optional callback for progress updates
     */
    suspend fun moveAlbum(
        sourceAlbumId: Long,
        targetParentId: Long?,
        progressCallback: ((String, Int, Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val sourceAlbum = albumDao.getById(sourceAlbumId) ?: return@withContext
        
        // Prevent moving album into itself
        if (targetParentId == sourceAlbumId) {
            throw IllegalArgumentException("Cannot move album into itself")
        }
        
        // Check if targetParentId is a child of sourceAlbum (circular check)
        if (targetParentId != null && isDescendantOf(sourceAlbumId, targetParentId)) {
            throw IllegalArgumentException("Cannot move album into its own descendant")
        }
        
        // Generate unique name if needed
        val uniqueName = generateUniqueAlbumName(sourceAlbum.name, targetParentId)
        
        progressCallback?.invoke("Moving album: ${sourceAlbum.name}", 0, 1)
        
        // Update album parentId and name
        // Note: When moving an album, the albumId stays the same, so files don't need to move
        // Files are stored in photos/{albumId}/ and thumbs/{albumId}/, so they remain valid
        val updated = sourceAlbum.copy(
            parentId = targetParentId,
            name = uniqueName,
            updatedAt = System.currentTimeMillis()
        )
        albumDao.update(updated)
        
        // Child albums don't need to be moved - their parentId already points to sourceAlbumId
        // They will automatically appear under the moved album
        
        Timber.d("CopyMoveService: Moved album ${sourceAlbum.name} (ID: $sourceAlbumId) to parent: $targetParentId")
    }
    
    /**
     * Check if targetId is a descendant of sourceId (to prevent circular moves)
     */
    private suspend fun isDescendantOf(sourceId: Long, targetId: Long): Boolean {
        var current: AlbumEntity? = albumDao.getById(targetId)
        while (current != null) {
            if (current.id == sourceId) {
                return true
            }
            current = current.parentId?.let { albumDao.getById(it) }
        }
        return false
    }
    
    /**
     * Generate unique album name within parent
     */
    private suspend fun generateUniqueAlbumName(baseName: String, parentId: Long?): String {
        if (albumDao.getByNameAndParent(baseName, parentId) == null) {
            return baseName
        }
        
        var counter = 1
        var candidateName: String
        do {
            candidateName = "$baseName($counter)"
            counter++
        } while (albumDao.getByNameAndParent(candidateName, parentId) != null)
        
        return candidateName
    }
    
    /**
     * Copy multiple albums and photos to target album
     * @param albumIds List of album IDs to copy
     * @param photoIds List of photo IDs to copy
     * @param targetAlbumId Destination album ID (null for root)
     * @param progressCallback Optional callback for progress updates
     * @return Result with counts of copied items
     */
    suspend fun copyItems(
        albumIds: List<Long>,
        photoIds: List<Long>,
        targetAlbumId: Long?,
        progressCallback: ((String, Int, Int) -> Unit)? = null
    ): CopyMoveResult = withContext(Dispatchers.IO) {
        val totalItems = albumIds.size + photoIds.size
        var processed = 0
        var albumsCopied = 0
        var photosCopied = 0
        val errors = mutableListOf<String>()
        
        // Copy albums
        albumIds.forEach { albumId ->
            try {
                // Prevent copying album into itself
                if (targetAlbumId == albumId) {
                    errors.add("Cannot copy album into itself")
                    processed++
                    progressCallback?.invoke("Skipping: Cannot copy into itself", processed, totalItems)
                    return@forEach
                }
                
                // Check circular move
                if (targetAlbumId != null && isDescendantOf(albumId, targetAlbumId)) {
                    errors.add("Cannot copy album into its own descendant")
                    processed++
                    progressCallback?.invoke("Skipping: Circular reference", processed, totalItems)
                    return@forEach
                }
                
                copyAlbum(albumId, targetAlbumId) { message, current, total ->
                    progressCallback?.invoke(message, processed + current, totalItems)
                }
                albumsCopied++
                processed++
                progressCallback?.invoke("Copied album", processed, totalItems)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy album $albumId")
                errors.add("Failed to copy album: ${e.message}")
                processed++
            }
        }
        
        // Copy photos
        photoIds.forEach { photoId ->
            try {
                val newPhotoId = photoRepo.copyPhoto(photoId, targetAlbumId ?: 0L)
                if (newPhotoId > 0) {
                    photosCopied++
                }
                processed++
                progressCallback?.invoke("Copied photo", processed, totalItems)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy photo $photoId")
                errors.add("Failed to copy photo: ${e.message}")
                processed++
            }
        }
        
        CopyMoveResult(
            albumsCopied = albumsCopied,
            photosCopied = photosCopied,
            errors = errors
        )
    }
    
    /**
     * Move multiple albums and photos to target album
     * @param albumIds List of album IDs to move
     * @param photoIds List of photo IDs to move
     * @param targetAlbumId Destination album ID (null for root)
     * @param progressCallback Optional callback for progress updates
     * @return Result with counts of moved items
     */
    suspend fun moveItems(
        albumIds: List<Long>,
        photoIds: List<Long>,
        targetAlbumId: Long?,
        progressCallback: ((String, Int, Int) -> Unit)? = null
    ): CopyMoveResult = withContext(Dispatchers.IO) {
        val totalItems = albumIds.size + photoIds.size
        var processed = 0
        var albumsMoved = 0
        var photosMoved = 0
        val errors = mutableListOf<String>()
        
        // Move albums
        albumIds.forEach { albumId ->
            try {
                // Prevent moving album into itself
                if (targetAlbumId == albumId) {
                    errors.add("Cannot move album into itself")
                    processed++
                    progressCallback?.invoke("Skipping: Cannot move into itself", processed, totalItems)
                    return@forEach
                }
                
                // Check circular move
                if (targetAlbumId != null && isDescendantOf(albumId, targetAlbumId)) {
                    errors.add("Cannot move album into its own descendant")
                    processed++
                    progressCallback?.invoke("Skipping: Circular reference", processed, totalItems)
                    return@forEach
                }
                
                moveAlbum(albumId, targetAlbumId) { message, current, total ->
                    progressCallback?.invoke(message, processed + current, totalItems)
                }
                albumsMoved++
                processed++
                progressCallback?.invoke("Moved album", processed, totalItems)
            } catch (e: Exception) {
                Timber.e(e, "Failed to move album $albumId")
                errors.add("Failed to move album: ${e.message}")
                processed++
            }
        }
        
        // Move photos
        photoIds.forEach { photoId ->
            try {
                photoRepo.movePhoto(photoId, targetAlbumId ?: 0L)
                photosMoved++
                processed++
                progressCallback?.invoke("Moved photo", processed, totalItems)
            } catch (e: Exception) {
                Timber.e(e, "Failed to move photo $photoId")
                errors.add("Failed to move photo: ${e.message}")
                processed++
            }
        }
        
        CopyMoveResult(
            albumsCopied = albumsMoved,
            photosCopied = photosMoved,
            errors = errors
        )
    }
}

/**
 * Result of copy/move operation
 */
data class CopyMoveResult(
    val albumsCopied: Int,
    val photosCopied: Int,
    val errors: List<String> = emptyList()
) {
    val isSuccess: Boolean
        get() = errors.isEmpty()
    
    val totalItems: Int
        get() = albumsCopied + photosCopied
}
