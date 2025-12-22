package com.example.photoapp10.feature.backup.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.photoapp10.feature.backup.domain.LocalBackup
import com.example.photoapp10.feature.backup.domain.LocalBackup.ImportOptions
import com.example.photoapp10.feature.backup.domain.LocalBackup.ImportOptions.Mode
import com.example.photoapp10.feature.backup.domain.ExportReport
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun BackupScreen() {
    Timber.i("BackupScreen: BackupScreen composable started")
    val app = LocalContext.current.applicationContext as Application
    Timber.i("BackupScreen: Got Application context")
    val vm: BackupViewModel = viewModel(factory = BackupViewModel.factory(app))
    Timber.i("BackupScreen: BackupViewModel created successfully")

    val scope = rememberCoroutineScope()
    var exportDirUri by remember { mutableStateOf<Uri?>(null) }
    var importDirUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAlbumIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showAlbumSelector by remember { mutableStateOf(false) }

    val albums by vm.albums.collectAsState(initial = emptyList())
    
    // Track albums changes
    LaunchedEffect(albums) {
        Timber.i("BackupScreen: Albums changed, count: ${albums.size}, ids: ${albums.map { it.id }}")
    }
    
    // Track button state changes
    LaunchedEffect(selectedAlbumIds) {
        Timber.i("BackupScreen: Selected albums changed: ${selectedAlbumIds.size} albums")
    }

    // SAF launchers
    val pickExportDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        Timber.i("BackupScreen: Export folder selected: $uri")
        exportDirUri = uri
        if (uri != null) {
            // Persist permission
            app.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Timber.i("BackupScreen: Permission persisted for export folder")
        }
    }
    val pickImportDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        importDirUri = uri
        if (uri != null) {
            app.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    val progress by vm.progress.collectAsState()
    val report by vm.report.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Local Backup", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export", style = MaterialTheme.typography.titleMedium)

                // Album selection
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // First row: Select All and Custom buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                Timber.i("BackupScreen: Select All clicked, albums: ${albums.map { it.id }}")
                                selectedAlbumIds = albums.map { it.id }.toSet()
                                Timber.i("BackupScreen: Selected albums after Select All: $selectedAlbumIds")
                            },
                            modifier = Modifier.weight(1f)
                        ) { 
                            Text("Select All (${albums.size})") 
                        }
                        Button(
                            onClick = { showAlbumSelector = true },
                            modifier = Modifier.weight(1f)
                        ) { 
                            Text("Custom (${selectedAlbumIds.size})") 
                        }
                    }
                    
                    // Second row: Clear Selection button
                    Button(
                        onClick = { 
                            Timber.i("BackupScreen: Clear Selection clicked")
                            selectedAlbumIds = emptySet()
                            Timber.i("BackupScreen: Selected albums after Clear Selection: $selectedAlbumIds")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Text("Clear Selection") 
                    }
                }

                Text(
                    "Selected: ${selectedAlbumIds.size} albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { 
                        Timber.i("BackupScreen: Choose Export Folder clicked")
                        pickExportDir.launch(null) 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Text("Choose Export Folder") 
                }

                if (exportDirUri != null) {
                    Text(
                        "Export to: ${exportDirUri.toString().substringAfterLast("/")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = {
                            Timber.i("BackupScreen: Start Export button clicked")
                            Timber.i("BackupScreen: Export URI: $exportDirUri")
                            Timber.i("BackupScreen: Selected albums: $selectedAlbumIds")
                            scope.launch {
                                exportDirUri?.let { uri ->
                                    vm.export(uri, selectedAlbumIds.toList())
                                }
                            }
                        },
                        enabled = selectedAlbumIds.isNotEmpty() && progress == null,
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Text("Start Export") 
                    }
                }
            }
        }

        Divider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Import", style = MaterialTheme.typography.titleMedium)

                Button(
                    onClick = { pickImportDir.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Text("Choose Import Folder") 
                }

                if (importDirUri != null) {
                    Text(
                        "Import from: ${importDirUri.toString().substringAfterLast("/")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    vm.import(importDirUri, ImportOptions(Mode.MERGE_LATEST_WINS))
                                }
                            },
                            enabled = progress == null,
                            modifier = Modifier.weight(1f)
                        ) { 
                            Text("Merge") 
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    vm.import(importDirUri, ImportOptions(Mode.REPLACE_ALL))
                                }
                            },
                            enabled = progress == null,
                            modifier = Modifier.weight(1f)
                        ) { 
                            Text("Replace") 
                        }
                    }

                    Text(
                        "Merge: Keep existing data, update with newer items\nReplace: Delete all data, restore from backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (progress != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(progress ?: "", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (report != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Report", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(report ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Album selector dialog
    if (showAlbumSelector) {
        AlbumSelectorDialog(
            albums = albums,
            selectedIds = selectedAlbumIds,
            onSelectionChanged = { selectedAlbumIds = it },
            onDismiss = { showAlbumSelector = false }
        )
    }
}

@Composable
private fun AlbumSelectorDialog(
    albums: List<AlbumEntity>,
    selectedIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Albums") },
        text = {
            LazyColumn {
                items(albums) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(album.id),
                            onCheckedChange = { checked ->
                                onSelectionChanged(
                                    if (checked) selectedIds + album.id
                                    else selectedIds - album.id
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(album.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${album.photoCount} photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    init {
        Timber.i("BackupViewModel: Constructor called")
    }
    
    private val backup = LocalBackup(
        context = app,
        db = Modules.provideDb(app),
        storage = Modules.provideStorage(app),
        prefs = UserPrefs(app)
    )
    private val albumDao = Modules.provideDb(app).albumDao()
    
    init {
        Timber.i("BackupViewModel: LocalBackup and albumDao created successfully")
    }

    val albums = albumDao.observeAlbums()
    
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()
    
    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report.asStateFlow()

    suspend fun export(dir: Uri, albumIds: List<Long>) {
        Timber.i("BackupViewModel.export called with albumIds: $albumIds")
        Timber.i("BackupViewModel.export called with dir: $dir")
        _progress.value = "Exporting albums and photos..."
        _report.value = null
        try {
            Timber.i("BackupViewModel.export calling backup.exportAlbums")
            val rep = backup.exportAlbums(dir, albumIds)
            Timber.i("BackupViewModel.export completed successfully")
            _progress.value = null
            _report.value = buildString {
                appendLine("✅ Export Complete")
                appendLine()
                appendLine("Albums: ${rep.albums}")
                appendLine("Photos: ${rep.photos}")
                appendLine("Files copied: ${rep.filesCopied}")
                appendLine("Files missing: ${rep.filesMissing}")
                appendLine()
                appendLine("Backup saved to:")
                appendLine("${rep.backupJsonUri}")
            }
        } catch (e: Exception) {
            Timber.e("BackupViewModel.export failed: ${e.message}", e)
            _progress.value = null
            _report.value = "❌ Export failed: ${e.message}"
        }
    }

    suspend fun import(dir: Uri?, options: ImportOptions) {
        require(dir != null) { "Pick import folder first" }
        _progress.value = "Importing from backup..."
        _report.value = null
        try {
            val r = backup.importFromFolder(dir, options)
            _progress.value = null
            _report.value = buildString {
                appendLine("✅ Import Complete (${options.mode.name.lowercase().replace('_', ' ')})")
                appendLine()
                appendLine("Albums:")
                appendLine("  • Inserted: ${r.albumsInserted}")
                appendLine("  • Updated: ${r.albumsUpdated}")
                appendLine()
                appendLine("Photos:")
                appendLine("  • Inserted: ${r.photosInserted}")
                appendLine("  • Updated: ${r.photosUpdated}")
                appendLine("  • Missing files: ${r.photosSkippedMissingFile}")
            }
        } catch (e: Exception) {
            _progress.value = null
            _report.value = "❌ Import failed: ${e.message}"
        }
    }
    

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BackupViewModel(app) as T
            }
        }
    }
}
