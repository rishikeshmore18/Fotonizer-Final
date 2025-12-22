package com.example.photoapp10.feature.photo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = com.example.photoapp10.feature.album.data.AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["albumId", "createdAt"]),
        Index(value = ["filename"]),
        Index(value = ["favorite"])
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val albumId: Long,
    val filename: String,
    val path: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val caption: String = "",
    /** Store emoji tags as glyph strings, NOT numbers */
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val takenAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Backup status: 0 = not backed up, >0 = backed up timestamp */
    val backedUpAt: Long = 0L
)


