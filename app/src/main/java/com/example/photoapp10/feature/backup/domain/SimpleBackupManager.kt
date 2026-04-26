package com.example.photoapp10.feature.backup.domain

import android.content.Context
import android.os.Environment
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.util.MediaFileSupport
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Simplified backup manager that creates versioned backup snapshots inside the default backup folder
 * and provides simple backup/restore functionality.
 */
class SimpleBackupManager(
    private val context: Context,
    private val db: AppDb,
    private val storage: AppStorage,
    private val prefs: UserPrefs
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val BACKUP_FOLDER_NAME = "PhotoApp10_Backups"
        private const val BACKUP_JSON_FILE = "backup.json"
        private const val MEDIA_FOLDER = "media"
        private const val PHOTOS_FOLDER = "photos"
        private const val THUMBS_FOLDER = "thumbs"
        private const val LEGACY_BACKUP_ID = "__legacy__"
    }

    fun getDefaultBackupFolderPath(): String {
        val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val backupDir = File(externalDir, BACKUP_FOLDER_NAME)
        return backupDir.absolutePath
    }

    private fun getDefaultBackupFolder(): File {
        val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val backupDir = File(externalDir, BACKUP_FOLDER_NAME)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IllegalStateException("Could not create backup folder. Check storage access.")
        }
        if (!backupDir.isDirectory) {
            throw IllegalStateException("Backup path is not a folder.")
        }
        return backupDir
    }

    suspend fun hasBackup(): Boolean = withContext(Dispatchers.IO) { listBackupsInternal().isNotEmpty() }

    suspend fun getBackupInfo(): BackupInfo? = withContext(Dispatchers.IO) { listBackupsInternal().firstOrNull() }

    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) { listBackupsInternal() }

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            Timber.i("SimpleBackupManager: Starting backup to versioned folder")

            val backupRootDir = getDefaultBackupFolder()
            val backupDir = createSnapshotDirectory(backupRootDir)
            if (!backupDir.canWrite()) {
                return@withContext BackupResult(
                    success = false,
                    albumsCount = 0,
                    photosCount = 0,
                    photosCopied = 0,
                    photosMissing = 0,
                    thumbsCopied = 0,
                    thumbsMissing = 0,
                    backupPath = backupDir.absolutePath,
                    message = "Backup failed: Could not write to backup folder. Check storage permission."
                )
            }

            val backupFile = File(backupDir, BACKUP_JSON_FILE)

            val albums = db.albumDao().getAllAlbums().map { album -> album.toBackupAlbum() }
            val photos = db.photoDao().getAllPhotos().map { photo ->
                val fileExtension = MediaFileSupport.normalizedExtension(photo.filename)
                BackupPhoto(
                    id = photo.id,
                    albumId = photo.albumId,
                    filename = photo.filename,
                    width = photo.width,
                    height = photo.height,
                    sizeBytes = photo.sizeBytes,
                    caption = photo.caption,
                    tags = photo.tags,
                    favorite = photo.favorite,
                    takenAt = photo.takenAt,
                    createdAt = photo.createdAt,
                    updatedAt = photo.updatedAt,
                    path = photo.path,
                    thumbPath = photo.thumbPath,
                    relativePath = MediaFileSupport.relativeMediaPath(photo.albumId, photo.id, fileExtension)
                )
            }

            val settings = BackupSettings(
                themeMode = prefs.themeFlow.firstOrNull()?.name?.lowercase() ?: "system",
                defaultSort = prefs.sortFlow.firstOrNull()?.name?.lowercase() ?: "date_new",
                lastSearch = prefs.lastSearchFlow.firstOrNull() ?: ""
            )

            val root = BackupRoot(
                createdAt = System.currentTimeMillis(),
                settings = settings,
                albums = albums,
                photos = photos
            )

            backupFile.writeText(json.encodeToString(root))

            val mediaDir = File(backupDir, MEDIA_FOLDER)
            val photosDir = File(mediaDir, PHOTOS_FOLDER)
            val thumbsDir = File(mediaDir, THUMBS_FOLDER)

            if ((!photosDir.exists() && !photosDir.mkdirs()) || (!thumbsDir.exists() && !thumbsDir.mkdirs())) {
                return@withContext BackupResult(
                    success = false,
                    albumsCount = 0,
                    photosCount = 0,
                    photosCopied = 0,
                    photosMissing = 0,
                    thumbsCopied = 0,
                    thumbsMissing = 0,
                    backupPath = backupDir.absolutePath,
                    message = "Backup failed: Could not create media folders in the backup location."
                )
            }

            var copiedPhotos = 0
            var missingPhotos = 0
            var copiedThumbs = 0
            var missingThumbs = 0

            photos.forEach { photo ->
                val fileExtension = MediaFileSupport.normalizedExtension(photo.filename)
                val srcPhoto = File(photo.path)
                val dstPhotoDir = File(photosDir, photo.albumId.toString())
                if (!dstPhotoDir.exists() && !dstPhotoDir.mkdirs()) {
                    throw IllegalStateException("Could not create backup folder for album ${photo.albumId}.")
                }
                val dstPhoto = File(dstPhotoDir, MediaFileSupport.backupMediaFileName(photo.id, fileExtension))

                if (srcPhoto.exists()) {
                    srcPhoto.copyTo(dstPhoto, overwrite = true)
                    copiedPhotos++
                } else {
                    Timber.w("Photo file missing: %s", srcPhoto.absolutePath)
                    missingPhotos++
                }

                val srcThumb = File(photo.thumbPath)
                if (srcThumb.exists()) {
                    val dstThumbDir = File(thumbsDir, photo.albumId.toString())
                    if (!dstThumbDir.exists() && !dstThumbDir.mkdirs()) {
                        throw IllegalStateException("Could not create thumbnail backup folder for album ${photo.albumId}.")
                    }
                    val dstThumb = File(dstThumbDir, "${photo.id}.jpg")
                    srcThumb.copyTo(dstThumb, overwrite = true)
                    copiedThumbs++
                } else {
                    missingThumbs++
                }
            }

            val result = BackupResult(
                success = true,
                albumsCount = albums.size,
                photosCount = photos.size,
                photosCopied = copiedPhotos,
                photosMissing = missingPhotos,
                thumbsCopied = copiedThumbs,
                thumbsMissing = missingThumbs,
                backupPath = backupDir.absolutePath,
                message = "Backup completed successfully"
            )

            Timber.i(
                "SimpleBackupManager: Backup completed - %s albums, %s photos",
                result.albumsCount,
                result.photosCount
            )
            result
        } catch (e: Exception) {
            Timber.e(e, "SimpleBackupManager: Backup failed")
            BackupResult(
                success = false,
                albumsCount = 0,
                photosCount = 0,
                photosCopied = 0,
                photosMissing = 0,
                thumbsCopied = 0,
                thumbsMissing = 0,
                backupPath = getDefaultBackupFolderPath(),
                message = "Backup failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    suspend fun restoreBackup(): RestoreResult = withContext(Dispatchers.IO) {
        val latestBackup = listBackupsInternal().firstOrNull()
            ?: return@withContext RestoreResult(success = false, message = "No backup found in default folder")
        restoreBackupFromInfo(latestBackup)
    }

    suspend fun restoreBackup(backupPath: String): RestoreResult = withContext(Dispatchers.IO) {
        val selectedBackup = listBackupsInternal().firstOrNull { it.backupPath == backupPath }
            ?: return@withContext RestoreResult(success = false, message = "Selected backup could not be found")
        restoreBackupFromInfo(selectedBackup)
    }

    private suspend fun restoreBackupFromInfo(backupInfo: BackupInfo): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Timber.i("SimpleBackupManager: Starting restore from %s", backupInfo.backupPath)

            val backupDir = File(backupInfo.backupPath)
            val backupFile = File(backupDir, BACKUP_JSON_FILE)
            if (!backupFile.exists()) {
                return@withContext RestoreResult(
                    success = false,
                    message = "No backup found in ${backupInfo.backupPath}"
                )
            }

            val root = json.decodeFromString<BackupRoot>(backupFile.readText())
            require(root.schemaVersion == BACKUP_SCHEMA_VERSION) { "Unsupported backup schema" }

            var albumsInserted = 0
            var albumsUpdated = 0
            var photosInserted = 0
            var photosUpdated = 0
            var photosMissing = 0

            val albumDao = db.albumDao()
            val photoDao = db.photoDao()
            val restoreTimestamp = System.currentTimeMillis()
            val albumIdMap = mutableMapOf<Long, Long>()
            val photoIdMap = mutableMapOf<Long, Long>()
            val mediaDir = File(backupDir, MEDIA_FOLDER)
            val photosDir = File(mediaDir, PHOTOS_FOLDER)
            val thumbsDir = File(mediaDir, THUMBS_FOLDER)

            val albumsById = root.albums.associateBy { it.id }
            val orderedAlbums = orderAlbumsForRestore(root.albums, albumsById)

            orderedAlbums.forEach { album ->
                val localParentId = album.parentId?.let { albumIdMap[it] }
                val existingById = albumDao.getById(album.id)
                val existingByNaturalKey = albumDao.getByNameAndParent(album.name, localParentId)
                val matchedAlbum = when {
                    existingById != null && existingById.parentId == localParentId -> existingById
                    existingByNaturalKey != null -> existingByNaturalKey
                    else -> null
                }

                val localAlbumId = when {
                    matchedAlbum == null -> {
                        val insertedId = albumDao.upsert(
                            AlbumEntity(
                                name = album.name,
                                coverPhotoId = null,
                                photoCount = 0,
                                favorite = album.favorite,
                                emoji = album.emoji,
                                updatedAt = album.updatedAt,
                                parentId = localParentId
                            )
                        )
                        albumsInserted++
                        insertedId
                    }
                    album.updatedAt > matchedAlbum.updatedAt || matchedAlbum.parentId != localParentId -> {
                        albumDao.update(
                            matchedAlbum.copy(
                                name = album.name,
                                favorite = album.favorite,
                                emoji = album.emoji,
                                updatedAt = maxOf(album.updatedAt, matchedAlbum.updatedAt),
                                parentId = localParentId
                            )
                        )
                        albumsUpdated++
                        matchedAlbum.id
                    }
                    else -> matchedAlbum.id
                }

                albumIdMap[album.id] = localAlbumId
            }

            root.photos.forEach { photo ->
                val targetAlbumId = albumIdMap[photo.albumId]
                if (targetAlbumId == null) {
                    Timber.w("Skipping photo restore because album %s could not be mapped", photo.albumId)
                    photosMissing++
                    return@forEach
                }

                val fileExtension = MediaFileSupport.normalizedExtension(photo.filename)
                val existingById = photoDao.getById(photo.id)
                val existingByNaturalKey = photoDao.findByNaturalKey(
                    albumId = targetAlbumId,
                    filename = photo.filename,
                    takenAt = photo.takenAt,
                    sizeBytes = photo.sizeBytes
                ) ?: photoDao.findByAlbumAndFilename(targetAlbumId, photo.filename)
                val matchedPhoto = when {
                    existingById != null && existingById.albumId == targetAlbumId -> existingById
                    existingByNaturalKey != null -> existingByNaturalKey
                    else -> null
                }

                val provisionalPhotoId = matchedPhoto?.id ?: photo.id
                val srcPhotoDir = File(photosDir, photo.albumId.toString())
                val srcPhoto = sequenceOf(
                    MediaFileSupport.backupMediaFileName(photo.id, fileExtension),
                    MediaFileSupport.backupMediaFileName(photo.id, "jpg")
                ).map { File(srcPhotoDir, it) }
                    .firstOrNull { it.exists() }
                val provisionalPhotoFile = storage.photoFile(targetAlbumId, provisionalPhotoId, fileExtension)
                var photoRestored = false

                if (srcPhoto != null && srcPhoto.exists()) {
                    try {
                        provisionalPhotoFile.parentFile?.mkdirs()
                        srcPhoto.copyTo(provisionalPhotoFile, overwrite = true)
                        photoRestored = true
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to restore photo: %s", photo.id)
                        photosMissing++
                    }
                } else {
                    Timber.w("Source photo not found for backup item: %s", photo.id)
                    photosMissing++
                }

                val srcThumbDir = File(thumbsDir, photo.albumId.toString())
                val srcThumb = File(srcThumbDir, "${photo.id}.jpg")
                val provisionalThumbFile = storage.thumbFile(targetAlbumId, provisionalPhotoId)
                val thumbRestored = if (srcThumb.exists()) {
                    try {
                        provisionalThumbFile.parentFile?.mkdirs()
                        srcThumb.copyTo(provisionalThumbFile, overwrite = true)
                        true
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to restore thumbnail: %s", photo.id)
                        false
                    }
                } else {
                    false
                }

                val localPhotoId = if (matchedPhoto == null) {
                    val insertedId = photoDao.upsert(
                        PhotoEntity(
                            albumId = targetAlbumId,
                            filename = photo.filename,
                            path = if (photoRestored) provisionalPhotoFile.absolutePath else "",
                            thumbPath = if (thumbRestored) provisionalThumbFile.absolutePath else "",
                            width = photo.width,
                            height = photo.height,
                            sizeBytes = if (photoRestored) provisionalPhotoFile.length() else photo.sizeBytes,
                            caption = photo.caption,
                            tags = photo.tags,
                            favorite = photo.favorite,
                            takenAt = photo.takenAt,
                            createdAt = photo.createdAt,
                            updatedAt = photo.updatedAt
                        )
                    )

                    val finalPhotoFile = if (photoRestored) storage.photoFile(targetAlbumId, insertedId, fileExtension) else provisionalPhotoFile
                    if (photoRestored && insertedId > 0L && insertedId != provisionalPhotoId) {
                        if (finalPhotoFile.exists()) {
                            finalPhotoFile.delete()
                        }
                        provisionalPhotoFile.renameTo(finalPhotoFile)
                    }

                    val finalThumbFile = if (thumbRestored) storage.thumbFile(targetAlbumId, insertedId) else provisionalThumbFile
                    if (thumbRestored && insertedId > 0L && insertedId != provisionalPhotoId) {
                        if (finalThumbFile.exists()) {
                            finalThumbFile.delete()
                        }
                        provisionalThumbFile.renameTo(finalThumbFile)
                    }

                    if (insertedId > 0L) {
                        photoDao.getById(insertedId)?.let { inserted ->
                            photoDao.update(
                                inserted.copy(
                                    path = if (photoRestored) finalPhotoFile.absolutePath else inserted.path,
                                    thumbPath = if (thumbRestored) finalThumbFile.absolutePath else inserted.thumbPath,
                                    sizeBytes = if (photoRestored) finalPhotoFile.length() else inserted.sizeBytes
                                )
                            )
                        }
                    }

                    photosInserted++
                    insertedId
                } else {
                    if (photo.updatedAt > matchedPhoto.updatedAt ||
                        matchedPhoto.filename != photo.filename ||
                        matchedPhoto.albumId != targetAlbumId
                    ) {
                        photoDao.update(
                            matchedPhoto.copy(
                                albumId = targetAlbumId,
                                filename = photo.filename,
                                path = when {
                                    photoRestored -> provisionalPhotoFile.absolutePath
                                    matchedPhoto.path.isNotBlank() -> matchedPhoto.path
                                    else -> photo.path
                                },
                                thumbPath = when {
                                    thumbRestored -> provisionalThumbFile.absolutePath
                                    matchedPhoto.thumbPath.isNotBlank() -> matchedPhoto.thumbPath
                                    else -> ""
                                },
                                width = photo.width,
                                height = photo.height,
                                sizeBytes = when {
                                    photoRestored -> provisionalPhotoFile.length()
                                    matchedPhoto.sizeBytes > 0L -> matchedPhoto.sizeBytes
                                    else -> photo.sizeBytes
                                },
                                caption = photo.caption,
                                tags = photo.tags,
                                favorite = photo.favorite,
                                takenAt = photo.takenAt,
                                createdAt = minOf(matchedPhoto.createdAt, photo.createdAt),
                                updatedAt = maxOf(matchedPhoto.updatedAt, photo.updatedAt)
                            )
                        )
                        photosUpdated++
                    }
                    matchedPhoto.id
                }

                if (localPhotoId > 0L) {
                    photoIdMap[photo.id] = localPhotoId
                }
            }

            albumIdMap.forEach { (backupAlbumId, localAlbumId) ->
                val backupAlbum = albumsById[backupAlbumId] ?: return@forEach
                val mappedCoverId = backupAlbum.coverPhotoId?.let { photoIdMap[it] }
                when {
                    mappedCoverId != null -> albumDao.setCover(localAlbumId, mappedCoverId, restoreTimestamp)
                    albumDao.getFirstPhotoId(localAlbumId) == null -> albumDao.setCover(localAlbumId, null, restoreTimestamp)
                }
                albumDao.updateCounts(
                    albumId = localAlbumId,
                    count = photoDao.countInAlbum(localAlbumId),
                    updatedAt = restoreTimestamp
                )
            }

            val result = RestoreResult(
                success = true,
                albumsInserted = albumsInserted,
                albumsUpdated = albumsUpdated,
                photosInserted = photosInserted,
                photosUpdated = photosUpdated,
                photosMissing = photosMissing,
                message = "Restore completed successfully"
            )

            Timber.i(
                "SimpleBackupManager: Restore completed - %s+%s albums, %s+%s photos",
                result.albumsInserted,
                result.albumsUpdated,
                result.photosInserted,
                result.photosUpdated
            )
            result
        } catch (e: Exception) {
            Timber.e(e, "SimpleBackupManager: Restore failed")
            RestoreResult(
                success = false,
                message = "Restore failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    private fun AlbumEntity.toBackupAlbum() = BackupAlbum(
        id = id,
        name = name,
        coverPhotoId = coverPhotoId,
        photoCount = photoCount,
        favorite = favorite,
        emoji = emoji,
        updatedAt = updatedAt,
        parentId = parentId
    )

    private fun listBackupsInternal(): List<BackupInfo> {
        val backupRoot = getDefaultBackupFolder()
        val backups = mutableListOf<BackupInfo>()

        readBackupInfo(
            backupDir = backupRoot,
            backupId = LEGACY_BACKUP_ID,
            displayName = "Legacy backup",
            isLegacy = true
        )?.let(backups::add)

        backupRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { snapshotDir ->
                readBackupInfo(
                    backupDir = snapshotDir,
                    backupId = snapshotDir.name,
                    displayName = snapshotDir.name
                )
            }
            ?.let(backups::addAll)

        return backups
            .sortedByDescending { it.createdAt }
            .mapIndexed { index, backup -> backup.copy(isLatest = index == 0) }
    }

    private fun readBackupInfo(
        backupDir: File,
        backupId: String,
        displayName: String,
        isLegacy: Boolean = false
    ): BackupInfo? {
        val backupFile = File(backupDir, BACKUP_JSON_FILE)
        if (!backupFile.exists()) {
            return null
        }

        return try {
            val root = json.decodeFromString<BackupRoot>(backupFile.readText())
            val mediaDir = File(backupDir, MEDIA_FOLDER)
            val totalSize = if (mediaDir.exists()) {
                mediaDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }

            BackupInfo(
                backupId = backupId,
                displayName = displayName,
                createdAt = root.createdAt,
                albumsCount = root.albums.size,
                photosCount = root.photos.size,
                totalSizeBytes = totalSize + backupFile.length(),
                backupPath = backupDir.absolutePath,
                isLegacy = isLegacy
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to read backup info from %s", backupDir.absolutePath)
            null
        }
    }

    private fun createSnapshotDirectory(backupRootDir: File): File {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        repeat(5) { attempt ->
            val suffix = if (attempt == 0) "" else "_$attempt"
            val candidate = File(backupRootDir, formatter.format(System.currentTimeMillis()) + suffix)
            if (!candidate.exists()) {
                if (candidate.mkdirs()) {
                    return candidate
                }
                throw IllegalStateException("Could not create backup snapshot folder.")
            }
        }
        throw IllegalStateException("Could not allocate a unique backup snapshot folder.")
    }

    private fun orderAlbumsForRestore(
        albums: List<BackupAlbum>,
        albumsById: Map<Long, BackupAlbum>
    ): List<BackupAlbum> {
        fun depthFor(album: BackupAlbum): Int {
            var depth = 0
            var cursor = album.parentId?.let(albumsById::get)
            while (cursor != null) {
                depth++
                cursor = cursor.parentId?.let(albumsById::get)
            }
            return depth
        }

        return albums.sortedWith(
            compareBy<BackupAlbum> { depthFor(it) }
                .thenBy { it.parentId ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
                .thenBy { it.id }
        )
    }

    data class BackupInfo(
        val backupId: String,
        val displayName: String,
        val createdAt: Long,
        val albumsCount: Int,
        val photosCount: Int,
        val totalSizeBytes: Long,
        val backupPath: String,
        val isLegacy: Boolean = false,
        val isLatest: Boolean = false
    )

    data class BackupResult(
        val success: Boolean,
        val albumsCount: Int,
        val photosCount: Int,
        val photosCopied: Int,
        val photosMissing: Int,
        val thumbsCopied: Int,
        val thumbsMissing: Int,
        val backupPath: String,
        val message: String
    )

    data class RestoreResult(
        val success: Boolean,
        val albumsInserted: Int = 0,
        val albumsUpdated: Int = 0,
        val photosInserted: Int = 0,
        val photosUpdated: Int = 0,
        val photosMissing: Int = 0,
        val message: String
    )
}
