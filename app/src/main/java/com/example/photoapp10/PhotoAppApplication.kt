package com.example.photoapp10

import android.app.Application
import com.rishikeshmore.fotonizer.BuildConfig
import timber.log.Timber

/**
 * Application class for PhotoApp10
 * Initializes logging and manages app lifecycle
 */
class PhotoAppApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize logging
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }
            
            Timber.d("PhotoAppApplication initialized successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize PhotoAppApplication")
            // Continue with app launch even if initialization fails
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        try {
            Timber.d("PhotoAppApplication terminated")
        } catch (e: Exception) {
            Timber.e(e, "Error during application termination")
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        try {
            Timber.w("Low memory warning received")
        } catch (e: Exception) {
            Timber.e(e, "Error handling low memory warning")
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            Timber.d("Memory trim requested: level $level")
        } catch (e: Exception) {
            Timber.e(e, "Error handling memory trim")
        }
    }
}
