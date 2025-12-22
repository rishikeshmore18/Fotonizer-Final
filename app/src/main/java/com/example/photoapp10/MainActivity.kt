package com.example.photoapp10

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.photoapp10.core.permissions.PermissionManager
import com.example.photoapp10.feature.backup.domain.CloudArchiveScheduler
import com.example.photoapp10.ui.theme.PhotoAppTheme
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
        
        enableEdgeToEdge()
        Timber.i("MainActivity: enableEdgeToEdge completed")
        
        // Check if this is first launch and request permissions
        if (!permissionManager.areAllPermissionsGranted()) {
            Timber.i("MainActivity: First launch - requesting permissions")
            requestPermissions()
        } else {
            Timber.i("MainActivity: All permissions already granted")
            // Schedule automatic archiving
            archiveScheduler.scheduleDailyArchive()
        }

        Timber.i("MainActivity: About to call setContent")
        try {
            setContent {
                Timber.i("MainActivity: Inside setContent lambda")
                PhotoAppTheme {
                    Timber.i("MainActivity: Inside PhotoAppTheme")
                    Surface(modifier = Modifier.fillMaxSize()) {
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
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Add camera permission if not granted
        if (!permissionManager.isCameraPermissionGranted()) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        // Add storage permissions if not granted (for older Android versions)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            if (!permissionManager.areStoragePermissionsGranted()) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
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