package com.example.photoapp10.feature.settings.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.photoapp10.core.account.AccountScopeManager
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.auth.AuthManager
import com.example.photoapp10.feature.backup.drive.driveAppDataOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val userPrefs = remember { Modules.provideUserPrefs(context) }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Debug button to test Drive access
        Button(
            onClick = {
                scope.launch {
                    try {
                        val drive = driveAppDataOrNull(context)
                        if (drive != null) {
                            val listRequest = drive.drive.files().list()
                                .setSpaces("appDataFolder")
                                .setFields("files(id,name,modifiedTime)")
                            
                            val fileList = listRequest.execute()
                            val fileNames = fileList.files?.map { it.name } ?: emptyList()
                            
                            Timber.d("DriveDebug: appDataFolder files: $fileNames")
                            Toast.makeText(context, "Drive OK - ${fileNames.size} files (see logcat)", Toast.LENGTH_LONG).show()
                        } else {
                            Timber.w("DriveDebug: Drive not available")
                            Toast.makeText(context, "Drive not available", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "DriveDebug: Error accessing Drive")
                        Toast.makeText(context, "Drive error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Drive Access (Debug)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sign out & forget device button
        OutlinedButton(
            onClick = {
                Timber.d("SettingsScreen: Sign out & forget device clicked")
                Modules.resetForAccountChange()
                AccountScopeManager.clearActiveAccount(context)
                AuthManager.signOut(context) {
                    scope.launch { 
                        userPrefs.setRememberDevice(context, false)
                        Timber.d("SettingsScreen: Device forgotten, navigating to signin")
                        
                        // Navigate to signin and clear back stack
                        navController.navigate("signin") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out & forget device")
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
