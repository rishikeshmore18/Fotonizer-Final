package com.example.photoapp10.feature.backup.domain

import android.content.Context
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.thumb.Thumbnailer
import com.example.photoapp10.core.util.MediaFileSupport
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.backup.drive.driveAppDataOrNull
import com.example.photoapp10.feature.backup.drive.DriveAppData
import com.example.photoapp10.feature.backup.drive.DriveUploader
import com.example.photoapp10.feature.photo.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class CloudArchiveManager(
    private val context: Context,
    private val db: AppDb,
    private val storage: AppStorage,
    private val thumbnailer: Thumbnailer
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    
    companion object {
        private const val ARCHIVE_FOLDER = "archive"
        private const val ARCHIVE_JSON = "archive.json"
        private const val ARCHIVE_PHOTOS = "photos"
    }

    data class ArchiveResult(
        val success: Boolean,
        val albumsArchived: Int,
        val photosArchived: Int,
        val photosSkipped: Int,
        val message: String
    )

    data class RestoreResult(
        val success: Boolean,
        val albumsRestored: Int,
        val photosRestored: Int,
        val photosSkipped: Int,
        val message: String
    )

    /**
     * Archive current app data to cloud as additive backup
     */
    suspend fun archiveToCloud(): ArchiveResult = withContext(Dispatchers.IO) {
        try {
            Timber.i("CloudArchiveManager: Starting cloud archive")
            
            val driveAppData = driveAppDataOrNull(context)
            if (driveAppData == null) {
                return@withContext ArchiveResult(
                    success = false,
                    albumsArchived = 0,
                    photosArchived = 0,
                    photosSkipped = 0,
                    message = "Google Drive not available"
                )
            }

            // 1. Get existing archive to compare
            val existingArchive = getExistingArchive(driveAppData)
            val existingPhotoIds = existingArchive?.archivedPhotos?.map { it.id }?.toSet() ?: emptySet()
            val existingAlbumIds = existingArchive?.archivedAlbums?.map { it.id }?.toSet() ?: emptySet()

            // 2. Get current app data
            val currentAlbums = db.albumDao().getAllAlbums()
            val currentPhotos = db.photoDao().getAllPhotos()

            // 3. Filter out already archived items
            val newAlbums = currentAlbums.filter { it.id !in existingAlbumIds }
            val newPhotos = currentPhotos.filter { it.id !in existingPhotoIds }

            Timber.d("CloudArchiveManager: Found ${newAlbums.size} new albums, ${newPhotos.size} new photos to archive")

            if (newAlbums.isEmpty() && newPhotos.isEmpty()) {
                return@withContext ArchiveResult(
                    success = true,
                    albumsArchived = 0,
                    photosArchived = 0,
                    photosSkipped = 0,
                    message = "No new data to archive"
                )
            }

            // 4. Upload new photos to archive
            val uploader = DriveUploader(driveAppData.drive)
            var photosArchived = 0
            var photosSkipped = 0
            val newlyArchivedPhotos = mutableListOf<ArchivePhoto>()

            newPhotos.forEach { photo ->
                try {
                    val fileExtension = MediaFileSupport.normalizedExtension(photo.filename)
                    val isVideo = MediaFileSupport.isVideoExtension(fileExtension)
                    
                    val file = storage.photoFile(photo.albumId, photo.id, fileExtension)
                    if (file.exists()) {
                        val archivePath = "$ARCHIVE_FOLDER/${MediaFileSupport.relativeMediaPath(photo.albumId, photo.id, fileExtension)}"
                        val mimeType = MediaFileSupport.mimeTypeForExtension(fileExtension)
                        val timeoutMs = if (isVideo) {
                            MediaFileSupport.VIDEO_TRANSFER_TIMEOUT_MS
                        } else {
                            MediaFileSupport.PHOTO_TRANSFER_TIMEOUT_MS
                        }
                        val success = uploader.putFileToPath(file, archivePath, mimeType, timeoutMs)
                        if (success) {
                            photosArchived++
                            newlyArchivedPhotos += photo.toArchivePhoto(fileExtension)
                        } else {
                            photosSkipped++
                        }
                    } else {
                        Timber.w("Photo/video file not found: ${file.absolutePath}")
                        photosSkipped++
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to archive photo ${photo.id}")
                    photosSkipped++
                }
            }

            // 5. Create updated archive.json
            val archivedAlbums = (existingArchive?.archivedAlbums ?: emptyList()) + 
                newAlbums.map { it.toArchiveAlbum() }
            val archivedPhotos = (existingArchive?.archivedPhotos ?: emptyList()) + 
                newlyArchivedPhotos

            val archiveRoot = ArchiveRoot(
                createdAt = existingArchive?.createdAt ?: System.currentTimeMillis(),
                lastArchiveAt = System.currentTimeMillis(),
                totalAlbums = archivedAlbums.size,
                totalPhotos = archivedPhotos.size,
                archivedAlbums = archivedAlbums,
                archivedPhotos = archivedPhotos
            )

            // 6. Upload archive.json
            val archiveJsonBytes = json.encodeToString(archiveRoot).toByteArray()
            val archiveJsonSuccess = uploader.putFileToPath(archiveJsonBytes, "$ARCHIVE_FOLDER/$ARCHIVE_JSON", "application/json")

            if (!archiveJsonSuccess) {
                return@withContext ArchiveResult(
                    success = false,
                    albumsArchived = 0,
                    photosArchived = 0,
                    photosSkipped = 0,
                    message = "Failed to upload archive metadata"
                )
            }

            val result = ArchiveResult(
                success = true,
                albumsArchived = newAlbums.size,
                photosArchived = photosArchived,
                photosSkipped = photosSkipped,
                message = "Archive completed successfully"
            )

            Timber.i("CloudArchiveManager: Archive completed - ${result.albumsArchived} albums, ${result.photosArchived} photos")
            result

        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveManager: Archive failed")
            ArchiveResult(
                success = false,
                albumsArchived = 0,
                photosArchived = 0,
                photosSkipped = 0,
                message = "Archive failed: ${e.message}"
            )
        }
    }

    /**
     * Restore from cloud archive (additive - only adds missing items)
     */
    suspend fun restoreFromArchive(): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Timber.i("CloudArchiveManager: Starting restore from archive")
            
            val driveAppData = driveAppDataOrNull(context)
            if (driveAppData == null) {
                return@withContext RestoreResult(
                    success = false,
                    albumsRestored = 0,
                    photosRestored = 0,
                    photosSkipped = 0,
                    message = "Google Drive not available"
                )
            }

            // 1. Download archive.json
            val archiveRoot = downloadArchive(driveAppData)
            if (archiveRoot == null) {
                Timber.w("CloudArchiveManager: No archive found in cloud")
                return@withContext RestoreResult(
                    success = false,
                    albumsRestored = 0,
                    photosRestored = 0,
                    photosSkipped = 0,
                    message = "No archive found in cloud"
                )
            }

            Timber.d("CloudArchiveManager: Found archive with ${archiveRoot.totalAlbums} albums, ${archiveRoot.totalPhotos} photos")

            // 2. Get current local data
            val localAlbums = db.albumDao().getAllAlbums()
            val localPhotos = db.photoDao().getAllPhotos()
            val localAlbumIds = localAlbums.map { it.id }.toSet()
            val localPhotoIds = localPhotos.map { it.id }.toSet()

            // 3. Find items to restore (only missing ones)
            val albumsToRestore = archiveRoot.archivedAlbums.filter { it.id !in localAlbumIds }
            val photosToRestore = archiveRoot.archivedPhotos.filter { it.id !in localPhotoIds }

            Timber.d("CloudArchiveManager: Found ${albumsToRestore.size} albums, ${photosToRestore.size} photos to restore")

            var albumsRestored = 0
            var photosRestored = 0
            var photosSkipped = 0

            // 4. Restore albums
            val albumDao = db.albumDao()
            albumsToRestore.forEach { archiveAlbum ->
                try {
                    albumDao.upsert(
                        AlbumEntity(
                            id = archiveAlbum.id,
                            name = archiveAlbum.name,
                            emoji = archiveAlbum.emoji,
                            photoCount = archiveAlbum.photoCount,
                            favorite = archiveAlbum.favorite,
                            updatedAt = archiveAlbum.originalUpdatedAt,
                            parentId = archiveAlbum.parentId
                        )
                    )
                    albumsRestored++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to restore album ${archiveAlbum.id}")
                }
            }

            // 5. Restore photos and download files
            val photoDao = db.photoDao()
            photosToRestore.forEach { archivePhoto ->
                try {
                    // Extract file extension from filename
                    val fileExtension = MediaFileSupport.normalizedExtension(archivePhoto.filename)
                    val isVideo = MediaFileSupport.isVideoExtension(fileExtension)
                    
                    // Download photo/video file from archive
                    val archivePath = "$ARCHIVE_FOLDER/${MediaFileSupport.relativeMediaPath(archivePhoto.albumId, archivePhoto.id, fileExtension)}"
                    val localFile = storage.photoFile(archivePhoto.albumId, archivePhoto.id, fileExtension)
                    
                    val downloaded = downloadPhotoFromArchive(driveAppData, archivePath, localFile)
                    if (downloaded) {
                        if (isVideo) {
                            // For videos, skip thumbnail generation (or implement video frame extraction later)
                            Timber.d("Video restored, skipping thumbnail generation")
                            photoDao.upsert(
                                PhotoEntity(
                                    id = archivePhoto.id,
                                    albumId = archivePhoto.albumId,
                                    filename = archivePhoto.filename,
                                    path = localFile.absolutePath,
                                    thumbPath = "", // No thumbnail for videos
                                    width = archivePhoto.width,
                                    height = archivePhoto.height,
                                    sizeBytes = localFile.length(),
                                    caption = archivePhoto.caption,
                                    tags = archivePhoto.tags,
                                    favorite = archivePhoto.favorite,
                                    takenAt = archivePhoto.takenAt,
                                    createdAt = archivePhoto.createdAt,
                                    updatedAt = archivePhoto.originalUpdatedAt
                                )
                            )
                        } else {
                            // For photos, generate thumbnail
                            val thumbFile = storage.thumbFile(archivePhoto.albumId, archivePhoto.id)
                            thumbnailer.generate(localFile, thumbFile)
                            
                            // Insert photo record
                            photoDao.upsert(
                                PhotoEntity(
                                    id = archivePhoto.id,
                                    albumId = archivePhoto.albumId,
                                    filename = archivePhoto.filename,
                                    path = localFile.absolutePath,
                                    thumbPath = thumbFile.absolutePath,
                                    width = archivePhoto.width,
                                    height = archivePhoto.height,
                                    sizeBytes = localFile.length(),
                                    caption = archivePhoto.caption,
                                    tags = archivePhoto.tags,
                                    favorite = archivePhoto.favorite,
                                    takenAt = archivePhoto.takenAt,
                                    createdAt = archivePhoto.createdAt,
                                    updatedAt = archivePhoto.originalUpdatedAt
                                )
                            )
                        }
                        photosRestored++
                    } else {
                        photosSkipped++
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to restore photo ${archivePhoto.id}")
                    photosSkipped++
                }
            }

            val result = RestoreResult(
                success = true,
                albumsRestored = albumsRestored,
                photosRestored = photosRestored,
                photosSkipped = photosSkipped,
                message = "Restore completed successfully"
            )

            Timber.i("CloudArchiveManager: Restore completed - ${result.albumsRestored} albums, ${result.photosRestored} photos")
            result

        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveManager: Restore failed")
            RestoreResult(
                success = false,
                albumsRestored = 0,
                photosRestored = 0,
                photosSkipped = 0,
                message = "Restore failed: ${e.message}"
            )
        }
    }

    private suspend fun getExistingArchive(driveAppData: DriveAppData): ArchiveRoot? {
        return try {
            val archiveFile = findArchiveFile(driveAppData)
            if (archiveFile != null) {
                val tempFile = File(context.cacheDir, "temp_archive.json")
                val downloaded = driveAppData.download(archiveFile.id, tempFile)
                if (downloaded) {
                    val content = tempFile.readText()
                    tempFile.delete()
                    json.decodeFromString<ArchiveRoot>(content)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get existing archive")
            null
        }
    }

    private suspend fun downloadArchive(driveAppData: DriveAppData): ArchiveRoot? {
        return try {
            val archiveFile = findArchiveFile(driveAppData)
            if (archiveFile != null) {
                val tempFile = File(context.cacheDir, "temp_archive.json")
                val downloaded = driveAppData.download(archiveFile.id, tempFile)
                if (downloaded) {
                    val content = tempFile.readText()
                    tempFile.delete()
                    json.decodeFromString<ArchiveRoot>(content)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download archive")
            null
        }
    }

    private suspend fun findArchiveFile(driveAppData: DriveAppData): DriveAppData.BackupFile? {
        return try {
            Timber.d("CloudArchiveManager: Searching for archive file: $ARCHIVE_FOLDER/$ARCHIVE_JSON")
            val listRequest = driveAppData.drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$ARCHIVE_FOLDER/$ARCHIVE_JSON' and trashed = false")
                .setFields("files(id,name,modifiedTime)")
                .setPageSize(1)

            val fileList = listRequest.execute()
            val files = fileList.files

            if (files.isNullOrEmpty()) {
                Timber.w("CloudArchiveManager: No archive file found")
                null
            } else {
                val file = files[0]
                Timber.d("CloudArchiveManager: Found archive file: ${file.name} (${file.id})")
                DriveAppData.BackupFile(
                    id = file.id,
                    name = file.name,
                    modifiedTimeMillis = file.modifiedTime?.value ?: 0L
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to find archive file")
            null
        }
    }

    private suspend fun downloadPhotoFromArchive(driveAppData: DriveAppData, archivePath: String, localFile: File): Boolean {
        return try {
            val listRequest = driveAppData.drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$archivePath' and trashed = false")
                .setFields("files(id,name)")
                .setPageSize(1)

            val fileList = listRequest.execute()
            val files = fileList.files

            if (files.isNullOrEmpty()) {
                Timber.w("Archive photo not found: $archivePath")
                false
            } else {
                val driveFile = files[0]
                localFile.parentFile?.mkdirs()
                driveAppData.download(driveFile.id, localFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download photo from archive: $archivePath")
            false
        }
    }

    private fun AlbumEntity.toArchiveAlbum() = ArchiveAlbum(
        id = id,
        name = name,
        emoji = emoji,
        photoCount = photoCount,
        favorite = favorite,
        archivedAt = System.currentTimeMillis(),
        originalUpdatedAt = updatedAt,
        parentId = parentId
    )

    private fun PhotoEntity.toArchivePhoto(fileExtension: String = MediaFileSupport.normalizedExtension(filename)) = ArchivePhoto(
        id = id,
        albumId = albumId,
        filename = filename,
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        caption = caption,
        tags = tags,
        favorite = favorite,
        takenAt = takenAt,
        createdAt = createdAt,
        originalUpdatedAt = updatedAt,
        archivedAt = System.currentTimeMillis(),
        relativePath = MediaFileSupport.relativeMediaPath(albumId, id, fileExtension)
    )
}
