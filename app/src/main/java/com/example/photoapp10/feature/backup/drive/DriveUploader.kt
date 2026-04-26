package com.example.photoapp10.feature.backup.drive

import android.util.Log
import com.example.photoapp10.core.util.MediaFileSupport
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class DriveUploader(private val drive: Drive) {

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 500L
        private const val API_TIMEOUT_MS = 15000L
        private const val PHOTO_UPLOAD_TIMEOUT_MS = MediaFileSupport.PHOTO_TRANSFER_TIMEOUT_MS
        private const val VIDEO_UPLOAD_TIMEOUT_MS = MediaFileSupport.VIDEO_TRANSFER_TIMEOUT_MS
        private const val DIRECT_UPLOAD_MAX_BYTES = 5L * 1024L * 1024L
        private const val PHOTO_UPLOAD_CHUNK_SIZE = MediaHttpUploader.MINIMUM_CHUNK_SIZE
        private const val VIDEO_UPLOAD_CHUNK_SIZE = MediaHttpUploader.MINIMUM_CHUNK_SIZE * 2
    }

    private suspend fun executeWithRetry(
        timeoutMs: Long = API_TIMEOUT_MS,
        operation: suspend (Drive) -> Unit
    ): Boolean {
        return try {
            withTimeout(timeoutMs) {
                operation(drive)
            }
            true
        } catch (e: Exception) {
            val isRetryable = e is TimeoutCancellationException || e.message?.let { message ->
                message.contains("401") ||
                    message.contains("403") ||
                    message.contains("429") ||
                    message.contains("500") ||
                    message.contains("502") ||
                    message.contains("503") ||
                    message.contains("504")
            } ?: false

            if (!isRetryable) {
                Log.e("DriveUploader", "Non-retryable error: ${e.message}")
                return false
            }

            Log.w("DriveUploader", "Retryable API error: ${e.message}")
            repeat(MAX_RETRY_ATTEMPTS) { attempt ->
                try {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                    withTimeout(timeoutMs) {
                        operation(drive)
                    }
                    return true
                } catch (retryException: Exception) {
                    Log.w("DriveUploader", "Retry attempt ${attempt + 1} failed: ${retryException.message}")
                    if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                        Log.e("DriveUploader", "All retry attempts failed")
                        return false
                    }
                }
            }

            false
        }
    }

    suspend fun putBackupJsonWithRetry(json: ByteArray, fileName: String = "backup.json"): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading $fileName (${json.size} bytes) with retry logic")

            val tempFile = createTempFile(json, ".json")
            val success = try {
                executeWithRetry { drive ->
                    val existing = findExistingFile(fileName)

                    val fileMetadata = DriveFile().apply {
                        name = fileName
                        parents = listOf("appDataFolder")
                    }

                    val mediaContent = com.google.api.client.http.FileContent("application/json", tempFile)

                    if (existing == null) {
                        val request = drive.files().create(fileMetadata, mediaContent)
                            .setFields("id,name,modifiedTime")
                        configureUploader(request.mediaHttpUploader, tempFile, isVideo = false, allowDirectUpload = true)
                        request.execute()
                    } else {
                        val updateMetadata = DriveFile().apply {
                            name = fileName
                        }
                        val request = drive.files().update(existing.id, updateMetadata, mediaContent)
                            .setFields("id,name,modifiedTime")
                        configureUploader(request.mediaHttpUploader, tempFile, isVideo = false, allowDirectUpload = true)
                        request.execute()
                    }
                }
            } finally {
                tempFile.delete()
            }

            if (success) {
                Log.d("DriveUploader", "Successfully uploaded $fileName")
            } else {
                Log.e("DriveUploader", "Failed to upload $fileName after retries")
            }

            success
        } catch (e: Exception) {
            Log.e("DriveUploader", "Failed to upload $fileName", e)
            false
        }
    }

    suspend fun putPhotoWithRetry(file: File, albumId: Long, photoId: Long, fileExtension: String = "jpg"): Boolean = withContext(Dispatchers.IO) {
        try {
            val remotePath = MediaFileSupport.relativeMediaPath(albumId, photoId, fileExtension)
            val isVideo = MediaFileSupport.isVideoExtension(fileExtension)
            val mimeType = MediaFileSupport.mimeTypeForExtension(fileExtension)
            val timeoutMs = if (isVideo) VIDEO_UPLOAD_TIMEOUT_MS else PHOTO_UPLOAD_TIMEOUT_MS
            val mediaType = if (isVideo) "video" else "photo"
            Log.d("DriveUploader", "Uploading $mediaType $remotePath (${file.length()} bytes) with retry logic")

            val success = executeWithRetry(timeoutMs) { drive ->
                val existing = findExistingFile(remotePath)

                val fileMetadata = DriveFile().apply {
                    name = remotePath
                    parents = listOf("appDataFolder")
                }

                val mediaContent = com.google.api.client.http.FileContent(mimeType, file)

                if (existing == null) {
                    val request = drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                    configureUploader(request.mediaHttpUploader, file, isVideo = isVideo, allowDirectUpload = true)
                    request.execute()
                } else {
                    val updateMetadata = DriveFile().apply {
                        name = remotePath
                    }
                    val request = drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                    configureUploader(request.mediaHttpUploader, file, isVideo = isVideo, allowDirectUpload = true)
                    request.execute()
                }
            }

            if (success) {
                Log.d("DriveUploader", "Successfully uploaded $mediaType $remotePath")
            } else {
                Log.e("DriveUploader", "Failed to upload $mediaType $remotePath after retries")
            }

            success
        } catch (e: Exception) {
            Log.e("DriveUploader", "Failed to upload file $albumId/$photoId", e)
            false
        }
    }

    suspend fun putBackupJson(json: ByteArray, fileName: String = "backup.json") = withContext(Dispatchers.IO) {
        val success = putBackupJsonWithRetry(json, fileName)
        if (!success) {
            throw Exception("Failed to upload backup.json after retries")
        }
    }

    suspend fun putPhoto(file: File, albumId: Long, photoId: Long) = withContext(Dispatchers.IO) {
        val success = putPhotoWithRetry(file, albumId, photoId)
        if (!success) {
            throw Exception("Failed to upload photo after retries")
        }
    }

    private suspend fun findExistingFile(fileName: String): DriveFile? = withContext(Dispatchers.IO) {
        try {
            withTimeout(API_TIMEOUT_MS) {
                val listRequest = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$fileName' and trashed = false")
                    .setFields("files(id,name)")
                    .setPageSize(1)

                val fileList: FileList = listRequest.execute()
                fileList.files?.firstOrNull()
            }
        } catch (e: Exception) {
            Log.w("DriveUploader", "Failed to check for existing file: $fileName", e)
            null
        }
    }

    private fun createTempFile(data: ByteArray, suffix: String = ".tmp"): File {
        val tempFile = File.createTempFile("backup", suffix)
        tempFile.writeBytes(data)
        return tempFile
    }

    suspend fun putPhotoToPath(file: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading photo to path: $remotePath")

            val success = executeWithRetry(PHOTO_UPLOAD_TIMEOUT_MS) { drive ->
                val existing = findFileByPath(remotePath)

                val fileMetadata = DriveFile().apply {
                    name = remotePath
                    parents = listOf("appDataFolder")
                }

                val mediaContent = com.google.api.client.http.FileContent("image/jpeg", file)

                if (existing == null) {
                    val request = drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                    configureUploader(request.mediaHttpUploader, file, isVideo = false, allowDirectUpload = true)
                    request.execute()
                } else {
                    val updateMetadata = DriveFile().apply {
                        name = remotePath
                    }
                    val request = drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                    configureUploader(request.mediaHttpUploader, file, isVideo = false, allowDirectUpload = true)
                    request.execute()
                }
            }

            if (success) {
                Log.d("DriveUploader", "Successfully uploaded photo to: $remotePath")
            } else {
                Log.e("DriveUploader", "Failed to upload photo to: $remotePath")
            }

            success
        } catch (e: Exception) {
            Log.e("DriveUploader", "Failed to upload photo to path: $remotePath", e)
            false
        }
    }

    suspend fun putFileToPath(data: ByteArray, remotePath: String, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading file to path: $remotePath")

            val tempFile = createTempFile(data)
            val success = try {
                executeWithRetry { drive ->
                    val existing = findFileByPath(remotePath)

                    val fileMetadata = DriveFile().apply {
                        name = remotePath
                        parents = listOf("appDataFolder")
                    }

                    val mediaContent = com.google.api.client.http.FileContent(mimeType, tempFile)

                    if (existing == null) {
                        val request = drive.files().create(fileMetadata, mediaContent)
                            .setFields("id,name,modifiedTime")
                        configureUploader(request.mediaHttpUploader, tempFile, isVideo = mimeType.startsWith("video/"), allowDirectUpload = true)
                        request.execute()
                    } else {
                        val updateMetadata = DriveFile().apply {
                            name = remotePath
                        }
                        val request = drive.files().update(existing.id, updateMetadata, mediaContent)
                            .setFields("id,name,modifiedTime")
                        configureUploader(request.mediaHttpUploader, tempFile, isVideo = mimeType.startsWith("video/"), allowDirectUpload = true)
                        request.execute()
                    }
                }
            } finally {
                tempFile.delete()
            }

            if (success) {
                Log.d("DriveUploader", "Successfully uploaded file to: $remotePath")
            } else {
                Log.e("DriveUploader", "Failed to upload file to: $remotePath")
            }

            success
        } catch (e: Exception) {
            Log.e("DriveUploader", "Failed to upload file to path: $remotePath", e)
            false
        }
    }

    suspend fun putFileToPath(
        file: File,
        remotePath: String,
        mimeType: String,
        timeoutMs: Long = PHOTO_UPLOAD_TIMEOUT_MS
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading stream file to path: $remotePath")

            val success = executeWithRetry(timeoutMs) { drive ->
                val existing = findFileByPath(remotePath)

                val fileMetadata = DriveFile().apply {
                    name = remotePath
                    parents = listOf("appDataFolder")
                }

                val mediaContent = com.google.api.client.http.FileContent(mimeType, file)
                val isVideo = mimeType.startsWith("video/")

                if (existing == null) {
                    val request = drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                    configureUploader(request.mediaHttpUploader, file, isVideo = isVideo, allowDirectUpload = true)
                    request.execute()
                } else {
                    val updateMetadata = DriveFile().apply {
                        name = remotePath
                    }
                    val request = drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                    configureUploader(request.mediaHttpUploader, file, isVideo = isVideo, allowDirectUpload = true)
                    request.execute()
                }
            }

            if (success) {
                Log.d("DriveUploader", "Successfully uploaded stream file to: $remotePath")
            } else {
                Log.e("DriveUploader", "Failed to upload stream file to: $remotePath")
            }

            success
        } catch (e: Exception) {
            Log.e("DriveUploader", "Failed to upload stream file to path: $remotePath", e)
            false
        }
    }

    private suspend fun findFileByPath(filePath: String): DriveFile? = withContext(Dispatchers.IO) {
        try {
            withTimeout(API_TIMEOUT_MS) {
                val listRequest = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$filePath' and trashed = false")
                    .setFields("files(id,name)")
                    .setPageSize(1)

                val fileList: FileList = listRequest.execute()
                fileList.files?.firstOrNull()
            }
        } catch (e: Exception) {
            Log.w("DriveUploader", "Failed to check for existing file: $filePath", e)
            null
        }
    }

    private fun configureUploader(
        uploader: MediaHttpUploader,
        file: File,
        isVideo: Boolean,
        allowDirectUpload: Boolean
    ) {
        val useDirectUpload = allowDirectUpload && !isVideo && file.length() <= DIRECT_UPLOAD_MAX_BYTES
        uploader.setDirectUploadEnabled(useDirectUpload)

        if (!useDirectUpload) {
            uploader.setChunkSize(if (isVideo) VIDEO_UPLOAD_CHUNK_SIZE else PHOTO_UPLOAD_CHUNK_SIZE)
        }
    }
}
