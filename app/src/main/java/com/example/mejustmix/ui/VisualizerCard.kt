package com.example.mejustmix.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mejustmix.ui.theme.getBrightness
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VisualizerCard(
    mixViewModel: MixViewModel, 
    fillHeight: Boolean = false,
    settingsViewModel: SettingsViewModel = viewModel()
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        onClick = { selectedTab = index },
                        selected = selectedTab == index,
                        icon = {
                            when (title) {
                                "Wheel" -> Icon(Icons.Outlined.Palette, null)
                                "Photo" -> Icon(Icons.Outlined.Image, null)
                                "Camera" -> Icon(Icons.Filled.CameraAlt, null)
                                "Sensor" -> Text("🌈") // Or a sensor icon
                                else -> null
                            }
                        }
                    ) { Text(title) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val pickerModifier = if (fillHeight) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth().height(when(selectedTab) { 0 -> 300.dp; 1 -> 400.dp; else -> 500.dp })
            }

            when (tabs.getOrNull(selectedTab)) {
                "Wheel" -> CircularColorPicker(color, { mixViewModel.setColor(it) }, pickerModifier)
                "Photo" -> ImageColorPicker(images, { images = it }, { mixViewModel.setColor(it) }, { uri -> photoToSave = uri; showSavePhotoDialog = true }, pickerModifier, requestedImageUri, { mixViewModel.setCurrentImage(null) })
                "Camera" -> CameraColorPicker({ color -> mixViewModel.setColor(color); selectedTab = 0 }, { selectedTab = 0 })
                "Sensor" -> SensorColorPicker(
                    settingsState = settingsState,
                    onTriggerScan = { settingsViewModel.triggerSpectralScan() },
                    onSetTarget = { data -> 
                        if (mixViewModel.setTargetColorFromSpectral(data)) {
                           selectedTab = 0 // Go back to Wheel/Preview
                        }
                    },
                    modifier = pickerModifier
                )
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
                                Text("Please Refill", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
    modifier: Modifier
) {
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
        
        Spacer(modifier = Modifier.height(24.dp))
        
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
                     Button(
                         onClick = { onSetTarget(settingsState.spectralData) },
                         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                     ) {
                         Text("Set as Target Color")
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