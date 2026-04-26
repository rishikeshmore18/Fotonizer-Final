package com.example.photoapp10.feature.backup

import android.app.Application
import androidx.work.*
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.TimeUnit

enum class SyncState { Idle, Syncing, Done, Error }

class DriveSyncManager(private val app: Application) {

    private val _state = MutableStateFlow(SyncState.Idle)
    val state: StateFlow<SyncState> = _state
    
    // Debounce mechanism to prevent rapid job restarts
    private var lastSyncRequestTime = 0L
    private val minSyncInterval = 2000L // 2 seconds minimum between sync requests

    fun observeState(): StateFlow<SyncState> = state

    /** Call on any CRUD change. This will coalesce into a single worker run. */
    fun requestSync(reason: String = "change") {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Debounce: ignore requests that are too frequent
            if (currentTime - lastSyncRequestTime < minSyncInterval) {
                Timber.d("DriveSyncManager: Sync request debounced - reason: $reason (${currentTime - lastSyncRequestTime}ms since last)")
                return
            }
            
            lastSyncRequestTime = currentTime
            Timber.d("DriveSyncManager: Sync requested - reason: $reason")
            
            // Queue sync work behind any active worker instead of cancelling in-flight uploads.
            val prefs = UserPrefs(app)
            val wifiOnly = try {
                prefs.wifiOnlyFlowReplay()
            } catch (e: Exception) {
                Timber.w(e, "DriveSyncManager: Failed to read wifiOnly preference, defaulting to true")
                true
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()

            val work = OneTimeWorkRequestBuilder<com.example.photoapp10.feature.backup.work.DriveSyncWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(app).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                work
            )
            
            _state.value = SyncState.Syncing
            Timber.d("DriveSyncManager: Sync work enqueued (wifiOnly=$wifiOnly)")
            
        } catch (e: Exception) {
            Timber.e(e, "DriveSyncManager: Failed to request sync")
            _state.value = SyncState.Error
        }
    }

    internal fun onWorkerFinished(ok: Boolean) {
        val newState = if (ok) SyncState.Done else SyncState.Error
        _state.value = newState
        Timber.d("DriveSyncManager: Worker finished - success=$ok, state=$newState")
        
        // Optionally reset to Idle after a short delay from UI layer
    }

    /** Reset state to Idle (call from UI after showing Done/Error for a while) */
    fun resetToIdle() {
        _state.value = SyncState.Idle
        Timber.d("DriveSyncManager: State reset to Idle")
    }

    companion object {
        const val UNIQUE_NAME = "drive_sync_once"
        const val WORK_TAG = "drive_sync"
    }
}
