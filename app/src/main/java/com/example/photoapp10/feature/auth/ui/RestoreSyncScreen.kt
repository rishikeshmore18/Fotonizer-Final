package com.example.photoapp10.feature.auth.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rishikeshmore.fotonizer.R
import com.example.photoapp10.feature.backup.drive.driveAppDataOrNull
import com.example.photoapp10.feature.backup.domain.DriveRestore
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreSyncScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<RestoreState>(RestoreState.Checking) }
    var progress by remember { mutableStateOf(DriveRestore.Progress("Starting…", 0, 0)) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Timber.i("RestoreSyncScreen: Starting backup check...")
        try {
            val drive = driveAppDataOrNull(ctx)
            if (drive == null) {
                Timber.w("RestoreSyncScreen: Drive service not available")
                state = RestoreState.NotFound
            } else {
                Timber.d("RestoreSyncScreen: Drive service available, checking for backup...")
                val latest = drive.findLatestBackup()
                if (latest == null) {
                    Timber.i("RestoreSyncScreen: No backup found")
                    state = RestoreState.NotFound 
                } else {
                    Timber.i("RestoreSyncScreen: Backup found: ${latest.name}")
                    state = RestoreState.Found
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "RestoreSyncScreen: Error during backup check")
            state = RestoreState.NotFound
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Restore Sync") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                RestoreState.Checking -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Checking for cloud backup...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                RestoreState.NotFound -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_dialog_info),
                            contentDescription = "No backup",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No cloud backup found",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "There are no backups available to restore from your Google Drive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { nav.navigateUp() }) { 
                            Text("Back to Albums") 
                        }
                    }
                }

                RestoreState.Found -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_sync),
                            contentDescription = "Backup found",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Cloud backup found",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Restore your albums & photos from Google Drive backup?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { nav.navigateUp() }) { 
                                Text("Cancel") 
                            }
                            Button(onClick = {
                                Timber.i("RestoreSyncScreen: User chose to restore backup")
                                state = RestoreState.Restoring
                                scope.launch {
                                    try {
                                        val drive = driveAppDataOrNull(ctx) ?: return@launch
                                        val engine = DriveRestore(ctx, drive)
                                        val (albums, photos) = engine.restoreLatest(
                                            mode = DriveRestore.Mode.MERGE_LATEST_WINS
                                        ) { p -> 
                                            progress = p 
                                            Timber.d("RestoreSyncScreen: Progress - ${p.step}: ${p.done}/${p.total}")
                                        }
                                        message = "Data synced successfully!\n$albums albums, $photos photos restored"
                                        Timber.i("RestoreSyncScreen: Restore completed - $albums albums, $photos photos")
                                    } catch (t: Throwable) {
                                        val errorMsg = "Restore failed: ${t.message ?: "Unknown error"}"
                                        message = errorMsg
                                        Timber.e(t, "RestoreSyncScreen: Restore failed")
                                    } finally {
                                        // Show completion message briefly then navigate back
                                        kotlinx.coroutines.delay(2000)
                                        nav.navigateUp()
                                    }
                                }
                            }) { 
                                Text("Restore") 
                            }
                        }
                    }
                }

                RestoreState.Restoring -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = progress.step,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        if (progress.total > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${progress.done} / ${progress.total}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f,
                                modifier = Modifier.fillMaxWidth(0.6f)
                            )
                        }
                        
                        // Show completion message if available
                        message?.let { msg ->
                            Spacer(Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class RestoreState { Checking, NotFound, Found, Restoring }
