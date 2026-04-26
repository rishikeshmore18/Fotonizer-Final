package com.example.photoapp10.feature.photo.domain

import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.thumb.Thumbnailer
import com.example.photoapp10.core.util.MediaFileSupport
import com.example.photoapp10.core.util.TextNorm
import com.example.photoapp10.feature.album.data.AlbumDao
import com.example.photoapp10.feature.backup.DriveSyncManager
import com.example.photoapp10.feature.backup.domain.RealTimeArchiveManager
import com.example.photoapp10.feature.photo.data.PhotoDao
import com.example.photoapp10.feature.photo.data.PhotoEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.photoapp10.core.util.ImageMetadata

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val albumDao: AlbumDao,
    private val storage: AppStorage,
    private val thumbnailer: Thumbnailer,
    private val syncManager: DriveSyncManager? = null,
    private val archiveManager: RealTimeArchiveManager? = null
) {
    /** Observe photos in an album using chosen sort */
    fun observePhotos(albumId: Long, sort: SortMode): Flow<List<PhotoEntity>> = when (sort) {
        SortMode.NAME_ASC -> photoDao.observePhotosNameAsc(albumId)
        SortMode.NAME_DESC -> photoDao.observePhotosNameDesc(albumId)
        SortMode.DATE_NEW -> photoDao.observePhotosDateNew(albumId)
        SortMode.DATE_OLD -> photoDao.observePhotosDateOld(albumId)
        SortMode.FAV_FIRST -> photoDao.observePhotosFavFirst(albumId)
    }

    /** Paged photos for an album using chosen sort */
    fun pagerForAlbum(albumId: Long, sort: SortMode, pageSize: Int = 60): Flow<PagingData<PhotoEntity>> {
        val src = when (sort) {
            SortMode.NAME_ASC -> { { photoDao.pagingNameAsc(albumId) } }
            SortMode.NAME_DESC -> { { photoDao.pagingNameDesc(albumId) } }
            SortMode.DATE_NEW -> { { photoDao.pagingDateNew(albumId) } }
            SortMode.DATE_OLD -> { { photoDao.pagingDateOld(albumId) } }
            SortMode.FAV_FIRST -> { { photoDao.pagingFavFirst(albumId) } }
        }
        return Pager(
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = src
        ).flow
    }

    suspend fun getPhoto(photoId: Long): PhotoEntity? = photoDao.getById(photoId)

    /** Insert a new photo record from an existing original file path (after CameraX capture) */
    suspend fun addPhotoFromPath(
        albumId: Long,
        originalPath: String,
        filename: String,
        width: Int,
        height: Int,
        sizeBytes: Long,
        takenAt: Long = System.currentTimeMillis()
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        // Create DB row first to get the actual ID
        val originalFile = File(originalPath)
        var entity = PhotoEntity(
            albumId = albumId,
            filename = filename,
            path = "", // Will be set after moving file
            thumbPath = "", // to be filled after thumbnail
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            takenAt = takenAt
        )
        val id = photoDao.upsert(entity)
        Timber.d("Inserted placeholder for photo, got id=$id")

        // 2. Extract file extension from original filename
        val fileExtension = originalFile.extension.ifBlank { "jpg" }.lowercase()
        val isVideo = fileExtension in listOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
        
        // 3. Move file to permanent storage using the DB ID as filename with correct extension
        val dest = storage.photoFile(albumId, id, fileExtension)
        Timber.d("Moving ${if (isVideo) "video" else "photo"} from $originalPath to ${dest.absolutePath}")
        
        // Ensure parent directory exists
        val parentDir = dest.parentFile
        if (parentDir != null && !parentDir.exists()) {
            val dirCreated = parentDir.mkdirs()
            if (!dirCreated) {
                Timber.e("Failed to create parent directory: ${parentDir.absolutePath}")
                photoDao.deleteById(id) // Clean up placeholder
                return@withContext Pair(0L, "")
            }
        }
        Timber.d("Created parent directories for ${dest.absolutePath}")

        val success = originalFile.renameTo(dest)
        if (!success) {
            Timber.w("renameTo failed for ${originalFile.absolutePath}, trying copy/delete fallback")
            try {
                originalFile.copyTo(dest, overwrite = true)
                val deleteSuccess = originalFile.delete()
                if (!deleteSuccess) {
                    Timber.w("Failed to delete original file after copy: ${originalFile.absolutePath}")
                }
                Timber.d("Successfully moved file using copy/delete fallback")
            } catch (e: Exception) {
                Timber.e(e, "Failed to move photo file with copy-delete fallback")
                photoDao.deleteById(id) // Clean up placeholder
                return@withContext Pair(0L, "")
            }
        } else {
            Timber.d("Successfully moved file using renameTo")
        }
        
        // Verify the file exists after moving
        if (!dest.exists()) {
            Timber.e("CRITICAL: File does not exist after move: ${dest.absolutePath}")
            photoDao.deleteById(id) // Clean up placeholder
            return@withContext Pair(0L, "")
        } else {
            Timber.d("Verified file exists after move: ${dest.absolutePath}, size: ${dest.length()}")
        }

        // 4. Update entity with correct paths and metadata
        if (isVideo) {
            // For videos, use provided dimensions or defaults
            entity = entity.copy(
                id = id,
                path = dest.absolutePath,
                width = if (width > 0) width else 1920,
                height = if (height > 0) height else 1080,
                sizeBytes = dest.length()
            )
            photoDao.update(entity)
            // Generate video thumbnail
            generateVideoThumbnail(entity)
        } else {
            // For photos, extract metadata and generate thumbnail
            val metadata = ImageMetadata.fromFile(dest)
            entity = entity.copy(
                id = id,
                path = dest.absolutePath,
                width = metadata.width,
                height = metadata.height,
                sizeBytes = metadata.sizeBytes
            )
            photoDao.update(entity)
            // Generate thumbnail for photos
            generateThumbnail(entity)
        }

        // Update album photo count
        val newCount = photoDao.countInAlbum(albumId)
        albumDao.updateCounts(albumId, newCount, System.currentTimeMillis())
        ensureAlbumCover(albumId, id)

        syncManager?.requestSync("addPhoto")
        archiveManager?.requestArchive("addPhoto")
        Pair(id, dest.absolutePath)
    }

    private suspend fun generateThumbnail(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        try {
            val source = File(photo.path)
            if (!source.exists()) {
                Timber.w("Cannot generate thumbnail, source is missing: ${photo.path}")
                return@withContext
            }
            val thumbDest = storage.thumbFile(photo.albumId, photo.id)
            val result = thumbnailer.generate(source, thumbDest)
            photoDao.updateThumbMeta(
                photo.id,
                thumbPath = result.path,
                width = result.width,
                height = result.height,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Thumbnail generation failed for ${photo.path}")
        }
    }

    private suspend fun generateVideoThumbnail(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        try {
            val source = File(photo.path)
            if (!source.exists()) {
                Timber.w("Cannot generate video thumbnail, source is missing: ${photo.path}")
                return@withContext
            }
            val thumbDest = storage.thumbFile(photo.albumId, photo.id)
            val result = thumbnailer.generateVideoThumbnail(source, thumbDest)
            photoDao.updateThumbMeta(
                photo.id,
                thumbPath = result.path,
                width = result.width,
                height = result.height,
                updatedAt = System.currentTimeMillis()
            )
            Timber.d("Video thumbnail generated successfully: ${result.path}")
        } catch (e: Exception) {
            Timber.e(e, "Video thumbnail generation failed for ${photo.path}")
        }
    }

    /** Delete a photo: DB row + files (original + thumb) and update album counts */
    suspend fun deletePhoto(photoId: Long) = withContext(Dispatchers.IO) {
        val p = photoDao.getById(photoId) ?: return@withContext
        try {
            File(p.path).delete()
            File(p.thumbPath).delete()
        } catch (e: Exception) {
            Timber.w(e, "File delete error for photoId=$photoId")
        }
        photoDao.delete(p)
        val newCount = photoDao.countInAlbum(p.albumId)
        albumDao.updateCounts(p.albumId, newCount, System.currentTimeMillis())
        refreshAlbumCoverAfterRemoval(p.albumId, p.id)
        syncManager?.requestSync("deletePhoto")
        archiveManager?.requestArchive("deletePhoto")
    }

    /** Move photo to another album (physically move files + update DB + counts) */
    suspend fun movePhoto(photoId: Long, targetAlbumId: Long) = withContext(Dispatchers.IO) {
        val p = photoDao.getById(photoId) ?: return@withContext
        if (p.albumId == targetAlbumId) return@withContext

        // Move original
        val newOriginal = storage.photoFile(targetAlbumId, photoId, File(p.path).extension.ifBlank { "jpg" })
        newOriginal.parentFile?.mkdirs()
        File(p.path).takeIf { it.exists() }?.renameTo(newOriginal)

        // Move thumbnail (regenerate if missing)
        val oldThumb = File(p.thumbPath)
        val newThumb = storage.thumbFile(targetAlbumId, photoId)
        if (oldThumb.exists()) {
            newThumb.parentFile?.mkdirs()
            oldThumb.renameTo(newThumb)
        } else {
            // if thumb missing, regenerate
            try {
                val isVideo = p.filename.endsWith(".mp4", ignoreCase = true) || 
                             p.filename.endsWith(".mov", ignoreCase = true) ||
                             p.filename.endsWith(".avi", ignoreCase = true) ||
                             p.filename.endsWith(".mkv", ignoreCase = true) ||
                             p.filename.endsWith(".webm", ignoreCase = true) ||
                             p.filename.endsWith(".3gp", ignoreCase = true)
                if (isVideo) {
                    val result = thumbnailer.generateVideoThumbnail(newOriginal, newThumb)
                    photoDao.updateThumbMeta(photoId, result.path, result.width, result.height, System.currentTimeMillis())
                } else {
                    val result = thumbnailer.generate(newOriginal, newThumb, maxDim = 512, jpegQuality = 85)
                    photoDao.updateThumbMeta(photoId, result.path, result.width, result.height, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Timber.w(e, "Thumb regen failed when moving photo $photoId")
            }
        }

        // Update row
        val updated = p.copy(
            albumId = targetAlbumId,
            path = newOriginal.absolutePath,
            thumbPath = newThumb.absolutePath,
            updatedAt = System.currentTimeMillis()
        )
        photoDao.update(updated)

        // Recount both albums
        albumDao.updateCounts(p.albumId, photoDao.countInAlbum(p.albumId), System.currentTimeMillis())
        albumDao.updateCounts(targetAlbumId, photoDao.countInAlbum(targetAlbumId), System.currentTimeMillis())
        refreshAlbumCoverAfterRemoval(p.albumId, p.id)
        ensureAlbumCover(targetAlbumId, photoId)
        
        syncManager?.requestSync("movePhoto")
        archiveManager?.requestArchive("movePhoto")
    }
    
    /** Copy photo to another album (creates new photo with new ID + copies files) */
    suspend fun copyPhoto(photoId: Long, targetAlbumId: Long): Long = withContext(Dispatchers.IO) {
        val source = photoDao.getById(photoId) ?: return@withContext 0L
        
        // Create new photo entity
        val now = System.currentTimeMillis()
        val newPhoto = source.copy(
            id = 0L, // Will be auto-generated
            albumId = targetAlbumId,
            path = "", // Will be set after file copy
            thumbPath = "", // Will be set after thumbnail copy
            createdAt = now,
            updatedAt = now,
            backedUpAt = 0L // Reset backup status for copied photo
        )
        val newId = photoDao.upsert(newPhoto)
        
        // Copy original file
        val sourceFile = File(source.path)
        val fileExtension = sourceFile.extension.ifBlank { "jpg" }
        val destFile = storage.photoFile(targetAlbumId, newId, fileExtension)
        destFile.parentFile?.mkdirs()
        
        if (sourceFile.exists()) {
            sourceFile.copyTo(destFile, overwrite = true)
        } else {
            Timber.w("Source photo file not found: ${sourceFile.absolutePath}")
            photoDao.deleteById(newId)
            return@withContext 0L
        }
        
        // Copy or regenerate thumbnail
        val sourceThumb = File(source.thumbPath)
        val destThumb = storage.thumbFile(targetAlbumId, newId)
        destThumb.parentFile?.mkdirs()
        
        val isVideo = source.filename.endsWith(".mp4", ignoreCase = true) || 
                     source.filename.endsWith(".mov", ignoreCase = true) ||
                     source.filename.endsWith(".avi", ignoreCase = true) ||
                     source.filename.endsWith(".mkv", ignoreCase = true) ||
                     source.filename.endsWith(".webm", ignoreCase = true) ||
                     source.filename.endsWith(".3gp", ignoreCase = true)
        
        if (sourceThumb.exists()) {
            sourceThumb.copyTo(destThumb, overwrite = true)
            // Update with copied thumb path
            photoDao.updateThumbMeta(newId, destThumb.absolutePath, source.width, source.height, now)
        } else {
            // Regenerate thumbnail
            try {
                val result = if (isVideo) {
                    thumbnailer.generateVideoThumbnail(destFile, destThumb)
                } else {
                    thumbnailer.generate(destFile, destThumb, maxDim = 512, jpegQuality = 85)
                }
                photoDao.updateThumbMeta(newId, result.path, result.width, result.height, now)
            } catch (e: Exception) {
                Timber.w(e, "Thumb generation failed when copying photo $photoId")
            }
        }
        
        // Update photo entity with paths
        val updated = newPhoto.copy(
            id = newId,
            path = destFile.absolutePath,
            thumbPath = destThumb.absolutePath
        )
        photoDao.update(updated)
        
        // Update target album count
        val newCount = photoDao.countInAlbum(targetAlbumId)
        albumDao.updateCounts(targetAlbumId, newCount, System.currentTimeMillis())
        ensureAlbumCover(targetAlbumId, newId)
        
        syncManager?.requestSync("copyPhoto")
        archiveManager?.requestArchive("copyPhoto")
        
        newId
    }

    suspend fun toggleFavorite(photoId: Long) {
        val p = photoDao.getById(photoId) ?: return
        photoDao.update(p.copy(favorite = !p.favorite, updatedAt = System.currentTimeMillis()))
        syncManager?.requestSync("toggleFavorite")
        archiveManager?.requestArchive("toggleFavorite")
    }

    suspend fun updateCaption(photoId: Long, caption: String) {
        val p = photoDao.getById(photoId) ?: return
        photoDao.update(p.copy(caption = caption.trim(), updatedAt = System.currentTimeMillis()))
        syncManager?.requestSync("updateCaption")
        archiveManager?.requestArchive("updateCaption")
    }

    suspend fun renamePhoto(photoId: Long, newName: String) = withContext(Dispatchers.IO) {
        val photo = photoDao.getById(photoId) ?: return@withContext

        val renamedFilename = try {
            MediaFileSupport.buildFilenamePreservingExtension(photo.filename, newName)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "renamePhoto: rejected blank rename for photoId=$photoId")
            return@withContext
        }

        if (renamedFilename == photo.filename) {
            Timber.d("renamePhoto: no-op rename for photoId=$photoId")
            return@withContext
        }

        val fileExtension = MediaFileSupport.normalizedExtension(renamedFilename)
        val canonicalPhotoFile = storage.photoFile(photo.albumId, photo.id, fileExtension)
        val canonicalThumbFile = storage.thumbFile(photo.albumId, photo.id)

        // Keep the on-disk storage contract stable: media remains stored by photoId + extension.
        normalizeMediaPath(photo.path, canonicalPhotoFile)
        normalizeMediaPath(photo.thumbPath, canonicalThumbFile)

        val resolvedPhotoPath = resolveStoredPath(photo.path, canonicalPhotoFile)
        val resolvedThumbPath = resolveThumbPath(photo.thumbPath, canonicalThumbFile)
        val now = System.currentTimeMillis()

        photoDao.update(
            photo.copy(
                filename = renamedFilename,
                path = resolvedPhotoPath,
                thumbPath = resolvedThumbPath,
                updatedAt = now,
                backedUpAt = 0L
            )
        )

        syncManager?.requestSync("renamePhoto")
        archiveManager?.requestArchive("renamePhoto")
    }

    /** Store emoji as glyph strings; normalize to lowercase for search */
    suspend fun updateTags(photoId: Long, tags: List<String>) {
        val normalized = tags.map { it.trim() }.filter { it.isNotEmpty() }
        val p = photoDao.getById(photoId) ?: return
        photoDao.update(p.copy(tags = normalized, updatedAt = System.currentTimeMillis()))
        syncManager?.requestSync("updateTags")
        archiveManager?.requestArchive("updateTags")
    }

    // Keep the existing method for backward compatibility
    suspend fun generateAndAttachThumbnail(photo: PhotoEntity) {
        withContext(Dispatchers.IO) {
            val src = File(photo.path)
            val dest = storage.thumbFile(photo.albumId, photo.id)
            val result = thumbnailer.generate(src, dest, maxDim = 512, jpegQuality = 85)
            photoDao.updateThumbMeta(
                photoId = photo.id,
                thumbPath = result.path,
                width = result.width,
                height = result.height,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /** Case/diacritic-insensitive search across filename, caption, and tags */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(
        rawQuery: String,
        albumId: Long? = null
    ): Flow<List<PhotoEntity>> {
        val q = "%${rawQuery.trim()}%"
        val baseFlow: Flow<List<PhotoEntity>> =
            if (albumId == null) {
                photoDao.searchByFilenameOrCaption(q)
            } else {
                albumDao.observeAllAlbums().flatMapLatest { albums ->
                    val albumIds = descendantAlbumIds(albumId, albums)
                    if (albumIds.isEmpty()) {
                        flowOf(emptyList())
                    } else if (albumIds.size == 1) {
                        photoDao.searchInAlbum(albumId, q)
                    } else {
                        photoDao.searchInAlbums(albumIds, q)
                    }
                }
            }

        // Client-side normalize + tags match + filename/caption fallback
        return baseFlow.map { list ->
            val nq = TextNorm.norm(rawQuery)
            if (nq.isBlank()) return@map emptyList<PhotoEntity>()
            list.filter { p ->
                val f = TextNorm.norm(p.filename)
                val c = TextNorm.norm(p.caption)
                val t = TextNorm.norm(p.tags.joinToString(" "))
                f.contains(nq) || c.contains(nq) || t.contains(nq)
            }
        }
    }

    /** Sort a list by mode (used for search results, not the paged album grid) */
    fun sortList(list: List<PhotoEntity>, mode: SortMode): List<PhotoEntity> = when (mode) {
        SortMode.NAME_ASC  -> list.sortedBy { it.filename.lowercase() }
        SortMode.NAME_DESC -> list.sortedByDescending { it.filename.lowercase() }
        SortMode.DATE_NEW  -> list.sortedByDescending { it.createdAt }
        SortMode.DATE_OLD  -> list.sortedBy { it.createdAt }
        SortMode.FAV_FIRST -> list.sortedWith(
            compareByDescending<PhotoEntity> { it.favorite }.thenByDescending { it.createdAt }
        )
    }

    // Favorites & Recents helpers
    fun observeFavorites(limit: Int = 20): Flow<List<PhotoEntity>> = photoDao.observeFavorites(limit)
    fun observeRecents(limit: Int = 20): Flow<List<PhotoEntity>> = photoDao.observeRecents(limit)

    private suspend fun ensureAlbumCover(albumId: Long, preferredPhotoId: Long) {
        val album = albumDao.getById(albumId) ?: return
        val currentCoverId = album.coverPhotoId
        val currentCoverExists = currentCoverId?.let { photoDao.getById(it) != null } == true

        if (currentCoverId != null && currentCoverExists) return

        val nextCoverId = when {
            photoDao.getById(preferredPhotoId) != null -> preferredPhotoId
            else -> albumDao.getFirstPhotoId(albumId)
        }

        if (currentCoverId != nextCoverId) {
            albumDao.setCover(albumId, nextCoverId, System.currentTimeMillis())
        }
    }

    private suspend fun refreshAlbumCoverAfterRemoval(albumId: Long, removedPhotoId: Long) {
        val album = albumDao.getById(albumId) ?: return
        val currentCoverId = album.coverPhotoId
        val currentCoverExists = currentCoverId?.let { photoDao.getById(it) != null } == true
        if (currentCoverId != removedPhotoId && currentCoverExists) return

        val nextCoverId = albumDao.getFirstPhotoId(albumId)
        if (currentCoverId != nextCoverId) {
            albumDao.setCover(albumId, nextCoverId, System.currentTimeMillis())
        }
    }

    private fun normalizeMediaPath(currentPath: String, canonicalFile: File) {
        if (currentPath.isBlank()) return

        val currentFile = File(currentPath)
        if (!currentFile.exists() || currentFile.absolutePath == canonicalFile.absolutePath) return

        canonicalFile.parentFile?.mkdirs()
        val moved = moveFileWithFallback(currentFile, canonicalFile)
        if (!moved) {
            Timber.w("normalizeMediaPath: failed to move ${currentFile.absolutePath} to ${canonicalFile.absolutePath}")
        }
    }

    private fun moveFileWithFallback(source: File, destination: File): Boolean {
        if (!source.exists()) return false

        return if (source.renameTo(destination)) {
            true
        } else {
            runCatching {
                source.copyTo(destination, overwrite = true)
                source.delete()
            }.isSuccess
        }
    }

    private fun resolveStoredPath(previousPath: String, canonicalFile: File): String {
        if (canonicalFile.exists()) return canonicalFile.absolutePath

        if (previousPath.isBlank()) return canonicalFile.absolutePath

        val previousFile = File(previousPath)
        return if (previousFile.exists()) previousFile.absolutePath else canonicalFile.absolutePath
    }

    private fun resolveThumbPath(previousThumbPath: String, canonicalThumbFile: File): String {
        if (canonicalThumbFile.exists()) return canonicalThumbFile.absolutePath

        if (previousThumbPath.isBlank()) return ""

        val previousThumb = File(previousThumbPath)
        return if (previousThumb.exists()) previousThumb.absolutePath else ""
    }

    private fun descendantAlbumIds(rootAlbumId: Long, albums: List<com.example.photoapp10.feature.album.data.AlbumEntity>): List<Long> {
        val childrenByParent = albums
            .filter { it.parentId != null }
            .groupBy { it.parentId }

        val result = linkedSetOf(rootAlbumId)
        val queue = ArrayDeque<Long>()
        queue.add(rootAlbumId)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            childrenByParent[currentId].orEmpty().forEach { child ->
                if (result.add(child.id)) {
                    queue.add(child.id)
                }
            }
        }

        return result.toList()
    }
}
