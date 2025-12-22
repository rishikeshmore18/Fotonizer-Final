package com.example.photoapp10.feature.backup.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.backup.drive.DriveUploader
import com.example.photoapp10.feature.backup.domain.BackupBuilders.backupRootFromDb
import com.example.photoapp10.feature.backup.drive.driveAppDataOrNull
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class DriveSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val SYNC_TIMEOUT_MS = 180000L  // 3 minutes total timeout
        private const val MAX_PHOTOS_PER_SYNC = 5    // Limit photos per sync to prevent long runs
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Overall timeout for the entire sync operation
            withTimeout(SYNC_TIMEOUT_MS) {
                Timber.d("DriveSyncWorker: Starting sync work")
                
                // Check if cancelled early
                ensureActive()
                
                val prefs = UserPrefs(context)
                val db = Modules.provideDb(context)
                val storage = Modules.provideStorage(context)

                // Must be signed in
                val driveAppData = driveAppDataOrNull(context)
                if (driveAppData == null) {
                    Timber.w("DriveSyncWorker: Drive service not available, retrying later")
                    return@withTimeout Result.retry()
                }

                // Check if cancelled before major operations
                ensureActive()

                // 1) Check for unbacked-up photos first
                Timber.d("DriveSyncWorker: Checking for unbacked-up photos")
                
                val allUnbackedPhotos = db.photoDao().getUnbackedUpPhotos()
                // Limit the number of photos processed in one sync to prevent long runs
                val unbackedPhotos = allUnbackedPhotos.take(MAX_PHOTOS_PER_SYNC)
                
                Timber.d("DriveSyncWorker: Found ${allUnbackedPhotos.size} total unbacked-up photos, processing ${unbackedPhotos.size} in this sync")

                // Short-circuit if no changes
                if (unbackedPhotos.isEmpty()) {
                    Timber.i("DriveSyncWorker: no pending changes")
                    return@withTimeout Result.success()
                }

                // 2) Process uploads sequentially to avoid overwhelming the API
                val uploader = DriveUploader(driveAppData.drive)
                var uploadedOk = 0
                var failed = 0
                var allUploadsSucceeded = true

                // Process photos one by one to avoid overwhelming the API
                unbackedPhotos.forEachIndexed { index, photo ->
                    try {
                        // Check if cancelled before each upload
                        ensureActive()
                        
                        val file = storage.photoFile(photo.albumId, photo.id)
                        if (file.exists()) {
                            val globalIndex = index + 1
                            Timber.d("DriveSyncWorker: Uploading photo ${photo.filename} ($globalIndex/${unbackedPhotos.size})")
                            
                            val success = uploader.putPhotoWithRetry(file, photo.albumId, photo.id)
                            if (success) {
                                // Mark photo as backed up
                                db.photoDao().markAsBackedUp(photo.id, System.currentTimeMillis())
                                uploadedOk++
                            } else {
                                failed++
                                allUploadsSucceeded = false
                                Timber.w("DriveSyncWorker: Failed to upload photo ${photo.id}")
                            }
                        } else {
                            failed++
                            allUploadsSucceeded = false
                            Timber.w("DriveSyncWorker: Photo file not found: ${file.absolutePath}")
                        }
                    } catch (e: CancellationException) {
                        Timber.d("DriveSyncWorker: Upload cancelled for photo ${photo.id}")
                        throw e // Re-throw to cancel the entire job
                    } catch (e: Exception) {
                        failed++
                        allUploadsSucceeded = false
                        Timber.e(e, "DriveSyncWorker: Failed to upload photo ${photo.id}")
                        // Continue with other photos for non-cancellation errors
                    }
                }

                // Log upload results
                Timber.i("DriveSyncWorker: uploadedOk=$uploadedOk failed=$failed")

                // Check if cancelled before final operations
                ensureActive()

                // 3) Upload backup.json only after all media uploads succeed
                if (allUploadsSucceeded) {
                    Timber.d("DriveSyncWorker: Building and uploading backup.json")
                    val root = backupRootFromDb(db)
                    val json = Json { prettyPrint = true; encodeDefaults = true }
                    val jsonBytes = json.encodeToString(root).toByteArray()

                    try {
                        uploader.putBackupJsonWithRetry(jsonBytes)
                        Timber.d("DriveSyncWorker: Successfully uploaded backup.json")
                        
                        // Mark all albums as backed up after successful backup.json upload
                        db.albumDao().markAllAsBackedUp(System.currentTimeMillis())
                    } catch (e: Exception) {
                        Timber.e(e, "DriveSyncWorker: Failed to upload backup.json")
                        allUploadsSucceeded = false
                    }
                }

                // 4) Update timestamp only if everything succeeded
                if (allUploadsSucceeded) {
                    val now = System.currentTimeMillis()
                    prefs.setLastSyncedAt(now)
                    Timber.i("DriveSyncWorker: advancing lastSyncedAt -> $now")
                    
                    // Notify manager of success
                    runCatching {
                        Modules.provideDriveSyncManager(context).onWorkerFinished(true)
                    }.onFailure { e ->
                        Timber.w(e, "DriveSyncWorker: Failed to notify sync manager")
                    }

                    Timber.i("DriveSyncWorker: Sync work completed successfully")
                    Result.success()
                } else {
                    // Something failed - don't advance timestamp, return retry
                    Timber.w("DriveSyncWorker: Sync failed, will retry on next run")
                    
                    // Notify manager of failure
                    runCatching {
                        Modules.provideDriveSyncManager(context).onWorkerFinished(false)
                    }.onFailure { e ->
                        Timber.w(e, "DriveSyncWorker: Failed to notify sync manager")
                    }
                    
                    Result.retry()
                }
            }
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.e("DriveSyncWorker: Sync work timed out after 3 minutes")
            
            // Notify manager of timeout (treat as failure)
            runCatching {
                Modules.provideDriveSyncManager(context).onWorkerFinished(false)
            }
            
            Result.retry()
            
        } catch (e: CancellationException) {
            Timber.d("DriveSyncWorker: Sync work cancelled")
            
            // Notify manager of cancellation (treat as failure)
            runCatching {
                Modules.provideDriveSyncManager(context).onWorkerFinished(false)
            }
            
            // Return failure for cancellation to prevent automatic retry
            Result.failure()
            
        } catch (e: Exception) {
            Timber.e(e, "DriveSyncWorker: Sync work failed")
            
            // Notify manager of failure
            runCatching {
                Modules.provideDriveSyncManager(context).onWorkerFinished(false)
            }
            
            Result.retry()
        }
    }
}
