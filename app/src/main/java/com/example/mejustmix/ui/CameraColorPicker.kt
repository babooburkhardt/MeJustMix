package com.example.mejustmix.ui

import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlin.math.pow

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraColorPicker(
    onColorPicked: (Color) -> Unit,
    onClose: () -> Unit
) {
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }
    
    if (permissionState.status.isGranted) {
        CameraContent(onColorPicked = onColorPicked, onClose = onClose)
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission needed")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun CameraContent(onColorPicked: (Color) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var rawColor by remember { mutableStateOf(Color.Gray) }
    var calibratedWhite by remember { mutableStateOf<Color?>(null) }
    var flashEnabled by remember { mutableStateOf(true) }
    var exposureCompensation by remember { mutableStateOf(0) }

    val finalColor by remember(rawColor, calibratedWhite) {
        derivedStateOf {
            if (calibratedWhite == null) {
                rawColor
            } else {
                calibrateColor(rawColor, calibratedWhite!!)
            }
        }
    }

    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var exposureState by remember { mutableStateOf<androidx.camera.core.ExposureState?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Control flash
    LaunchedEffect(flashEnabled, cameraControl) {
        cameraControl?.enableTorch(flashEnabled)
    }

    // Control exposure compensation
    LaunchedEffect(exposureCompensation, cameraControl, exposureState) {
        exposureState?.let { state ->
            if (state.isExposureCompensationSupported) {
                cameraControl?.setExposureCompensationIndex(exposureCompensation)
            }
        }
    }

    // Setup camera
    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(view.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // OPTIMIZATION: Gentle throttle to reduce CPU usage while maintaining responsiveness
            var lastAnalysisTime = 0L
            val ANALYSIS_INTERVAL_MS = 33L // ~30 FPS - smooth but efficient

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                val currentTime = System.currentTimeMillis()
                try {
                    if (currentTime - lastAnalysisTime >= ANALYSIS_INTERVAL_MS) {
                        rawColor = getCenterColor(image)
                        lastAnalysisTime = currentTime
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                cameraControl = camera.cameraControl
                exposureState = camera.cameraInfo.exposureState
                cameraControl?.enableTorch(flashEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraControl?.enableTorch(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Targeting circle
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(80.dp)) {
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    style = Stroke(width = 2f),
                    alpha = 0.8f
                )
            }
        }

        // Close button
        IconButton(
            onClick = {
                cameraControl?.enableTorch(false)
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }

        // Side: Flash toggle + Exposure compensation
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flash toggle - more prominent
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { flashEnabled = !flashEnabled }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (flashEnabled) MaterialTheme.colorScheme.primary else Color.DarkGray,
                            CircleShape
                        )
                        .border(
                            2.dp,
                            if (flashEnabled) Color.White else Color.Gray,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        "Toggle Flash",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (flashEnabled) "ON" else "OFF",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Divider(
                modifier = Modifier.width(40.dp),
                color = Color.White.copy(alpha = 0.3f)
            )
            
            // Exposure compensation (brightness)
            val maxEV = exposureState?.exposureCompensationRange?.upper ?: 6
            val minEV = exposureState?.exposureCompensationRange?.lower ?: -6
            
            if (exposureState?.isExposureCompensationSupported == true) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { 
                            if (exposureCompensation < maxEV) {
                                exposureCompensation++
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, "Brighter", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    
                    // EV indicator
                    Text(
                        "EV",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    
                    // Visual brightness bar
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(80.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        val range = maxEV - minEV
                        val position = if (range > 0) {
                            ((exposureCompensation - minEV).toFloat() / range) * 80
                        } else 40f
                        
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(position.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.White)
                        )
                    }
                    
                    Text(
                        exposureCompensation.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                    
                    IconButton(
                        onClick = { 
                            if (exposureCompensation > minEV) {
                                exposureCompensation--
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, "Dimmer", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Bottom: Instructions + Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status message
            AnimatedContent(
                targetState = calibratedWhite == null,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { notCalibrated ->
                if (notCalibrated) {
                    Text(
                        "1. Point at white paper",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                } else {
                    Text(
                        "2. Tap color circle to pick ↓",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Calibrate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        calibratedWhite = rawColor
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(
                                if (calibratedWhite == null) MaterialTheme.colorScheme.primary 
                                else Color.DarkGray,
                                CircleShape
                            )
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WbSunny,
                            "Calibrate",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (calibratedWhite == null) "Calibrate" else "✓ Done",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Pick Color
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(enabled = calibratedWhite != null) {
                        if (calibratedWhite != null) {
                            cameraControl?.enableTorch(false)
                            onColorPicked(finalColor)
                        }
                    }
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (calibratedWhite != null) 1f else 0.85f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    )
                    
                    // OPTIMIZATION: Only animate when calibrated (stops animation in background)
                    val pulseScale = if (calibratedWhite != null) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulseAnimation")
                        val pulse by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )
                        pulse
                    } else {
                        1f
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(scale * pulseScale)
                            .background(finalColor, CircleShape)
                            .border(
                                4.dp, 
                                if (calibratedWhite != null) Color(0xFF4CAF50) else Color.Gray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (calibratedWhite != null) {
                            Icon(
                                Icons.Default.TouchApp,
                                "Tap to pick",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (calibratedWhite != null) "TAP TO PICK" else "Pick Color",
                        color = if (calibratedWhite != null) Color(0xFF4CAF50) else Color.Gray,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (calibratedWhite != null) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun getCenterColor(image: ImageProxy): Color {
    val planes = image.planes
    val buffer = planes[0].buffer
    val width = image.width
    val height = image.height
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    
    val centerX = width / 2
    val centerY = height / 2
    
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0
    
    // OPTIMIZATION: Reduced from 30 (900 pixels) to 15 (225 pixels) for better performance
    val sampleSize = 15
    for (dy in -sampleSize/2 until sampleSize/2) {
        for (dx in -sampleSize/2 until sampleSize/2) {
            val x = (centerX + dx).coerceIn(0, width - 1)
            val y = (centerY + dy).coerceIn(0, height - 1)
            
            val offset = (y * rowStride) + (x * pixelStride)
            
            try {
                buffer.position(offset)
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                
                rSum += r
                gSum += g
                bSum += b
                count++
            } catch (e: Exception) {
                continue
            }
        }
    }
    
    if (count == 0) return Color.Gray
    
    val avgR = (rSum / count).toInt()
    val avgG = (gSum / count).toInt()
    val avgB = (bSum / count).toInt()
    
    return Color(avgR, avgG, avgB)
}

private fun calibrateColor(target: Color, whiteRef: Color): Color {
    val tR = (target.red * 255).toInt()
    val tG = (target.green * 255).toInt()
    val tB = (target.blue * 255).toInt()
    
    val wR = (whiteRef.red * 255).toInt()
    val wG = (whiteRef.green * 255).toInt()
    val wB = (whiteRef.blue * 255).toInt()
    
    val wRSafe = wR.coerceAtLeast(1)
    val wGSafe = wG.coerceAtLeast(1)
    val wBSafe = wB.coerceAtLeast(1)
    
    val newR = ((tR.toFloat() / wRSafe) * 255).toInt().coerceIn(0, 255)
    val newG = ((tG.toFloat() / wGSafe) * 255).toInt().coerceIn(0, 255)
    val newB = ((tB.toFloat() / wBSafe) * 255).toInt().coerceIn(0, 255)
    
    return Color(newR, newG, newB)
}
