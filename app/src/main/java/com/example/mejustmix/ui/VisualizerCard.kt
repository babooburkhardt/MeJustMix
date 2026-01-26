package com.example.mejustmix.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.mejustmix.ui.components.ModernPillTab
import com.example.mejustmix.ui.components.ModernPillTabRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mejustmix.ui.theme.getBrightness
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VisualizerCard(
    mixViewModel: MixViewModel, 
    fillHeight: Boolean = false,
    settingsViewModel: SettingsViewModel = viewModel(),
    onImportSpectralData: () -> Unit = {}
) {
    val color by mixViewModel.color.collectAsState()
    // OPTION A: Using the K-M predicted color (Physics Engine)
    val predictedColor by mixViewModel.predictedColor.collectAsState()
    val paintMix by mixViewModel.paintMix.collectAsState()
    val totalVolume by mixViewModel.totalVolume.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    
    // Use screen color for dispense button instead of predicted color
    val buttonColor = color
    val contentColor = if (buttonColor.getBrightness() > 0.5f) Color.Black else Color.White

    val requestedImageUri by mixViewModel.currentImageUri.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    
    val tabs = remember(settingsState.cameraColorPickerEnabled, settingsState.spectralSensorEnabled) {
        val list = mutableListOf("Wheel", "Photo")
        if (settingsState.cameraColorPickerEnabled) list.add("Camera")
        if (settingsState.spectralSensorEnabled) list.add("Sensor")
        list
    }

    val uriListSaver = listSaver<List<Uri>, String>(
        save = { list -> list.map { it.toString() } },
        restore = { list -> list.map { Uri.parse(it) } }
    )

    var images by rememberSaveable(stateSaver = uriListSaver) { mutableStateOf(emptyList()) }
    var showManualBaseDialog by rememberSaveable { mutableStateOf(false) }
    var showSavePhotoDialog by remember { mutableStateOf(false) }
    var photoToSave by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(requestedImageUri) {
        requestedImageUri?.let { uri ->
            if (uri !in images) images = images + uri
            if (selectedTab != 1) selectedTab = 1
        }
    }

    if (showSavePhotoDialog && photoToSave != null) {
        SavePhotoDialog(
            mixViewModel = mixViewModel,
            imageUri = photoToSave!!,
            onDismissRequest = { 
                showSavePhotoDialog = false
                photoToSave = null
            },
            onSave = { folderName ->
                mixViewModel.savePhotoToLibrary(folderName, photoToSave!!)
                showSavePhotoDialog = false
                photoToSave = null
            }
        )
    }

    if (showManualBaseDialog) {
        ManualBaseDialog(
            totalVolume = totalVolume,
            onDismissRequest = { showManualBaseDialog = false },
            onConfirm = { name, transp, incWhite ->
                mixViewModel.manualBaseName.value = name
                mixViewModel.manualTransparency.value = transp
                mixViewModel.includeWhitePump.value = incWhite
                showManualBaseDialog = false
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (fillHeight) 16.dp else 0.dp)
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (fillHeight) Modifier.fillMaxSize() else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pill-shaped TabRow for Wheel/Photo/Camera/Sensor
            ModernPillTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    if (title == "Sensor") {
                        val selected = selectedTab == index
                        val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Tab(
                            selected = selected,
                            onClick = { selectedTab = index },
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).zIndex(2f),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🌈", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = contentColor
                                    )
                                }
                            }
                        )
                    } else {
                        ModernPillTab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = title,
                            icon = when (title) {
                                "Wheel" -> Icons.Outlined.Palette
                                "Photo" -> Icons.Outlined.Image
                                "Camera" -> Icons.Filled.CameraAlt
                                else -> null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val pickerModifierBase = if (fillHeight) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth()
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val duration = 300
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(duration)) { it } + fadeIn(animationSpec = tween(duration)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(duration)) { -it } + fadeOut(animationSpec = tween(duration)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(duration)) { -it } + fadeIn(animationSpec = tween(duration)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(duration)) { it } + fadeOut(animationSpec = tween(duration)))
                    }
                },
                label = "VisualizerContentTransition"
            ) { targetIndex ->
                val currentTab = tabs.getOrNull(targetIndex)
                val pickerModifier = pickerModifierBase.then(
                    if (!fillHeight) Modifier.height(when(targetIndex) { 0 -> 300.dp; 1 -> 400.dp; else -> 500.dp }) else Modifier
                )
                
                when (currentTab) {
                    "Wheel" -> CircularColorPicker(color, { mixViewModel.setColor(it) }, pickerModifier)
                    "Photo" -> ImageColorPicker(images, { images = it }, { mixViewModel.setColor(it) }, { uri -> photoToSave = uri; showSavePhotoDialog = true }, pickerModifier, requestedImageUri, { mixViewModel.setCurrentImage(null) })
                    "Camera" -> CameraColorPicker({ color -> mixViewModel.setColor(color); selectedTab = 0 }, { selectedTab = 0 })
                    "Sensor" -> SensorColorPicker(
                        settingsState = settingsState,
                        onTriggerScan = { settingsViewModel.triggerSpectralScan() },
                        onSetTarget = { data -> 
                            mixViewModel.setTargetColorFromSpectral(data)
                            selectedTab = 0 // Go back to Wheel/Preview
                        },
                        onExportData = { settingsViewModel.exportSpectralData() },
                        onImportData = onImportSpectralData,
                        modifier = pickerModifier
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // PHYSICS-BASED PREVIEW
            if (settingsState.showRealPaintPreview) {
                RealPaintPreviewComparison(
                    screenColor = color,
                    predictedColor = predictedColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val isEnabled = mixViewModel.isMixPossible(totalVolume)
            val needsRefill = mixViewModel.needsRefill(totalVolume)
            val isManualMode = mixViewModel.manualBaseName.value != null
            val cornerShape = RoundedCornerShape(12.dp)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .shadow(8.dp, cornerShape)
                        .combinedClickable(
                            onClick = { if (isEnabled) mixViewModel.sendMix() },
                            onLongClick = { if (isManualMode) mixViewModel.clearManualMode() else showManualBaseDialog = true }
                        ),
                    shape = cornerShape,
                    color = if (isEnabled) buttonColor else Color.Red,
                    contentColor = if (isEnabled) contentColor else Color.Black,
                    border = if (isEnabled) BorderStroke(2.dp, contentColor) else null,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEnabled) {
                                Icon(Icons.Filled.Science, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("DISPENSE COLORS", fontSize = 16.sp, fontWeight = FontWeight.Black)
                            } else {
                                // Show specific error message
                                val errorMessage = if (needsRefill) "Please Refill" else "Not Connected"
                                Text(errorMessage, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isManualMode, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    Row {
                        Spacer(modifier = Modifier.width(12.dp))
                        val transp = mixViewModel.manualTransparency.value
                        val manualVol = totalVolume * transp
                        val basePercent = (transp * 100).roundToInt()
                        
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .height(56.dp)
                                .shadow(2.dp, cornerShape)
                                .background(Color.White, cornerShape)
                                .border(1.dp, Color.LightGray, cornerShape)
                                .clip(cornerShape)
                                .combinedClickable(onClick = { showManualBaseDialog = true }, onLongClick = { showManualBaseDialog = true }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("BASE", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black), color = Color.Gray)
                            Text("$basePercent%", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Color.Black)
                            Text("${String.format("%.1f", manualVol)}ml", style = TextStyle(fontSize = 10.sp), color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            VolumeSelection(mixViewModel = mixViewModel, settingsViewModel = settingsViewModel)
        }
    }
}

@Composable
fun SensorColorPicker(
    settingsState: SettingsUiState,
    onTriggerScan: () -> Unit,
    onSetTarget: (List<Float>) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    modifier: Modifier
) {
    var showSpectralGraph by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Spectral Sensor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Status: ${settingsState.spectralConnectionStatus}",
            style = MaterialTheme.typography.bodyMedium,
            color = if(settingsState.spectralConnectionStatus.contains("Connected")) Color(0xFF4CAF50) else Color.Gray
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Import Button (for users without sensor)
        OutlinedButton(
            onClick = onImportData,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Import Scan from File")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Scan Button
        Button(
            onClick = onTriggerScan,
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
        ) {
            Text("SCAN SURFACE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (settingsState.spectralData != null) {
            val validWhite = settingsState.whiteReference != null && settingsState.whiteReference.size == 18
            
            if (validWhite) {
                 val colorInt = com.example.mejustmix.services.KubelkaMunkColorMixing.calculateRGBFromSpectral(
                     settingsState.spectralData, 
                     settingsState.whiteReference!!
                 )
                 
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Box(
                         modifier = Modifier
                             .size(80.dp)
                             .background(Color(colorInt), RoundedCornerShape(12.dp))
                             .border(2.dp, Color.Gray, RoundedCornerShape(12.dp))
                     )
                     Spacer(modifier = Modifier.height(16.dp))
                     
                     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         Button(
                             onClick = { onSetTarget(settingsState.spectralData) },
                             colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                         ) {
                             Text("Set as Target")
                         }
                         
                         OutlinedButton(onClick = onExportData) {
                             Text("Export CSV")
                         }
                     }
                     
                     Spacer(modifier = Modifier.height(12.dp))
                     
                     // Debug: Spectral Graph Toggle
                     TextButton(onClick = { showSpectralGraph = !showSpectralGraph }) {
                         Text(
                             if (showSpectralGraph) "Hide Spectral Graph ▲" else "Show Spectral Graph ▼",
                             style = MaterialTheme.typography.bodySmall
                         )
                     }
                     
                     if (showSpectralGraph) {
                         SpectralGraph(
                             data = settingsState.spectralData,
                             modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp)
                         )
                     }
                 }
            } else {
                 Text(
                     "⚠️ White Reference Missing", 
                     color = MaterialTheme.colorScheme.error,
                     fontWeight = FontWeight.Bold
                 )
                 Text(
                     "Please Calibrate White in Settings or Calibration Wizard first.",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                 )
            }
        } else {
            Text("No Data Scanned", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun SpectralGraph(data: List<Float>, modifier: Modifier = Modifier) {
    // AS7265x wavelengths in nm
    val wavelengths = listOf(410, 435, 460, 485, 510, 535, 560, 585, 610, 645, 680, 705, 730, 760, 810, 860, 900, 940)
    
    // Extract colors in Composable context
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier
        .background(surfaceColor, RoundedCornerShape(8.dp))
        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
        .padding(16.dp)
    ) {
        val maxValue = data.maxOrNull() ?: 1f
        val minValue = data.minOrNull() ?: 0f
        val range = maxValue - minValue
        
        if (range == 0f || data.size != 18) return@Canvas
        
        val width = size.width
        val height = size.height
        val stepX = width / (data.size - 1).toFloat()
        
        // Draw grid lines
        for (i in 0..4) {
            val y = height * i.toFloat() / 4f
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }
        
        // Draw spectral curve
        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index.toFloat() * stepX
            val y = height - ((value - minValue) / range * height)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3f)
        )
        
        // Draw data points
        data.forEachIndexed { index, value ->
            val x = index.toFloat() * stepX
            val y = height - ((value - minValue) / range * height)
            drawCircle(
                color = primaryColor,
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}