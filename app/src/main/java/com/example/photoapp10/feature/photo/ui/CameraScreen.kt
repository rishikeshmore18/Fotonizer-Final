package com.example.photoapp10.feature.photo.ui

import android.Manifest
import android.content.Context
import android.graphics.Matrix
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.photoapp10.core.permissions.CameraPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
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
    
    // Camera state
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    
    // UI state
    val isFlashEnabled by viewModel.isFlashEnabled.collectAsStateWithLifecycle()
    val currentZoomRatio by viewModel.currentZoomRatio.collectAsStateWithLifecycle()
    val showSavedOverlay by viewModel.showSavedOverlay.collectAsStateWithLifecycle()
    val lastCapturedPhotoPath by viewModel.lastCapturedPhotoPath.collectAsStateWithLifecycle()
    
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
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    CameraPermission(
        onGranted = {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onCameraProviderReady = { provider, previewView ->
                    cameraProvider = provider
                    startCamera(provider, previewView, lifecycleOwner, cameraExecutor) { cam, imgCap ->
                        camera = cam
                        imageCapture = imgCap
                    }
                },
                onZoomChanged = { ratio ->
                    viewModel.updateZoomRatio(ratio)
                }
            )
            
            // Handle photo capture
            LaunchedEffect(captureTrigger) {
                if (captureTrigger > 0) {
                    capturePhoto(imageCapture, camera, isFlashEnabled, targetAlbumId, viewModel, context)
                }
            }
            
            // Apply zoom changes to camera
            LaunchedEffect(currentZoomRatio) {
                camera?.cameraControl?.setZoomRatio(currentZoomRatio)
            }
            
            // Apply flash changes to camera
            LaunchedEffect(isFlashEnabled) {
                camera?.cameraControl?.enableTorch(isFlashEnabled)
            }
            
            // Camera controls overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Top controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    // Flash toggle
                    IconButton(
                        onClick = { viewModel.toggleFlash() },
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (isFlashEnabled) "Flash On" else "Flash Off",
                            tint = Color.White
                        )
                    }
                }
                
                // Zoom controls - positioned above shutter button
                ZoomControls(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    currentZoom = currentZoomRatio,
                    onZoomLevelSelected = { level ->
                        viewModel.setZoomLevel(level)
                        camera?.cameraControl?.setZoomRatio(level.toFloat())
                    }
                )
                
                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Shutter button with animation
                    AnimatedShutterButton(
                        onClick = {
                            captureTrigger++
                        },
                        modifier = Modifier.size(80.dp)
                    )
                }
                
                // Photo thumbnail in bottom left
                lastCapturedPhotoPath?.let { photoPath ->
                    PhotoThumbnail(
                        photoPath = photoPath,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        onClick = {
                            // Navigate to photo detail with URL-encoded path
                            val encodedPath = java.net.URLEncoder.encode(photoPath, "UTF-8")
                            navController.navigate("photo_detail/$encodedPath")
                        }
                    )
                }
                
            }
        }
    )
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraProviderReady: (ProcessCameraProvider, PreviewView) -> Unit,
    onZoomChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                
                // Add pinch-to-zoom gesture detector
                val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    private var lastZoomRatio = 1.0f
                    
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        lastZoomRatio = 1.0f
                        return true
                    }
                    
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val delta = detector.scaleFactor
                        val newZoomRatio = (lastZoomRatio * delta).coerceIn(1.0f, 2.0f) // Limit to 2x max
                        onZoomChanged(newZoomRatio)
                        lastZoomRatio = newZoomRatio
                        return true
                    }
                })
                
                setOnTouchListener { _, event ->
                    scaleGestureDetector.onTouchEvent(event)
                    true
                }
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                onCameraProviderReady(cameraProvider, previewView)
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

private fun startCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    executor: ExecutorService,
    onCameraReady: (Camera, ImageCapture) -> Unit
) {
    val preview = Preview.Builder().build()
    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
    
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    try {
        cameraProvider.unbindAll()
        
        // Bind preview to PreviewView
        preview.setSurfaceProvider(previewView.surfaceProvider)
        
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
        
        onCameraReady(camera, imageCapture)
        
    } catch (exc: Exception) {
        Timber.e(exc, "Camera binding failed")
    }
}

private fun capturePhoto(
    imageCapture: ImageCapture?,
    camera: Camera?,
    isFlashEnabled: Boolean,
    albumId: Long,
    viewModel: CameraViewModel,
    context: Context
) {
    
    // Set flash mode
    val flashMode = if (isFlashEnabled) {
        ImageCapture.FLASH_MODE_ON
    } else {
        ImageCapture.FLASH_MODE_OFF
    }
    
    // Create temp file
    val photoFile = viewModel.createTempPhotoFile(albumId)
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture?.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Timber.d("Photo saved successfully: ${photoFile.absolutePath}")
                viewModel.savePhotoToAlbum(photoFile, albumId)
            }
            
            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "Photo capture failed")
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
            .background(
                Color.Black.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            )
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
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onZoomLevelSelected(level) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SavedOverlay(
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "saved_overlay_alpha"
    )
    
    Box(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f * alpha),
                RoundedCornerShape(16.dp)
            )
            .padding(24.dp)
    ) {
        Text(
            text = "Saved ✓",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnimatedShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "shutter_scale"
    )
    
    val outerScale by animateFloatAsState(
        targetValue = if (isPressed) 1.1f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 200f
        ),
        label = "outer_scale"
    )
    
    Box(
        modifier = modifier
            .scale(outerScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                // Reset after a short delay
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(150)
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    CircleShape
                )
        )
        
        // Inner button
        Box(
            modifier = Modifier
                .size(60.dp)
                .scale(scale)
                .background(
                    Color.White,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        Color.Black,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photoPath: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .background(
                Color.Black.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photoPath,
            contentDescription = "Last captured photo",
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_menu_camera),
            placeholder = painterResource(android.R.drawable.ic_menu_camera)
        )
    }
}
