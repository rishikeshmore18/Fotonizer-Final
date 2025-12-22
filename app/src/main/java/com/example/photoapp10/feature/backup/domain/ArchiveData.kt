package com.example.photoapp10.feature.backup.domain

import kotlinx.serialization.Serializable

/**
 * Root data structure for cloud archive
 */
@Serializable
data class ArchiveRoot(
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val lastArchiveAt: Long,
    val totalAlbums: Int,
    val totalPhotos: Int,
    val archivedAlbums: List<ArchiveAlbum>,
    val archivedPhotos: List<ArchivePhoto>
)

/**
 * Archived album data
 */
@Serializable
data class ArchiveAlbum(
    val id: Long,
    val name: String,
    val emoji: String?,
    val photoCount: Int,
    val favorite: Boolean,
    val archivedAt: Long,
    val originalUpdatedAt: Long
)

/**
 * Archived photo data
 */
@Serializable
data class ArchivePhoto(
    val id: Long,
    val albumId: Long,
    val filename: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val caption: String,
    val tags: List<String>,
    val favorite: Boolean,
    val takenAt: Long,
    val createdAt: Long,
    val originalUpdatedAt: Long,
    val archivedAt: Long,
    val relativePath: String
)


