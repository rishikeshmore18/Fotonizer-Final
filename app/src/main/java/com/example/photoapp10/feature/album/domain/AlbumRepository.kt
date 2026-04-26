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
    
    // Observe albums by parent (for nested folders)
    fun observeAlbumsByParent(parentId: Long, sort: SortMode = SortMode.DATE_NEW): Flow<List<AlbumEntity>> = when (sort) {
        SortMode.DATE_NEW -> albumDao.observeAlbumsByParentDateNew(parentId)
        SortMode.DATE_OLD -> albumDao.observeAlbumsByParentDateOld(parentId)
        SortMode.NAME_ASC -> albumDao.observeAlbumsByParentNameAsc(parentId)
        SortMode.NAME_DESC -> albumDao.observeAlbumsByParentNameDesc(parentId)
        SortMode.FAV_FIRST -> albumDao.observeAlbumsByParentDateNew(parentId)
    }
    
    suspend fun getChildAlbumsCount(parentId: Long): Int = albumDao.getChildAlbumsCount(parentId)
    
    suspend fun hasChildAlbums(albumId: Long): Boolean = albumDao.hasChildAlbums(albumId)
    
    suspend fun getAlbumById(albumId: Long): AlbumEntity? = albumDao.getById(albumId)

    fun observeAllAlbums(): Flow<List<AlbumEntity>> = albumDao.observeAllAlbums()
    
    suspend fun getBreadcrumbPath(albumId: Long): List<AlbumEntity> {
        val path = mutableListOf<AlbumEntity>()
        var current: AlbumEntity? = albumDao.getById(albumId)
        while (current != null) {
            path.add(0, current) // Add to front to maintain order
            current = current.parentId?.let { albumDao.getById(it) }
        }
        return path
    }

    suspend fun createAlbum(name: String, parentId: Long? = null): Long {
        // Prevent nesting the "default" album
        if (name.trim().lowercase() == "default") {
            throw IllegalArgumentException("Cannot create nested 'default' album")
        }
        
        // Prevent creating albums inside the default album
        if (parentId != null) {
            val parent = albumDao.getById(parentId)
            if (parent?.name?.lowercase() == "default") {
                throw IllegalArgumentException("Cannot create albums inside 'default' album")
            }
        }
        
        val now = System.currentTimeMillis()
        val uniqueName = generateUniqueAlbumName(name.trim(), parentId)
        val id = albumDao.upsert(
            AlbumEntity(name = uniqueName, updatedAt = now, parentId = parentId)
        )
        syncManager?.requestSync("createAlbum")
        archiveManager?.requestArchive("createAlbum")
        return id
    }
    
    /**
     * Generates a unique album name by appending a counter suffix if duplicates exist
     * Example: "My Album" -> "My Album(1)" -> "My Album(2)" etc.
     * @param parentId If provided, checks uniqueness within that parent folder
     */
    private suspend fun generateUniqueAlbumName(baseName: String, parentId: Long?): String {
        // Check if the base name is already unique within the parent
        if (albumDao.getByNameAndParent(baseName, parentId) == null) {
            return baseName
        }
        
        // Find the next available counter
        var counter = 1
        var candidateName: String
        do {
            candidateName = "$baseName($counter)"
            counter++
        } while (albumDao.getByNameAndParent(candidateName, parentId) != null)
        
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
        // Check if album has child albums - if so, delete them recursively
        val childAlbums = albumDao.getChildAlbums(entity.id)
        
        // Delete child albums first (cascade)
        childAlbums.forEach { child ->
            deleteAlbum(child)
        }
        
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
        val firstPhotoId = findRepresentativeCoverPhotoId(albumId)
        albumDao.setCover(albumId, firstPhotoId, System.currentTimeMillis())
    }

    private suspend fun findRepresentativeCoverPhotoId(albumId: Long): Long? {
        albumDao.getFirstPhotoId(albumId)?.let { return it }

        val childAlbums = albumDao.getChildAlbums(albumId)
        childAlbums.forEach { child ->
            val childCoverId = findRepresentativeCoverPhotoId(child.id)
            if (childCoverId != null) {
                return childCoverId
            }
        }

        return null
    }
}
