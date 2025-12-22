package com.example.photoapp10.feature.auth.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.backup.drive.driveAppDataOrNull
import com.example.photoapp10.feature.backup.domain.DriveRestore
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun RestoreGateScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val scope = rememberCoroutineScope()
    val userPrefs = remember { Modules.provideUserPrefs(ctx) }

    var state by remember { mutableStateOf<State>(State.Checking) }
    var progress by remember { mutableStateOf(DriveRestore.Progress("Starting…", 0, 0)) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Timber.i("RestoreGateScreen: Starting backup check...")
        try {
            val drive = driveAppDataOrNull(ctx)
            if (drive == null) {
                Timber.w("RestoreGateScreen: Drive service not available")
                state = State.NotFound // not signed in or drive build failed
            } else {
                Timber.d("RestoreGateScreen: Drive service available, checking for backup...")
                val latest = drive.findLatestBackup()
                state = if (latest == null) {
                    Timber.i("RestoreGateScreen: No backup found")
                    State.NotFound 
                } else {
                    Timber.i("RestoreGateScreen: Backup found: ${latest.name}")
                    State.Found
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "RestoreGateScreen: Error during backup check")
            state = State.NotFound
        }
    }

    when (state) {
        State.Checking -> CenterBox { 
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

        State.NotFound -> {
            CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No cloud backup found",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Starting fresh with your account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        Timber.i("RestoreGateScreen: No backup - continuing to albums")
                        scope.launch {
                            userPrefs.setRestoreGateShown(ctx, true)
                        }
                        nav.navigate("albums") { popUpTo("restore_gate") { inclusive = true } }
                    }) { 
                        Text("Continue") 
                    }
                }
            }
        }

        State.Found -> {
            CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Cloud backup found",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Restore your albums & photos metadata now?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            Timber.i("RestoreGateScreen: User chose to skip restore")
                            scope.launch {
                                userPrefs.setRestoreGateShown(ctx, true)
                            }
                            nav.navigate("albums") { popUpTo("restore_gate") { inclusive = true } }
                        }) { 
                            Text("Skip") 
                        }
                        Button(onClick = {
                            Timber.i("RestoreGateScreen: User chose to restore backup")
                            state = State.Restoring
                            scope.launch {
                                try {
                                    // Mark restore gate as shown before starting restore
                                    userPrefs.setRestoreGateShown(ctx, true)
                                    
                                    val drive = driveAppDataOrNull(ctx) ?: return@launch
                                    val engine = DriveRestore(ctx, drive)
                                    val (albums, photos) = engine.restoreLatest(
                                        mode = DriveRestore.Mode.MERGE_LATEST_WINS
                                    ) { p -> 
                                        progress = p 
                                        Timber.d("RestoreGateScreen: Progress - ${p.step}: ${p.done}/${p.total}")
                                    }
                                    message = "Data synced completely\n$albums albums, $photos photos restored"
                                    Timber.i("RestoreGateScreen: Restore completed - $albums albums, $photos photos")
                                } catch (t: Throwable) {
                                    val errorMsg = "Restore failed: ${t.message ?: "Unknown error"}"
                                    message = errorMsg
                                    Timber.e(t, "RestoreGateScreen: Restore failed")
                                } finally {
                                    // Navigate to Albums either way
                                    kotlinx.coroutines.delay(2000) // Show message briefly
                                    nav.navigate("albums") { popUpTo("restore_gate") { inclusive = true } }
                                }
                            }
                        }) { 
                            Text("Restore") 
                        }
                    }
                }
            }
        }

        State.Restoring -> {
            CenterBox {
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

private enum class State { Checking, NotFound, Found, Restoring }

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(24.dp), 
        contentAlignment = Alignment.Center, 
        content = content
    )
}
