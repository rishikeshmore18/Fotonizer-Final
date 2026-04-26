package com.example.photoapp10.feature.backup.domain

import android.content.Context
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.thumb.Thumbnailer
import com.example.photoapp10.feature.backup.CloudArchiveQueueManager
import timber.log.Timber

/**
 * Real-time archive manager that triggers cloud archiving on data changes
 * Uses debounced requests to avoid excessive uploads while maintaining real-time behavior
 */
class RealTimeArchiveManager(
    private val context: Context,
    private val db: AppDb,
    private val storage: AppStorage,
    private val thumbnailer: Thumbnailer,
    private val queueManager: CloudArchiveQueueManager
) {
    // Debounced archive requests (5-second debounce to avoid excessive uploads)
    private var lastArchiveRequestTime = 0L
    private val minArchiveInterval = 5000L // 5 seconds
    
    /**
     * Request a real-time archive operation
     * Uses debouncing to prevent excessive uploads while maintaining responsiveness
     */
    fun requestArchive(reason: String = "data_change") {
        val currentTime = System.currentTimeMillis()
        
        // Check if enough time has passed since last request
        if (currentTime - lastArchiveRequestTime < minArchiveInterval) {
            Timber.d("RealTimeArchiveManager: Skipping archive request (too soon) - reason: $reason")
            return
        }
        
        lastArchiveRequestTime = currentTime
        Timber.d("RealTimeArchiveManager: Requesting archive - reason: $reason")

        queueManager.requestArchive(reason = reason, force = false)
    }
    
    /**
     * Force an immediate archive operation (bypasses debouncing)
     * Use sparingly for critical operations
     */
    fun forceArchive(reason: String = "force_request") {
        Timber.d("RealTimeArchiveManager: Force archive requested - reason: $reason")

        queueManager.requestArchive(reason = reason, force = true)
    }
}
