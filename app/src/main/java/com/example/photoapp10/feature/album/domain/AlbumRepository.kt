package com.example.photoapp10.feature.album.domain

import com.example.photoapp10.feature.album.data.AlbumDao
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.backup.DriveSyncManager
import com.example.photoapp10.feature.backup.domain.RealTimeArchiveManager
import com.example.photoapp10.feature.photo.domain.SortMode
import kotlinx.coroutines.flow.Flow

class AlbumRepository(
    private val albumDao: AlbumDao,
    private val syncManager: DriveSyncManager? = null,
    private val archiveManager: RealTimeArchiveManager? = null
) {
    fun observeAlbums(): Flow<List<AlbumEntity>> = albumDao.observeAlbums()
    
    fun observeAlbums(sort: SortMode): Flow<List<AlbumEntity>> = when (sort) {
        SortMode.DATE_NEW -> {
            timber.log.Timber.d("AlbumRepository: Using DATE_NEW sort (id DESC - newest first)")
            albumDao.observeAlbumsDateNew()
        }
        SortMode.DATE_OLD -> {
            timber.log.Timber.d("AlbumRepository: Using DATE_OLD sort (id ASC - oldest first)")
            albumDao.observeAlbumsDateOld()
        }
        SortMode.NAME_ASC -> {
            timber.log.Timber.d("AlbumRepository: Using NAME_ASC sort (name ASC)")
            albumDao.observeAlbumsNameAsc()
        }
        SortMode.NAME_DESC -> {
            timber.log.Timber.d("AlbumRepository: Using NAME_DESC sort (name DESC)")
            albumDao.observeAlbumsNameDesc()
        }
        SortMode.FAV_FIRST -> {
            timber.log.Timber.d("AlbumRepository: Using FAV_FIRST sort (favorites first)")
            albumDao.observeAlbumsDateNew() // Favorites first is already handled by ORDER BY favorite DESC
        }
    }

    suspend fun createAlbum(name: String): Long {
        val now = System.currentTimeMillis()
        val uniqueName = generateUniqueAlbumName(name.trim())
        val id = albumDao.upsert(
            AlbumEntity(name = uniqueName, updatedAt = now)
        )
        syncManager?.requestSync("createAlbum")
        archiveManager?.requestArchive("createAlbum")
        return id
    }
    
    /**
     * Generates a unique album name by appending a counter suffix if duplicates exist
     * Example: "My Album" -> "My Album(1)" -> "My Album(2)" etc.
     */
    private suspend fun generateUniqueAlbumName(baseName: String): String {
        // Check if the base name is already unique
        if (albumDao.getByName(baseName) == null) {
            return baseName
        }
        
        // Find the next available counter
        var counter = 1
        var candidateName: String
        do {
            candidateName = "$baseName($counter)"
            counter++
        } while (albumDao.getByName(candidateName) != null)
        
        return candidateName
    }

    suspend fun findOrCreateDefaultAlbum(): Long {
        val existingDefault = albumDao.getByName("default")
        return if (existingDefault != null) {
            existingDefault.id
        } else {
            val now = System.currentTimeMillis()
            albumDao.upsert(
                AlbumEntity(name = "default", updatedAt = now)
            )
        }
    }

    suspend fun renameAlbum(albumId: Long, newName: String) {
        val existing = albumDao.getById(albumId) ?: return
        albumDao.update(existing.copy(name = newName.trim(), updatedAt = System.currentTimeMillis()))
        syncManager?.requestSync("renameAlbum")
        archiveManager?.requestArchive("renameAlbum")
    }

    suspend fun deleteAlbum(entity: AlbumEntity) {
        // Cascade will delete photos due to FK; file cleanup handled at higher layer if needed
        albumDao.delete(entity)
        syncManager?.requestSync("deleteAlbum")
        archiveManager?.requestArchive("deleteAlbum")
    }

    suspend fun setCover(albumId: Long, photoId: Long?) {
        albumDao.setCover(albumId, photoId, System.currentTimeMillis())
        syncManager?.requestSync("setCover")
        archiveManager?.requestArchive("setCover")
    }

    suspend fun updateCounts(albumId: Long, count: Int) {
        albumDao.updateCounts(albumId, count, System.currentTimeMillis())
    }

    fun searchAlbums(query: String): Flow<List<AlbumEntity>> = albumDao.searchAlbums(query)

    suspend fun toggleFavorite(albumId: Long) {
        val existing = albumDao.getById(albumId) ?: return
        albumDao.setFavorite(albumId, !existing.favorite, System.currentTimeMillis())
        syncManager?.requestSync("toggleFavorite")
        archiveManager?.requestArchive("toggleFavorite")
    }

    suspend fun setEmoji(albumId: Long, emoji: String?) {
        albumDao.setEmoji(albumId, emoji, System.currentTimeMillis())
        syncManager?.requestSync("setEmoji")
        archiveManager?.requestArchive("setEmoji")
    }

    suspend fun updateCoverFromFirstPhoto(albumId: Long) {
        val firstPhotoId = albumDao.getFirstPhotoId(albumId)
        albumDao.setCover(albumId, firstPhotoId, System.currentTimeMillis())
    }
}

