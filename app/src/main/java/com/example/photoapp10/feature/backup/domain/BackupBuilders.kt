package com.example.photoapp10.feature.backup.domain

import com.example.photoapp10.core.db.AppDb
import timber.log.Timber

object BackupBuilders {
    suspend fun backupRootFromDb(db: AppDb): BackupRoot {
        return try {
            Timber.d("BackupBuilders: Building backup from database")
            
            val albums = db.albumDao().getAllOnce().map { a ->
                BackupAlbum(
                    id = a.id,
                    name = a.name,
                    coverPhotoId = a.coverPhotoId,
                    photoCount = a.photoCount,
                    favorite = a.favorite,
                    emoji = a.emoji,
                    updatedAt = a.updatedAt
                )
            }
            
            val photos = db.photoDao().getAllOnce().map { p ->
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
                    relativePath = "photos/${p.albumId}/${p.id}.jpg"
                )
            }
            
            val settings = BackupSettings(
                themeMode = "system",
                defaultSort = "date_new",
                lastSearch = ""
            )
            
            val root = BackupRoot(
                createdAt = System.currentTimeMillis(),
                settings = settings,
                albums = albums,
                photos = photos
            )
            
            Timber.d("BackupBuilders: Created backup with ${albums.size} albums and ${photos.size} photos")
            return root
            
        } catch (e: Exception) {
            Timber.e(e, "BackupBuilders: Failed to build backup from database")
            throw e
        }
    }
}
