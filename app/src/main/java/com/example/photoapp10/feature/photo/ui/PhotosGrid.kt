package com.example.photoapp10.feature.photo.ui

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.settings.data.UserPrefs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.example.photoapp10.core.selection.SelectionState
import com.example.photoapp10.core.selection.rememberSelectionState
import com.example.photoapp10.core.selection.SelectionBadge
import com.example.photoapp10.core.selection.selectionBorder
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import timber.log.Timber
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosGrid(
    albumId: Long,
    onPhotoClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier,
    selectionState: SelectionState<PhotoEntity>? = null,
    onToggleFavorite: ((Long) -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as Application
    val photosViewModel: PhotosGridViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(c: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PhotosGridViewModel(app, albumId) as T
            }
        }
    )
    val paging = photosViewModel.paged.collectAsLazyPagingItems()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(
            count = paging.itemCount,
            key = paging.itemKey { it.id }
        ) { idx ->
            val p = paging[idx] ?: return@items
            
            // Cache selection state to avoid multiple reads
            val isSelected = remember(p.id) { 
                derivedStateOf { selectionState?.isSelected(p) == true }
            }
            val isSelectionMode = remember { 
                derivedStateOf { selectionState?.isSelectionMode?.value == true }
            }
            
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .selectionBorder(isSelected.value)
                    .pointerInput(p.id) {
                        detectTapGestures(
                            onTap = { 
                                try {
                                    if (isSelectionMode.value) {
                                        selectionState?.toggleSelection(p)
                                    } else {
                                        // Check if photo file exists before navigating
                                        val photoFile = java.io.File(p.path)
                                        val thumbFile = if (p.thumbPath.isNotBlank()) java.io.File(p.thumbPath) else null
                                        
                                        if (photoFile.exists() || thumbFile?.exists() == true) {
                                            onPhotoClick(p)
                                        } else {
                                            android.util.Log.w("PhotosGrid", "Photo file does not exist: ${p.path}")
                                            // Could show a toast or snackbar here to inform user
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PhotosGrid", "Error in tap gesture", e)
                                }
                            },
                            onLongPress = { 
                                try {
                                    selectionState?.toggleSelection(p)
                                } catch (e: Exception) {
                                    android.util.Log.e("PhotosGrid", "Error in long press", e)
                                }
                            }
                        )
                    }
            ) {
                AsyncImage(
                    model = p.thumbPath.ifBlank { p.path },
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    error = painterResource(android.R.drawable.ic_menu_camera),
                    placeholder = painterResource(android.R.drawable.ic_menu_camera)
                )
                
                // Selection badge overlay
                if (isSelected.value) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        val selectionNumber = selectionState?.getSelectionNumber(p) ?: 1
                        
                        // Comprehensive debug logging
                        android.util.Log.d("PhotosGrid", "Photo ${p.id} is selected with number: $selectionNumber")
                        android.util.Log.d("PhotosGrid", "Selection state: ${selectionState?.selectedItems?.value}")
                        android.util.Log.d("PhotosGrid", "Total selected count: ${selectionState?.getSelectedCount()}")
                        
                        // Debug logging for selection numbering issues
                        if (selectionState != null) {
                            val isValid = selectionState.validateSelectionState()
                            android.util.Log.d("PhotosGrid", "Selection state validation: $isValid")
                            if (!isValid) {
                                android.util.Log.w("PhotosGrid", "Invalid selection state for photo ${p.id}, number: $selectionNumber")
                            }
                        }
                        
                        SelectionBadge(
                            selectionNumber = selectionNumber,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                
                // Favorite star icon overlay
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { 
                            // Use the callback if available, otherwise use photosViewModel
                            if (onToggleFavorite != null) {
                                onToggleFavorite(p.id)
                            } else {
                                photosViewModel.toggleFavorite(p.id)
                            }
                        },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (p.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (p.favorite) "Remove from favorites" else "Add to favorites",
                            tint = if (p.favorite) Color(0xFF2196F3) else Color(0xFF666666), // Blue when favorited, gray when not
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Backup status indicator
                if (p.backedUpAt > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = "Backed up",
                            tint = Color.Green,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

class PhotosGridViewModel(
    app: Application,
    albumId: Long
) : AndroidViewModel(app) {

    private val repo: PhotoRepository = Modules.providePhotoRepository(app)
    private val userPrefs = UserPrefs(app)

    // Observe UserPrefs directly instead of using initialSort parameter
    private val sortFlow = userPrefs.sortFlow

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val paged: Flow<PagingData<PhotoEntity>> = sortFlow
        .flatMapLatest { s -> repo.pagerForAlbum(albumId, s) }
        .cachedIn(viewModelScope)

    fun setSort(mode: SortMode) { 
        viewModelScope.launch { userPrefs.setSort(mode) }
    }
    
    fun toggleFavorite(photoId: Long) = viewModelScope.launch {
        try {
            if (photoId > 0) {
                repo.toggleFavorite(photoId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error toggling favorite: $photoId")
        }
    }
}
