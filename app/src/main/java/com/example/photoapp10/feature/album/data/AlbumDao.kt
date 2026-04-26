package com.example.photoapp10.feature.album.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Delete
    suspend fun delete(album: AlbumEntity)

    // Root albums only (parentId IS NULL) - for home screen
    @Query("SELECT * FROM albums WHERE parentId IS NULL ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, updatedAt DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>
    
    // Album sort methods - root albums only
    @Query("SELECT * FROM albums WHERE parentId IS NULL ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, id DESC")
    fun observeAlbumsDateNew(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId IS NULL ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, id ASC")
    fun observeAlbumsDateOld(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId IS NULL ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, name ASC")
    fun observeAlbumsNameAsc(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId IS NULL ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, name DESC")
    fun observeAlbumsNameDesc(): Flow<List<AlbumEntity>>
    
    // Get albums by parent ID (for nested folders)
    @Query("SELECT * FROM albums WHERE parentId = :parentId ORDER BY favorite DESC, updatedAt DESC")
    fun observeAlbumsByParent(parentId: Long): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId = :parentId ORDER BY favorite DESC, id DESC")
    fun observeAlbumsByParentDateNew(parentId: Long): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId = :parentId ORDER BY favorite DESC, id ASC")
    fun observeAlbumsByParentDateOld(parentId: Long): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId = :parentId ORDER BY favorite DESC, name ASC")
    fun observeAlbumsByParentNameAsc(parentId: Long): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE parentId = :parentId ORDER BY favorite DESC, name DESC")
    fun observeAlbumsByParentNameDesc(parentId: Long): Flow<List<AlbumEntity>>
    
    // Get child albums count
    @Query("SELECT COUNT(*) FROM albums WHERE parentId = :parentId")
    suspend fun getChildAlbumsCount(parentId: Long): Int
    
    // Check if album has child albums
    @Query("SELECT COUNT(*) > 0 FROM albums WHERE parentId = :albumId")
    suspend fun hasChildAlbums(albumId: Long): Boolean
    
    // Get all child albums synchronously (for deletion)
    @Query("SELECT * FROM albums WHERE parentId = :parentId")
    suspend fun getChildAlbums(parentId: Long): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getById(albumId: Long): AlbumEntity?

    @Query("SELECT * FROM albums")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY updatedAt DESC")
    fun observeAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE name = :name AND parentId IS NULL LIMIT 1")
    suspend fun getByName(name: String): AlbumEntity?
    
    @Query("SELECT * FROM albums WHERE name = :name AND parentId = :parentId LIMIT 1")
    suspend fun getByNameAndParent(name: String, parentId: Long?): AlbumEntity?

    @Query("UPDATE albums SET photoCount = :count, updatedAt = :updatedAt WHERE id = :albumId")
    suspend fun updateCounts(albumId: Long, count: Int, updatedAt: Long)

    @Query("UPDATE albums SET coverPhotoId = :photoId, updatedAt = :updatedAt WHERE id = :albumId")
    suspend fun setCover(albumId: Long, photoId: Long?, updatedAt: Long)

    // Search albums by name and emoji across the full hierarchy
    @Query("SELECT * FROM albums WHERE name LIKE :q OR emoji LIKE :q ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, updatedAt DESC")
    fun searchAlbums(q: String): Flow<List<AlbumEntity>>

    @Query("UPDATE albums SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :albumId")
    suspend fun setFavorite(albumId: Long, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE albums SET emoji = :emoji, updatedAt = :updatedAt WHERE id = :albumId")
    suspend fun setEmoji(albumId: Long, emoji: String?, updatedAt: Long)

    // Get first photo from album for cover
    @Query("SELECT id FROM photos WHERE albumId = :albumId ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirstPhotoId(albumId: Long): Long?

    @Query("DELETE FROM albums")
    suspend fun deleteAll()

    // Backup support - get all albums at once
    @Query("SELECT * FROM albums ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<AlbumEntity>

    // Backup status methods
    @Query("UPDATE albums SET backedUpAt = :timestamp")
    suspend fun markAllAsBackedUp(timestamp: Long)

    @Query("UPDATE albums SET backedUpAt = 0 WHERE id = :albumId")
    suspend fun markAlbumAsNotBackedUp(albumId: Long)
}
