package com.example.photoapp10.feature.album.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.photoapp10.R
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.selection.rememberSelectionState
import com.example.photoapp10.feature.backup.SyncState
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.photo.ui.PhotosGrid
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(albumId: Long, nav: NavController) {
    // Add safety check for albumId
    if (albumId <= 0) {
        androidx.compose.material3.Text("Invalid album ID")
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vm: AlbumDetailViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AlbumDetailViewModel(
                    app = nav.context.applicationContext as android.app.Application,
                    albumId = albumId
                ) as T
            }
        }
    )

    val photos by vm.photos.collectAsState()
    val sort by vm.sort.collectAsState()
    val album by vm.album.collectAsState()
    val successMessage by vm.successMessage.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    
    // Selection state for photos
    val selectionState = rememberSelectionState<com.example.photoapp10.feature.photo.data.PhotoEntity>()
    
    // Cloud sync state
    val syncManager = Modules.provideDriveSyncManager(context)
    val syncState by syncManager.state.collectAsState()
    
    // Track current action mode
    var currentAction by remember { mutableStateOf<String?>(null) } // "favorite", "share", "delete"
    
    // Auto-enter selection mode when currentAction is set
    LaunchedEffect(currentAction) {
        if (currentAction != null && !selectionState.isSelectionMode.value) {
            // Enter selection mode when an action is triggered from normal mode
            selectionState.enterSelectionMode()
        }
    }
    
    // Handle success message display and auto-dismiss
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            // Auto-dismiss the message after 3 seconds
            kotlinx.coroutines.delay(3000)
            vm.clearSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = {
            // Display success message as snackbar
            val message = successMessage
            if (message != null) {
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        androidx.compose.material3.TextButton(
                            onClick = { vm.clearSuccessMessage() }
                        ) {
                            androidx.compose.material3.Text("Dismiss")
                        }
                    }
                ) {
                    androidx.compose.material3.Text(message)
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (selectionState.isSelectionMode.value) {
                            "${selectionState.getSelectedCount()} selected"
                        } else {
                            album?.name ?: "Album"
                        }
                    ) 
                },
                navigationIcon = {
                    if (selectionState.isSelectionMode.value) {
                        IconButton(onClick = { 
                            selectionState.clearSelection()
                            currentAction = null
                        }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Cancel selection"
                            )
                        }
                    } else {
                        IconButton(onClick = { nav.navigateUp() }) {
                            Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectionState.isSelectionMode.value && currentAction != null) {
                        // Done button for action mode
                        IconButton(onClick = {
                            try {
                                val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                if (selectedPhotoIds.isNotEmpty()) {
                                    when (currentAction) {
                                        "favorite" -> vm.toggleFavorites(selectedPhotoIds)
                                        "share" -> vm.sharePhotos(selectedPhotoIds)
                                        "delete" -> vm.deleteAllPhotos(selectedPhotoIds)
                                    }
                                }
                                selectionState.clearSelection()
                                currentAction = null
                            } catch (e: Exception) {
                                // Handle any errors gracefully
                                selectionState.clearSelection()
                                currentAction = null
                            }
                        }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_input_add),
                                contentDescription = "Done"
                            )
                        }
                    } else if (!selectionState.isSelectionMode.value) {
                        // Cloud sync indicator
                        IconButton(
                            onClick = {
                                if (syncState == SyncState.Idle || syncState == SyncState.Done || syncState == SyncState.Error) {
                                    syncManager.requestSync("manual_trigger")
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (syncState) {
                                    SyncState.Syncing -> {
                                        // Animated rotating sync icon
                                        val infiniteTransition = rememberInfiniteTransition(label = "sync")
                                        val rotation by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "rotation"
                                        )
                                        Icon(
                                            painter = painterResource(R.drawable.sync_24),
                                            contentDescription = "Syncing",
                                            modifier = Modifier
                                                .size(24.dp)
                                                .graphicsLayer { rotationZ = rotation }
                                        )
                                    }
                                    SyncState.Done -> Icon(
                                        painter = painterResource(R.drawable.cloud_done_24),
                                        contentDescription = "Sync Complete",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    SyncState.Error -> Text(
                                        text = "❌", 
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    else -> Icon(
                                        painter = painterResource(R.drawable.cloud_24),
                                        contentDescription = "Cloud Sync",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        
                        // Sort button in top right corner
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                                contentDescription = "Sort"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionState.isSelectionMode.value && currentAction != null) {
                        // Action mode - show cancel button
                        IconButton(onClick = { 
                            selectionState.clearSelection()
                            currentAction = null
                        }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Cancel"
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Show action type
                        Text(
                            text = when (currentAction) {
                                "favorite" -> "Select photos to favorite"
                                "share" -> "Select photos to share"
                                "delete" -> "Select photos to delete"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Done button
                        IconButton(onClick = {
                            try {
                                val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                if (selectedPhotoIds.isNotEmpty()) {
                                    when (currentAction) {
                                        "favorite" -> vm.toggleFavorites(selectedPhotoIds)
                                        "share" -> vm.sharePhotos(selectedPhotoIds)
                                        "delete" -> vm.deleteAllPhotos(selectedPhotoIds)
                                    }
                                }
                                selectionState.clearSelection()
                                currentAction = null
                            } catch (e: Exception) {
                                // Handle any errors gracefully
                                selectionState.clearSelection()
                                currentAction = null
                            }
                        }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_input_add),
                                contentDescription = "Done"
                            )
                        }
                    } else {
                        // Normal mode - show regular buttons
                        IconButton(onClick = { nav.navigate("search/$albumId") }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_search),
                                contentDescription = "Search"
                            )
                        }
                        
                        IconButton(
                            onClick = { 
                                // Navigate to CameraX screen for this album
                                nav.navigate("camera/$albumId")
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_camera),
                                contentDescription = "Camera",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.menu_24),
                                    contentDescription = "Menu"
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (selectionState.isSelectionMode.value) {
                                    // Selection mode menu items
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = { 
                                            // If photos are already selected, share them immediately
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.sharePhotos(selectedPhotoIds)
                                                selectionState.clearSelection()
                                            } else {
                                                // No photos selected, enter selection mode for share
                                                currentAction = "share"
                                            }
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_share),
                                                contentDescription = "Share"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Favorite") },
                                        onClick = { 
                                            // Favorite selected photos
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            vm.toggleFavorites(selectedPhotoIds)
                                            selectionState.clearSelection()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.btn_star_big_on),
                                                contentDescription = "Favorite"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = { 
                                            // Delete selected photos
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            vm.deleteAllPhotos(selectedPhotoIds)
                                            selectionState.clearSelection()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_delete),
                                                contentDescription = "Delete"
                                            )
                                        }
                                    )
                                } else {
                                    // Normal mode menu items
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = { 
                                            photos.forEach { photo ->
                                                selectionState.toggleSelection(photo)
                                            }
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_agenda),
                                                contentDescription = "Select All"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = { 
                                            // Enter selection mode for share
                                            currentAction = "share"
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_share),
                                                contentDescription = "Share"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Favorite") },
                                        onClick = { 
                                            // Enter selection mode for favorite
                                            currentAction = "favorite"
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.btn_star_big_on),
                                                contentDescription = "Favorite"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete All") },
                                        onClick = { 
                                            showDeleteAllDialog = true
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_delete),
                                                contentDescription = "Delete All"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { inner ->
        if (photos.isEmpty()) {
            EmptyAlbum()
        } else {
            PhotosGrid(
                albumId = albumId, 
                onPhotoClick = { p ->
                    nav.navigate("photo/${p.id}/$albumId")
                },
                selectionState = selectionState,
                onToggleFavorite = { photoId -> vm.toggleFavorite(photoId) },
                modifier = Modifier.padding(inner)
            )
        }
    }
    
    // Sort menu - simplified with only newest and oldest
    if (showSortMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { androidx.compose.material3.Text("Sort Photos") },
            text = { androidx.compose.material3.Text("Choose how to sort your photos") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        vm.setSort(SortMode.DATE_NEW)
                        showSortMenu = false
                    }
                ) {
                    androidx.compose.material3.Text("📅 Newest First")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        vm.setSort(SortMode.DATE_OLD)
                        showSortMenu = false
                    }
                ) {
                    androidx.compose.material3.Text("📅 Oldest First")
                }
            }
        )
    }
    
    // Delete All confirmation dialog
    if (showDeleteAllDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { androidx.compose.material3.Text("Delete All Photos") },
            text = { androidx.compose.material3.Text("Are you sure you want to delete all ${photos.size} photos? This action cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        // Delete all photos
                        val photoIds = photos.map { it.id }
                        vm.deleteAllPhotos(photoIds)
                        showDeleteAllDialog = false
                    }
                ) {
                    androidx.compose.material3.Text("Delete All")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteAllDialog = false }
                ) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun EmptyAlbum() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No photos in this album")
    }
}