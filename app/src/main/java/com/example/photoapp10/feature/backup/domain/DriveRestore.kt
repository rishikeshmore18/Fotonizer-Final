package com.example.photoapp10.feature.backup.domain

import android.content.Context
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.backup.drive.DriveAppData
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.photo.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class DriveRestore(
    private val context: Context,
    private val drive: DriveAppData
) {
    private val db = Modules.provideDb(context)
    private val storage = Modules.provideStorage(context)
    private val thumbnailer = Modules.provideThumbnailer()
    private val json = Json { ignoreUnknownKeys = true }

    enum class Mode { MERGE_LATEST_WINS, REPLACE_ALL }

    data class Progress(val step: String, val done: Int, val total: Int)

    /** Returns number of albums/photos restored; throws on fatal errors. */
    suspend fun restoreLatest(
        mode: Mode,
        onProgress: (Progress) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            Timber.d("DriveRestore: Starting restore with mode $mode")
            onProgress(Progress("Checking for backup...", 0, 1))
            
            val latest = drive.findLatestBackup()
            if (latest == null) {
                Timber.w("DriveRestore: No backup found")
                return@withContext 0 to 0
            }

            onProgress(Progress("Downloading backup...", 0, 1))
            
            // 1) Download backup.json to cache
            val tmp = File(context.cacheDir, "restore/backup.json").apply { 
                parentFile?.mkdirs() 
            }
            drive.download(latest.id, tmp)

            onProgress(Progress("Parsing backup data...", 0, 1))
            
            // 2) Parse
            val backupText = tmp.readText()
            Timber.d("DriveRestore: Backup file size: ${backupText.length} characters")
            
            val root = json.decodeFromString(BackupRoot.serializer(), backupText)
            Timber.d("DriveRestore: Parsed backup with ${root.albums.size} albums and ${root.photos.size} photos")

            // 3) Replace all? (clear DB before import)
            if (mode == Mode.REPLACE_ALL) {
                Timber.d("DriveRestore: Clearing all tables for REPLACE_ALL mode")
                onProgress(Progress("Clearing existing data...", 0, 1))
                db.clearAllTables()
            }

            // 4) Upsert albums
            onProgress(Progress("Restoring albums...", 0, root.albums.size))
            val albumDao = db.albumDao()
            var aIns = 0
            var aUpd = 0
            
            root.albums.forEachIndexed { index, ba ->
                try {
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
                        aIns++
                        Timber.d("DriveRestore: Inserted album '${ba.name}' (${ba.id})")
                    } else if (ba.updatedAt > existing.updatedAt || mode == Mode.REPLACE_ALL) {
                        albumDao.update(existing.copy(
                            name = ba.name, 
                            coverPhotoId = ba.coverPhotoId,
                            photoCount = ba.photoCount, 
                            favorite = ba.favorite,
                            emoji = ba.emoji,
                            updatedAt = ba.updatedAt
                        ))
                        aUpd++
                        Timber.d("DriveRestore: Updated album '${ba.name}' (${ba.id})")
                    } else {
                        Timber.d("DriveRestore: Skipped album '${ba.name}' (${ba.id}) - not newer")
                    }
                    
                    onProgress(Progress("Restoring albums...", index + 1, root.albums.size))
                } catch (e: Exception) {
                    Timber.e(e, "DriveRestore: Failed to restore album ${ba.id}")
                    // Continue with other albums
                }
            }

            // 5) Upsert photos and download actual photo files
            val photoDao = db.photoDao()
            var pIns = 0
            var pUpd = 0
            var downloadedOk = 0
            var missing = 0
            val total = root.photos.size
            
            root.photos.forEachIndexed { index, bp ->
                try {
                    val existing = photoDao.getById(bp.id)
                    
                    // Smart restore: Skip if already backed up and up to date
                    if (existing != null && 
                        existing.backedUpAt > 0 && 
                        existing.updatedAt >= bp.updatedAt) {
                        Timber.d("DriveRestore: Skipping photo ${bp.id} - already up to date")
                        onProgress(Progress("Restoring photos & downloading files", index + 1, total))
                        return@forEachIndexed
                    }
                    
                    val dst = storage.photoFile(bp.albumId, bp.id)
                    
                    // Download the actual photo file from Drive
                    val photoDownloaded = downloadPhotoFile(bp.albumId, bp.id, dst)
                    if (!photoDownloaded) {
                        missing++
                        Timber.w("DriveRestore: missing remote file for photo ${bp.id}")
                        // Skip DB upsert entirely if download failed
                        onProgress(Progress("Restoring photos & downloading files", index + 1, total))
                        return@forEachIndexed
                    } else {
                        downloadedOk++
                        Timber.d("DriveRestore: Successfully downloaded photo file: ${dst.absolutePath}")
                        
                        // Immediately regenerate thumbnail after successful download
                        try {
                            val thumbFile = storage.thumbFile(bp.albumId, bp.id)
                            val thumbResult = thumbnailer.generate(dst, thumbFile)
                            Timber.d("DriveRestore: Generated thumbnail for photo ${bp.id}: ${thumbResult.path}")
                        } catch (e: Exception) {
                            Timber.e(e, "DriveRestore: Error generating thumbnail for photo ${bp.id}")
                        }
                    }
                    
                    if (existing == null) {
                        photoDao.upsert(
                            PhotoEntity(
                                id = bp.id,
                                albumId = bp.albumId,
                                filename = bp.filename,
                                path = dst.absolutePath, // Now points to downloaded file
                                thumbPath = storage.thumbFile(bp.albumId, bp.id).absolutePath, // Thumbnail path
                                width = bp.width,
                                height = bp.height,
                                sizeBytes = bp.sizeBytes,
                                caption = bp.caption,
                                tags = bp.tags,
                                favorite = bp.favorite,
                                takenAt = bp.takenAt,
                                createdAt = bp.createdAt,
                                updatedAt = bp.updatedAt
                            )
                        )
                        pIns++
                        Timber.d("DriveRestore: Inserted photo '${bp.filename}' (${bp.id})")
                    } else if (bp.updatedAt > existing.updatedAt || mode == Mode.REPLACE_ALL) {
                        photoDao.update(existing.copy(
                            albumId = bp.albumId,
                            filename = bp.filename,
                            path = dst.absolutePath, // Now points to downloaded file
                            thumbPath = storage.thumbFile(bp.albumId, bp.id).absolutePath, // Thumbnail path
                            width = bp.width,
                            height = bp.height,
                            sizeBytes = bp.sizeBytes,
                            caption = bp.caption,
                            tags = bp.tags,
                            favorite = bp.favorite,
                            takenAt = bp.takenAt,
                            updatedAt = bp.updatedAt
                        ))
                        pUpd++
                        Timber.d("DriveRestore: Updated photo '${bp.filename}' (${bp.id})")
                    } else {
                        Timber.d("DriveRestore: Skipped photo '${bp.filename}' (${bp.id}) - not newer")
                    }
                    
                    // Mark photo as backed up after successful restore
                    if (photoDownloaded) {
                        photoDao.markAsBackedUp(bp.id, System.currentTimeMillis())
                    }
                    
                    onProgress(Progress("Restoring photos & downloading files", index + 1, total))
                } catch (e: Exception) {
                    Timber.e(e, "DriveRestore: Failed to restore photo ${bp.id}")
                    missing++
                    // Continue with other photos
                }
            }

            // 6) Update album counts to stay consistent
            try {
                val albumDao = db.albumDao()
                root.albums.forEach { album ->
                    val photoCount = root.photos.count { it.albumId == album.id }
                    albumDao.updateCounts(album.id, photoCount, System.currentTimeMillis())
                }
                Timber.d("DriveRestore: Updated album photo counts")
                
                // Mark all albums as backed up after successful restore
                albumDao.markAllAsBackedUp(System.currentTimeMillis())
            } catch (e: Exception) {
                Timber.e(e, "DriveRestore: Failed to update album counts")
            }

            // Clean up temp file
            tmp.delete()

            val totalAlbums = aIns + aUpd
            val totalPhotos = pIns + pUpd
            
            // Log comprehensive summary
            Timber.i("DriveRestore: downloadedOk=$downloadedOk missing=$missing")
            Timber.i("DriveRestore: Completed - Albums: $totalAlbums ($aIns new, $aUpd updated), Photos: $totalPhotos ($pIns new, $pUpd updated)")
            
            // Return counts (albums restored/updated not strictly required; we report photo count as progress)
            totalAlbums to totalPhotos
        } catch (e: Exception) {
            Timber.e(e, "DriveRestore: Fatal error during restore")
            throw e
        }
    }

    /** Download a photo file from Drive appDataFolder to local storage */
    private suspend fun downloadPhotoFile(albumId: Long, photoId: Long, dst: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("DriveRestore: Looking for photo file in Drive: photos/$albumId/$photoId.jpg")
            
            // Search for the photo file in Drive appDataFolder
            val listRequest = drive.drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'photos/$albumId/$photoId.jpg' and trashed = false")
                .setFields("files(id,name)")
                .setPageSize(1)

            val fileList = listRequest.execute()
            val files = fileList.files

            if (files.isNullOrEmpty()) {
                Timber.w("DriveRestore: Photo file not found in Drive: photos/$albumId/$photoId.jpg")
                return@withContext false
            }

            val driveFile = files[0]
            Timber.d("DriveRestore: Found photo file in Drive: ${driveFile.name} (${driveFile.id})")

            // Download the file
            val success = drive.download(driveFile.id, dst)
            if (success && dst.exists()) {
                Timber.d("DriveRestore: Successfully downloaded photo: ${dst.absolutePath} (${dst.length()} bytes)")
                return@withContext true
            } else {
                Timber.e("DriveRestore: Failed to download photo file or file doesn't exist after download")
                return@withContext false
            }

        } catch (e: Exception) {
            Timber.e(e, "DriveRestore: Error downloading photo file for album $albumId, photo $photoId")
            return@withContext false
        }
    }
}
