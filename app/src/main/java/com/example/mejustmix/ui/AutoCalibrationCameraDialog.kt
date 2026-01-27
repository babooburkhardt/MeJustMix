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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.toArgb
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.utils.CalibrationTarget
import com.example.mejustmix.utils.CameraCalibrationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Rect as AndroidRect

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
    
    // Sampling State
    var loupePosition by remember { mutableStateOf(Offset(200f, 200f)) } // Screen coords
    var imageRect by remember { mutableStateOf(Size.Zero) } // Size of image on screen
    var imageOffset by remember { mutableStateOf(Offset.Zero) } // Offset of image on screen
    
    // Calibration Data
    // Store sampled RGB integers. null = not set.
    val samples = remember { mutableStateMapOf<CalibrationTarget, Int>() }
    var whiteRefColor by remember { mutableStateOf<Int?>(null) }
    
    // Current Preview under Loupe
    var currentSampledColor by remember { mutableStateOf(Color.Transparent) }
    
    // Camera/Gallery Launchers
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) currentBitmap = bitmap
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
             scope.launch(Dispatchers.IO) {
                try {
                     val stream = context.contentResolver.openInputStream(uri)
                     val bmp = BitmapFactory.decodeStream(stream)
                     currentBitmap = bmp
                } catch (e: Exception) {
                    // Handle error
                }
             }
        }
    }
    
    // Color Sampling Logic
    fun sampleCurrentPosition() {
        val bmp = currentBitmap ?: return
        
        // Map screen coords (loupePosition) to Bitmap coords
        // imageOffset is top-left of image relative to the Box
        // imageRect is size of image on screen
        
        val relX = loupePosition.x - imageOffset.x
        val relY = loupePosition.y - imageOffset.y
        
        if (relX < 0 || relY < 0 || relX > imageRect.width || relY > imageRect.height) {
            currentSampledColor = Color.Transparent
            return
        }
        
        // Scale to bitmap
        val scaleX = bmp.width / imageRect.width
        val scaleY = bmp.height / imageRect.height
        
        val bmpX = (relX * scaleX).toInt()
        val bmpY = (relY * scaleY).toInt()
        
        // Sample 30px box around point (scaled)
        val boxSize = (30 * scaleX).toInt().coerceAtLeast(1)
        val rect = AndroidRect(
            bmpX - boxSize/2, 
            bmpY - boxSize/2, 
            bmpX + boxSize/2, 
            bmpY + boxSize/2
        )
        
        val colorInt = CameraCalibrationUtils.sampleAverageColor(bmp, rect)
        currentSampledColor = Color(colorInt)
    }
    
    // Auto-sample when moving loupe
    LaunchedEffect(loupePosition, currentBitmap, imageRect) {
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
                        .pointerInput(currentBitmap) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                loupePosition += dragAmount
                            }
                        }
                ) {
                    if (currentBitmap != null) {
                        ImageWithLoupe(
                            bitmap = currentBitmap!!,
                            loupePos = loupePosition,
                            onLayout = { offset, size ->
                                imageOffset = offset
                                imageRect = size
                            }
                        )
                    } else {
                        // Empty State
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Take a photo of your test sheet", color = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { cameraLauncher.launch() }) {
                                    Icon(Icons.Default.CameraAlt, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Camera")
                                }
                                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                                    Icon(Icons.Default.Image, null)
                                    Spacer(Modifier.width(8.dp))
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
                        
                        // Current Loupe Preview
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Loupe Color:")
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(currentSampledColor, CircleShape)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageWithLoupe(
    bitmap: Bitmap,
    loupePos: Offset,
    onLayout: (Offset, Size) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val boxRatio = maxWidth / maxHeight
        
        // Calculate fit
        var displayWidth = 0f
        var displayHeight = 0f
        var offsetX = 0f
        var offsetY = 0f
        
        val density = LocalDensity.current
        
        if (imageRatio > boxRatio) {
            // Limited by width
            displayWidth = maxWidth.value * density.density
            displayHeight = displayWidth / imageRatio
            offsetY = (maxHeight.value * density.density - displayHeight) / 2f
        } else {
            // Limited by height
            displayHeight = maxHeight.value * density.density
            displayWidth = displayHeight * imageRatio
            offsetX = (maxWidth.value * density.density - displayWidth) / 2f
        }

        // Report layout
        SideEffect {
            onLayout(Offset(offsetX, offsetY), Size(displayWidth, displayHeight))
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Calibration Image",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        
        // Draw Loupe Cursor
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = 40f,
                center = loupePos,
                style = Stroke(width = 4f)
            )
            drawCircle(
                color = Color.Black,
                radius = 41f,
                center = loupePos,
                style = Stroke(width = 1f)
            )
            // Crosshair
            drawLine(Color.White, Offset(loupePos.x - 10, loupePos.y), Offset(loupePos.x + 10, loupePos.y), 2f)
            drawLine(Color.White, Offset(loupePos.x, loupePos.y - 10), Offset(loupePos.x, loupePos.y + 10), 2f)
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
