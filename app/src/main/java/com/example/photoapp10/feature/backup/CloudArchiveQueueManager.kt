package com.example.photoapp10.feature.backup

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.photoapp10.feature.backup.work.CloudArchiveWorker
import com.example.photoapp10.feature.settings.data.UserPrefs
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CloudArchiveQueueManager(private val app: Application) {
    private var lastRequestAt = 0L

    fun requestArchive(reason: String = "change", force: Boolean = false) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastRequestAt < MIN_REQUEST_INTERVAL_MS) {
                Timber.d("CloudArchiveQueueManager: Archive request debounced (reason=$reason)")
                return
            }
            lastRequestAt = now

            val prefs = UserPrefs(app)
            val wifiOnly = try {
                prefs.wifiOnlyFlowReplay()
            } catch (e: Exception) {
                Timber.w(e, "CloudArchiveQueueManager: Failed to read wifiOnly preference, defaulting to true")
                true
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val inputData = Data.Builder()
                .putString(CloudArchiveWorker.KEY_REASON, reason)
                .putBoolean(CloudArchiveWorker.KEY_FORCE_REQUEST, force)
                .build()

            val work = OneTimeWorkRequestBuilder<CloudArchiveWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(app).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                work
            )

            Timber.d("CloudArchiveQueueManager: Archive work enqueued (reason=$reason, force=$force, wifiOnly=$wifiOnly)")
        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveQueueManager: Failed to enqueue archive work")
        }
    }

    companion object {
        const val UNIQUE_NAME = "cloud_archive_once"
        const val WORK_TAG = "cloud_archive_once"
        private const val MIN_REQUEST_INTERVAL_MS = 2000L
    }
}
