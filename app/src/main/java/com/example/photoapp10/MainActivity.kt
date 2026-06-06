package com.example.photoapp10

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.photoapp10.core.permissions.PermissionManager
import com.example.photoapp10.feature.backup.domain.CloudArchiveScheduler
import com.example.photoapp10.feature.backup.work.TempModeCleanupWorker
import com.example.photoapp10.ui.theme.PhotoAppTheme
import com.rishikeshmore.fotonizer.BuildConfig
import timber.log.Timber

class MainActivity : ComponentActivity() {
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var archiveScheduler: CloudArchiveScheduler
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Timber.i("MainActivity: All permissions granted")
            // Schedule automatic archiving
            archiveScheduler.scheduleDailyArchive()
        } else {
            Timber.w("MainActivity: Some permissions denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Timber FIRST, before any other operations
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("MainActivity: onCreate started")
        
        // Initialize managers
        permissionManager = PermissionManager(this)
        archiveScheduler = CloudArchiveScheduler(this)
        
        Timber.i("MainActivity: Window setup completed")

        Timber.i("MainActivity: About to call setContent")
        try {
            setContent {
                Timber.i("MainActivity: Inside setContent lambda")
                PhotoAppTheme {
                    Timber.i("MainActivity: Inside PhotoAppTheme")
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Timber.i("MainActivity: About to call AppNav")
                        AppNav()
                        Timber.i("MainActivity: AppNav called successfully")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "MainActivity: Error in setContent")
            // Fallback UI or error handling could be added here
        }
        Timber.i("MainActivity: setContent completed")

        window.decorView.post {
            ensureLaunchSurfaceVisible()
            runDeferredStartupWork()
        }
    }

    private fun ensureLaunchSurfaceVisible() {
        try {
            window.decorView.alpha = 1f
            val attrs = window.attributes
            if (attrs.alpha != 1f) {
                attrs.alpha = 1f
                window.attributes = attrs
            }
            window.decorView.requestLayout()
            window.decorView.invalidate()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                reportFullyDrawn()
            }
        } catch (e: Exception) {
            Timber.e(e, "MainActivity: Failed to ensure launch surface visibility")
        }
    }

    private fun runDeferredStartupWork() {
        try {
            TempModeCleanupWorker.schedule(this)

            // Check permissions after the first UI frame has been scheduled.
            if (!permissionManager.areAllPermissionsGranted()) {
                Timber.i("MainActivity: First launch - requesting permissions")
                requestPermissions()
            } else {
                Timber.i("MainActivity: All permissions already granted")
                archiveScheduler.scheduleDailyArchive()
            }
        } catch (e: Exception) {
            Timber.e(e, "MainActivity: Deferred startup work failed")
        }
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Add camera permission if not granted
        if (!permissionManager.isCameraPermissionGranted()) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        // Add microphone permission for video recording
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // Add background permissions if not granted
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (!permissionManager.areBackgroundPermissionsGranted()) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            Timber.i("MainActivity: Requesting permissions: ${permissionsToRequest.joinToString()}")
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
