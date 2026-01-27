package com.example.mejustmix.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.utils.CalibrationTarget
import com.example.mejustmix.utils.CameraCalibrationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.graphics.Rect as AndroidRect
import kotlin.math.roundToInt

import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCalibrationCameraDialog(
    onDismissRequest: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // States
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Viewport State
    var containerSize by remember { mutableStateOf(Size.Zero) }
    
    // Sampling State
    // We'll init this to center once we know container size
    var loupePosition by remember { mutableStateOf(Offset.Unspecified) } 
    
    // Image Transform State
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Calculated Image Placement (Fill/Fit logic)
    var imageRect by remember { mutableStateOf(Size.Zero) } // Scaled size (before zoom)
    var imageTopLeft by remember { mutableStateOf(Offset.Zero) } // Offset (before pan)
    
    // Calibration Data
    val samples = remember { mutableStateMapOf<CalibrationTarget, Int>() }
    var whiteRefColor by remember { mutableStateOf<Int?>(null) }
    
    // Current Preview under Loupe
    var currentSampledColor by remember { mutableStateOf(Color.Transparent) }
    
    // Camera/Gallery Launchers
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            currentBitmap = bitmap
            scale = 1f
            offset = Offset.Zero
        }
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
             scope.launch(Dispatchers.IO) {
                // Use safe optimized loader
                val bmp = loadOptimizedBitmap(context, uri)
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        currentBitmap = bmp
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
             }
        }
    }
    
    // Initialize Loupe Center
    LaunchedEffect(containerSize) {
        if (containerSize != Size.Zero && loupePosition == Offset.Unspecified) {
            loupePosition = Offset(containerSize.width / 2f, containerSize.height / 2f)
        }
    }
    
    // Color Sampling Logic
    fun sampleCurrentPosition() {
        val bmp = currentBitmap ?: return
        if (loupePosition == Offset.Unspecified || containerSize == Size.Zero) return

        // Pivot is center of container
        val pivotX = containerSize.width / 2f
        val pivotY = containerSize.height / 2f

        // 1. Untransform Screen (Loupe) Point to Box Point (Reverse graphicsLayer)
        // Screen = (Box - Pivot) * Scale + Pivot + Offset
        // Box - Pivot = (Screen - Pivot - Offset) / Scale
        // Box = ((Screen - Pivot - Offset) / Scale) + Pivot
        
        val boxX = ((loupePosition.x - pivotX - offset.x) / scale) + pivotX
        val boxY = ((loupePosition.y - pivotY - offset.y) / scale) + pivotY
        
        // 2. Untransform Box Point to Bitmap Point (Reverse ContentScale.Fit)
        // Box = ImageTopLeft + (Bitmap * FitScale)
        // Bitmap = (Box - ImageTopLeft) / FitScale
        
        val fitScaleX = imageRect.width / bmp.width.toFloat()
        if (fitScaleX == 0f) return
        
        val relX = (boxX - imageTopLeft.x) / fitScaleX
        val relY = (boxY - imageTopLeft.y) / fitScaleX
        
        // Check bounds (in Bitmap coordinates)
        if (relX < 0 || relY < 0 || relX > bmp.width || relY > bmp.height) {
            currentSampledColor = Color.Transparent
            return
        }
        
        // Sample area
        val sampleSizePx = 10 
        val rect = AndroidRect(
            (relX - sampleSizePx).toInt(), 
            (relY - sampleSizePx).toInt(), 
            (relX + sampleSizePx).toInt(), 
            (relY + sampleSizePx).toInt()
        )
        
        val colorInt = CameraCalibrationUtils.sampleAverageColor(bmp, rect)
        currentSampledColor = Color(colorInt)
    }

    // Auto-sample when anything changes
    LaunchedEffect(loupePosition, currentBitmap, scale, offset, imageRect, containerSize) {
        sampleCurrentPosition()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Manual Calibration") },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                saveManualCalibration(samples, settingsViewModel)
                                onDismissRequest()
                            },
                            enabled = samples.isNotEmpty()
                        ) {
                            Text("Apply", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                // Image Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                        .onGloballyPositioned { coords ->
                            containerSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                        }
                        // Handle Loupe Dragging Layer
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, _, _ ->
                                // Only accumulate pan for the loupe position
                                if (loupePosition != Offset.Unspecified) {
                                    loupePosition += pan
                                }
                            }
                        }
                ) {
                    if (currentBitmap != null) {
                        ImageWithTransform(
                            bitmap = currentBitmap!!,
                            scale = 1f,
                            offset = Offset.Zero,
                            onLayout = { pos, rect ->
                                imageTopLeft = pos
                                imageRect = rect
                            }
                        )
                        
                        // Loupe Layer
                        if (loupePosition != Offset.Unspecified) {
                            LoupeCursor(
                                position = loupePosition,
                                color = currentSampledColor,
                                onDrag = { dragAmount ->
                                    loupePosition += dragAmount
                                }
                            )
                        }
                        
                    } else {
                        // Empty State
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Text("Take a CLOSE-UP photo", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Ensure blobs are large and clear", color = Color.LightGray)
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { cameraLauncher.launch() }) {
                                    Text("Open Camera")
                                }
                                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                                    Text("Gallery")
                                }
                            }
                        }
                    }
                }
                
                // Tools / Targets
                if (currentBitmap != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Set White Reference first", style = MaterialTheme.typography.labelSmall)
                        
                        // White Ref Button
                        val hasWhite = samples.containsKey(CalibrationTarget.WHITE_REF)
                        Button(
                            onClick = {
                                val color = currentSampledColor.toArgb()
                                samples[CalibrationTarget.WHITE_REF] = color
                                whiteRefColor = color
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasWhite) Color.Green else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (hasWhite) Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (hasWhite) "White Ref Set (Update)" else "Set White Reference using Loupe")
                        }
                        
                        Divider()
                        
                        Text("2. Sample Pigments", style = MaterialTheme.typography.labelSmall)
                        
                        val instructionsText = if (whiteRefColor == null) "Set White Ref above to enable sampling" else "Drag Loupe & Tap Below"
                        Text(instructionsText, color = if(whiteRefColor == null) Color.Red else Color.Gray, style = MaterialTheme.typography.bodySmall)

                        // Target Grid/List
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(CalibrationTarget.values().filter { it != CalibrationTarget.WHITE_REF }) { target ->
                                val sample = samples[target]
                                val isSet = sample != null
                                
                                val borderColor = if(target.name.contains("CYAN")) Color.Cyan 
                                     else if(target.name.contains("MAGENTA")) Color.Magenta 
                                     else if(target.name.contains("YELLOW")) Color.Yellow 
                                     else Color.Black

                                Surface(
                                    selected = isSet,
                                    onClick = {
                                        if (whiteRefColor == null) return@Surface
                                        val rawColor = currentSampledColor.toArgb()
                                        val corrected = correctWhiteBalance(rawColor, whiteRefColor!!)
                                        samples[target] = corrected
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if(isSet) borderColor else Color.Gray),
                                    color = if(isSet) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSet) {
                                            Box(Modifier.size(12.dp).background(Color(sample!!), CircleShape).border(1.dp, Color.White, CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                        } else {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(target.pigmentName + if(target.isMasstone) " (M)" else " (T)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageWithTransform(
    bitmap: Bitmap,
    scale: Float,
    offset: Offset,
    onLayout: (Offset, Size) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val boxRatio = maxWidth / maxHeight
        
        // Calculate fit center logic manually to report correct bounds to parent
        val density = LocalDensity.current
        var displayWidth = 0f
        var displayHeight = 0f
        var startX = 0f
        var startY = 0f
        
        if (imageRatio > boxRatio) {
            // Limited by width
            displayWidth = maxWidth.value * density.density
            displayHeight = displayWidth / imageRatio
            startY = (maxHeight.value * density.density - displayHeight) / 2f
        } else {
            // Limited by height
            displayHeight = maxHeight.value * density.density
            displayWidth = displayHeight * imageRatio
            startX = (maxWidth.value * density.density - displayWidth) / 2f
        }

        SideEffect {
            onLayout(Offset(startX, startY), Size(displayWidth, displayHeight))
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Calibration Image",
            contentScale = ContentScale.Fit, // This handles the initial fit
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
fun LoupeCursor(
    position: Offset,
    color: Color,
    onDrag: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val sizeDp = 120.dp
    val radiusPx = with(density) { (sizeDp / 2).toPx() }
    
    Box(
        modifier = Modifier
            .offset { 
                IntOffset(
                    (position.x - radiusPx).roundToInt(), 
                    (position.y - radiusPx).roundToInt()
                ) 
            }
            .size(sizeDp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Center of Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            
            // Outer Ring
            drawCircle(
                color = Color.Black,
                radius = 32.dp.toPx(), // Visual size
                center = Offset(cx, cy),
                style = Stroke(width = 8f)
            )
            drawCircle(
                color = Color.White,
                radius = 32.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 6f)
            )
            
            // Crosshair
            drawLine(Color.White, Offset(cx - 20f, cy), Offset(cx + 20f, cy), 4f)
            drawLine(Color.White, Offset(cx, cy - 20f), Offset(cx, cy + 20f), 4f)
            
            // Sampled Color Preview Ring (Inside)
            drawCircle(
                 color = color,
                 radius = 24.dp.toPx(),
                 center = Offset(cx, cy),
                 style = Stroke(width = 10f)
            )
        }
    }
}

fun correctWhiteBalance(rawColor: Int, whiteRef: Int): Int {
    // Simple Von Kries scaling
    val rRaw = android.graphics.Color.red(rawColor)
    val gRaw = android.graphics.Color.green(rawColor)
    val bRaw = android.graphics.Color.blue(rawColor)
    
    val rWhite = android.graphics.Color.red(whiteRef).coerceAtLeast(1)
    val gWhite = android.graphics.Color.green(whiteRef).coerceAtLeast(1)
    val bWhite = android.graphics.Color.blue(whiteRef).coerceAtLeast(1)
    
    // Scale so white ref becomes 255
    val rNew = (rRaw * (255f / rWhite)).coerceIn(0f, 255f).toInt()
    val gNew = (gRaw * (255f / gWhite)).coerceIn(0f, 255f).toInt()
    val bNew = (bRaw * (255f / bWhite)).coerceIn(0f, 255f).toInt()
    
    return android.graphics.Color.rgb(rNew, gNew, bNew)
}

fun saveManualCalibration(
    samples: Map<CalibrationTarget, Int>, 
    viewModel: SettingsViewModel
) {
    // Only process pairs where Masstone is present
    listOf("Cyan", "Magenta", "Yellow", "Black").forEach { name ->
        val masstoneTarget = CalibrationTarget.values().find { it.pigmentName == name && it.isMasstone }
        val tintTarget = CalibrationTarget.values().find { it.pigmentName == name && !it.isMasstone }
        
        val masstoneColor = samples[masstoneTarget]
        val tintColor = samples[tintTarget]
        
        if (masstoneColor != null) {
            val currentKS = viewModel.getPigmentKS(name)
            
            // Calc K/S from Masstone
            val rgbKS = com.example.mejustmix.services.KubelkaMunkColorMixing.rgbToKS(masstoneColor)
            
            // Calc S from Tint
            var newS = currentKS.s
            var tintHex = currentKS.tintHex
            
            if (tintColor != null) {
                val tintKS = com.example.mejustmix.services.KubelkaMunkColorMixing.rgbToKS(tintColor)
                newS = com.example.mejustmix.services.KubelkaMunkColorMixing.solveScattering(rgbKS, tintKS, 1.0f)
                tintHex = String.format("#%06X", tintColor and 0xFFFFFF)
            }
            
            val finalKS = KSColor(
                ksR = rgbKS.ksR,
                ksG = rgbKS.ksG,
                ksB = rgbKS.ksB,
                s = newS,
                tintHex = tintHex
            )
            
            viewModel.updatePigmentKS(name, finalKS)
        }
    }
}
