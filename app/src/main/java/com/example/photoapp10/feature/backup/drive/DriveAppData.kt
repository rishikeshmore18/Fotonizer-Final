package com.example.photoapp10.feature.backup.drive

import android.content.Context
import android.util.Log
import com.example.photoapp10.feature.auth.AuthManager
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DriveAppData(private val context: Context, val drive: Drive) {
    companion object { 
        private const val BACKUP = "backup.json"
        private const val API_TIMEOUT_MS = 15000L // 15 second timeout per operation
    }

    data class BackupFile(val id: String, val name: String, val modifiedTimeMillis: Long)

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
            if (e is TimeoutCancellationException) {
                Log.w("DriveAppData", "Operation timed out after ${timeoutMs}ms")
                false
            } else if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                Log.w("DriveAppData", "Authentication error, attempting to refresh token")
                val refreshedDrive = AuthManager.refreshDriveService(context, drive)
                if (refreshedDrive != null) {
                    try {
                        withTimeout(timeoutMs) {
                            operation(refreshedDrive)
                        }
                        true
                    } catch (retryException: Exception) {
                        Log.e("DriveAppData", "Operation failed even after token refresh", retryException)
                        false
                    }
                } else {
                    Log.e("DriveAppData", "Failed to refresh Drive service")
                    false
                }
            } else {
                Log.e("DriveAppData", "Non-authentication error", e)
                false
            }
        }
    }

    suspend fun findLatestBackup(): BackupFile? = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveAppData", "Searching for latest backup in appDataFolder via Drive client")

            var result: BackupFile? = null
            val success = executeWithRetry { drive ->
                val listRequest = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name = '$BACKUP' and trashed = false")
                    .setFields("files(id,name,modifiedTime)")
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1)

                val fileList: FileList = listRequest.execute()
                val files = fileList.files

                if (files.isNullOrEmpty()) {
                    Log.d("DriveAppData", "No backup.json found in appDataFolder")
                    result = null
                } else {
                    val file = files[0]
                    val modifiedTime = file.modifiedTime?.value ?: 0L
                    
                    result = BackupFile(
                        id = file.id,
                        name = file.name,
                        modifiedTimeMillis = modifiedTime
                    )
                    
                    result?.let { backup ->
                        Log.d("DriveAppData", "Found backup: ${backup.name} (${backup.id}) modified at ${backup.modifiedTimeMillis}")
                    }
                }
            }
            
            return@withContext if (success) result else null

        } catch (e: Exception) {
            Log.e("DriveAppData", "Failed to find latest backup", e)
            return@withContext null
        }
    }

    suspend fun download(fileId: String, dst: File, timeoutMs: Long = API_TIMEOUT_MS): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveAppData", "Downloading file $fileId to ${dst.absolutePath} via Drive client")

            val success = executeWithRetry(timeoutMs) { drive ->
                dst.parentFile?.mkdirs()

                FileOutputStream(dst).use { outputStream ->
                    drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                }

                Log.d("DriveAppData", "Successfully downloaded file: ${dst.length()} bytes")
            }
            
            return@withContext success

        } catch (e: Exception) {
            Log.e("DriveAppData", "Failed to download file $fileId", e)
            return@withContext false
        }
    }
}

suspend fun driveAppDataOrNull(ctx: Context): DriveAppData? {
    return try {
        val drive = AuthManager.buildDriveService(ctx)
        if (drive == null) {
            Log.w("DriveAppData", "No Drive service available")
            return null
        }

        Log.d("DriveAppData", "Created DriveAppData with Drive client")
        DriveAppData(ctx, drive)
    } catch (e: Exception) {
        Log.e("DriveAppData", "Failed to create DriveAppData", e)
        null
    }
}
