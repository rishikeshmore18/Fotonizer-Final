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

    @Query("SELECT * FROM albums ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, updatedAt DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>
    
    // Album sort methods
    @Query("SELECT * FROM albums ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, id DESC")
    fun observeAlbumsDateNew(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, id ASC")
    fun observeAlbumsDateOld(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, name ASC")
    fun observeAlbumsNameAsc(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums ORDER BY CASE WHEN name = 'default' THEN 0 ELSE 1 END, favorite DESC, name DESC")
    fun observeAlbumsNameDesc(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getById(albumId: Long): AlbumEntity?

    @Query("SELECT * FROM albums")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): AlbumEntity?

    @Query("UPDATE albums SET photoCount = :count, updatedAt = :updatedAt WHERE id = :albumId")
    suspend fun updateCounts(albumId: Long, count: Int, updatedAt: Long)

    @Query("UPDATE albums SET coverPhotoId = :photoId, updatedAt = :updatedAt WHERE id = :albumId")
    suspend fun setCover(albumId: Long, photoId: Long?, updatedAt: Long)

    // Search albums by name and emoji
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

