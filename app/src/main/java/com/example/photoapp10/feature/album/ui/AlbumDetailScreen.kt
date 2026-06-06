package com.example.photoapp10.feature.album.ui

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.rishikeshmore.fotonizer.R
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.selection.rememberSelectionState
import com.example.photoapp10.core.selection.SelectionBadge
import com.example.photoapp10.core.selection.selectionBorder
import com.example.photoapp10.core.copymove.rememberCopyMoveState
import com.example.photoapp10.core.copymove.CopyMoveService
import com.example.photoapp10.feature.album.data.AlbumEntity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.example.photoapp10.feature.backup.SyncState
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.photo.ui.StaticPhotosGrid
import com.example.photoapp10.ui.components.FloatingBottomBar
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
    val subAlbums by vm.subAlbums.collectAsState()
    val sort by vm.sort.collectAsState()
    val album by vm.album.collectAsState()
    val breadcrumbPath by vm.breadcrumbPath.collectAsState()
    val successMessage by vm.successMessage.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showNewAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    
    // Selection state for photos
    val selectionState = rememberSelectionState<com.example.photoapp10.feature.photo.data.PhotoEntity>()
    
    // Selection state for albums (sub-albums)
    val albumSelectionState = rememberSelectionState<AlbumEntity>()
    
    // Copy/Move state
    val copyMoveState = rememberCopyMoveState()
    
    // Cloud sync state
    val syncManager = Modules.provideDriveSyncManager(context)
    val syncState by syncManager.state.collectAsState()
    
    // Track current action mode
    var currentAction by remember { mutableStateOf<String?>(null) } // "favorite", "share", "delete"
    
    // Progress dialog state
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(1) }
    
    // Auto-enter selection mode when currentAction is set
    LaunchedEffect(currentAction) {
        if (currentAction != null && !selectionState.isSelectionMode.value) {
            // Enter selection mode when an action is triggered from normal mode
            selectionState.enterSelectionMode()
            albumSelectionState.enterSelectionMode() // Also enter selection mode for albums
        }
    }
    
    // Sync selection modes - when one enters selection mode, the other should too
    LaunchedEffect(selectionState.isSelectionMode.value) {
        if (selectionState.isSelectionMode.value && !albumSelectionState.isSelectionMode.value) {
            albumSelectionState.enterSelectionMode()
        } else if (!selectionState.isSelectionMode.value && albumSelectionState.isSelectionMode.value) {
            // If photos exit selection mode but albums are still selected, keep albums in selection mode
            // Only exit if albums are also empty
            if (albumSelectionState.getSelectedCount() == 0) {
                albumSelectionState.exitSelectionMode()
            }
        }
    }
    
    LaunchedEffect(albumSelectionState.isSelectionMode.value) {
        if (albumSelectionState.isSelectionMode.value && !selectionState.isSelectionMode.value) {
            selectionState.enterSelectionMode()
        } else if (!albumSelectionState.isSelectionMode.value && selectionState.isSelectionMode.value) {
            // If albums exit selection mode but photos are still selected, keep photos in selection mode
            // Only exit if photos are also empty
            if (selectionState.getSelectedCount() == 0) {
                selectionState.exitSelectionMode()
            }
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

    val subAlbumsMissingCover = remember(subAlbums) {
        subAlbums.any { child -> child.id > 0 && (child.coverPhotoId == null || child.coverPhotoId <= 0L) }
    }

    LaunchedEffect(subAlbumsMissingCover, subAlbums.size) {
        if (!subAlbumsMissingCover) return@LaunchedEffect
        vm.updateSubAlbumCovers()
    }
    
    // Note: Back button is allowed to work in copy/move mode
    // The state persists across navigation, so users can navigate between albums
    // and paste in different locations. The mode only exits via Paste or Cancel buttons.

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
                    if (selectionState.isSelectionMode.value || albumSelectionState.isSelectionMode.value) {
                        val totalSelected = selectionState.getSelectedCount() + albumSelectionState.getSelectedCount()
                        Text("$totalSelected selected")
                    } else {
                        // Show breadcrumb path
                        val breadcrumbs = breadcrumbPath
                        if (breadcrumbs.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                breadcrumbs.forEachIndexed { index, album ->
                                    if (index > 0) {
                                        Text(" > ", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        album.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Text(album?.name ?: "Album")
                        }
                    }
                },
                navigationIcon = {
                    when {
                        // In copy/move mode - show back button, state persists across navigation
                        copyMoveState.hasPendingOperation() -> {
                            IconButton(onClick = { nav.navigateUp() }) {
                                Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back")
                            }
                        }
                        // In selection mode - show cancel button
                        (selectionState.isSelectionMode.value || albumSelectionState.isSelectionMode.value) -> {
                            IconButton(onClick = { 
                                selectionState.clearSelection()
                                albumSelectionState.clearSelection()
                                currentAction = null
                            }) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                    contentDescription = "Cancel selection"
                                )
                            }
                        }
                        // Normal mode - show back button
                        else -> {
                            IconButton(onClick = { nav.navigateUp() }) {
                                Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back")
                            }
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
            BottomAppBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                when {
                    // Paste mode - show Paste/Cancel
                    copyMoveState.hasPendingOperation() -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel button
                            TextButton(onClick = { 
                                copyMoveState.clear()
                            }) {
                                Text("Cancel")
                            }
                            
                            // Paste button
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        showProgressDialog = true
                                        progressMessage = "Pasting..."
                                        progressCurrent = 0
                                        progressTotal = 1
                                        
                                        val copyMoveService = CopyMoveService(
                                            albumDao = Modules.provideDb(context).albumDao(),
                                            photoDao = Modules.provideDb(context).photoDao(),
                                            albumRepo = Modules.provideAlbumRepository(context),
                                            photoRepo = Modules.providePhotoRepository(context),
                                            storage = Modules.provideStorage(context)
                                        )
                                        
                                        val albumIds = copyMoveState.getSelectedAlbumIds()
                                        val photoIds = copyMoveState.getSelectedPhotoIds()
                                        val operationType = copyMoveState.operationType.value
                                        
                                        val result = if (operationType == com.example.photoapp10.core.copymove.CopyMoveState.OperationType.COPY) {
                                            copyMoveService.copyItems(
                                                albumIds = albumIds,
                                                photoIds = photoIds,
                                                targetAlbumId = albumId, // Paste into current album
                                                progressCallback = { msg, current, total ->
                                                    progressMessage = msg
                                                    progressCurrent = current
                                                    progressTotal = total
                                                }
                                            )
                                        } else {
                                            copyMoveService.moveItems(
                                                albumIds = albumIds,
                                                photoIds = photoIds,
                                                targetAlbumId = albumId, // Paste into current album
                                                progressCallback = { msg, current, total ->
                                                    progressMessage = msg
                                                    progressCurrent = current
                                                    progressTotal = total
                                                }
                                            )
                                        }
                                        
                                        showProgressDialog = false
                                        
                                        if (result.isSuccess) {
                                            Toast.makeText(
                                                context,
                                                "${operationType?.name ?: "Operation"} completed: ${result.albumsCopied} albums, ${result.photosCopied} items",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Operation completed with errors: ${result.errors.joinToString()}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        
                                        copyMoveState.clear()
                                        selectionState.clearSelection()
                                        albumSelectionState.clearSelection()
                                    } catch (e: Exception) {
                                        showProgressDialog = false
                                        Toast.makeText(
                                            context,
                                            "Error: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        timber.log.Timber.e(e, "Error during paste operation")
                                    }
                                }
                            }) {
                                Text("Paste")
                            }
                            
                            // Hamburger menu
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
                                    // Menu items can be added here if needed in paste mode
                                    // For now, keeping it simple
                                }
                            }
                        }
                    }
                    
                    // Selection mode - show Copy/Move/Cancel (for photos or albums)
                    (selectionState.isSelectionMode.value || albumSelectionState.isSelectionMode.value) && currentAction == null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cancel button
                            TextButton(onClick = { 
                                selectionState.clearSelection()
                                albumSelectionState.clearSelection()
                            }) {
                                Text("Cancel")
                            }
                            
                            // Copy button
                            Button(onClick = {
                                val selectedAlbums = albumSelectionState.getSelectedItems()
                                val selectedPhotos = selectionState.getSelectedItems()
                                copyMoveState.startCopy(selectedAlbums, selectedPhotos)
                                selectionState.clearSelection()
                                albumSelectionState.clearSelection()
                            }) {
                                Text("Copy")
                            }
                            
                            // Move button
                            Button(onClick = {
                                val selectedAlbums = albumSelectionState.getSelectedItems()
                                val selectedPhotos = selectionState.getSelectedItems()
                                copyMoveState.startMove(selectedAlbums, selectedPhotos)
                                selectionState.clearSelection()
                                albumSelectionState.clearSelection()
                            }) {
                                Text("Move")
                            }
                            
                            // Hamburger menu
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
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = {
                                            // Select all photos and albums
                                            selectionState.selectAll(photos)
                                            albumSelectionState.selectAll(subAlbums)
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
                                            // Share selected photos (albums can't be shared)
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.sharePhotos(selectedPhotoIds)
                                            }
                                            selectionState.clearSelection()
                                            albumSelectionState.clearSelection()
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
                                            // Favorite selected photos and albums
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.toggleFavorites(selectedPhotoIds)
                                            }
                                            val selectedAlbums = albumSelectionState.getSelectedItems()
                                            selectedAlbums.forEach { album ->
                                                vm.toggleFavoriteAlbum(album.id)
                                            }
                                            selectionState.clearSelection()
                                            albumSelectionState.clearSelection()
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
                                            // Delete selected photos and albums
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.deleteAllPhotos(selectedPhotoIds)
                                            }
                                            val selectedAlbums = albumSelectionState.getSelectedItems()
                                            selectedAlbums.forEach { album ->
                                                vm.deleteAlbum(album)
                                            }
                                            selectionState.clearSelection()
                                            albumSelectionState.clearSelection()
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_delete),
                                                contentDescription = "Delete"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Action mode (favorite/share/delete) - show cancel and done
                    selectionState.isSelectionMode.value && currentAction != null -> {
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
                    }
                    
                    // Normal mode - show regular buttons
                    else -> {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            FloatingBottomBar(
                                leftIcon = painterResource(R.drawable.create_new_folder_24),
                                leftLabel = "Album",
                                onLeftClick = { showNewAlbumDialog = true },
                                centerIcon = painterResource(android.R.drawable.ic_menu_camera),
                                centerLabel = "Camera",
                                onCenterClick = { nav.navigate("camera/$albumId") },
                                rightIcon = painterResource(R.drawable.menu_24),
                                rightLabel = "More",
                                onRightClick = { showMenu = true }
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 28.dp)
                            ) {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (selectionState.isSelectionMode.value || albumSelectionState.isSelectionMode.value) {
                                    // Selection mode menu items - handle both photos and albums
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = { 
                                            // Share selected photos (albums can't be shared)
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.sharePhotos(selectedPhotoIds)
                                            }
                                            selectionState.clearSelection()
                                            albumSelectionState.clearSelection()
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
                                            // Favorite selected photos and albums
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.toggleFavorites(selectedPhotoIds)
                                            }
                                            val selectedAlbums = albumSelectionState.getSelectedItems()
                                            selectedAlbums.forEach { album ->
                                                vm.toggleFavoriteAlbum(album.id)
                                            }
                                            selectionState.clearSelection()
                                            albumSelectionState.clearSelection()
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
                                            // Delete selected photos and albums
                                            val selectedPhotoIds = selectionState.getSelectedItems().map { it.id }
                                            if (selectedPhotoIds.isNotEmpty()) {
                                                vm.deleteAllPhotos(selectedPhotoIds)
                                            }
                                            val selectedAlbums = albumSelectionState.getSelectedItems()
                                            selectedAlbums.forEach { album ->
                                                vm.deleteAlbum(album)
                                            }
                                            selectionState.clearSelection()
                                            albumSelectionState.clearSelection()
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
                                    ModernPhotoActionsMenuContent(
                                        onSelectAll = {
                                            // Select all photos
                                            photos.forEach { photo ->
                                                if (!selectionState.isSelected(photo)) {
                                                    selectionState.toggleSelection(photo)
                                                }
                                            }
                                            // Select all albums
                                            subAlbums.forEach { album ->
                                                if (!albumSelectionState.isSelected(album)) {
                                                    albumSelectionState.toggleSelection(album)
                                                }
                                            }
                                            showMenu = false
                                        },
                                        onShare = {
                                            // Enter selection mode for share
                                            currentAction = "share"
                                            showMenu = false
                                        },
                                        onFavorite = {
                                            // Enter selection mode for favorite
                                            currentAction = "favorite"
                                            showMenu = false
                                        },
                                        onDeleteAll = {
                                            showDeleteAllDialog = true
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    ) { inner ->
        val hasContent = subAlbums.isNotEmpty() || photos.isNotEmpty()
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                AlbumSearchBar(
                    nav = nav,
                    albumId = albumId,
                    placeholder = "Search this album"
                )
            }

            if (!hasContent) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No photos or folders in this album")
                    }
                }
            } else {
                if (subAlbums.isNotEmpty()) {
                    item {
                        Text(
                            "Folders",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    item {
                        AlbumsGridInDetail(
                            items = subAlbums,
                            onOpen = { album ->
                                nav.navigate("album/${album.id}")
                            },
                            onRename = { album ->
                                renameTarget = album
                            },
                            nav = nav,
                            vm = vm,
                            selectionState = albumSelectionState,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                if (photos.isNotEmpty()) {
                    item {
                        Text(
                            "Photos",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    item {
                        StaticPhotosGrid(
                            photos = photos,
                            onPhotoClick = { photo ->
                                nav.navigate("photo/${photo.id}/$albumId")
                            },
                            selectionState = selectionState,
                            onToggleFavorite = { photoId -> vm.toggleFavorite(photoId) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Sort menu - simplified with only newest and oldest
    if (showSortMenu) {
        ModernSortPhotosDialog(
            onDismissRequest = { showSortMenu = false },
            onNewestFirst = {
                vm.setSort(SortMode.DATE_NEW)
                showSortMenu = false
            },
            onOldestFirst = {
                vm.setSort(SortMode.DATE_OLD)
                showSortMenu = false
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
    
    // New album dialog
    if (showNewAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showNewAlbumDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newAlbumName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            val id = vm.createAlbum(name)
                            showNewAlbumDialog = false
                            newAlbumName = ""
                            if (id > 0) {
                                nav.navigate("album/$id")
                            }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewAlbumDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    // Progress dialog for copy/move operations
    if (showProgressDialog) {
        AlertDialog(
            onDismissRequest = { /* Don't allow dismissing during operation */ },
            title = { Text("Processing...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (progressTotal > 0) {
                        Text(
                            text = "$progressCurrent / $progressTotal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
    
    // Rename/emoji dialog (reuse from AlbumsScreen)
    renameTarget?.let { target ->
        RenameAlbumDialog(
            album = target,
            onDismiss = { renameTarget = null },
            onRename = { new -> 
                // Use the target album's ID for renaming
                vm.renameAlbumById(target.id, new)
                renameTarget = null 
            },
            onSetEmoji = { emoji -> 
                // Use the target album's ID for emoji assignment
                vm.setEmojiForAlbum(target.id, emoji) 
            }
        )
    }
}

@Composable
private fun ModernSortPhotosDialog(
    onDismissRequest: () -> Unit,
    onNewestFirst: () -> Unit,
    onOldestFirst: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(26.dp),
        containerColor = Color(0xFFFCF8FF),
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "Sort Photos",
                color = Color(0xFF1F2430),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose how to sort your photos",
                    color = Color(0xFF6B6476),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(14.dp))

                ModernPhotoSortOptionRow(
                    badge = "1",
                    title = "Newest First",
                    subtitle = "Recently added photos first",
                    onClick = onNewestFirst
                )
                ModernPhotoSortOptionRow(
                    badge = "9",
                    title = "Oldest First",
                    subtitle = "Earliest added photos first",
                    onClick = onOldestFirst
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF2B0B5F),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun ModernPhotoSortOptionRow(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFFF0E7FA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                color = Color(0xFF2B0B5F),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF1F2430),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF6B6476),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ModernPhotoActionsMenuContent(
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onDeleteAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(214.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false
            )
            .background(
                color = Color(0xFFFCF8FF),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        ModernPhotoActionRow(
            iconRes = android.R.drawable.ic_menu_agenda,
            title = "Select All",
            onClick = onSelectAll
        )
        ModernPhotoActionRow(
            iconRes = android.R.drawable.ic_menu_share,
            title = "Share",
            onClick = onShare
        )
        ModernPhotoActionRow(
            iconRes = android.R.drawable.btn_star_big_on,
            title = "Favorite",
            onClick = onFavorite
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            color = Color(0xFFE5DDF2),
            thickness = 1.dp
        )

        ModernPhotoActionRow(
            iconRes = android.R.drawable.ic_menu_delete,
            title = "Delete All",
            onClick = onDeleteAll
        )
    }
}

@Composable
private fun ModernPhotoActionRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFF0E7FA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                tint = Color(0xFF2B0B5F),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = title,
            color = Color(0xFF1F2430),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyAlbum(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No photos or folders in this album")
    }
}

@Composable
private fun AlbumsGridInDetail(
    items: List<AlbumEntity>,
    onOpen: (AlbumEntity) -> Unit,
    onRename: (AlbumEntity) -> Unit,
    nav: NavController,
    vm: AlbumDetailViewModel,
    selectionState: com.example.photoapp10.core.selection.SelectionState<AlbumEntity>? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { album ->
                    AlbumCardSimple(
                        album = album,
                        viewModel = vm,
                        onClick = { 
                            if (selectionState?.isSelectionMode?.value == true) {
                                selectionState?.toggleSelection(album)
                            } else {
                                onOpen(album)
                            }
                        },
                        onRename = { onRename(album) },
                        onLongPress = { 
                            selectionState?.toggleSelection(album)
                            // Ensure selection mode is entered
                            selectionState?.enterSelectionMode()
                        },
                        selectionState = selectionState,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCardSimple(
    album: AlbumEntity,
    viewModel: AlbumDetailViewModel,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    selectionState: com.example.photoapp10.core.selection.SelectionState<AlbumEntity>? = null,
    modifier: Modifier = Modifier
) {
    val isSelected = selectionState?.isSelected(album) == true
    var coverImagePath by remember(album.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(album.id, album.coverPhotoId) {
        coverImagePath = viewModel.getCoverImagePath(album)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray),
        modifier = modifier
            .selectionBorder(isSelected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongPress?.invoke()
                }
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val currentCoverPath = coverImagePath
                if (currentCoverPath != null && currentCoverPath.isNotBlank()) {
                    AsyncImage(
                        model = currentCoverPath,
                        contentDescription = "Album cover",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No cover",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Show assigned emoji if any (same style as main album cards)
                if (album.emoji?.isNotBlank() == true) {
                    Text(
                        text = album.emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(24.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
                
                // Selection badge overlay
                if (isSelected) {
                    val selectionNumber = selectionState?.getSelectionNumber(album) ?: 1
                    SelectionBadge(
                        selectionNumber = selectionNumber,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(4.dp))
                
                // Action buttons row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onRename) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_edit),
                            contentDescription = "Rename"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameAlbumDialog(
    album: AlbumEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onSetEmoji: (String?) -> Unit
) {
    var text by remember { mutableStateOf(album.name ?: "") }
    var emojiText by remember { mutableStateOf(album.emoji ?: "") }
    var emojiTextFieldValue by remember { mutableStateOf(TextFieldValue(emojiText, TextRange(emojiText.length))) }
    val context = LocalContext.current
    
    // Update emojiTextFieldValue when dialog opens
    LaunchedEffect(album.emoji) {
        val currentEmoji = album.emoji ?: ""
        emojiTextFieldValue = TextFieldValue(currentEmoji, TextRange(currentEmoji.length))
        emojiText = currentEmoji
    }
    
    // Emoji validation function
    fun isValidEmoji(text: String): Boolean {
        if (text.isEmpty()) return true // Empty is valid (unassign)
        // Check if the text contains only emoji characters
        val emojiRegex = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]+|[\u2600-\u26FF]|[\u2700-\u27BF]""")
        return emojiRegex.matches(text)
    }
    
    val isValidInput = isValidEmoji(emojiTextFieldValue.text)
    
    // Function to show notifications
    fun showNotification(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Folder") },
        text = {
            Column {
                // Album name section
                Text(
                    text = "Folder Name",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text, 
                    onValueChange = { text = it }, 
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Emoji section - always visible
                Text(
                    text = "Assign Emoji",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = emojiTextFieldValue,
                    onValueChange = { newValue ->
                        val text = newValue.text
                        // Limit to single emoji and validate
                        if (text.length <= 2 && (text.isEmpty() || isValidEmoji(text))) {
                            emojiTextFieldValue = newValue
                        }
                    },
                    label = { Text("Emoji") },
                    placeholder = { Text("Enter emoji or leave empty to remove") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValidInput) {
                                onSetEmoji(emojiTextFieldValue.text.ifBlank { null })
                            }
                        }
                    ),
                    isError = !isValidInput && emojiTextFieldValue.text.isNotEmpty()
                )
                
                if (!isValidInput && emojiTextFieldValue.text.isNotEmpty()) {
                    Text(
                        text = "Please enter a valid emoji",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { 
                    Text("Cancel", color = MaterialTheme.colorScheme.onPrimary) 
                }
                Button(
                    onClick = { 
                        val originalName = album.name ?: ""
                        val originalEmoji = album.emoji ?: ""
                        val newName = text.trim()
                        val newEmoji = emojiTextFieldValue.text.trim().ifBlank { null }
                        
                        // Handle rename
                        if (newName != originalName) {
                            onRename(newName)
                            showNotification("Renamed to $newName")
                        }
                        
                        // Handle emoji assignment
                        if (isValidInput) {
                            onSetEmoji(newEmoji)
                            when {
                                newEmoji != null && originalEmoji.isEmpty() -> showNotification("Emoji assigned $newEmoji")
                                newEmoji != null && originalEmoji != newEmoji -> showNotification("Emoji updated to $newEmoji")
                                newEmoji == null && originalEmoji.isNotEmpty() -> showNotification("Emoji removed")
                            }
                        }
                        
                        // Always close dialog after save
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { 
                    Text("Save", color = MaterialTheme.colorScheme.onPrimary) 
                }
            }
        },
        dismissButton = null
    )
}
