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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

/**
 * Redesigned Camera Color Picker - Clean, minimal UI that stays within card bounds.
 * 
 * Features:
 * - Compact viewfinder that doesn't cover navigation tabs
 * - Simple two-step flow: calibrate white → pick color
 * - Minimal controls: just flash toggle and exposure
 * - Large color preview with tap-to-pick
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraColorPicker(
    onColorPicked: (Color) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }
    
    if (permissionState.status.isGranted) {
        CameraContentMinimal(
            onColorPicked = onColorPicked,
            onClose = onClose,
            modifier = modifier
        )
    } else {
        // Permission request UI
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Camera permission needed",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun CameraContentMinimal(
    onColorPicked: (Color) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var rawColor by remember { mutableStateOf(Color.Gray) }
    var calibratedWhite by remember { mutableStateOf<Color?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    
    // Internal EV offset: we use -10 as our "zero" for better color accuracy
    val evOffset = -10
    var exposureAdjustment by remember { mutableIntStateOf(0) } // User-facing: -X to +X around our baseline
    val actualExposure = evOffset + exposureAdjustment // What we send to camera

    val finalColor by remember(rawColor, calibratedWhite) {
        derivedStateOf {
            if (calibratedWhite == null) rawColor
            else calibrateColor(rawColor, calibratedWhite!!)
        }
    }

    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var exposureState by remember { mutableStateOf<androidx.camera.core.ExposureState?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Control flash
    LaunchedEffect(flashEnabled, cameraControl) {
        cameraControl?.enableTorch(flashEnabled)
    }

    // Control exposure
    LaunchedEffect(actualExposure, cameraControl, exposureState) {
        exposureState?.let { state ->
            if (state.isExposureCompensationSupported) {
                val clamped = actualExposure.coerceIn(
                    state.exposureCompensationRange.lower,
                    state.exposureCompensationRange.upper
                )
                cameraControl?.setExposureCompensationIndex(clamped)
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

            var lastAnalysisTime = 0L
            val analysisIntervalMs = 50L

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                val currentTime = System.currentTimeMillis()
                try {
                    if (currentTime - lastAnalysisTime >= analysisIntervalMs) {
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

    val isCalibrated = calibratedWhite != null

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Viewfinder - compact size
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
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

            // Center crosshair - smaller
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                )
            }

            // Top-right: Flash + Exposure - more compact
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Flash toggle
                IconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = "Flash",
                        tint = if (flashEnabled) Color.Yellow else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Exposure controls - show user-facing adjustment (0 = our -10 baseline)
                if (exposureState?.isExposureCompensationSupported == true) {
                    val minEV = exposureState?.exposureCompensationRange?.lower ?: -12
                    val maxEV = exposureState?.exposureCompensationRange?.upper ?: 12
                    // Calculate user-facing range relative to our offset
                    val userMin = minEV - evOffset  // e.g., -12 - (-10) = -2
                    val userMax = maxEV - evOffset  // e.g., 12 - (-10) = 22
                    
                    IconButton(
                        onClick = { if (exposureAdjustment > userMin) exposureAdjustment-- },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Darker",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Text(
                        "${if (exposureAdjustment >= 0) "+" else ""}$exposureAdjustment",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.Center
                    )
                    
                    IconButton(
                        onClick = { if (exposureAdjustment < userMax) exposureAdjustment++ },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Brighter",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom hint - smaller
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (!isCalibrated) "Optional: Set white for better accuracy" else "Point at color",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom action row: Calibrate + Color pick - more compact
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calibrate white button (optional)
            OutlinedButton(
                onClick = { calibratedWhite = rawColor },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isCalibrated) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        Color.Transparent
                )
            ) {
                Icon(
                    if (isCalibrated) Icons.Outlined.Check else Icons.Default.WbSunny,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isCalibrated) "White Set" else "Set White",
                    fontSize = 13.sp
                )
            }

            // Color pick button - always enabled
            Button(
                onClick = {
                    cameraControl?.enableTorch(false)
                    onColorPicked(finalColor)
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = finalColor,
                    contentColor = if (finalColor.luminance() > 0.5f) 
                        Color.Black 
                    else 
                        Color.White
                )
            ) {
                Text(
                    "USE THIS COLOR",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Calculate luminance for determining text color contrast.
 */
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
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
    
    val sampleSize = 15
    for (dy in -sampleSize / 2 until sampleSize / 2) {
        for (dx in -sampleSize / 2 until sampleSize / 2) {
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
    
    val wR = (whiteRef.red * 255).toInt().coerceAtLeast(1)
    val wG = (whiteRef.green * 255).toInt().coerceAtLeast(1)
    val wB = (whiteRef.blue * 255).toInt().coerceAtLeast(1)
    
    val newR = ((tR.toFloat() / wR) * 255).toInt().coerceIn(0, 255)
    val newG = ((tG.toFloat() / wG) * 255).toInt().coerceIn(0, 255)
    val newB = ((tB.toFloat() / wB) * 255).toInt().coerceIn(0, 255)
    
    return Color(newR, newG, newB)
}
