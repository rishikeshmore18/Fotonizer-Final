package com.example.photoapp10.feature.album.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItem
import androidx.compose.material3.Divider
import androidx.compose.material3.FabPosition
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.example.photoapp10.core.util.TimeFormat
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.album.ui.HomeSections
import com.example.photoapp10.core.selection.SelectionState
import com.example.photoapp10.core.selection.rememberSelectionState
import com.example.photoapp10.core.selection.SelectionBadge
import com.example.photoapp10.core.selection.selectionBorder
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.auth.AuthManager
import com.example.photoapp10.feature.backup.SyncState
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.R

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    nav: NavController,
    vm: AlbumsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Add comprehensive safety check for ViewModel
    if (vm == null) {
        androidx.compose.material3.Text("Error initializing view model")
        return
    }
    
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Selection state
    val selectionState = rememberSelectionState<AlbumEntity>()
    
    // New album dialog state
    var showNewAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    
    // Highlight new album state
    var highlightedAlbumId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    val albums by vm.albums.collectAsState()
    
    // Sync state observation
    val syncManager = remember { Modules.provideDriveSyncManager(context) }
    val syncState by syncManager.observeState().collectAsState()
    
    // Personalized greeting
    var greeting by remember { mutableStateOf("Hi User!") }
    
    // Update greeting when auth state changes
    LaunchedEffect(Unit) {
        greeting = "Hi ${AuthManager.getUserDisplayName(context)}!"
    }
    
    // Auto-reset sync state after showing Done/Error for a while
    LaunchedEffect(syncState) {
        if (syncState == SyncState.Done) {
            // Reset to idle after showing done for 3 seconds
            kotlinx.coroutines.delay(3000)
            syncManager.resetToIdle()
        } else if (syncState == SyncState.Error) {
            // Reset to idle after showing error for 5 seconds
            kotlinx.coroutines.delay(5000)
            syncManager.resetToIdle()
        }
    }
    
    // Add comprehensive null safety for albums
    val safeAlbums = try {
        albums ?: emptyList<AlbumEntity>()
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Error accessing albums state")
        emptyList<AlbumEntity>()
    }

    // Update cover images when albums change - safer implementation
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(100) // Small delay to ensure ViewModel is ready
            vm.updateCovers()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error updating covers in LaunchedEffect")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (selectionState.isSelectionMode.value) {
                            "${selectionState.getSelectedCount()} selected"
                        } else {
                            greeting
                        },
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    if (selectionState.isSelectionMode.value) {
                        IconButton(onClick = { selectionState.clearSelection() }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Cancel selection"
                            )
                        }
                    }
                },
                actions = {
                    // Cloud sync status indicator with animation - clickable for manual sync
                    IconButton(
                        onClick = {
                            // Trigger manual backup/sync when cloud icon is clicked
                            if (syncState == SyncState.Idle || syncState == SyncState.Done || syncState == SyncState.Error) {
                                timber.log.Timber.d("AlbumsScreen: Manual sync triggered via cloud icon")
                                syncManager.requestSync("manual_trigger")
                            } else {
                                timber.log.Timber.d("AlbumsScreen: Sync already in progress, ignoring click")
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
                    IconButton(onClick = { 
                        timber.log.Timber.d("AlbumsScreen: Sort button clicked")
                        showSortMenu = true 
                    }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                            contentDescription = "Sort"
                        )
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
                    // Create Album button on the left
                    IconButton(
                        onClick = { 
                            showNewAlbumDialog = true
                        },
                        modifier = Modifier.size(48.dp) // Standard size
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.create_new_folder_24),
                            contentDescription = "Create Album",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Camera button in the center (larger) - direct camera access
                    IconButton(
                        onClick = { 
                            // Navigate to CameraX screen for default album
                            nav.navigate("camera/0")
                        },
                        modifier = Modifier.size(72.dp) // Larger than other icons
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_camera),
                            contentDescription = "Camera",
                            modifier = Modifier.size(40.dp) // Larger icon
                        )
                    }
                    
                    // Hamburger menu on the right
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
                                        try {
                                            // Share functionality not implemented yet
                                            // This will be added in a future update
                                        } catch (e: Exception) {
                                            // Handle any errors gracefully
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
                                        try {
                                            selectionState.getSelectedItems().forEach { album ->
                                                vm.toggleFavorite(album.id)
                                            }
                                            selectionState.clearSelection()
                                        } catch (e: Exception) {
                                            // Handle any errors gracefully
                                            selectionState.clearSelection()
                                        }
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
                                        try {
                                            // Delete selected albums
                                            val selectedAlbums = selectionState.getSelectedItems()
                                            vm.deleteAlbums(selectedAlbums)
                                            selectionState.clearSelection()
                                        } catch (e: Exception) {
                                            // Handle any errors gracefully
                                            selectionState.clearSelection()
                                        }
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
                                // Normal mode menu items - Restore Sync, Local Backup, Sign Out
                                DropdownMenuItem(
                                    text = { Text("Restore Sync") },
                                    onClick = { 
                                        nav.navigate("restore_sync")
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_cloud_sync),
                                            contentDescription = "Restore Sync"
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Force Backup") },
                                    onClick = { 
                                        nav.navigate("backup")
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(android.R.drawable.ic_menu_save),
                                            contentDescription = "Force Backup"
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sign Out") },
                                    onClick = { 
                                        showMenu = false
                                        try {
                                            AuthManager.signOut(context) {
                                                // Navigate back to sign in screen after successful sign out
                                                nav.navigate("signin") {
                                                    popUpTo(0) { inclusive = true } // Clear entire navigation stack
                                                }
                                            }
                                        } catch (e: Exception) {
                                            timber.log.Timber.e(e, "AlbumsScreen: Error signing out")
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(android.R.drawable.ic_menu_revert),
                                            contentDescription = "Sign Out"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            // 1) HomeSections (Recent Search, Favorites, Recents)
            item {
                HomeSections(nav = nav)
                Spacer(Modifier.height(8.dp))
            }

            // 2) Albums section title
            item {
                Text(
                    "Albums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 3) Albums using regular Column with Rows (no nested scrollable)
            if (safeAlbums.isEmpty()) {
                item {
                    EmptyState(onCreate = { showAdd = true })
                }
            } else {
                item {
                    AlbumsGrid(
                        items = safeAlbums,
                        onOpen = { album ->
                            try {
                                if (album != null && selectionState.isSelectionMode.value) {
                                    selectionState.toggleSelection(album)
                                } else if (album != null) {
                                    nav.navigate("album/${album.id}")
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Error opening album")
                            }
                        },
                        onRename = { album ->
                            try {
                                if (album != null) {
                                    renameTarget = album
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Error setting rename target")
                            }
                        },
                        onDelete = { album ->
                            try {
                                if (album != null) {
                                    deleteTarget = album
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Error setting delete target")
                            }
                        },
                        onToggleFavorite = { album ->
                            try {
                                if (album != null && album.id > 0) {
                                    vm.toggleFavorite(album.id)
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Error toggling favorite")
                            }
                        },
                        onLongPress = { album ->
                            try {
                                if (album != null) {
                                    selectionState.toggleSelection(album)
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Error handling long press")
                            }
                        },
                        viewModel = vm,
                        selectionState = selectionState,
                        highlightedAlbumId = highlightedAlbumId
                    )
                }
            }

            // Extra bottom space for bottom bar
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAdd) AddAlbumDialog(
        onDismiss = { showAdd = false },
        onCreate = { name ->
            scope.launch {
                val id = vm.createAndReturnId(name)
                showAdd = false
                if (id > 0) {
                    // Navigate to the newly created album
                    nav.navigate("album/$id")
                }
            }
        }
    )

    renameTarget?.let { target ->
        RenameAlbumDialog(
            album = target,
            onDismiss = { renameTarget = null },
            onRename = { new -> vm.rename(target.id, new); renameTarget = null },
            onSetEmoji = { emoji -> vm.setEmoji(target.id, emoji) }
        )
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            albumName = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = { vm.delete(target); deleteTarget = null }
        )
    }


    // New album name dialog for camera
    if (showNewAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showNewAlbumDialog = false },
            title = { Text("Create album") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newAlbumName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            val id = vm.createAndReturnId(name)
                            showNewAlbumDialog = false
                            newAlbumName = ""
                            if (id > 0) {
                                // Stay on home screen and highlight the new album
                                highlightedAlbumId = id
                                
                                // Auto-scroll to show the new album
                                delay(100) // Allow list to update
                                val albumIndex = safeAlbums.indexOfFirst { it.id == id }
                                if (albumIndex >= 0) {
                                    // Scroll to item (index 2 is the albums section in LazyColumn)
                                    listState.animateScrollToItem(2)
                                }
                                
                                // Clear highlight after 2 seconds
                                delay(2000)
                                highlightedAlbumId = null
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
    
    // Sort menu - comprehensive with all options
    if (showSortMenu) {
        AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { Text("Sort Albums") },
            text = { 
                Column {
                    Text("Choose how to sort your albums")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Newest to Oldest
                    TextButton(
                        onClick = {
                            timber.log.Timber.d("AlbumsScreen: Newest to Oldest clicked")
                            vm.setSort(SortMode.DATE_NEW)
                            showSortMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Newest to Oldest")
                    }
                    
                    // Oldest to Newest
                    TextButton(
                        onClick = {
                            timber.log.Timber.d("AlbumsScreen: Oldest to Newest clicked")
                            vm.setSort(SortMode.DATE_OLD)
                            showSortMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Oldest to Newest")
                    }
                    
                    // A-Z
                    TextButton(
                        onClick = {
                            timber.log.Timber.d("AlbumsScreen: A-Z clicked")
                            vm.setSort(SortMode.NAME_ASC)
                            showSortMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("A-Z")
                    }
                    
                    // Z-A
                    TextButton(
                        onClick = {
                            timber.log.Timber.d("AlbumsScreen: Z-A clicked")
                            vm.setSort(SortMode.NAME_DESC)
                            showSortMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Z-A")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortMenu = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
}

@Composable
private fun AlbumsGrid(
    items: List<AlbumEntity>?,
    onOpen: (AlbumEntity) -> Unit,
    onRename: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
    onToggleFavorite: (AlbumEntity) -> Unit,
    onLongPress: (AlbumEntity) -> Unit,
    viewModel: AlbumsViewModel?,
    selectionState: SelectionState<AlbumEntity>?,
    highlightedAlbumId: Long?
) {
    // Add comprehensive null safety checks
    if (items.isNullOrEmpty() || viewModel == null || selectionState == null) {
        return
    }
    
    // Use Column with Rows to avoid nested scrollable containers
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { album ->
                    if (album != null) {
                        AlbumCard(
                            album = album,
                            onClick = { 
                                try {
                                    onOpen(album)
                                } catch (e: Exception) {
                                    // Handle any errors gracefully
                                }
                            },
                            onRename = { 
                                try {
                                    onRename(album)
                                } catch (e: Exception) {
                                    // Handle any errors gracefully
                                }
                            },
                            onDelete = { 
                                try {
                                    onDelete(album)
                                } catch (e: Exception) {
                                    // Handle any errors gracefully
                                }
                            },
                            onToggleFavorite = { 
                                try {
                                    onToggleFavorite(album)
                                } catch (e: Exception) {
                                    // Handle any errors gracefully
                                }
                            },
                            onLongPress = { 
                                try {
                                    onLongPress(album)
                                } catch (e: Exception) {
                                    // Handle any errors gracefully
                                }
                            },
                            viewModel = viewModel,
                            selectionState = selectionState,
                            isHighlighted = album.id == highlightedAlbumId,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Fill remaining space if the row is not complete
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumEntity?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit,
    viewModel: AlbumsViewModel?,
    selectionState: SelectionState<AlbumEntity>?,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Add comprehensive null safety checks
    if (album == null || viewModel == null || selectionState == null) {
        return
    }
    
    // Safe access to album properties
    val albumName = album.name?.ifBlank { "Unnamed Album" } ?: "Unnamed Album"
    val isFavorite = album.favorite ?: false
    val albumId = album.id ?: -1L
    
    // Blue glow animation for highlighted albums
    val glowColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF2196F3) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "highlight_glow"
    )
    
    Card(
        onClick = {
            try {
                onClick()
            } catch (e: Exception) {
                // Handle any errors gracefully
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray),
        modifier = modifier
            .selectionBorder(selectionState.isSelected(album))
            .border(
                width = if (isHighlighted) 3.dp else 0.dp,
                color = glowColor,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { 
                        try {
                            onLongPress()
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover image box with favorite star overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                // Cover image or placeholder
                var coverImagePath by remember(albumId) { mutableStateOf<String?>(null) }
                
                LaunchedEffect(albumId, album.coverPhotoId) {
                    try {
                        if (albumId > 0) {
                            val path = viewModel.getCoverImagePath(album)
                            coverImagePath = path
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // This is expected when the composition is cancelled
                        // Don't log this as an error
                    } catch (e: Exception) {
                        // Handle any other errors gracefully
                        coverImagePath = null
                    }
                }
                
                val currentCoverPath = coverImagePath
                if (currentCoverPath != null && currentCoverPath.isNotBlank()) {
                    AsyncImage(
                        model = currentCoverPath,
                        contentDescription = "Album cover",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        Text(
                            text = "No cover",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                
                // Show assigned emoji only (if any)
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
                
                // Favorite star overlay (top right)
                IconButton(
                    onClick = {
                        try {
                            onToggleFavorite()
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isFavorite) android.R.drawable.btn_star_big_on 
                            else android.R.drawable.btn_star_big_off
                        ),
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Selection badge overlay
                if (selectionState.isSelected(album)) {
                    val selectionNumber = selectionState.getSelectionNumber(album) ?: 1
                    SelectionBadge(
                        selectionNumber = selectionNumber,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                // Album name - make sure it's visible
                Text(
                    text = albumName,
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
                    IconButton(onClick = {
                        try {
                            onRename()
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                        }
                    }) {
                        Icon(painterResource(android.R.drawable.ic_menu_edit), contentDescription = "Rename")
                    }
                    IconButton(onClick = {
                        try {
                            onDelete()
                        } catch (e: Exception) {
                            // Handle any errors gracefully
                        }
                    }) {
                        Icon(painterResource(android.R.drawable.ic_menu_delete), contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No albums yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create your first album to start organizing photos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCreate) { Text("Create album") }
    }
}

@Composable
private fun AddAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New album") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("Album name") })
        },
        confirmButton = {
            TextButton(onClick = { onCreate(text) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
        title = { Text("Edit Album") },
        text = {
            Column {
                // Album name section
                Text(
                    text = "Album Name",
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
        dismissButton = null // Remove dismiss button since we have Cancel in confirmButton
    )
}

@Composable
private fun ConfirmDeleteDialog(
    albumName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete album") },
        text = { Text("Delete \"$albumName\"? All photos in this album will be removed.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
