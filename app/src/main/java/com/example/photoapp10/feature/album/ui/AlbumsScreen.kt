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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FabPosition
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.photoapp10.core.copymove.rememberCopyMoveState
import com.example.photoapp10.core.copymove.CopyMoveService
import androidx.compose.material.icons.Icons
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.account.AccountScopeManager
import com.example.photoapp10.core.account.TempModeManager
import com.example.photoapp10.feature.auth.AuthManager
import com.example.photoapp10.feature.backup.SyncState
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.ui.components.FloatingBottomBar
import com.example.photoapp10.ui.components.FloatingCopyMoveAction
import com.example.photoapp10.ui.components.FloatingCopyMoveBar
import com.example.photoapp10.ui.theme.FavoriteStarColor
import com.rishikeshmore.fotonizer.R

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
    
    // Copy/Move state
    val copyMoveState = rememberCopyMoveState()
    
    // New album dialog state
    var showNewAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    
    // Progress dialog state
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(1) }
    
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

    val albumsMissingCover = remember(safeAlbums) {
        safeAlbums.any { album ->
            album.id > 0 && album.photoCount > 0 && (album.coverPhotoId == null || album.coverPhotoId <= 0)
        }
    }

    LaunchedEffect(albumsMissingCover, safeAlbums.size) {
        if (!albumsMissingCover) return@LaunchedEffect
        try {
            vm.updateCovers()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error updating missing album covers")
        }
    }
    
    // Note: Back button is allowed to work in copy/move mode
    // The state persists across navigation, so users can navigate between albums
    // and paste in different locations. The mode only exits via Paste or Cancel buttons.

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
            when {
                    // Paste mode - show Paste/Cancel
                    copyMoveState.hasPendingOperation() -> {
                        FloatingCopyMoveBar(
                            onCancelClick = { copyMoveState.clear() },
                            menuIcon = painterResource(R.drawable.menu_24),
                            onMenuClick = { showMenu = true },
                            menuContent = {
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    // Menu items can be added here if needed in paste mode
                                    // For now, keeping it simple
                                }
                            }
                        ) {
                            FloatingCopyMoveAction(label = "Paste", onClick = {
                                // Paste operation will be handled when user navigates to destination
                                // For root level, paste to root (null parentId)
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
                                                targetAlbumId = null, // Root level
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
                                                targetAlbumId = null, // Root level
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
                            })
                        }
                    }

                    // Selection mode - show Copy/Move/Cancel
                    selectionState.isSelectionMode.value -> {
                        FloatingCopyMoveBar(
                            onCancelClick = { selectionState.clearSelection() },
                            menuIcon = painterResource(R.drawable.menu_24),
                            onMenuClick = { showMenu = true },
                            menuContent = {
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = {
                                            selectionState.selectAll(safeAlbums)
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
                                            // Share functionality not implemented yet
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
                                            selectionState.getSelectedItems().forEach { album ->
                                                vm.toggleFavorite(album.id)
                                            }
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
                                            val selectedAlbums = selectionState.getSelectedItems()
                                            vm.deleteAlbums(selectedAlbums)
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
                                }
                            }
                        ) {
                            FloatingCopyMoveAction(label = "Copy", onClick = {
                                val selectedAlbums = selectionState.getSelectedItems()
                                copyMoveState.startCopy(selectedAlbums, emptyList())
                                selectionState.clearSelection()
                            })
                            FloatingCopyMoveAction(label = "Move", onClick = {
                                val selectedAlbums = selectionState.getSelectedItems()
                                copyMoveState.startMove(selectedAlbums, emptyList())
                                selectionState.clearSelection()
                            })
                        }
                    }
                    
                    // Normal mode - show regular buttons
                    else -> {
                        FloatingBottomBar(
                            leftIcon = painterResource(R.drawable.create_new_folder_24),
                            leftLabel = "Album",
                            onLeftClick = { showNewAlbumDialog = true },
                            centerIcon = painterResource(android.R.drawable.ic_menu_camera),
                            centerLabel = "Camera",
                            onCenterClick = { nav.navigate("camera/0") },
                            rightIcon = painterResource(R.drawable.menu_24),
                            rightLabel = "More",
                            onRightClick = { showMenu = true }
                        )
                    }
            }
        }
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 132.dp)
        ) {
            // Temp Mode banner — content auto-deletes in 7 days
            if (TempModeManager.isTempMode(context)) {
                item {
                    Text(
                        text = "Content in Temp Mode will auto-delete after 7 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

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
    
    if (
        showMenu &&
        !copyMoveState.hasPendingOperation() &&
        !selectionState.isSelectionMode.value
    ) {
        val isTempMode = TempModeManager.isTempMode(context)

        ModernMoreMenuBottomSheet(
            isTempMode = isTempMode,
            onDismissRequest = { showMenu = false },
            onBackup = {
                nav.navigate("backup")
                showMenu = false
            },
            onRestore = {
                nav.navigate("restore_sync")
                showMenu = false
            },
            onTempMode = {
                showMenu = false
                TempModeManager.enterTempMode(context)
                nav.navigate("albums") {
                    popUpTo(0) { inclusive = true }
                }
            },
            onBackToPrimary = {
                showMenu = false
                TempModeManager.exitTempMode(context)
                nav.navigate("albums") {
                    popUpTo(0) { inclusive = true }
                }
            },
            onAbout = {
                nav.navigate("about")
                showMenu = false
            },
            onSignOut = {
                showMenu = false
                try {
                    if (isTempMode) TempModeManager.exitTempMode(context)
                    Modules.resetForAccountChange()
                    AccountScopeManager.clearActiveAccount(context)
                    AuthManager.signOut(context) {
                        nav.navigate("signin") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "AlbumsScreen: Error signing out")
                }
            }
        )
    }

    // Sort menu - comprehensive with all options
    if (showSortMenu) {
        ModernSortAlbumsBottomSheet(
            onDismissRequest = { showSortMenu = false },
            onNewestToOldest = {
                timber.log.Timber.d("AlbumsScreen: Newest to Oldest clicked")
                vm.setSort(SortMode.DATE_NEW)
                showSortMenu = false
            },
            onOldestToNewest = {
                timber.log.Timber.d("AlbumsScreen: Oldest to Newest clicked")
                vm.setSort(SortMode.DATE_OLD)
                showSortMenu = false
            },
            onNameAscending = {
                timber.log.Timber.d("AlbumsScreen: A-Z clicked")
                vm.setSort(SortMode.NAME_ASC)
                showSortMenu = false
            },
            onNameDescending = {
                timber.log.Timber.d("AlbumsScreen: Z-A clicked")
                vm.setSort(SortMode.NAME_DESC)
                showSortMenu = false
            }
        )
    }
    
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernMoreMenuBottomSheet(
    isTempMode: Boolean,
    onDismissRequest: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onTempMode: () -> Unit,
    onBackToPrimary: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Color(0xFFFCF8FF),
        contentColor = Color(0xFF1F2430),
        tonalElevation = 6.dp,
        scrimColor = Color.Black.copy(alpha = 0.46f),
        dragHandle = { ModernBottomSheetHandle() }
    ) {
        ModernMoreMenuContent(
            isTempMode = isTempMode,
            onBackup = onBackup,
            onRestore = onRestore,
            onTempMode = onTempMode,
            onBackToPrimary = onBackToPrimary,
            onAbout = onAbout,
            onSignOut = onSignOut
        )
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSortAlbumsBottomSheet(
    onDismissRequest: () -> Unit,
    onNewestToOldest: () -> Unit,
    onOldestToNewest: () -> Unit,
    onNameAscending: () -> Unit,
    onNameDescending: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Color(0xFFFCF8FF),
        contentColor = Color(0xFF1F2430),
        tonalElevation = 6.dp,
        scrimColor = Color.Black.copy(alpha = 0.46f),
        dragHandle = { ModernBottomSheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Sort Albums",
                color = Color(0xFF1F2430),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose how to sort your albums",
                color = Color(0xFF6B6476),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModernSortOptionRow(
                badge = "1",
                title = "Newest to Oldest",
                subtitle = "Recently created albums first",
                onClick = onNewestToOldest
            )
            ModernSortOptionRow(
                badge = "9",
                title = "Oldest to Newest",
                subtitle = "Earliest created albums first",
                onClick = onOldestToNewest
            )
            ModernSortOptionRow(
                badge = "A",
                title = "A-Z",
                subtitle = "Album names ascending",
                onClick = onNameAscending
            )
            ModernSortOptionRow(
                badge = "Z",
                title = "Z-A",
                subtitle = "Album names descending",
                onClick = onNameDescending
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF2B0B5F),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(12.dp)
            )
        }
    }
}

@Composable
private fun ModernBottomSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(5.dp)
                .background(Color(0xFFD8D0E2), RoundedCornerShape(50))
        )
    }
}

@Composable
private fun ModernSortOptionRow(
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
private fun ModernMoreMenuContent(
    isTempMode: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onTempMode: () -> Unit,
    onBackToPrimary: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        if (isTempMode) {
            ModernMenuRow(
                icon = {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_revert),
                        contentDescription = "Back to Primary Mode",
                        tint = Color(0xFF2B0B5F),
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Back to Primary",
                subtitle = "Return to your main albums",
                onClick = onBackToPrimary
            )
        } else {
            ModernMenuRow(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_upload_24),
                        contentDescription = "Backup",
                        tint = Color(0xFF2B0B5F),
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Backup",
                subtitle = "Save your photos",
                onClick = onBackup
            )
            ModernMenuRow(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore_24),
                        contentDescription = "Restore",
                        tint = Color(0xFF2B0B5F),
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Restore",
                subtitle = "Restore sync",
                onClick = onRestore
            )
            ModernMenuRow(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock_24),
                        contentDescription = "Temp Mode",
                        tint = Color(0xFF2B0B5F),
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Temp Mode",
                subtitle = "Temporarily hide photos",
                onClick = onTempMode
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            color = Color(0xFFE5DDF2),
            thickness = 1.dp
        )

        ModernMenuRow(
            icon = {
                ModernInfoIcon()
            },
            title = "About",
            subtitle = null,
            onClick = onAbout
        )
        ModernMenuRow(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_logout_24),
                    contentDescription = "Sign Out",
                    tint = Color(0xFF2B0B5F),
                    modifier = Modifier.size(24.dp)
                )
            },
            title = "Sign Out",
            subtitle = null,
            onClick = onSignOut
        )
    }
}

@Composable
private fun ModernMenuRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
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
                .size(44.dp)
                .background(Color(0xFFF0E7FA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
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
            if (subtitle != null) {
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
}

@Composable
private fun ModernInfoIcon() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = 2.dp,
                color = Color(0xFF2B0B5F),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "i",
            color = Color(0xFF2B0B5F),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            )
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

@OptIn(ExperimentalFoundationApi::class)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray),
        modifier = modifier
            .selectionBorder(selectionState.isSelected(album))
            .border(
                width = if (isHighlighted) 3.dp else 0.dp,
                color = glowColor,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = {
                    try {
                        onClick()
                    } catch (e: Exception) {
                        // Handle any errors gracefully
                    }
                },
                onLongClick = {
                    try {
                        onLongPress()
                    } catch (e: Exception) {
                        // Handle any errors gracefully
                    }
                }
            )
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
                        tint = if (isFavorite) FavoriteStarColor else MaterialTheme.colorScheme.onSurfaceVariant
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
