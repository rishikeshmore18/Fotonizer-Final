package com.example.photoapp10.feature.photo.ui

import android.app.Application
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.util.MediaFileSupport
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.settings.data.UserPrefs
import com.example.photoapp10.ui.theme.FavoriteStarColor
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MinZoomScale = 1f
private const val MaxZoomScale = 5f
private const val DoubleTapZoomScale = 2.5f
private const val ZoomThreshold = 1.05f
private const val PinchSensitivity = 1.5f
private const val DoubleTapAnimationMillis = 350

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoHorizontalPager(
    photos: List<PhotoEntity>,
    currentPhotoId: Long,
    modifier: Modifier = Modifier,
    onPhotoChanged: (PhotoEntity) -> Unit = {}
) {
    val orderedPhotos = photos
    val initialIndex = orderedPhotos.indexOfFirst { it.id == currentPhotoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { orderedPhotos.size }
    )
    var zoomedPhotoId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage < orderedPhotos.size) {
            onPhotoChanged(orderedPhotos[pagerState.currentPage])
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = zoomedPhotoId == null
    ) { page ->
        if (page < orderedPhotos.size) {
            val photo = orderedPhotos[page]
            val isVideo = MediaFileSupport.isVideoExtension(
                photo.filename.substringAfterLast('.', "")
            ) || photo.path.endsWith(".mp4", ignoreCase = true)

            if (isVideo) {
                VideoPlayer(videoPath = photo.path)
            } else {
                ZoomableImage(
                    photoPath = photo.path,
                    onZoomChanged = { isZoomed ->
                        zoomedPhotoId = when {
                            isZoomed -> photo.id
                            zoomedPhotoId == photo.id -> null
                            else -> zoomedPhotoId
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ZoomableImage(
    photoPath: String,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var scale by remember(photoPath) { mutableStateOf(MinZoomScale) }
    var offset by remember(photoPath) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(photoPath) { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val latestContainerSize by rememberUpdatedState(containerSize)
    val isZoomed = scale > ZoomThreshold

    LaunchedEffect(isZoomed) {
        onZoomChanged(isZoomed)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center
    ) {
        if (File(photoPath).exists()) {
            AsyncImage(
                model = photoPath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(photoPath, containerSize) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)

                            do {
                                val event = awaitPointerEvent()
                                val pressedPointers = event.changes.count { it.pressed }
                                val shouldHandleTransform = pressedPointers > 1 || scale > ZoomThreshold

                                if (shouldHandleTransform) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val adjustedZoom = 1f + (zoom - 1f) * PinchSensitivity
                                    val newScale = (scale * adjustedZoom).coerceIn(MinZoomScale, MaxZoomScale)

                                    scale = newScale
                                    offset = if (newScale <= MinZoomScale) {
                                        Offset.Zero
                                    } else {
                                        clampOffset(
                                            offset = offset + pan,
                                            scale = newScale,
                                            containerSize = latestContainerSize
                                        )
                                    }

                                    event.changes.forEach { change ->
                                        if (change.pressed) {
                                            change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(photoPath, containerSize) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > ZoomThreshold) {
                                    scope.launch {
                                        animateZoomState(
                                            startScale = scale,
                                            endScale = MinZoomScale,
                                            startOffset = offset,
                                            endOffset = Offset.Zero,
                                            onUpdate = { animatedScale, animatedOffset ->
                                                scale = animatedScale
                                                offset = animatedOffset
                                            }
                                        )
                                    }
                                } else {
                                    val targetOffset = clampOffset(
                                        offset = computeDoubleTapOffset(
                                            tapOffset = tapOffset,
                                            containerSize = latestContainerSize,
                                            targetScale = DoubleTapZoomScale
                                        ),
                                        scale = DoubleTapZoomScale,
                                        containerSize = latestContainerSize
                                    )
                                    scope.launch {
                                        animateZoomState(
                                            startScale = scale,
                                            endScale = DoubleTapZoomScale,
                                            startOffset = offset,
                                            endOffset = targetOffset,
                                            onUpdate = { animatedScale, animatedOffset ->
                                                scale = animatedScale
                                                offset = animatedOffset
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    },
                contentScale = ContentScale.Fit,
                error = painterResource(android.R.drawable.ic_menu_camera)
            )
        } else {
            Text("File not found", color = Color.White)
        }
    }
}

private fun clampOffset(offset: Offset, scale: Float, containerSize: IntSize): Offset {
    if (containerSize.width == 0 || containerSize.height == 0 || scale <= MinZoomScale) {
        return Offset.Zero
    }

    val maxX = ((containerSize.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
    val maxY = ((containerSize.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun computeDoubleTapOffset(
    tapOffset: Offset,
    containerSize: IntSize,
    targetScale: Float
): Offset {
    if (containerSize.width == 0 || containerSize.height == 0) {
        return Offset.Zero
    }

    val center = Offset(
        x = containerSize.width / 2f,
        y = containerSize.height / 2f
    )
    return (center - tapOffset) * (targetScale - 1f)
}

private suspend fun animateZoomState(
    startScale: Float,
    endScale: Float,
    startOffset: Offset,
    endOffset: Offset,
    onUpdate: (Float, Offset) -> Unit
) {
    val scaleAnim = Animatable(startScale)
    val offsetXAnim = Animatable(startOffset.x)
    val offsetYAnim = Animatable(startOffset.y)

    kotlinx.coroutines.coroutineScope {
        launch {
            scaleAnim.animateTo(
                targetValue = endScale,
                animationSpec = tween(
                    durationMillis = DoubleTapAnimationMillis,
                    easing = FastOutSlowInEasing
                )
            ) {
                onUpdate(value, Offset(offsetXAnim.value, offsetYAnim.value))
            }
        }
        launch {
            offsetXAnim.animateTo(
                targetValue = endOffset.x,
                animationSpec = tween(
                    durationMillis = DoubleTapAnimationMillis,
                    easing = FastOutSlowInEasing
                )
            ) {
                onUpdate(scaleAnim.value, Offset(value, offsetYAnim.value))
            }
        }
        launch {
            offsetYAnim.animateTo(
                targetValue = endOffset.y,
                animationSpec = tween(
                    durationMillis = DoubleTapAnimationMillis,
                    easing = FastOutSlowInEasing
                )
            ) {
                onUpdate(scaleAnim.value, Offset(offsetXAnim.value, value))
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(videoPath: String) {
    val context = LocalContext.current
    val videoFile = remember(videoPath) { File(videoPath) }
    val fileExists = remember(videoFile) { videoFile.exists() }

    val exoPlayer = remember(videoPath) {
        if (fileExists) {
            ExoPlayer.Builder(context).build().apply {
                val uri = if (videoPath.startsWith("content://") || videoPath.contains("://")) {
                    android.net.Uri.parse(videoPath)
                } else {
                    android.net.Uri.fromFile(videoFile)
                }
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
            }
        } else {
            null
        }
    }

    DisposableEffect(videoPath) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(videoPath) {
        exoPlayer?.let { player ->
            if (fileExists) {
                val uri = if (videoPath.startsWith("content://") || videoPath.contains("://")) {
                    android.net.Uri.parse(videoPath)
                } else {
                    android.net.Uri.fromFile(videoFile)
                }
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!fileExists) {
            Text(
                "Video file not found",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        useController = true
                        controllerShowTimeoutMs = 3000
                        controllerAutoShow = true
                        controllerHideOnTouch = false
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(color = Color.White)
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
    val androidContext = LocalContext.current
    val focusManager = LocalFocusManager.current
    val photo by vm.photo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by vm.snackbarMessage.collectAsState()
    var currentPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    val displayPhoto by remember(photoPath, photo) {
        derivedStateOf {
            if (photoPath != null && photo == null) {
                PhotoEntity(
                    id = -1L,
                    albumId = albumId,
                    filename = File(photoPath).name,
                    path = photoPath,
                    thumbPath = "",
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Photo not found", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { nav.navigateUp() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }
    val display = displayPhoto ?: return

    LaunchedEffect(display.id) {
        currentPhoto = display
    }

    val albumPhotos by vm.albumPhotos.collectAsState()
    LaunchedEffect(albumPhotos) {
        currentPhoto?.let { current ->
            val updatedPhoto = albumPhotos.find { it.id == current.id }
            if (updatedPhoto != null) {
                currentPhoto = updatedPhoto
            }
        }
    }

    var isRenaming by remember { mutableStateOf(false) }
    var renameText by remember(currentPhoto?.id, currentPhoto?.filename) {
        mutableStateOf(
            TextFieldValue(
                currentPhoto?.filename?.let(MediaFileSupport::displayNameWithoutExtension) ?: ""
            )
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Photo") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(modifier = Modifier.height(80.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            currentPhoto?.let { photoEntity ->
                                vm.toggleFavorite(photoEntity.id)
                            }
                        },
                        enabled = (currentPhoto?.id ?: 0L) > 0
                    ) {
                        Icon(
                            imageVector = if (currentPhoto?.favorite == true) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (currentPhoto?.favorite == true) "Remove from favorites" else "Add to favorites",
                            tint = if ((currentPhoto?.id ?: 0L) > 0) {
                                if (currentPhoto?.favorite == true) FavoriteStarColor else Color(0xFF666666)
                            } else {
                                Color(0xFFCCCCCC)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = {
                        val authority = androidContext.packageName + ".fileprovider"
                        val uri = FileProvider.getUriForFile(androidContext, authority, File(display.path))
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        androidContext.startActivity(Intent.createChooser(share, "Share photo"))
                    }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_share),
                            contentDescription = "Share"
                        )
                    }

                    IconButton(
                        onClick = { isRenaming = !isRenaming },
                        enabled = photoId > 0
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_edit),
                            contentDescription = "Edit",
                            tint = if (photoId > 0) Color.Unspecified else Color(0xFFCCCCCC)
                        )
                    }

                    IconButton(onClick = { vm.requestDelete(onDone = { nav.navigateUp() }) }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_delete),
                            contentDescription = "Delete"
                        )
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
            val photosToShow = if (photoPath != null && photo == null) {
                displayPhoto?.let { listOf(it) } ?: emptyList()
            } else {
                albumPhotos
            }

            PhotoHorizontalPager(
                photos = photosToShow,
                currentPhotoId = if (photoPath != null && photo == null) -1L else photoId,
                modifier = Modifier.fillMaxSize(),
                onPhotoChanged = { photoEntity ->
                    currentPhoto = photoEntity
                    isRenaming = false
                    focusManager.clearFocus()
                }
            )

            if (isRenaming) {
                val currentExtension = currentPhoto?.filename
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotBlank() }
                val renameTitle = if (
                    currentExtension != null && MediaFileSupport.isVideoExtension(currentExtension)
                ) {
                    "Rename Video"
                } else {
                    "Rename Photo"
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = renameTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("File name") },
                            singleLine = true,
                            supportingText = {
                                currentExtension?.let { extension ->
                                    Text("Extension .$extension will be preserved")
                                }
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    isRenaming = false
                                    focusManager.clearFocus()
                                }
                            ) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    currentPhoto?.let { photoEntity ->
                                        if (renameText.text.isBlank()) {
                                            vm.showSnackbar("Name can't be empty")
                                            return@let
                                        }
                                        vm.rename(photoEntity.id, renameText.text)
                                        isRenaming = false
                                        focusManager.clearFocus()
                                        vm.showSnackbar("Name updated")
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
    private val userPrefs = UserPrefs(app)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    private val sort = userPrefs.sortFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SortMode.DATE_NEW
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val albumPhotos = when (context) {
        "favorites" -> photos.observeFavorites(limit = 1000)
        "recents" -> photos.observeRecents(limit = 1000)
        else -> sort.flatMapLatest { mode ->
            photos.observePhotos(albumId, mode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            albumPhotos.collect { loadedPhotos ->
                android.util.Log.d("PhotoDetailVM", "Album photos loaded: ${loadedPhotos.size} photos for album $albumId")
                loadedPhotos.forEachIndexed { index, photoEntity ->
                    android.util.Log.d("PhotoDetailVM", "Photo $index: id=${photoEntity.id}, filename=${photoEntity.filename}")
                }
            }
        }
    }

    private val _photo = MutableStateFlow<PhotoEntity?>(null)
    val photo: StateFlow<PhotoEntity?> = _photo

    init {
        viewModelScope.launch {
            if (photoId > 0) {
                _photo.value = photos.getPhoto(photoId)
            }
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

    fun rename(photoId: Long, newName: String) = viewModelScope.launch {
        if (photoId > 0) {
            photos.renamePhoto(photoId, newName)
            refresh()
        }
    }

    fun requestDelete(onDone: () -> Unit) = viewModelScope.launch {
        if (photoId > 0) {
            photos.deletePhoto(photoId)
            onDone()
        } else {
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
        val currentPhotos = albumPhotos.value
        val currentIndex = currentPhotos.indexOfFirst { it.id == photoId }
        return if (currentIndex > 0) currentPhotos[currentIndex - 1].id else null
    }

    fun navigateToNext(): Long? {
        if (photoId <= 0) return null
        val currentPhotos = albumPhotos.value
        val currentIndex = currentPhotos.indexOfFirst { it.id == photoId }
        return if (currentIndex < currentPhotos.size - 1) currentPhotos[currentIndex + 1].id else null
    }

    private fun refresh() = viewModelScope.launch {
        if (photoId > 0) {
            _photo.value = photos.getPhoto(photoId)
        }
    }
}
