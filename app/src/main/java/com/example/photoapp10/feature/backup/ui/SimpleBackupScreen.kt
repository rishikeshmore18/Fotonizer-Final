package com.example.photoapp10.feature.backup.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.backup.domain.CloudArchiveManager
import com.example.photoapp10.feature.backup.domain.SimpleBackupManager
import com.example.photoapp10.feature.settings.data.UserPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SimpleBackupScreen() {
    Timber.i("SimpleBackupScreen: Starting")
    val app = LocalContext.current.applicationContext as Application
    val vm: SimpleBackupViewModel = viewModel(factory = SimpleBackupViewModel.factory(app))

    val backupInfo by vm.backupInfo.collectAsState()
    val backups by vm.backups.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()
    val result by vm.result.collectAsState()
    val lastArchiveTime by vm.lastArchiveTime.collectAsState()
    val wifiOnly by vm.wifiOnly.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        val message = result ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(Unit) {
        vm.loadBackupInfo()
        vm.loadLastArchiveTime()
        vm.loadNetworkPreference()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Backup & Archive",
                style = MaterialTheme.typography.titleLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Sync Network",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose which network to use for backup & sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (wifiOnly) {
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1f)
                            ) { Text("WiFi Only") }
                            OutlinedButton(
                                onClick = { vm.setNetworkPreference(false) },
                                modifier = Modifier.weight(1f)
                            ) { Text("WiFi + Data") }
                        } else {
                            OutlinedButton(
                                onClick = { vm.setNetworkPreference(true) },
                                modifier = Modifier.weight(1f)
                            ) { Text("WiFi Only") }
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1f)
                            ) { Text("WiFi + Data") }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Local Backup",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        vm.getBackupFolderPath(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "This folder is accessible via the Files app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    backupInfo?.let { info ->
                        HorizontalDivider()
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                        Text(
                            "Latest Backup",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("Created: ${dateFormat.format(Date(info.createdAt))}")
                        Text("Albums: ${info.albumsCount}")
                        Text("Photos: ${info.photosCount}")
                        Text("Size: ${formatBytes(info.totalSizeBytes)}")
                        Text("Saved backups: ${backups.size}")
                        if (info.isLegacy) {
                            Text(
                                "Using a legacy single-folder backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { vm.createBackup() },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text("Create Backup")
                        }

                        Button(
                            onClick = { showRestoreDialog = true },
                            enabled = !isProcessing && backups.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text("Restore Backup")
                        }
                    }

                    if (backups.isEmpty()) {
                        Text(
                            "No backup found. Create a backup first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isProcessing) {
                        Text(
                            "Processing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    result?.let { resultText ->
                        HorizontalDivider()
                        Text(
                            "Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            resultText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Cloud Archive",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Permanent cloud backup that never deletes data. Only adds new items. ${formatLastArchiveTime(lastArchiveTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { vm.archiveToCloud() },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text("Backup to Cloud")
                    }

                    Button(
                        onClick = { vm.restoreFromArchive() },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text("Restore from Cloud")
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Choose which local backup to restore. The latest backup is shown first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    backups.forEach { backup ->
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    buildString {
                                        append(dateFormat.format(Date(backup.createdAt)))
                                        if (backup.isLatest) append(" • Latest")
                                        if (backup.isLegacy) append(" • Legacy")
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Albums: ${backup.albumsCount} • Photos: ${backup.photosCount}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Size: ${formatBytes(backup.totalSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    backup.backupPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        showRestoreDialog = false
                                        vm.restoreBackup(backup.backupPath)
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Restore This Backup")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showRestoreDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

class SimpleBackupViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPrefs(app)

    private val backupManager = SimpleBackupManager(
        context = app,
        db = Modules.provideDb(app),
        storage = Modules.provideStorage(app),
        prefs = prefs
    )

    private val archiveManager = CloudArchiveManager(
        context = app,
        db = Modules.provideDb(app),
        storage = Modules.provideStorage(app),
        thumbnailer = Modules.provideThumbnailer()
    )
    private val archiveQueueManager = Modules.provideCloudArchiveQueueManager(app)

    private val _backupInfo = MutableStateFlow<SimpleBackupManager.BackupInfo?>(null)
    val backupInfo: StateFlow<SimpleBackupManager.BackupInfo?> = _backupInfo.asStateFlow()

    private val _backups = MutableStateFlow<List<SimpleBackupManager.BackupInfo>>(emptyList())
    val backups: StateFlow<List<SimpleBackupManager.BackupInfo>> = _backups.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    private val _lastArchiveTime = MutableStateFlow<Long?>(null)
    val lastArchiveTime: StateFlow<Long?> = _lastArchiveTime.asStateFlow()

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val lastArchiveAtFlow = prefs.lastArchiveAtFlow

    fun getBackupFolderPath(): String = backupManager.getDefaultBackupFolderPath()

    fun loadBackupInfo() {
        viewModelScope.launch {
            try {
                val backupList = backupManager.listBackups()
                _backups.value = backupList
                _backupInfo.value = backupList.firstOrNull()
                Timber.d("SimpleBackupViewModel: Loaded backup info: ${_backupInfo.value}")
            } catch (e: Exception) {
                Timber.e(e, "SimpleBackupViewModel: Failed to load backup info")
            }
        }
    }

    fun loadLastArchiveTime() {
        viewModelScope.launch {
            try {
                val timestamp = lastArchiveAtFlow.first()
                _lastArchiveTime.value = if (timestamp > 0L) timestamp else null
                Timber.d("SimpleBackupViewModel: Loaded last archive time: $timestamp")
            } catch (e: Exception) {
                Timber.e(e, "SimpleBackupViewModel: Failed to load last archive time")
            }
        }
    }

    fun loadNetworkPreference() {
        viewModelScope.launch {
            try {
                _wifiOnly.value = prefs.wifiOnlyFlow.first()
            } catch (e: Exception) {
                Timber.e(e, "SimpleBackupViewModel: Failed to load network preference")
            }
        }
    }

    fun setNetworkPreference(wifiOnly: Boolean) {
        viewModelScope.launch {
            try {
                prefs.setWifiOnly(wifiOnly)
                _wifiOnly.value = wifiOnly
                Timber.d("SimpleBackupViewModel: Network preference set to wifiOnly=$wifiOnly")
            } catch (e: Exception) {
                Timber.e(e, "SimpleBackupViewModel: Failed to set network preference")
            }
        }
    }

    fun createBackup() {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _result.value = "Creating local backup..."

        viewModelScope.launch {
            try {
                val result = backupManager.createBackup()

                _result.value = if (result.success) {
                    buildString {
                        appendLine("Backup created successfully")
                        appendLine()
                        appendLine("Albums: ${result.albumsCount}")
                        appendLine("Photos: ${result.photosCount}")
                        appendLine("Photos copied: ${result.photosCopied}")
                        if (result.photosMissing > 0) {
                            appendLine("Photos missing: ${result.photosMissing}")
                        }
                        appendLine("Thumbnails copied: ${result.thumbsCopied}")
                        if (result.thumbsMissing > 0) {
                            appendLine("Thumbnails missing: ${result.thumbsMissing}")
                        }
                        appendLine()
                        appendLine("Backup saved to:")
                        appendLine(result.backupPath)
                    }
                } else {
                    result.message.ifBlank { "Backup failed for an unknown reason." }
                }

                loadBackupInfo()
            } catch (e: Exception) {
                Timber.e(e, "SimpleBackupViewModel: Create backup failed")
                _result.value = "Backup failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun restoreBackup(backupPath: String? = null) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _result.value = "Restoring local backup..."

        viewModelScope.launch {
            try {
                val result = if (backupPath.isNullOrBlank()) {
                    backupManager.restoreBackup()
                } else {
                    backupManager.restoreBackup(backupPath)
                }

                _result.value = if (result.success) {
                    buildString {
                        appendLine("Restore completed successfully")
                        appendLine()
                        appendLine("Albums:")
                        appendLine("  - Inserted: ${result.albumsInserted}")
                        appendLine("  - Updated: ${result.albumsUpdated}")
                        appendLine()
                        appendLine("Photos:")
                        appendLine("  - Inserted: ${result.photosInserted}")
                        appendLine("  - Updated: ${result.photosUpdated}")
                        if (result.photosMissing > 0) {
                            appendLine("  - Missing files: ${result.photosMissing}")
                        }
                        appendLine()
                        appendLine("Used merge mode (kept existing data, updated with newer items)")
                    }
                } else {
                    result.message.ifBlank { "Restore failed for an unknown reason." }
                }

                loadBackupInfo()
            } catch (e: Exception) {
                Timber.e(e, "SimpleBackupViewModel: Restore backup failed")
                _result.value = "Restore failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun archiveToCloud() {
        viewModelScope.launch {
            try {
                _isProcessing.value = false
                _result.value = null
                archiveQueueManager.requestArchive(reason = "manual_backup", force = true)
                _result.value = buildString {
                    appendLine("Cloud backup queued")
                    appendLine()
                    appendLine("The archive will upload in the background.")
                    appendLine("If the device is offline, it will start automatically when the network matches your backup preference.")
                }
                return@launch
            } catch (e: Exception) {
                Timber.e(e, "Cloud archive failed")
                _result.value = "Archive failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun restoreFromArchive() {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _result.value = "Restoring from cloud archive..."

        viewModelScope.launch {
            try {
                val result = archiveManager.restoreFromArchive()

                _result.value = if (result.success) {
                    buildString {
                        appendLine("Archive restore completed")
                        appendLine()
                        appendLine("Albums restored: ${result.albumsRestored}")
                        appendLine("Photos restored: ${result.photosRestored}")
                        if (result.photosSkipped > 0) {
                            appendLine("Photos skipped: ${result.photosSkipped}")
                        }
                        appendLine()
                        appendLine("Only missing items were restored - existing data preserved")
                    }
                } else {
                    result.message.ifBlank { "Archive restore failed for an unknown reason." }
                }
            } catch (e: Exception) {
                Timber.e(e, "Archive restore failed")
                _result.value = "Restore failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SimpleBackupViewModel(app) as T
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$bytes bytes"
    }
}

private fun formatLastArchiveTime(timestamp: Long?): String {
    return if (timestamp != null) {
        val date = Date(timestamp)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        "Last backed up at ${timeFormat.format(date)} on ${dateFormat.format(date)}"
    } else {
        "The app will automatically archive at 11 PM every night"
    }
}
