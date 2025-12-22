package com.example.photoapp10.core.file

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.io.IOException

class AppStorage(private val context: Context) {

    private val filesDir = context.filesDir
    private val photosDir = File(filesDir, "photos")
    private val thumbsDir = File(filesDir, "thumbs")

    init {
        // Ensure base directories exist
        photosDir.mkdirs()
        thumbsDir.mkdirs()
        Timber.d("AppStorage initialized: photos=${photosDir.absolutePath}, thumbs=${thumbsDir.absolutePath}")
    }

    // Directory management
    fun getPhotosDir(albumId: Long): File {
        val dir = File(photosDir, albumId.toString())
        dir.mkdirs()
        return dir
    }

    fun getThumbsDir(albumId: Long): File {
        val dir = File(thumbsDir, albumId.toString())
        dir.mkdirs()
        return dir
    }

    /** photos/{albumId}/{photoId}.jpg */
    fun photoFile(albumId: Long, photoId: Long, ext: String = "jpg"): File {
        val dir = File(context.filesDir, "photos/$albumId").apply { mkdirs() }
        return File(dir, "$photoId.$ext")
    }

    /** thumbs/{albumId}/{photoId}.jpg */
    fun thumbFile(albumId: Long, photoId: Long): File {
        val dir = File(context.filesDir, "thumbs/$albumId").apply { mkdirs() }
        return File(dir, "$photoId.jpg")
    }

    /** Utility if you have an absolute path and want its sibling thumb path */
    fun thumbFileForSourcePath(srcPath: String, albumId: Long, photoId: Long): File =
        thumbFile(albumId, photoId)

    // File operations
    fun createPhotoFile(albumId: Long, filename: String): File {
        val dir = getPhotosDir(albumId)
        return File(dir, filename)
    }

    fun createThumbFile(albumId: Long, filename: String): File {
        val dir = getThumbsDir(albumId)
        return File(dir, filename)
    }

    fun moveFile(source: File, destination: File): Boolean {
        return try {
            if (source.exists()) {
                val parentDir = destination.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }
                val success = source.renameTo(destination)
                if (success) {
                    Timber.d("File moved: ${source.absolutePath} -> ${destination.absolutePath}")
                } else {
                    Timber.e("Failed to move file: ${source.absolutePath} -> ${destination.absolutePath}")
                }
                success
            } else {
                Timber.e("Source file does not exist: ${source.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error moving file: ${source.absolutePath} -> ${destination.absolutePath}")
            false
        }
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val success = file.delete()
                if (success) {
                    Timber.d("File deleted: ${file.absolutePath}")
                } else {
                    Timber.e("Failed to delete file: ${file.absolutePath}")
                }
                success
            } else {
                Timber.w("File does not exist: ${file.absolutePath}")
                true // Consider it "deleted" if it doesn't exist
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting file: ${file.absolutePath}")
            false
        }
    }

    fun deleteAlbumFiles(albumId: Long): Boolean {
        val photosDeleted = deleteDirectory(getPhotosDir(albumId))
        val thumbsDeleted = deleteDirectory(getThumbsDir(albumId))
        return photosDeleted && thumbsDeleted
    }

    private fun deleteDirectory(dir: File): Boolean {
        return try {
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                    }
                }
                val success = dir.delete()
                if (success) {
                    Timber.d("Directory deleted: ${dir.absolutePath}")
                } else {
                    Timber.e("Failed to delete directory: ${dir.absolutePath}")
                }
                success
            } else {
                true // Consider it "deleted" if it doesn't exist
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting directory: ${dir.absolutePath}")
            false
        }
    }

    // Size and space checks
    fun getFileSize(file: File): Long {
        return if (file.exists() && file.isFile) {
            file.length()
        } else {
            0L
        }
    }

    fun getDirectorySize(dir: File): Long {
        return if (dir.exists() && dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else {
            0L
        }
    }

    fun getTotalAppStorageSize(): Long {
        return getDirectorySize(filesDir)
    }

    fun getAvailableSpace(): Long {
        return try {
            val stat = StatFs(filesDir.absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            Timber.e(e, "Error getting available space")
            0L
        }
    }

    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        val availableSpace = getAvailableSpace()
        val hasSpace = availableSpace > requiredBytes
        Timber.d("Space check: required=$requiredBytes, available=$availableSpace, hasSpace=$hasSpace")
        return hasSpace
    }

    // FileProvider URI for sharing
    fun getUriForFile(file: File): Uri? {
        return try {
            if (file.exists()) {
                val authority = "${context.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, file)
            } else {
                Timber.e("Cannot create URI for non-existent file: ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating URI for file: ${file.absolutePath}")
            null
        }
    }

    // Utility methods for testing
    fun createDummyFile(albumId: Long, filename: String, content: String = "dummy content"): File? {
        return try {
            val file = createPhotoFile(albumId, filename)
            file.writeText(content)
            Timber.d("Created dummy file: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Timber.e(e, "Error creating dummy file: $filename")
            null
        }
    }

    fun listFiles(albumId: Long): List<File> {
        val photosDir = getPhotosDir(albumId)
        return photosDir.listFiles()?.toList() ?: emptyList()
    }

    // Storage info
    data class StorageInfo(
        val totalAppSize: Long,
        val availableSpace: Long,
        val photoCount: Int,
        val albumCount: Int
    )

    fun getStorageInfo(): StorageInfo {
        val albumCount = photosDir.listFiles()?.count { it.isDirectory } ?: 0
        val photoCount = photosDir.walkTopDown()
            .filter { it.isFile && (it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")) }
            .count()
        
        return StorageInfo(
            totalAppSize = getTotalAppStorageSize(),
            availableSpace = getAvailableSpace(),
            photoCount = photoCount,
            albumCount = albumCount
        )
    }

    companion object {
        private const val MIN_FREE_SPACE_MB = 100L * 1024 * 1024 // 100 MB

        fun formatBytes(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$bytes bytes"
            }
        }
    }
}
