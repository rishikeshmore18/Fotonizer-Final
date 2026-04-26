package com.example.photoapp10.core.util

object MediaFileSupport {
    private val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
    private val invalidFilenameChars = Regex("[\\\\/:*?\"<>|]")
    private val repeatedWhitespace = Regex("\\s+")
    const val PHOTO_TRANSFER_TIMEOUT_MS = 60000L
    const val VIDEO_TRANSFER_TIMEOUT_MS = 120000L

    fun normalizedExtension(filename: String, defaultExt: String = "jpg"): String =
        filename.substringAfterLast('.', defaultExt).lowercase()

    fun displayNameWithoutExtension(filename: String): String {
        val extension = filename.substringAfterLast('.', "")
        return if (extension.isNotBlank() && filename.length > extension.length + 1) {
            filename.dropLast(extension.length + 1)
        } else {
            filename
        }
    }

    fun sanitizeDisplayName(rawName: String): String =
        rawName
            .replace(invalidFilenameChars, "_")
            .replace(repeatedWhitespace, " ")
            .trim()
            .trim('.')

    fun buildFilenamePreservingExtension(currentFilename: String, requestedName: String): String {
        val extension = currentFilename.substringAfterLast('.', "")
        var baseName = sanitizeDisplayName(requestedName)

        if (extension.isNotBlank() && baseName.endsWith(".$extension", ignoreCase = true)) {
            baseName = baseName.dropLast(extension.length + 1)
        }

        baseName = baseName.trim().trim('.')
        require(baseName.isNotBlank()) { "Renamed filename cannot be blank" }

        return if (extension.isBlank()) baseName else "$baseName.$extension"
    }

    fun isVideoExtension(extension: String): Boolean = extension.lowercase() in videoExtensions

    fun mimeTypeForExtension(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> if (isVideoExtension(extension)) "video/*" else "image/jpeg"
        }
    }

    fun relativeMediaPath(albumId: Long, photoId: Long, extension: String): String =
        "photos/$albumId/$photoId.${extension.lowercase()}"

    fun backupMediaFileName(photoId: Long, extension: String): String =
        "$photoId.${extension.lowercase()}"
}
