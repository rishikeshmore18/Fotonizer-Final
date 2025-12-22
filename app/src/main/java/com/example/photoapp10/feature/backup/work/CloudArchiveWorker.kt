package com.example.photoapp10.feature.backup.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.backup.domain.CloudArchiveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class CloudArchiveWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.i("CloudArchiveWorker: Starting scheduled cloud archive at 11 PM")
            
            val archiveManager = CloudArchiveManager(
                context = context,
                db = Modules.provideDb(context),
                storage = Modules.provideStorage(context),
                thumbnailer = Modules.provideThumbnailer()
            )
            
            val result = archiveManager.archiveToCloud()
            
            if (result.success) {
                Timber.i("CloudArchiveWorker: Scheduled archive completed successfully - ${result.albumsArchived} albums, ${result.photosArchived} photos")
                Result.success()
            } else {
                Timber.w("CloudArchiveWorker: Scheduled archive failed: ${result.message}")
                Result.retry()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveWorker: Scheduled archive failed with exception")
            Result.retry()
        }
    }
}


