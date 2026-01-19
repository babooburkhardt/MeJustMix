package com.example.mejustmix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    mixViewModel: MixViewModel,
    settingsViewModel: SettingsViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Dialog States
    var showPrimeDialogForAxis by rememberSaveable { mutableStateOf<String?>(null) }
    var showRefillDialogForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAxisSelectorForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    
    // Calibration States
    var calibrationChooserIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showFlowCalibratorForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showKSCalibrationForColor by rememberSaveable { mutableStateOf<String?>(null) }  // Changed to K/S
    var showRetractionCalibrator by rememberSaveable { mutableStateOf(false) }
    var showKubelkaMunkSettings by rememberSaveable { mutableStateOf(false) }

    // --- FILE PICKERS ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { mixViewModel.exportBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { mixViewModel.importBackup(it) }
    }

    // --- DIALOGS LOGIC ---
    
    if (showPrimeDialogForAxis != null) {
        PrimingDialog(
            onDismissRequest = { showPrimeDialogForAxis = null },
            onConfirm = { amount ->
                showPrimeDialogForAxis?.let { axis -> mixViewModel.primePump(axis, amount) }
                showPrimeDialogForAxis = null
            }
        )
    }

    if (showRefillDialogForIndex != null) {
        val index = showRefillDialogForIndex!!
        val pump = uiState.pumps[index]
        RefillDialog(
            pumpName = pump.name,
            currentLevel = pump.currentVolumeMl,
            onDismissRequest = { showRefillDialogForIndex = null },
            onConfirm = { amount, isAdding ->
                val newVolume = if (isAdding) pump.currentVolumeMl + amount else amount
                settingsViewModel.updatePumpVolume(index, newVolume)
                showRefillDialogForIndex = null
            }
        )
    }
    
    // --- AXIS SELECTOR DIALOG ---
    if (showAxisSelectorForIndex != null) {
        val index = showAxisSelectorForIndex!!
        val pump = uiState.pumps[index]
        val axes = listOf("X", "Y", "Z", "A", "B")
        
        AlertDialog(
            onDismissRequest = { showAxisSelectorForIndex = null },
            title = { Text("Select Axis for ${pump.name}") },
            text = {
                Column {
                    axes.forEach { axis ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = pump.axis == axis,
                                onClick = {
                                    settingsViewModel.onPumpAxisChanged(index, axis)
                                    showAxisSelectorForIndex = null
                                }
                            )
                            Text(
                                text = "Axis $axis",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAxisSelectorForIndex = null }) { Text("Cancel") }
            }
        )
    }

    // 1. Calibration Chooser
    if (calibrationChooserIndex != null) {
        val index = calibrationChooserIndex!!
        val pump = uiState.pumps.getOrNull(index)
        
        if (pump != null) {
            AlertDialog(
                onDismissRequest = { calibrationChooserIndex = null },
                title = { Text("Calibrate ${pump.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What would you like to calibrate?", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { 
                                calibrationChooserIndex = null
                                showFlowCalibratorForIndex = index
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                Text("Flow Rate", fontWeight = FontWeight.Bold)
                                Text("Adjust Steps/mL accuracy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        
                        val isPigment = listOf("Cyan", "Magenta", "Yellow", "Black", "White").any { pump.name.contains(it, ignoreCase = true) }
                        if (isPigment) {
                            Button(
                                onClick = { 
                                    calibrationChooserIndex = null
                                    showKSCalibrationForColor = pump.name
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                    Text("Color Calibration", fontWeight = FontWeight.Bold)
                                    Text("Scan pigment to set K/S values", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { calibrationChooserIndex = null }) { Text("Cancel") } }
            )
        } else {
            calibrationChooserIndex = null
        }
    }

    // 2. Flow Calibrator
    if (showFlowCalibratorForIndex != null) {
        FlowCalibratorDialog(
            initialPumpIndex = showFlowCalibratorForIndex!!,
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel,
            lockedToPump = true,
            onDismissRequest = { showFlowCalibratorForIndex = null }
        )
    }

    // 3. K/S Quick Calibration (replaces old PigmentTuner for color calibration)
    if (showKSCalibrationForColor != null) {
        val pigmentName = showKSCalibrationForColor!!
        val currentKS = settingsViewModel.getPigmentKS(pigmentName)
        
        SimpleKMCalibrationDialog(
            pigmentName = pigmentName,
            currentKSColor = currentKS,
            onCalibrated = { newKSColor ->
                settingsViewModel.updatePigmentKS(pigmentName, newKSColor)
            },
            onDismissRequest = { showKSCalibrationForColor = null },
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel
        )
    }

    // 4. Retraction Calibrator
    if (showRetractionCalibrator) {
        RetractionCalibratorDialog(
            onDismissRequest = { showRetractionCalibrator = false },
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel
        )
    }
    
    // 5. Kubelka-Munk Settings
    if (showKubelkaMunkSettings) {
        KubelkaMunkSettingsDialog(
            settingsViewModel = settingsViewModel,
            onDismissRequest = { showKubelkaMunkSettings = false },
            mixViewModel = mixViewModel
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Machine Settings") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.ipAddress,
                        onValueChange = { settingsViewModel.updateIpAddress(it) },
                        label = { Text("FluidNC IP Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    Button(
                        onClick = {
                            try {
                                val url = "http://${uiState.ipAddress}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open FluidNC Web Control")
                    }
                }
                item {
                    OutlinedTextField(
                        value = uiState.flowRate,
                        onValueChange = { settingsViewModel.updateFlowRate(it) },
                        label = { Text("Flow Rate (mL/sec)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.retractionSteps,
                            onValueChange = {
                                settingsViewModel.updateRetractionSteps(it)
                                it.toFloatOrNull()?.let { steps -> mixViewModel.setRetraction(steps) }
                            },
                            label = { Text("Retraction Steps") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedButton(onClick = { showRetractionCalibrator = true }) {
                            Text("Tune")
                        }
                    }
                }
                
                item { HorizontalDivider() }
                item { Text("Pump Configuration", style = MaterialTheme.typography.titleSmall) }

                // PUMP LIST
                itemsIndexed(uiState.pumps) { index, pump ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(modifier = Modifier.size(16.dp)) { 
                                    drawCircle(color = Color(pump.colorArgb)) 
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(pump.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.weight(1f))
                                
                                TextButton(onClick = { showAxisSelectorForIndex = index }) {
                                    Text("Axis ${pump.axis}", fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { calibrationChooserIndex = index },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Calibrate")
                                }
                                OutlinedButton(onClick = { showPrimeDialogForAxis = pump.axis }) {
                                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Prime")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${String.format("%.0f", pump.currentVolumeMl)}ml", 
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(40.dp)
                                )
                                val animatedProgress by animateFloatAsState(
                                    targetValue = (pump.currentVolumeMl / pump.maxVolumeMl).coerceIn(0f, 1f),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "progress"
                                )
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(pump.colorArgb),
                                    trackColor = Color.LightGray.copy(alpha = 0.3f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { showRefillDialogForIndex = index }) { 
                                    Text("Refill") 
                                }
                            }
                        }
                    }
                }

                item { HorizontalDivider() }
                item { Text("Data Management", style = MaterialTheme.typography.titleSmall) }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val date = java.text.SimpleDateFormat("yyyyMMdd").format(java.util.Date())
                                exportLauncher.launch("MeJustMix_Backup_$date.zip")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            // Icon(Icons.Outlined.Upload, null) // Add icon if you want
                            Text("Export Backup")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            // Icon(Icons.Outlined.Download, null)
                            Text("Import Backup")
                        }
                    }
                    Text(
                        "Exports all colors, photos, and machine settings to a single file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                item { HorizontalDivider() }
                item { Text("Other", style = MaterialTheme.typography.titleSmall) }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.showRealityCheck,
                            onCheckedChange = { settingsViewModel.toggleRealityCheck(it) }
                        )
                        Text("Show 'Reality Check' Warnings")
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.bypassConnectionCheck,
                            onCheckedChange = { settingsViewModel.toggleBypassConnectionCheck(it) }
                        )
                        Text("Bypass Connection Check")
                    }
                }

                // --- FIXED SECTION: Use Correct ViewModel Names ---
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.cameraColorPickerEnabled, // Was enableCameraPicker
                            onCheckedChange = { settingsViewModel.toggleCameraColorPicker(it) } // Was toggleCameraPicker
                        )
                        Text("Enable Camera Color Picker (Beta)")
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.showTerminal,
                            onCheckedChange = { settingsViewModel.toggleShowTerminal(it) }
                        )
                        Text("Show Debug Terminal")
                    }
                }
                
                item { HorizontalDivider() }
                item { 
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Color Mixing Algorithm", style = MaterialTheme.typography.titleSmall)
                        
                        // Show current mode status
                        if (uiState.useKubelkaMunk) {
                            Text(
                                "Currently using: Spectral absorption/scattering (K-M)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Currently using: Simplified RGB-based mixing",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.useKubelkaMunk,
                                onCheckedChange = { settingsViewModel.toggleKubelkaMunk(it) }
                            )
                            Text("Use Kubelka-Munk Theory")
                        }
                        
                        if (uiState.useKubelkaMunk) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "⚠️ More accurate but may be slower on older devices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                OutlinedButton(
                                    onClick = { showKubelkaMunkSettings = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📊 Edit K/S Values")
                                }
                            }
                        }
                    }
                }
                
                item { HorizontalDivider() }
                item { Text("Display Settings", style = MaterialTheme.typography.titleSmall) }
                
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.showRealPaintPreview,
                                onCheckedChange = { settingsViewModel.toggleRealPaintPreview(it) }
                            )
                            Text("🎨 Show 'Real Paint' Preview")
                        }
                        
                        if (uiState.showRealPaintPreview) {
                            Text(
                                "Simulates how paint actually looks (darker, less saturated than screens)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 40.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePumpSettingsDialog(
    pumpIndex: Int,
    mixViewModel: MixViewModel,
    settingsViewModel: SettingsViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val pump = uiState.pumps.getOrNull(pumpIndex) ?: return

    var showPrimeDialogForAxis by rememberSaveable { mutableStateOf<String?>(null) }
    var showRefillDialogForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAxisSelector by rememberSaveable { mutableStateOf(false) }
    
    // Dialog States
    var showChooser by rememberSaveable { mutableStateOf(false) }
    var showFlowCalibrator by rememberSaveable { mutableStateOf(false) }
    var showKSCalibration by rememberSaveable { mutableStateOf(false) }  // Changed to K/S

    // --- NESTED DIALOGS ---
    
    if (showPrimeDialogForAxis != null) {
        PrimingDialog(
            onDismissRequest = { showPrimeDialogForAxis = null },
            onConfirm = { amount ->
                showPrimeDialogForAxis?.let { axis -> mixViewModel.primePump(axis, amount) }
                showPrimeDialogForAxis = null
            }
        )
    }

    if (showRefillDialogForIndex != null) {
        RefillDialog(
            pumpName = pump.name,
            currentLevel = pump.currentVolumeMl,
            onDismissRequest = { showRefillDialogForIndex = null },
            onConfirm = { amount, isAdding ->
                val newVolume = if (isAdding) pump.currentVolumeMl + amount else amount
                settingsViewModel.updatePumpVolume(pumpIndex, newVolume)
                showRefillDialogForIndex = null
            }
        )
    }
    
    // Axis Selector for Single Pump
    if (showAxisSelector) {
        val axes = listOf("X", "Y", "Z", "A", "B")
        AlertDialog(
            onDismissRequest = { showAxisSelector = false },
            title = { Text("Select Axis for ${pump.name}") },
            text = {
                Column {
                    axes.forEach { axis ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = pump.axis == axis,
                                onClick = {
                                    settingsViewModel.onPumpAxisChanged(pumpIndex, axis)
                                    showAxisSelector = false
                                }
                            )
                            Text(
                                text = "Axis $axis",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAxisSelector = false }) { Text("Cancel") } }
        )
    }

    // 1. Chooser
    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Calibrate ${pump.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What would you like to calibrate?", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = { 
                            showChooser = false
                            showFlowCalibrator = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                            Text("Flow Rate", fontWeight = FontWeight.Bold)
                            Text("Adjust Steps/mL accuracy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    val isPigment = listOf("Cyan", "Magenta", "Yellow", "Black", "White").any { pump.name.contains(it, ignoreCase = true) }
                    if (isPigment) {
                        Button(
                            onClick = { 
                                showChooser = false
                                showKSCalibration = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                Text("Color Calibration", fontWeight = FontWeight.Bold)
                                Text("Scan pigment to set K/S values", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChooser = false }) { Text("Cancel") } }
        )
    }

    // 2. Flow Calibrator
    if (showFlowCalibrator) {
        FlowCalibratorDialog(
            initialPumpIndex = pumpIndex,
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel,
            lockedToPump = true,
            onDismissRequest = { showFlowCalibrator = false }
        )
    }

    // 3. K/S Quick Calibration
    if (showKSCalibration) {
        val currentKS = settingsViewModel.getPigmentKS(pump.name)
        
        SimpleKMCalibrationDialog(
            pigmentName = pump.name,
            currentKSColor = currentKS,
            onCalibrated = { newKSColor ->
                settingsViewModel.updatePigmentKS(pump.name, newKSColor)
            },
            onDismissRequest = { showKSCalibration = false },
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("${pump.name} Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Info & Calibrate Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showAxisSelector = true }) {
                        Text("Axis: ${pump.axis}", fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedButton(onClick = { showChooser = true }) {
                        Text("Calibrate")
                    }
                }

                OutlinedButton(
                    onClick = { showPrimeDialogForAxis = pump.axis },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Prime Pump")
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.0f", pump.currentVolumeMl)}ml", 
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(40.dp)
                    )
                    val animatedProgress by animateFloatAsState(
                        targetValue = (pump.currentVolumeMl / pump.maxVolumeMl).coerceIn(0f, 1f),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "singleProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(pump.colorArgb),
                        trackColor = Color.LightGray.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showRefillDialogForIndex = pumpIndex }) { Text("Refill") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Close") }
        }
    )
}
