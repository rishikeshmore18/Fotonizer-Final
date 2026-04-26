package com.example.photoapp10.feature.backup.domain

import kotlinx.serialization.Serializable

const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupRoot(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val createdAt: Long,
    val appVersion: String = "1.0",
    val settings: BackupSettings,
    val albums: List<BackupAlbum>,
    val photos: List<BackupPhoto>
)

@Serializable
data class BackupSettings(
    val themeMode: String,           // "system" | "light" | "dark"
    val defaultSort: String,         // "name_asc" | "name_desc" | ...
    val lastSearch: String = ""
)

@Serializable
data class BackupAlbum(
    val id: Long,
    val name: String,
    val coverPhotoId: Long? = null,
    val photoCount: Int,
    val favorite: Boolean = false,
    val emoji: String? = null,
    val updatedAt: Long,
    /** Parent album ID for nested folders. null = root level album */
    val parentId: Long? = null
)

@Serializable
data class BackupPhoto(
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
    val updatedAt: Long,
    val path: String, // Actual file path
    val thumbPath: String, // Actual thumbnail path
    val relativePath: String // e.g. "photos/{albumId}/{photoId}.jpg"
)

data class ExportReport(
    val albums: Int,
    val photos: Int,
    val filesCopied: Int,
    val filesMissing: Int,
    val backupJsonUri: android.net.Uri
)
