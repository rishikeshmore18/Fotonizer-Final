package com.example.photoapp10.feature.photo.ui

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.selection.SelectionBadge
import com.example.photoapp10.core.selection.SelectionState
import com.example.photoapp10.core.selection.selectionBorder
import com.example.photoapp10.core.util.MediaPreviewResolver
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.settings.data.UserPrefs
import com.example.photoapp10.ui.theme.FavoriteStarColor
import kotlin.math.floor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

private val GridSpacing = 8.dp
private val MinGridCellSize = 120.dp

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
        columns = GridCells.Adaptive(MinGridCellSize),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing)
    ) {
        items(
            count = paging.itemCount,
            key = paging.itemKey { it.id }
        ) { idx ->
            val photo = paging[idx] ?: return@items
            PhotoGridTile(
                photo = photo,
                onPhotoClick = onPhotoClick,
                selectionState = selectionState,
                onToggleFavorite = onToggleFavorite ?: { photoId: Long ->
                    photosViewModel.toggleFavorite(photoId)
                    Unit
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StaticPhotosGrid(
    photos: List<PhotoEntity>,
    onPhotoClick: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier,
    selectionState: SelectionState<PhotoEntity>? = null,
    onToggleFavorite: ((Long) -> Unit)? = null,
    minCellSize: Dp = MinGridCellSize
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = remember(maxWidth, minCellSize) {
            maxOf(1, floor((maxWidth / minCellSize).toDouble()).toInt())
        }
        val rows = remember(photos, columns) { photos.chunked(columns) }

        Column(
            verticalArrangement = Arrangement.spacedBy(GridSpacing)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GridSpacing)
                ) {
                    rowItems.forEach { photo ->
                        PhotoGridTile(
                            photo = photo,
                            onPhotoClick = onPhotoClick,
                            selectionState = selectionState,
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridTile(
    photo: PhotoEntity,
    onPhotoClick: (PhotoEntity) -> Unit,
    selectionState: SelectionState<PhotoEntity>? = null,
    onToggleFavorite: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isSelected = remember(photo.id) {
        derivedStateOf { selectionState?.isSelected(photo) == true }
    }
    val isSelectionMode = remember(selectionState) {
        derivedStateOf { selectionState?.isSelectionMode?.value == true }
    }
    val isVideo = remember(photo.filename, photo.path) { isVideoMedia(photo) }
    val previewPath = remember(photo.thumbPath, photo.path) {
        MediaPreviewResolver.resolvePreviewPath(photo.thumbPath, photo.path)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .selectionBorder(isSelected.value)
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = {
                        try {
                            if (isSelectionMode.value) {
                                selectionState?.toggleSelection(photo)
                            } else {
                                val photoFile = java.io.File(photo.path)
                                val thumbFile = photo.thumbPath
                                    .takeIf { it.isNotBlank() }
                                    ?.let { path -> java.io.File(path) }

                                if (photoFile.exists() || thumbFile?.exists() == true) {
                                    onPhotoClick(photo)
                                } else {
                                    android.util.Log.w("PhotosGrid", "Photo file does not exist: ${photo.path}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PhotosGrid", "Error in tap gesture", e)
                        }
                    },
                    onLongPress = {
                        try {
                            selectionState?.toggleSelection(photo)
                        } catch (e: Exception) {
                            android.util.Log.e("PhotosGrid", "Error in long press", e)
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = previewPath,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_menu_camera),
            placeholder = painterResource(android.R.drawable.ic_menu_camera)
        )

        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_media_play),
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (isSelected.value) {
            val selectionNumber = selectionState?.getSelectionNumber(photo) ?: 1
            SelectionBadge(
                selectionNumber = selectionNumber,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick = { onToggleFavorite?.invoke(photo.id) },
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    imageVector = if (photo.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (photo.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (photo.favorite) FavoriteStarColor else Color(0xFF666666),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (photo.backedUpAt > 0) {
            Icon(
                imageVector = Icons.Filled.CloudDone,
                contentDescription = "Backed up",
                tint = Color.Green,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(16.dp)
            )
        }
    }
}

private fun isVideoMedia(photo: PhotoEntity): Boolean {
    return photo.filename.endsWith(".mp4", ignoreCase = true) ||
        photo.filename.endsWith(".mov", ignoreCase = true) ||
        photo.filename.endsWith(".avi", ignoreCase = true) ||
        photo.filename.endsWith(".mkv", ignoreCase = true) ||
        photo.filename.endsWith(".webm", ignoreCase = true) ||
        photo.filename.endsWith(".3gp", ignoreCase = true) ||
        photo.path.endsWith(".mp4", ignoreCase = true) ||
        photo.path.endsWith(".mov", ignoreCase = true) ||
        photo.path.endsWith(".avi", ignoreCase = true) ||
        photo.path.endsWith(".mkv", ignoreCase = true) ||
        photo.path.endsWith(".webm", ignoreCase = true) ||
        photo.path.endsWith(".3gp", ignoreCase = true)
}

class PhotosGridViewModel(
    app: Application,
    albumId: Long
) : AndroidViewModel(app) {

    private val repo: PhotoRepository = Modules.providePhotoRepository(app)
    private val userPrefs = UserPrefs(app)
    private val sortFlow = userPrefs.sortFlow

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val paged: Flow<PagingData<PhotoEntity>> = sortFlow
        .flatMapLatest { sort -> repo.pagerForAlbum(albumId, sort) }
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
