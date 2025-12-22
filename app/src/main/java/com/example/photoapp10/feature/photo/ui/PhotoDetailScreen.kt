package com.example.photoapp10.feature.photo.ui

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.photo.domain.SortMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewModelScope
import java.io.File
import androidx.compose.material3.SnackbarHostState

// Smooth horizontal photo pager using Compose HorizontalPager
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoHorizontalPager(
    photos: List<PhotoEntity>,
    currentPhotoId: Long,
    modifier: Modifier = Modifier,
    onPhotoChanged: (PhotoEntity) -> Unit = {}
) {
    // Ensure photos are consistently ordered (newest first) like Recent/Favourite views
    val sortedPhotos = photos.sortedByDescending { it.createdAt }
    
    val initialIndex = sortedPhotos.indexOfFirst { it.id == currentPhotoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { sortedPhotos.size }
    )
    
    // Debug logging
    LaunchedEffect(sortedPhotos) {
        android.util.Log.d("PhotoPager", "Sorted photos order: ${sortedPhotos.map { "${it.id}:${it.filename}" }}")
        android.util.Log.d("PhotoPager", "Current photo ID: $currentPhotoId")
        android.util.Log.d("PhotoPager", "Initial index: $initialIndex")
    }
    
    LaunchedEffect(pagerState.currentPage) {
        android.util.Log.d("PhotoPager", "Current page: ${pagerState.currentPage}, Total photos: ${sortedPhotos.size}")
        if (pagerState.currentPage < sortedPhotos.size) {
            val currentPhoto = sortedPhotos[pagerState.currentPage]
            android.util.Log.d("PhotoPager", "Showing photo: ${currentPhoto.filename}")
            onPhotoChanged(currentPhoto)
        }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier
    ) { page ->
        if (page < sortedPhotos.size) {
            val photo = sortedPhotos[page]
            val photoFile = File(photo.path)
            
            if (photoFile.exists()) {
                AsyncImage(
                    model = photo.path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    error = painterResource(android.R.drawable.ic_menu_camera),
                    placeholder = painterResource(android.R.drawable.ic_menu_camera)
                )
            } else {
                // Show placeholder for missing photos
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_camera),
                            contentDescription = "Photo not available",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Photo not available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photoId: Long,
    albumId: Long,
    nav: NavController,
    photoPath: String? = null,
    context: String? = null
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: PhotoDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PhotoDetailViewModel(app, photoId, albumId, photoPath, context) as T
            }
        }
    )
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val photo by vm.photo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by vm.snackbarMessage.collectAsState()
    
    // Track the currently viewed photo in the pager
    var currentPhoto by remember { mutableStateOf<PhotoEntity?>(null) }
    
    // Handle photoPath case - create a temporary PhotoEntity
    val displayPhoto by remember(photoPath, photo) {
        derivedStateOf {
            if (photoPath != null && photo == null) {
                // Create a temporary PhotoEntity for the file path
                PhotoEntity(
                    id = -1L, // Temporary ID
                    albumId = albumId,
                    filename = java.io.File(photoPath).name,
                    path = photoPath,
                    thumbPath = "", // No thumbnail for temporary photos
                    width = 0,
                    height = 0,
                    sizeBytes = 0,
                    takenAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                photo
            }
        }
    }
    
    // Handle snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.clearSnackbarMessage()
        }
    }

    if (displayPhoto == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Photo not found", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { nav.navigateUp() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }
    val p = displayPhoto ?: return

    // Initialize currentPhoto with the initial photo
    LaunchedEffect(p.id) {
        currentPhoto = p
    }
    
    // Keep currentPhoto in sync with albumPhotos updates (for immediate UI updates)
    val albumPhotos by vm.albumPhotos.collectAsState()
    LaunchedEffect(albumPhotos) {
        currentPhoto?.let { current ->
            val updatedPhoto = albumPhotos.find { it.id == current.id }
            if (updatedPhoto != null) {
                currentPhoto = updatedPhoto
            }
        }
    }

    // State for caption editing - reactive to currentPhoto changes
    var isEditingCaption by remember { mutableStateOf(false) }
    var caption by remember(currentPhoto?.id) { 
        mutableStateOf(TextFieldValue(currentPhoto?.caption ?: "")) 
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Photo") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            // Bottom action bar with favorite, share, delete, and edit buttons
            BottomAppBar(
                modifier = Modifier.height(80.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite button (disabled for temporary photos)
                    IconButton(
                        onClick = { 
                            currentPhoto?.let { photo ->
                                vm.toggleFavorite(photo.id)
                            }
                        },
                        enabled = (currentPhoto?.id ?: 0L) > 0
                    ) {
                        Icon(
                            imageVector = if (currentPhoto?.favorite == true) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (currentPhoto?.favorite == true) "Remove from favorites" else "Add to favorites",
                            tint = if ((currentPhoto?.id ?: 0L) > 0) {
                                if (currentPhoto?.favorite == true) Color(0xFF2196F3) else Color(0xFF666666)
                            } else Color(0xFFCCCCCC), // Grayed out for temporary photos
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Share button
                    IconButton(onClick = {
                        val authority = context.packageName + ".fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, File(p.path))
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share photo"))
                    }) {
                        Icon(painterResource(android.R.drawable.ic_menu_share), contentDescription = "Share")
                    }
                    
                    // Edit button (disabled for temporary photos)
                    IconButton(
                        onClick = { isEditingCaption = !isEditingCaption },
                        enabled = photoId > 0
                    ) {
                        Icon(
                            painterResource(android.R.drawable.ic_menu_edit), 
                            contentDescription = "Edit",
                            tint = if (photoId > 0) Color.Unspecified else Color(0xFFCCCCCC)
                        )
                    }
                    
                    // Delete button
                    IconButton(onClick = { vm.requestDelete(onDone = { nav.navigateUp() }) }) {
                        Icon(painterResource(android.R.drawable.ic_menu_delete), contentDescription = "Delete")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // Smooth horizontal photo pager with native-like slide transitions
            // Handle photoPath case - create a single-item list for temporary photos
            val photosToShow = if (photoPath != null && photo == null) {
                displayPhoto?.let { listOf(it) } ?: emptyList()
            } else {
                albumPhotos
            }
            
            PhotoHorizontalPager(
                photos = photosToShow,
                currentPhotoId = if (photoPath != null && photo == null) -1L else photoId,
                modifier = Modifier.fillMaxSize(),
                onPhotoChanged = { photo ->
                    currentPhoto = photo
                    // Reset caption editing state when changing photos
                    isEditingCaption = false
                    focusManager.clearFocus()
                }
            )

            // Caption editing overlay
            if (isEditingCaption) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Edit Caption",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Caption") },
                            maxLines = 3
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { 
                                    isEditingCaption = false
                                    focusManager.clearFocus()
                                }
                            ) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    currentPhoto?.let { photo ->
                                        vm.updateCaption(photo.id, caption.text)
                                        isEditingCaption = false
                                        focusManager.clearFocus()
                                        vm.showSnackbar("Caption saved")
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

class PhotoDetailViewModel(
    app: Application,
    private val photoId: Long,
    private val albumId: Long,
    private val photoPath: String? = null,
    private val context: String? = null
) : AndroidViewModel(app) {
    private val photos: PhotoRepository = Modules.providePhotoRepository(app)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    // Get all photos for navigation based on context
    val albumPhotos = when (context) {
        "favorites" -> photos.observeFavorites(limit = 1000) // Get all favorites
        "recents" -> photos.observeRecents(limit = 1000) // Get all recents
        else -> photos.observePhotos(albumId, SortMode.DATE_NEW) // Get album photos
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Debug: Log when album photos are loaded
        viewModelScope.launch {
            albumPhotos.collect { photos ->
                android.util.Log.d("PhotoDetailVM", "Album photos loaded: ${photos.size} photos for album $albumId")
                photos.forEachIndexed { index, photo ->
                    android.util.Log.d("PhotoDetailVM", "Photo $index: id=${photo.id}, filename=${photo.filename}")
                }
            }
        }
    }

    private val _photo = MutableStateFlow<PhotoEntity?>(null)
    val photo: StateFlow<PhotoEntity?> = _photo

    init {
        viewModelScope.launch {
            // Only try to get photo if photoId is valid (> 0)
            if (photoId > 0) {
                _photo.value = photos.getPhoto(photoId)
            }
            // If photoPath is provided, we'll handle it in the UI layer
        }
    }

    fun toggleFavorite(targetPhotoId: Long) = viewModelScope.launch {
        if (targetPhotoId > 0) {
            photos.toggleFavorite(targetPhotoId)
            refresh()
        }
    }

    fun updateCaption(photoId: Long, text: String) = viewModelScope.launch {
        if (photoId > 0) {
            photos.updateCaption(photoId, text)
            refresh()
        }
    }

    fun requestDelete(onDone: () -> Unit) = viewModelScope.launch {
        if (photoId > 0) {
            photos.deletePhoto(photoId)
            onDone()
        } else {
            // For temporary photos (from camera), just navigate back
            onDone()
        }
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun navigateToPrevious(): Long? {
        if (photoId <= 0) return null
        val photos = albumPhotos.value
        val currentIndex = photos.indexOfFirst { it.id == photoId }
        
        return if (currentIndex > 0) {
            photos[currentIndex - 1].id
        } else {
            null
        }
    }

    fun navigateToNext(): Long? {
        if (photoId <= 0) return null
        val photos = albumPhotos.value
        val currentIndex = photos.indexOfFirst { it.id == photoId }
        
        return if (currentIndex < photos.size - 1) {
            photos[currentIndex + 1].id
        } else {
            null
        }
    }

    private fun refresh() = viewModelScope.launch {
        // Reload the photo after mutation and update the state
        if (photoId > 0) {
            val updated = photos.getPhoto(photoId)
            _photo.value = updated
        }
    }
}

