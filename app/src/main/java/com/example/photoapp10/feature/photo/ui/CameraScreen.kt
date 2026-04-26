package com.example.photoapp10.feature.photo.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.photoapp10.core.permissions.CameraPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    navController: NavController,
    albumId: Long = 0L,
    viewModel: CameraViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = CameraViewModel.factory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lock camera screen to portrait orientation; restore on exit
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    // Camera state
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    // UI state
    val isFlashEnabled by viewModel.isFlashEnabled.collectAsStateWithLifecycle()
    val currentZoomRatio by viewModel.currentZoomRatio.collectAsStateWithLifecycle()
    val showSavedOverlay by viewModel.showSavedOverlay.collectAsStateWithLifecycle()
    val lastCapturedPhotoPath by viewModel.lastCapturedPhotoPath.collectAsStateWithLifecycle()
    
    // Mode state
    var isVideoMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationNanos by remember { mutableStateOf(0L) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    
    // Capture trigger
    var captureTrigger by remember { mutableStateOf(0) }
    
    // Get target album ID
    var targetAlbumId by remember(albumId) { mutableStateOf(albumId) }
    
    // Update target album ID asynchronously
    LaunchedEffect(albumId) {
        if (albumId > 0) {
            targetAlbumId = albumId
        } else {
            targetAlbumId = viewModel.getTargetAlbumId(albumId)
        }
    }
    
    CameraPermission(
        onGranted = {
            // Main layout setup with proper system bars handling
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black) // Background for areas not covered by preview
            ) {
                // 1. Camera Preview (Full Screen)
                // We keep one instance and just rebind use cases
                var previewView by remember { mutableStateOf<PreviewView?>(null) }
                
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            
                            val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                private var lastZoomRatio = 1.0f
                                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                                    lastZoomRatio = 1.0f
                                    return true
                                }
                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                    val delta = detector.scaleFactor
                                    val newZoomRatio = (lastZoomRatio * delta).coerceIn(1.0f, 2.0f)
                                    viewModel.updateZoomRatio(newZoomRatio)
                                    return true
                                }
                            })
                            
                            setOnTouchListener { _, event ->
                                scaleGestureDetector.onTouchEvent(event)
                                true
                            }
                            previewView = this
                        }
                    },
                    update = { 
                        // View update unrelated to camera binding logic
                    }
                )

                // Initialize Camera Provider
                LaunchedEffect(Unit) {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        cameraProvider = providerFuture.get()
                    }, ContextCompat.getMainExecutor(context))
                }

                // Rebind Camera when Mode or Provider changes
                LaunchedEffect(cameraProvider, isVideoMode, previewView, lensFacing) {
                    val provider = cameraProvider
                    val view = previewView
                    if (provider != null && view != null) {
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()
                        bindCameraUseCases(
                            provider,
                            view,
                            lifecycleOwner,
                            isVideoMode,
                            cameraSelector
                        ) { cam, imgCap, vidCap ->
                            camera = cam
                            imageCapture = imgCap
                            videoCapture = vidCap
                        }
                    }
                }

                // Capture Logic
                LaunchedEffect(captureTrigger) {
                    Timber.d("LaunchedEffect triggered: captureTrigger=$captureTrigger, isVideoMode=$isVideoMode, isRecording=$isRecording")
                    if (captureTrigger > 0) {
                        if (isVideoMode) {
                            if (isRecording) {
                                // STOP Recording
                                activeRecording?.stop()
                                activeRecording = null
                                isRecording = false
                            } else {
                                // START Recording
                                Timber.d("Attempting to start video recording. isVideoMode=$isVideoMode, videoCapture=${videoCapture != null}")
                                
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    Timber.w("Microphone permission not granted")
                                    viewModel.updateSnackbar("Microphone permission required for video recording")
                                    return@LaunchedEffect
                                }
                                
                                val vidCap = videoCapture
                                if (vidCap == null) {
                                    Timber.w("VideoCapture is null - camera may not be ready yet")
                                    viewModel.updateSnackbar("Camera not ready. Please wait a moment...")
                                    return@LaunchedEffect
                                }
                                
                                try {
                                    val videoFile = viewModel.createTempVideoFile(targetAlbumId)
                                    // Ensure parent directory exists
                                    videoFile.parentFile?.mkdirs()
                                    
                                    Timber.d("Creating video file: ${videoFile.absolutePath}")
                                    val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                    
                                    // Prepare and start recording
                                    val recorder = vidCap.output
                                    Timber.d("Preparing recording with recorder: $recorder")
                                    val pendingRecording = recorder.prepareRecording(context, outputOptions)
                                        .withAudioEnabled()
                                    
                                    Timber.d("Starting recording...")
                                    activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Start -> {
                                                isRecording = true
                                                Timber.d("Video recording started successfully")
                                            }
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                recordingDurationNanos = 0L
                                                if (!event.hasError()) {
                                                    Timber.d("Video recording finalized successfully: ${videoFile.absolutePath}")
                                                    viewModel.saveVideoToAlbum(videoFile, targetAlbumId)
                                                } else {
                                                    Timber.e("Video recording error: ${event.cause}")
                                                    viewModel.updateSnackbar("Recording failed: ${event.cause?.message ?: "Unknown error"}")
                                                    activeRecording?.close()
                                                    activeRecording = null
                                                }
                                            }
                                            is VideoRecordEvent.Status -> {
                                                recordingDurationNanos = event.recordingStats.recordedDurationNanos
                                            }
                                        }
                                    }
                                    Timber.d("Video recording initiated for file: ${videoFile.absolutePath}")
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to start video recording: ${e.message}")
                                    viewModel.updateSnackbar("Failed to start recording: ${e.message ?: "Unknown error"}")
                                }
                            }
                        } else {
                            // PHOTO Capture
                             capturePhoto(imageCapture, camera, isFlashEnabled, targetAlbumId, viewModel, context)
                        }
                    }
                }
                
                // Zoom & Flash Updates
                LaunchedEffect(currentZoomRatio) { camera?.cameraControl?.setZoomRatio(currentZoomRatio) }
                LaunchedEffect(isFlashEnabled) { camera?.cameraControl?.enableTorch(isFlashEnabled) }

                // Lock UI to portrait dimensions - calculate once and remember
                val density = LocalDensity.current
                val portraitDimensions = remember {
                    val screenWidthPx = context.resources.displayMetrics.widthPixels
                    val screenHeightPx = context.resources.displayMetrics.heightPixels
                    // Portrait: width < height (always use smaller as width, larger as height)
                    val portraitWidthPx = minOf(screenWidthPx, screenHeightPx)
                    val portraitHeightPx = maxOf(screenWidthPx, screenHeightPx)
                    with(density) {
                        Pair(portraitWidthPx.toDp(), portraitHeightPx.toDp())
                    }
                }

                // UI Overlay - Locked to portrait dimensions, anchored to top-left
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    Column(
                        modifier = Modifier
                            .size(width = portraitDimensions.first, height = portraitDimensions.second)
                            .align(Alignment.TopStart),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }

                        if (isRecording) {
                            RecordingTimer(recordingDurationNanos)
                        }

                        IconButton(
                            onClick = { viewModel.toggleFlash() },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                "Flash",
                                tint = Color.White
                            )
                        }
                    }

                    // BOTTOM CONTROLS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent), // ensure click-through if needed, or gradient
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Zoom Controls
                        ZoomControls(
                            currentZoom = currentZoomRatio,
                            onZoomLevelSelected = { level ->
                                viewModel.setZoomLevel(level)
                                camera?.cameraControl?.setZoomRatio(level.toFloat())
                            }
                        )
                        
                        Spacer(Modifier.height(16.dp))

                        // Control Panel (Modes + Shutter + Thumbnail)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Mode Switcher
                            Row(
                                modifier = Modifier.padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                ModeText("PHOTO", !isVideoMode) { isVideoMode = false }
                                ModeText("VIDEO", isVideoMode) { isVideoMode = true }
                            }

                            // Shutter Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Thumbnail
                                Box(modifier = Modifier.size(60.dp)) {
                                    lastCapturedPhotoPath?.let { path ->
                                        PhotoThumbnail(photoPath = path) {
                                            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                                            navController.navigate("photo_detail/$encodedPath")
                                        }
                                    }
                                }

                                // Shutter Button
                                AnimatedShutterButton(
                                    isRecording = isRecording,
                                    isVideoMode = isVideoMode,
                                    onClick = { 
                                        Timber.d("Shutter button clicked. isVideoMode=$isVideoMode, videoCapture=${videoCapture != null}")
                                        if (isVideoMode && videoCapture == null) {
                                            viewModel.updateSnackbar("Camera not ready. Please wait...")
                                        } else {
                                            captureTrigger++
                                        }
                                    },
                                    modifier = Modifier.size(72.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (isRecording) return@IconButton

                                        val requestedLensFacing =
                                            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                                CameraSelector.LENS_FACING_FRONT
                                            } else {
                                                CameraSelector.LENS_FACING_BACK
                                            }

                                        val nextSelector = CameraSelector.Builder()
                                            .requireLensFacing(requestedLensFacing)
                                            .build()

                                        val provider = cameraProvider
                                        if (provider == null) {
                                            lensFacing = requestedLensFacing
                                            return@IconButton
                                        }

                                        try {
                                            if (provider.hasCamera(nextSelector)) {
                                                lensFacing = requestedLensFacing
                                            } else {
                                                viewModel.updateSnackbar("Requested camera is not available on this device")
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to switch camera lens")
                                            viewModel.updateSnackbar("Unable to switch camera")
                                        }
                                    },
                                    enabled = !isRecording,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(android.R.drawable.ic_menu_rotate),
                                        contentDescription = "Switch camera",
                                        tint = if (isRecording) Color.Gray else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                    
                    if (showSavedOverlay) {
                        SavedOverlay(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    )
}

@SuppressLint("RestrictedApi")
private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isVideoMode: Boolean,
    cameraSelector: CameraSelector,
    onCameraReady: (androidx.camera.core.Camera, ImageCapture?, VideoCapture<Recorder>?) -> Unit
) {
    val preview = Preview.Builder().build()
    
    try {
        cameraProvider.unbindAll()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        
        var camera: androidx.camera.core.Camera? = null
        var imageCapture: ImageCapture? = null
        var videoCapture: VideoCapture<Recorder>? = null
        
        if (isVideoMode) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
        } else {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
                
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        }
        
        onCameraReady(camera, imageCapture, videoCapture)
        
    } catch (exc: Exception) {
        Timber.e(exc, "Camera binding failed")
    }
}

@Composable
private fun ModeText(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (isSelected) Color.Yellow else Color.White,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun RecordingTimer(durationNanos: Long) {
    Box(
        modifier = Modifier
            .background(Color.Red, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val seconds = (durationNanos / 1_000_000_000)
        Text(
            text = String.format("%02d:%02d", seconds / 60, seconds % 60),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

// ... Keep helper functions ...
private fun capturePhoto(
    imageCapture: ImageCapture?,
    camera: androidx.camera.core.Camera?,
    isFlashEnabled: Boolean,
    albumId: Long,
    viewModel: CameraViewModel,
    context: Context
) {
    if (imageCapture == null) return
    imageCapture.flashMode = if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    val photoFile = viewModel.createTempPhotoFile(albumId)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                viewModel.savePhotoToAlbum(photoFile, albumId)
            }
            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "Photo capture failed")
                viewModel.updateSnackbar("Capture failed: ${exception.message}")
            }
        }
    )
}

@Composable
private fun ZoomControls(
    modifier: Modifier = Modifier,
    currentZoom: Float,
    onZoomLevelSelected: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(1, 2).forEach { level ->
            val isSelected = when (level) {
                1 -> currentZoom <= 1.5f
                2 -> currentZoom > 1.5f
                else -> false
            }
            Text(
                text = "${level}×",
                color = if (isSelected) Color.Yellow else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onZoomLevelSelected(level) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SavedOverlay(modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "saved")
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f * alpha), RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Text("Saved ✓", color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AnimatedShutterButton(
    isRecording: Boolean,
    isVideoMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "press_scale"
    )
    val innerScale by animateFloatAsState(
        targetValue = if (isRecording) 0.5f else 1.0f,
        label = "inner_scale"
    )
    val innerColor by animateColorAsState(
        targetValue = if (isVideoMode) Color.Red else Color.White,
        label = "inner_color"
    )
    val shapeCorner by animateIntAsState(
        targetValue = if (isRecording) 25 else 50,
        label = "shape"
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    isPressed = true
                    delay(80)
                    isPressed = false
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .background(Color.Black, CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.8f)
                .scale(innerScale)
                .background(
                    innerColor,
                    RoundedCornerShape(percent = shapeCorner) 
                )
        )
    }
}

@Composable
private fun PhotoThumbnail(photoPath: String, onClick: () -> Unit) {
    val isVideo = remember(photoPath) { 
        photoPath.endsWith(".mp4", ignoreCase = true) || 
        photoPath.endsWith(".mov", ignoreCase = true) ||
        photoPath.endsWith(".avi", ignoreCase = true) ||
        photoPath.endsWith(".mkv", ignoreCase = true) ||
        photoPath.endsWith(".webm", ignoreCase = true) ||
        photoPath.endsWith(".3gp", ignoreCase = true)
    }
    
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photoPath,
            contentDescription = "Thumbnail",
            modifier = Modifier
                .size(56.dp) // Slightly smaller than container
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        // Show play icon overlay for videos
        if (isVideo) {
            Icon(
                painter = painterResource(android.R.drawable.ic_media_play),
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .padding(4.dp)
            )
        }
    }
}
