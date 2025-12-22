package com.example.photoapp10.feature.backup.drive

import android.util.Log
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class DriveUploader(private val drive: Drive) {

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 2  // Reduced from 3 to 2
        private const val RETRY_DELAY_MS = 500L   // Reduced from 1000L to 500L
        private const val API_TIMEOUT_MS = 15000L // 15 second timeout per operation
    }

    // Improved retry helper with timeout and better error handling
    private suspend fun executeWithRetry(operation: suspend (Drive) -> Unit): Boolean {
        return try {
            withTimeout(API_TIMEOUT_MS) {
                operation(drive)
            }
            true
        } catch (e: Exception) {
            // Check for retryable errors
            val isRetryable = e.message?.let { message ->
                message.contains("401") || 
                message.contains("403") || 
                message.contains("429") ||
                message.contains("500") ||
                message.contains("502") ||
                message.contains("503") ||
                message.contains("504")
            } ?: false
            
            if (isRetryable) {
                Log.w("DriveUploader", "Retryable API error: ${e.message}")
                
                // Limited retry with shorter delays
                repeat(MAX_RETRY_ATTEMPTS) { attempt ->
                    try {
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
                        withTimeout(API_TIMEOUT_MS) {
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
            } else {
                Log.e("DriveUploader", "Non-retryable error: ${e.message}")
                false
            }
        }
    }

    suspend fun putBackupJsonWithRetry(json: ByteArray, fileName: String = "backup.json"): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading $fileName (${json.size} bytes) with retry logic")
            
            val success = executeWithRetry { drive ->
                // Check if file already exists
                val existing = findExistingFile(fileName)
                
                val fileMetadata = DriveFile().apply {
                    name = fileName
                    parents = listOf("appDataFolder")
                }
                
                val mediaContent = com.google.api.client.http.FileContent(
                    "application/json",
                    createTempFile(json)
                )
                
                if (existing == null) {
                    // Create new file
                    drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                } else {
                    // Update existing file
                    val updateMetadata = DriveFile().apply {
                        name = fileName
                    }
                    drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                }
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

    suspend fun putPhotoWithRetry(file: File, albumId: Long, photoId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val remotePath = "photos/$albumId/$photoId.jpg"
            Log.d("DriveUploader", "Uploading photo $remotePath (${file.length()} bytes) with retry logic")
            
            val success = executeWithRetry { drive ->
                // Check if photo already exists
                val existing = findExistingFile(remotePath)
                
                val fileMetadata = DriveFile().apply {
                    name = remotePath
                    parents = listOf("appDataFolder")
                }
                
                val mediaContent = com.google.api.client.http.FileContent(
                    "image/jpeg",
                    file
                )
                
                if (existing == null) {
                    // Create new photo
                    drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                } else {
                    // Update existing photo
                    val updateMetadata = DriveFile().apply {
                        name = remotePath
                    }
                    drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                }
            }
            
            if (success) {
                Log.d("DriveUploader", "Successfully uploaded photo $remotePath")
            } else {
                Log.e("DriveUploader", "Failed to upload photo $remotePath after retries")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e("DriveUploader", "Failed to upload photo $albumId/$photoId", e)
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
    
    private fun createTempFile(data: ByteArray): java.io.File {
        val tempFile = java.io.File.createTempFile("backup", ".json")
        tempFile.writeBytes(data)
        return tempFile
    }

    /**
     * Upload photo to specific path in Drive
     */
    suspend fun putPhotoToPath(file: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading photo to path: $remotePath")
            
            val success = executeWithRetry { drive ->
                val existing = findFileByPath(remotePath)
                
                val fileMetadata = DriveFile().apply {
                    name = remotePath
                    parents = listOf("appDataFolder")
                }
                
                val mediaContent = com.google.api.client.http.FileContent("image/jpeg", file)
                
                if (existing == null) {
                    drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                } else {
                    val updateMetadata = DriveFile().apply {
                        name = remotePath
                    }
                    drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
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

    /**
     * Upload file to specific path in Drive
     */
    suspend fun putFileToPath(data: ByteArray, remotePath: String, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveUploader", "Uploading file to path: $remotePath")
            
            val success = executeWithRetry { drive ->
                val existing = findFileByPath(remotePath)
                
                val fileMetadata = DriveFile().apply {
                    name = remotePath
                    parents = listOf("appDataFolder")
                }
                
                val mediaContent = com.google.api.client.http.FileContent(
                    mimeType,
                    createTempFile(data)
                )
                
                if (existing == null) {
                    drive.files().create(fileMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                } else {
                    val updateMetadata = DriveFile().apply {
                        name = remotePath
                    }
                    drive.files().update(existing.id, updateMetadata, mediaContent)
                        .setFields("id,name,modifiedTime")
                        .execute()
                }
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
}