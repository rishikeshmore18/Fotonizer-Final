package com.example.photoapp10.feature.photo.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deleteById(photoId: Long)

    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getById(photoId: Long): PhotoEntity?

    @Query(
        "SELECT * FROM photos " +
            "WHERE albumId = :albumId AND filename = :filename AND takenAt = :takenAt AND sizeBytes = :sizeBytes " +
            "LIMIT 1"
    )
    suspend fun findByNaturalKey(
        albumId: Long,
        filename: String,
        takenAt: Long,
        sizeBytes: Long
    ): PhotoEntity?

    @Query("SELECT * FROM photos WHERE albumId = :albumId AND filename = :filename LIMIT 1")
    suspend fun findByAlbumAndFilename(albumId: Long, filename: String): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE albumId = :albumId")
    suspend fun countInAlbum(albumId: Long): Int

    @Query("SELECT * FROM photos WHERE albumId = :albumId")
    suspend fun getAllInAlbum(albumId: Long): List<PhotoEntity>

    // Update thumbnail metadata
    @Query("UPDATE photos SET thumbPath = :thumbPath, width = :width, height = :height, updatedAt = :updatedAt WHERE id = :photoId")
    suspend fun updateThumbMeta(photoId: Long, thumbPath: String, width: Int, height: Int, updatedAt: Long)

    // Observe photos with different sort orders
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY filename ASC")
    fun observePhotosNameAsc(albumId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY filename DESC")
    fun observePhotosNameDesc(albumId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY createdAt DESC")
    fun observePhotosDateNew(albumId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY createdAt ASC")
    fun observePhotosDateOld(albumId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY favorite DESC, createdAt DESC")
    fun observePhotosFavFirst(albumId: Long): Flow<List<PhotoEntity>>

    // Paging sources for different sort orders
    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY filename ASC")
    fun pagingNameAsc(albumId: Long): PagingSource<Int, PhotoEntity>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY filename DESC")
    fun pagingNameDesc(albumId: Long): PagingSource<Int, PhotoEntity>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY createdAt DESC")
    fun pagingDateNew(albumId: Long): PagingSource<Int, PhotoEntity>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY createdAt ASC")
    fun pagingDateOld(albumId: Long): PagingSource<Int, PhotoEntity>

    @Query("SELECT * FROM photos")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE albumId = :albumId ORDER BY favorite DESC, createdAt DESC")
    fun pagingFavFirst(albumId: Long): PagingSource<Int, PhotoEntity>

    // Search methods
    @Query("SELECT * FROM photos WHERE filename LIKE :query OR caption LIKE :query")
    fun searchByFilenameOrCaption(query: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId = :albumId AND (filename LIKE :query OR caption LIKE :query)")
    fun searchInAlbum(albumId: Long, query: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE albumId IN (:albumIds) AND (filename LIKE :query OR caption LIKE :query)")
    fun searchInAlbums(albumIds: List<Long>, query: String): Flow<List<PhotoEntity>>

    // Favorites and recents
    @Query("SELECT * FROM photos WHERE favorite = 1 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeFavorites(limit: Int): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecents(limit: Int): Flow<List<PhotoEntity>>

    @Query("DELETE FROM photos")
    suspend fun deleteAll()

    // Backup support - get all photos at once
    @Query("SELECT * FROM photos ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun getChangedSince(since: Long): List<PhotoEntity>

    // Backup status methods
    @Query("SELECT * FROM photos WHERE backedUpAt = 0 ORDER BY updatedAt ASC")
    suspend fun getUnbackedUpPhotos(): List<PhotoEntity>

    @Query("UPDATE photos SET backedUpAt = :timestamp WHERE id = :photoId")
    suspend fun markAsBackedUp(photoId: Long, timestamp: Long)

    @Query("UPDATE photos SET backedUpAt = 0 WHERE id = :photoId")
    suspend fun markAsNotBackedUp(photoId: Long)
}
