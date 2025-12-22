package com.example.photoapp10.feature.backup.domain

import android.content.Context
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.thumb.Thumbnailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Real-time archive manager that triggers cloud archiving on data changes
 * Uses debounced requests to avoid excessive uploads while maintaining real-time behavior
 */
class RealTimeArchiveManager(
    private val context: Context,
    private val db: AppDb,
    private val storage: AppStorage,
    private val thumbnailer: Thumbnailer
) {
    // Debounced archive requests (5-second debounce to avoid excessive uploads)
    private var lastArchiveRequestTime = 0L
    private val minArchiveInterval = 5000L // 5 seconds
    
    // Coroutine scope for background operations
    private val archiveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Cloud archive manager instance
    private val cloudArchiveManager = CloudArchiveManager(context, db, storage, thumbnailer)
    
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
        
        // Launch archive operation in background
        archiveScope.launch {
            try {
                // Small delay to allow for potential batch operations
                delay(1000) // 1 second delay
                
                val result = cloudArchiveManager.archiveToCloud()
                
                if (result.success) {
                    Timber.i("RealTimeArchiveManager: Archive completed successfully - Albums: ${result.albumsArchived}, Photos: ${result.photosArchived}")
                } else {
                    Timber.w("RealTimeArchiveManager: Archive failed - ${result.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "RealTimeArchiveManager: Error during real-time archive")
                // Don't throw - this is a background operation that shouldn't affect main functionality
            }
        }
    }
    
    /**
     * Force an immediate archive operation (bypasses debouncing)
     * Use sparingly for critical operations
     */
    fun forceArchive(reason: String = "force_request") {
        Timber.d("RealTimeArchiveManager: Force archive requested - reason: $reason")
        
        archiveScope.launch {
            try {
                val result = cloudArchiveManager.archiveToCloud()
                
                if (result.success) {
                    Timber.i("RealTimeArchiveManager: Force archive completed - Albums: ${result.albumsArchived}, Photos: ${result.photosArchived}")
                } else {
                    Timber.w("RealTimeArchiveManager: Force archive failed - ${result.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "RealTimeArchiveManager: Error during force archive")
            }
        }
    }
}
