package com.example.photoapp10.feature.backup.domain

import android.content.Context
import androidx.work.*
import com.example.photoapp10.feature.settings.data.UserPrefs
import com.example.photoapp10.feature.backup.work.CloudArchiveWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CloudArchiveScheduler(private val context: Context) {
    
    companion object {
        private const val WORK_NAME = "cloud_archive_scheduled"
        private const val HOUR_23 = 23 // 11 PM
        private const val MINUTE_0 = 0
    }

    /**
     * Schedule daily cloud archiving at 11 PM
     */
    fun scheduleDailyArchive() {
        try {
            Timber.i("CloudArchiveScheduler: Scheduling daily cloud archive at 11 PM")
            val wifiOnly = try {
                UserPrefs(context).wifiOnlyFlowReplay()
            } catch (e: Exception) {
                Timber.w(e, "CloudArchiveScheduler: Failed to read wifiOnly preference, defaulting to true")
                true
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val dailyArchiveRequest = PeriodicWorkRequestBuilder<CloudArchiveWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("cloud_archive")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyArchiveRequest
            )
            
            Timber.i("CloudArchiveScheduler: Daily cloud archive scheduled successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveScheduler: Failed to schedule daily archive")
        }
    }

    /**
     * Cancel the scheduled archiving
     */
    fun cancelScheduledArchive() {
        try {
            Timber.i("CloudArchiveScheduler: Cancelling scheduled cloud archive")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveScheduler: Failed to cancel scheduled archive")
        }
    }

    /**
     * Check if archiving is scheduled
     */
    fun isScheduled(): Boolean {
        return try {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
            
            workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        } catch (e: Exception) {
            Timber.e(e, "CloudArchiveScheduler: Failed to check schedule status")
            false
        }
    }

    /**
     * Calculate initial delay to reach 11 PM today or tomorrow
     */
    private fun calculateInitialDelay(): Long {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        
        // Set target time to 11 PM
        calendar.set(java.util.Calendar.HOUR_OF_DAY, HOUR_23)
        calendar.set(java.util.Calendar.MINUTE, MINUTE_0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        
        // If it's already past 11 PM today, schedule for tomorrow
        if (calendar.timeInMillis <= now) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        
        val delay = calendar.timeInMillis - now
        Timber.d("CloudArchiveScheduler: Initial delay calculated: ${delay / (1000 * 60 * 60)} hours")
        
        return delay
    }
}
