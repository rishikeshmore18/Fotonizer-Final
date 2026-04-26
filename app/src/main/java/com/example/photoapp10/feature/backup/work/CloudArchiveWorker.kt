package com.example.photoapp10.feature.backup.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.backup.domain.CloudArchiveManager
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class CloudArchiveWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        archiveMutex.withLock {
            try {
                val reason = inputData.getString(KEY_REASON) ?: "scheduled"
                val forceRequest = inputData.getBoolean(KEY_FORCE_REQUEST, false)
                Timber.i("CloudArchiveWorker: Starting cloud archive (reason=$reason, force=$forceRequest)")

                val archiveManager = CloudArchiveManager(
                    context = context,
                    db = Modules.provideDb(context),
                    storage = Modules.provideStorage(context),
                    thumbnailer = Modules.provideThumbnailer()
                )

                val result = archiveManager.archiveToCloud()

                if (result.success) {
                    UserPrefs(context).setLastArchiveAt(System.currentTimeMillis())
                    Timber.i("CloudArchiveWorker: Archive completed successfully - ${result.albumsArchived} albums, ${result.photosArchived} photos")
                    Result.success()
                } else {
                    Timber.w("CloudArchiveWorker: Archive failed: ${result.message}")
                    Result.retry()
                }
            } catch (e: Exception) {
                Timber.e(e, "CloudArchiveWorker: Archive failed with exception")
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_REASON = "reason"
        const val KEY_FORCE_REQUEST = "force_request"
        private val archiveMutex = Mutex()
    }
}
