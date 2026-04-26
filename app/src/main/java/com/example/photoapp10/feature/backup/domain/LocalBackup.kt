package com.example.photoapp10.feature.backup.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

class LocalBackup(
    private val context: Context,
    private val db: AppDb,
    private val storage: AppStorage,
    private val prefs: UserPrefs
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------- EXPORT ----------

    /**
     * Export selected albums to the given SAF directory Uri.
     * Creates structure:
     *   <chosenDir>/
     *     backup.json
     *     media/photos/{albumId}/{photoId}.jpg
     *     media/thumbs/{albumId}/{photoId}.jpg   (optional)
     */
    suspend fun exportAlbums(targetDir: Uri, albumIds: List<Long>): ExportReport =
        withContext(Dispatchers.IO) {
            Timber.i("Starting backup for albums: $albumIds")
            val cr = context.contentResolver
            val dir = DocumentFile.fromTreeUri(context, targetDir)
                ?: error("Invalid target directory")
            
            // Remove existing backup.json if present
            dir.findFile("backup.json")?.delete()
            val backupDoc = dir.createFile("application/json", "backup.json")
                ?: error("Could not create backup.json")

            // Collect data
            val albums = db.albumDao().run {
                albumIds.mapNotNull { getById(it) }.map { a -> a.toBackupAlbum() }
            }
            val photos = db.photoDao().run {
                albumIds.flatMap { id ->
                    getAllInAlbum(id)
                }.map { p ->
                    val fileExtension = MediaFileSupport.normalizedExtension(p.filename)
                    Timber.d("Found photo in DB: id=${p.id}, path='${p.path}', thumbPath='${p.thumbPath}', filename='${p.filename}'")
                    BackupPhoto(
                        id = p.id,
                        albumId = p.albumId,
                        filename = p.filename,
                        width = p.width,
                        height = p.height,
                        sizeBytes = p.sizeBytes,
                        caption = p.caption,
                        tags = p.tags,
                        favorite = p.favorite,
                        takenAt = p.takenAt,
                        createdAt = p.createdAt,
                        updatedAt = p.updatedAt,
                        path = p.path,
                        thumbPath = p.thumbPath,
                        relativePath = MediaFileSupport.relativeMediaPath(p.albumId, p.id, fileExtension)
                    )
                }
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

            // Write backup.json
            cr.openOutputStream(backupDoc.uri, "w")?.use { 
                it.write(json.encodeToString(root).toByteArray()) 
            } ?: error("Failed to open output stream for backup.json")

            // Copy media
            val mediaDir = ensureDir(dir, "media")
            val photosDir = ensureDir(mediaDir, "photos")
            val thumbsDir = ensureDir(mediaDir, "thumbs")

            var copied = 0
            var missing = 0
            photos.forEach { bp ->
                val fileExtension = MediaFileSupport.normalizedExtension(bp.filename)
                // Use the actual path from PhotoEntity
                val src = File(bp.path)
                Timber.d("Exporting photo: ${bp.id}, src=${src.absolutePath}, exists=${src.exists()}")
                
                val dstParent = ensureDir(photosDir, bp.albumId.toString())
                if (src.exists()) {
                    copyIfExists(
                        cr,
                        src,
                        dstParent,
                        MediaFileSupport.backupMediaFileName(bp.id, fileExtension),
                        MediaFileSupport.mimeTypeForExtension(fileExtension)
                    )?.let { copied++ } ?: run { missing++ }
                } else {
                    Timber.w("Photo file does not exist: ${src.absolutePath}")
                    missing++
                }

                // Use the actual thumbPath from PhotoEntity
                val thumb = File(bp.thumbPath)
                Timber.d("Exporting thumbnail: ${bp.id}, src=${thumb.absolutePath}, exists=${thumb.exists()}")
                
                val dstThumbParent = ensureDir(thumbsDir, bp.albumId.toString())
                if (thumb.exists()) {
                    copyIfExists(cr, thumb, dstThumbParent, "${bp.id}.jpg", "image/jpeg")
                } else {
                    Timber.w("Thumbnail file does not exist: ${thumb.absolutePath}")
                }
            }

            ExportReport(
                albums = albums.size,
                photos = photos.size,
                filesCopied = copied,
                filesMissing = missing,
                backupJsonUri = backupDoc.uri
            ).also {
                // Log final summary
                Timber.i("Backup completed: albums=${it.albums}, photos=${it.photos}, copied=${it.filesCopied}, missing=${it.filesMissing}")
            }
        }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
            ?: error("Cannot create directory: $name")
    }

    private fun copyIfExists(
        cr: ContentResolver,
        src: File,
        dstDir: DocumentFile,
        dstName: String,
        mimeType: String
    ): Uri? {
        if (!src.exists()) {
            Timber.w("Source file does not exist: ${src.absolutePath}")
            return null
        }
        
        val dst = dstDir.createFile(mimeType, dstName) ?: run {
            Timber.e("Failed to create destination file: $dstName")
            return null
        }
        
        return try {
            cr.openOutputStream(dst.uri)?.use { out ->
                src.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            Timber.d("Successfully copied: ${src.absolutePath} -> ${dst.uri}")
            dst.uri
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy file: ${src.absolutePath} -> ${dst.uri}")
            // Try to delete the failed destination file
            try {
                dst.delete()
            } catch (deleteException: Exception) {
                Timber.e(deleteException, "Failed to delete failed destination file: ${dst.uri}")
            }
            null
        }
    }

    private fun AlbumEntity.toBackupAlbum() = BackupAlbum(
        id = id, 
        name = name, 
        coverPhotoId = coverPhotoId,
        photoCount = photoCount, 
        favorite = favorite,
        emoji = emoji,
        updatedAt = updatedAt
    )

    // ---------- IMPORT ----------

    data class ImportOptions(val mode: Mode) {
        enum class Mode { MERGE_LATEST_WINS, REPLACE_ALL }
    }

    data class ImportReport(
        val albumsInserted: Int,
        val albumsUpdated: Int,
        val photosInserted: Int,
        val photosUpdated: Int,
        val photosSkippedMissingFile: Int
    )

    suspend fun importFromFolder(folderUri: Uri, options: ImportOptions): ImportReport =
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(context, folderUri)
                ?: error("Invalid folder")
            val backupDoc = dir.findFile("backup.json") ?: error("backup.json not found")
            val root = context.contentResolver.openInputStream(backupDoc.uri)?.use {
                json.decodeFromString<BackupRoot>(String(it.readBytes()))
            } ?: error("Failed to open backup.json")
            require(root.schemaVersion == BACKUP_SCHEMA_VERSION) { "Unsupported backup schema." }

            if (options.mode == ImportOptions.Mode.REPLACE_ALL) {
                db.clearAllTables()
            }

            var albumsInserted = 0
            var albumsUpdated = 0
            var photosInserted = 0
            var photosUpdated = 0
            var photosMissing = 0

            // Upsert albums
            val albumDao = db.albumDao()
            root.albums.forEach { ba ->
                val existing = albumDao.getById(ba.id)
                if (existing == null) {
                    albumDao.upsert(
                        AlbumEntity(
                            id = ba.id,
                            name = ba.name,
                            coverPhotoId = ba.coverPhotoId,
                            photoCount = ba.photoCount,
                            favorite = ba.favorite,
                            emoji = ba.emoji,
                            updatedAt = ba.updatedAt
                        )
                    )
                    albumsInserted++
                } else {
                    if (ba.updatedAt > existing.updatedAt || options.mode == ImportOptions.Mode.REPLACE_ALL) {
                        albumDao.update(existing.copy(
                            name = ba.name,
                            coverPhotoId = ba.coverPhotoId,
                            photoCount = ba.photoCount,
                            favorite = ba.favorite,
                            emoji = ba.emoji,
                            updatedAt = ba.updatedAt
                        ))
                        albumsUpdated++
                    }
                }
            }

            // Upsert photos + copy files
            val photoDao = db.photoDao()
            val media = dir.findFile("media")
            val photosDir = media?.findFile("photos")
            
            Timber.d("Import: media dir exists=${media != null}, photos dir exists=${photosDir != null}")

            root.photos.forEach { bp ->
                val fileExtension = MediaFileSupport.normalizedExtension(bp.filename)
                // copy original file back into app-private storage
                val albumDir = photosDir?.findFile(bp.albumId.toString())
                val srcDoc = sequenceOf(
                    MediaFileSupport.backupMediaFileName(bp.id, fileExtension),
                    MediaFileSupport.backupMediaFileName(bp.id, "jpg")
                ).mapNotNull { fileName ->
                    albumDir?.findFile(fileName)?.takeIf { it.exists() }
                }.firstOrNull()
                val dst = storage.photoFile(bp.albumId, bp.id, fileExtension)
                var filePresent = false
                
                Timber.d("Importing photo: ${bp.id}, albumDir exists=${albumDir != null}, srcDoc exists=${srcDoc?.exists()}")
                
                if (srcDoc != null && srcDoc.exists()) {
                    try {
                        context.contentResolver.openInputStream(srcDoc.uri)?.use { input ->
                            dst.parentFile?.mkdirs()
                            dst.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        filePresent = true
                        Timber.d("Successfully restored photo: ${bp.id} -> ${dst.absolutePath}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to restore photo: ${bp.id}")
                        photosMissing++
                    }
                } else {
                    Timber.w("Source photo not found in backup: ${bp.id}")
                    photosMissing++
                }

                // Also restore thumbnail if it exists in backup
                val thumbsDir = media?.findFile("thumbs")
                val thumbAlbumDir = thumbsDir?.findFile(bp.albumId.toString())
                val thumbSrcDoc = thumbAlbumDir?.findFile("${bp.id}.jpg")
                val thumbDst = storage.thumbFile(bp.albumId, bp.id)
                var thumbPresent = false
                
                if (thumbSrcDoc != null && thumbSrcDoc.exists()) {
                    try {
                        context.contentResolver.openInputStream(thumbSrcDoc.uri)?.use { input ->
                            thumbDst.parentFile?.mkdirs()
                            thumbDst.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        thumbPresent = true
                        Timber.d("Successfully restored thumbnail: ${bp.id} -> ${thumbDst.absolutePath}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to restore thumbnail: ${bp.id}")
                    }
                } else {
                    Timber.w("Source thumbnail not found in backup: ${bp.id}")
                }

                val existing = photoDao.getById(bp.id)
                if (existing == null) {
                    photoDao.upsert(
                        PhotoEntity(
                            id = bp.id,
                            albumId = bp.albumId,
                            filename = bp.filename,
                            path = dst.absolutePath,
                            thumbPath = if (thumbPresent) thumbDst.absolutePath else "",
                            width = bp.width,
                            height = bp.height,
                            sizeBytes = if (filePresent) dst.length() else bp.sizeBytes,
                            caption = bp.caption,
                            tags = bp.tags,
                            favorite = bp.favorite,
                            takenAt = bp.takenAt,
                            createdAt = bp.createdAt,
                            updatedAt = bp.updatedAt
                        )
                    )
                    photosInserted++
                } else {
                    if (bp.updatedAt > existing.updatedAt || options.mode == ImportOptions.Mode.REPLACE_ALL) {
                        photoDao.update(existing.copy(
                            albumId = bp.albumId,
                            filename = bp.filename,
                            path = dst.absolutePath,
                            thumbPath = if (thumbPresent) thumbDst.absolutePath else existing.thumbPath,
                            width = bp.width,
                            height = bp.height,
                            sizeBytes = if (filePresent) dst.length() else existing.sizeBytes,
                            caption = bp.caption,
                            tags = bp.tags,
                            favorite = bp.favorite,
                            takenAt = bp.takenAt,
                            createdAt = bp.createdAt,
                            updatedAt = bp.updatedAt
                        ))
                        photosUpdated++
                    }
                }
            }

            ImportReport(
                albumsInserted, albumsUpdated,
                photosInserted, photosUpdated,
                photosMissing
            ).also {
                // Log final summary
                Timber.i("Import completed: albums+${it.albumsInserted}/${it.albumsUpdated}, photos+${it.photosInserted}/${it.photosUpdated}, missing=${it.photosSkippedMissingFile}")
            }
        }
}
