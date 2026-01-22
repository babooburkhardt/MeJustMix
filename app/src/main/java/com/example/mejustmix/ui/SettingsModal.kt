package com.example.mejustmix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import com.example.mejustmix.data.ConnectionType


/**
 * Expandable section header for settings
 */
@Composable
fun SettingsSectionHeader(
    icon: String,
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                icon, 
                fontSize = 24.sp,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    mixViewModel: MixViewModel,
    settingsViewModel: SettingsViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val activeMachine by settingsViewModel.machineManager.activeMachine.collectAsState()
    val context = LocalContext.current
    
    // Expanded sections state - connection expanded by default
    var expandedSection by rememberSaveable { mutableStateOf("connection") }
    
    // --- BLE PERMISSION LAUNCHER ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Proceed if granted
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
             settingsViewModel.connectSpectralSensor()
        }
    }
    
    // Dialog States
    var showPrimeDialogForAxis by rememberSaveable { mutableStateOf<String?>(null) }
    var showRefillDialogForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAxisSelectorForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    
    // Calibration States
    var calibrationChooserIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showFlowCalibratorForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showKSCalibrationForColor by rememberSaveable { mutableStateOf<String?>(null) }
    var showCalibrationWizardForColor by rememberSaveable { mutableStateOf<String?>(null) }
    var showPigmentTunerForColor by rememberSaveable { mutableStateOf<String?>(null) }
    var showRetractionCalibrator by rememberSaveable { mutableStateOf(false) }
    var showKubelkaMunkSettings by rememberSaveable { mutableStateOf(false) }
    
    // Pulse Mode States
    var showPulseCalibrationForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRollerCalibrationForIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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
    
    val spectralImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { settingsViewModel.importSpectralData(it) }
    }

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
                        // Show color calibration for pigments - use Pigment Strength or K/S based on KM setting
                        if (isPigment) {
                            if (uiState.useKubelkaMunk) {
                                // KM Enabled: K/S Calibration
                                Button(
                                    onClick = { 
                                        calibrationChooserIndex = null
                                        if (uiState.spectralSensorEnabled) {
                                            showCalibrationWizardForColor = pump.name
                                        } else {
                                            showKSCalibrationForColor = pump.name
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                        Text("Color Calibration", fontWeight = FontWeight.Bold)
                                        if (uiState.spectralSensorEnabled) {
                                            Text("Open Guided Wizard ✨", style = MaterialTheme.typography.labelSmall)
                                        } else {
                                            Text("Manual Entry / View", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                // KM Disabled: Pigment Strength
                                Button(
                                    onClick = { 
                                        calibrationChooserIndex = null
                                        showPigmentTunerForColor = pump.name
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                        Text("Color Calibration", fontWeight = FontWeight.Bold)
                                        Text("Visual matching calibration", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        
                        // Roller Position Calibration (Pulse Mode)
                        if (uiState.usePulseMode) {
                            Button(
                                onClick = { 
                                    calibrationChooserIndex = null
                                    showRollerCalibrationForIndex = index
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                    Text("Roller Position", fontWeight = FontWeight.Bold)
                                    Text("Set home position for pulse mode", style = MaterialTheme.typography.labelSmall)
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

    if (showFlowCalibratorForIndex != null) {
        FlowCalibratorDialog(
            initialPumpIndex = showFlowCalibratorForIndex!!,
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel,
            lockedToPump = true,
            onDismissRequest = { showFlowCalibratorForIndex = null }
        )
    }

    if (showCalibrationWizardForColor != null) {
        CalibrationWizardDialog(
            pigmentName = showCalibrationWizardForColor!!,
            onDismissRequest = { showCalibrationWizardForColor = null },
            onCalibrated = { newKS ->
                settingsViewModel.updatePigmentKS(showCalibrationWizardForColor!!, newKS)
            },
            settingsViewModel = settingsViewModel
        )
    }

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
    
    if (showPigmentTunerForColor != null) {
        PigmentTunerDialog(
            onDismissRequest = { showPigmentTunerForColor = null },
            mixViewModel = mixViewModel,
            lockedColor = showPigmentTunerForColor,
            settingsViewModel = settingsViewModel
        )
    }

    if (showRetractionCalibrator) {
        RetractionCalibratorDialog(
            onDismissRequest = { showRetractionCalibrator = false },
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel
        )
    }
    
    if (showKubelkaMunkSettings) {
        KubelkaMunkSettingsDialog(
            settingsViewModel = settingsViewModel,
            onDismissRequest = { showKubelkaMunkSettings = false },
            mixViewModel = mixViewModel
        )
    }
    
    if (showPulseCalibrationForIndex != null) {
        val index = showPulseCalibrationForIndex!!
        val pump = uiState.pumps.getOrNull(index)
        
        if (pump != null) {
            PulseCalibrationDialog(
                pump = pump,
                pumpIndex = index,
                onDismiss = { showPulseCalibrationForIndex = null },
                onSave = { mlPerPulse ->
                    settingsViewModel.updatePumpMlPerPulse(index, mlPerPulse)
                    showPulseCalibrationForIndex = null
                },
                onDispensePulses = { pulseCount ->
                    settingsViewModel.dispensePulsesForCalibration(index, pulseCount)
                },
                onPrimeToPulseHome = {
                    settingsViewModel.primePumpToHome(index)
                },
                onSaveAngle = { angle, drift ->
                    settingsViewModel.savePumpAngle(index, angle, drift)
                }
            )
        } else {
            showPulseCalibrationForIndex = null
        }
    }
    
    if (showRollerCalibrationForIndex != null) {
        val index = showRollerCalibrationForIndex!!
        val pump = uiState.pumps.getOrNull(index)
        
        if (pump != null) {
            RollerPositionDialog(
                pump = pump,
                onDismiss = { showRollerCalibrationForIndex = null },
                onSavePosition = { offsetSteps ->
                    // Convert steps to angle for storage (optional, or store steps directly)
                    settingsViewModel.savePumpAngle(index, offsetSteps, null)
                    showRollerCalibrationForIndex = null
                }
            )
        } else {
            showRollerCalibrationForIndex = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Machine Settings") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. CONNECTION SETTINGS
                item {
                    SettingsSectionHeader(
                        icon = "🔧",
                        title = "Connection Settings",
                        subtitle = "FluidNC IP and web control",
                        expanded = expandedSection == "connection",
                        onClick = { expandedSection = if (expandedSection == "connection") "" else "connection" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "connection",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Connection Mode Toggle
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Connection Mode",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // BLE Button
                                        FilterChip(
                                            selected = (uiState.connectionMode ?: ConnectionType.WIFI) == ConnectionType.BLE,
                                            onClick = { settingsViewModel.setConnectionMode(ConnectionType.BLE) },
                                            label = { Text("Bluetooth") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Bluetooth,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        // WiFi Button
                                        FilterChip(
                                            selected = (uiState.connectionMode ?: ConnectionType.WIFI) == ConnectionType.WIFI,
                                            onClick = { settingsViewModel.setConnectionMode(ConnectionType.WIFI) },
                                            label = { Text("WiFi") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    // Show current connection info
                                    Text(
                                        text = when (uiState.connectionMode ?: ConnectionType.WIFI) {
                                            ConnectionType.BLE -> "Using Bluetooth connection"
                                            ConnectionType.WIFI -> "Using WiFi connection"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Show IP field only in WiFi mode
                            if (uiState.connectionMode == ConnectionType.WIFI || uiState.connectionMode == null) {
                                OutlinedTextField(
                                    value = uiState.ipAddress,
                                    onValueChange = { settingsViewModel.updateIpAddress(it) },
                                    label = { Text("FluidNC IP Address") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
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
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.bypassConnectionCheck,
                                    onCheckedChange = { settingsViewModel.toggleBypassConnectionCheck(it) }
                                )
                                Column {
                                    Text("Bypass Connection Check")
                                    Text(
                                        "⚠️ Disables all commands to machine",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 1.5 SPECTRAL SENSOR
                if (uiState.spectralSensorEnabled) {
                    item {
                        SettingsSectionHeader(
                            icon = "🌈",
                            title = "Spectral Sensor",
                            subtitle = uiState.spectralConnectionStatus,
                            expanded = expandedSection == "spectral",
                            onClick = { expandedSection = if (expandedSection == "spectral") "" else "spectral" }
                        )
                    }
                    
                    item {
                        AnimatedVisibility(
                            visible = expandedSection == "spectral",
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Status: ${uiState.spectralConnectionStatus}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            // Request permissions first
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.BLUETOOTH_SCAN,
                                                    android.Manifest.permission.BLUETOOTH_CONNECT,
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Connect")
                                    }
                                    
                                    OutlinedButton(
                                        onClick = { settingsViewModel.disconnectSpectralSensor() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Disconnect")
                                    }
                                }
                                
                                Button(
                                    onClick = { settingsViewModel.triggerSpectralScan() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = uiState.spectralConnectionStatus == "Ready" || uiState.spectralConnectionStatus.startsWith("Data")
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Trigger Reading")
                                }
                                
                                uiState.spectralData?.let { data ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Sensor Data (18 Channels)", fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(8.dp))
                                            // Simple visualization of the list
                                            Text(
                                                data.chunked(6).joinToString("\n") { chunk ->
                                                    chunk.joinToString(", ") { "%.0f".format(it) }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. DISPENSING SETTINGS
                item {
                    SettingsSectionHeader(
                        icon = "🎯",
                        title = "Dispensing Settings",
                        subtitle = "Flow rate and retraction",
                        expanded = expandedSection == "dispensing",
                        onClick = { expandedSection = if (expandedSection == "dispensing") "" else "dispensing" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "dispensing",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.flowRate,
                                onValueChange = { settingsViewModel.updateFlowRate(it) },
                                label = { Text("Flow Rate (mL/sec)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            
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
                    }
                }

                // 3. PUMP CONFIGURATION
                item {  
                    SettingsSectionHeader(
                        icon = "💧",
                        title = "Pump Configuration",
                        subtitle = "${uiState.pumps.size} pumps configured",
                        expanded = expandedSection == "pumps",
                        onClick = { expandedSection = if (expandedSection == "pumps") "" else "pumps" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "pumps",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.pumps.forEachIndexed { index, pump ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
                        }
                    }
                }

                // 4. COLOR MIXING ALGORITHM
                item {
                    SettingsSectionHeader(
                        icon = "🎨",
                        title = "Color Mixing Algorithm",
                        subtitle = if (uiState.useKubelkaMunk) "Kubelka-Munk (Spectral)" else "RGB-based (Simple)",
                        expanded = expandedSection == "colormix",
                        onClick = { expandedSection = if (expandedSection == "colormix") "" else "colormix" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "colormix",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.useKubelkaMunk,
                                    onCheckedChange = { settingsViewModel.toggleKubelkaMunk(it) }
                                )
                                Column {
                                    Text("Use Kubelka-Munk Theory")
                                    Text(
                                        "More accurate spectral absorption/scattering model",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            if (uiState.useKubelkaMunk) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "⚠️ More accurate but may be slower on older devices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    
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
                }

                // 5. PULSE MODE
                item {
                    SettingsSectionHeader(
                        icon = "🔄",
                        title = "Pulse Mode",
                        subtitle = if (uiState.usePulseMode) "Enabled" else "Disabled",
                        expanded = expandedSection == "pulse",
                        onClick = { expandedSection = if (expandedSection == "pulse") "" else "pulse" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "pulse",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PulseModeSettingsCard(
                                usePulseMode = uiState.usePulseMode,
                                pulseMinimum = uiState.pulseMinimum,
                                onTogglePulseMode = { settingsViewModel.togglePulseMode(it) },
                                onPulseMinimumChange = { settingsViewModel.updatePulseMinimum(it) },
                                onCalibratePump = { index -> showRollerCalibrationForIndex = index },
                                onSnapAllToHome = { settingsViewModel.snapAllPumpsToHome() },
                                pumps = uiState.pumps
                            )
                            
                            if (uiState.usePulseMode) {
                                Text(
                                    "💡 Tip: Before dispensing, visually check that each pump roller is aligned to its home position (just past the compression point).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            

                            
                            // --- Pulse Compensation Geometry ---
                            if (uiState.usePulseMode) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                
                                Text(
                                    "⚡ Pump Geometry (for velocity compensation)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Motor speed is automatically modulated based on pillow geometry for smoother flow.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("📐 Pump Geometry", fontWeight = FontWeight.SemiBold)
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = String.format("%.1f", uiState.pillowLengthMm),
                                                onValueChange = { 
                                                    it.toFloatOrNull()?.let { v -> settingsViewModel.setPillowLengthMm(v) }
                                                },
                                                label = { Text("Pillow (mm)") },
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = String.format("%.1f", uiState.tubeInnerDiameterMm),
                                                onValueChange = { 
                                                    it.toFloatOrNull()?.let { v -> settingsViewModel.setTubeInnerDiameterMm(v) }
                                                },
                                                label = { Text("Tube ID (mm)") },
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true
                                            )
                                        }
                                        
                                        OutlinedTextField(
                                            value = String.format("%.1f", uiState.fullDiameterSectionMm),
                                            onValueChange = { 
                                                it.toFloatOrNull()?.let { v -> settingsViewModel.setFullDiameterSectionMm(v) }
                                            },
                                            label = { Text("Full Diameter Section (mm)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        
                                        // Calculated profile summary
                                        val taperLengthMm = (uiState.pillowLengthMm - uiState.fullDiameterSectionMm) / 2f
                                        val taperPercent = ((taperLengthMm / uiState.pillowLengthMm) * 100).toInt()
                                        
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("📊 Calculated Profile:", style = MaterialTheme.typography.labelMedium)
                                                Text(
                                                    "Taper Zone: $taperPercent% (${String.format("%.1f", taperLengthMm)}mm each side)",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    "Speed Boost: 2.0x in taper zones",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        
                                        // --- Dynamic Acceleration Control ---
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "🚀 Dynamic Acceleration",
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    "Adjust FluidNC acceleration for taper zones",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            Switch(
                                                checked = uiState.useDynamicAcceleration,
                                                onCheckedChange = { settingsViewModel.setUseDynamicAcceleration(it) }
                                            )
                                        }
                                        
                                        if (uiState.useDynamicAcceleration) {
                                            OutlinedTextField(
                                                value = String.format("%.0f", uiState.taperAcceleration),
                                                onValueChange = { 
                                                    it.toFloatOrNull()?.let { v -> settingsViewModel.setTaperAcceleration(v) }
                                                },
                                                label = { Text("Taper Accel (mm/s²)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                supportingText = {
                                                    Text(
                                                        "Range: 50-2000 mm/s². Higher = faster transitions.",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            )
                                            
                                            OutlinedTextField(
                                                value = String.format("%.0f", uiState.nominalAcceleration),
                                                onValueChange = { 
                                                    it.toFloatOrNull()?.let { v -> settingsViewModel.setNominalAcceleration(v) }
                                                },
                                                label = { Text("Nominal Accel (mm/s²)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                supportingText = {
                                                    Text(
                                                        "Range: 100-3000 mm/s². Full-flow zone acceleration.",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. DISPLAY SETTINGS
                item {
                    SettingsSectionHeader(
                        icon = "📊",
                        title = "Display Settings",
                        subtitle = "Preview options and warnings",
                        expanded = expandedSection == "display",
                        onClick = { expandedSection = if (expandedSection == "display") "" else "display" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "display",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.showRealityCheck,
                                    onCheckedChange = { settingsViewModel.toggleRealityCheck(it) }
                                )
                                Text("Show 'Reality Check' Warnings")
                            }
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = uiState.showRealPaintPreview,
                                        onCheckedChange = { settingsViewModel.toggleRealPaintPreview(it) }
                                    )
                                    Text("🎨 Show 'Real Paint' Preview")
                                }
                                
                                Text(
                                    "⚠️ This feature is currently inaccurate and under development",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 40.dp)
                                )
                                
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
                }

                // 7. DATA MANAGEMENT
                item {
                    SettingsSectionHeader(
                        icon = "💾",
                        title = "Data Management",
                        subtitle = "Export and import backups",
                        expanded = expandedSection == "data",
                        onClick = { expandedSection = if (expandedSection == "data") "" else "data" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "data",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
                                    Text("Export Backup")
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import Backup")
                                }
                            }
                            Text(
                                "Exports all colors, photos, and machine settings to a single file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // 8. DEBUG OPTIONS
                item {
                    SettingsSectionHeader(
                        icon = "🐛",
                        title = "Debug Options",
                        subtitle = "Developer and experimental features",
                        expanded = expandedSection == "debug",
                        onClick = { expandedSection = if (expandedSection == "debug") "" else "debug" }
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = expandedSection == "debug",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.cameraColorPickerEnabled,
                                    onCheckedChange = { settingsViewModel.toggleCameraColorPicker(it) }
                                )
                                Text("Enable Camera Color Picker (Beta)")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.showTerminal,
                                    onCheckedChange = { settingsViewModel.toggleShowTerminal(it) }
                                )
                                Text("Show Debug Terminal")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = uiState.spectralSensorEnabled,
                                    onCheckedChange = { settingsViewModel.toggleSpectralSensor(it) }
                                )
                                Column {
                                    Text("Enable Spectral Sensor")
                                    Text(
                                        "Unlock AS7265x Triad integration",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // --- FluidNC Speed Limits ---
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            Text(
                                "⚙️ FluidNC Speed Limits",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Configure max speed for \"you can go faster\" mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            
                            OutlinedTextField(
                                value = String.format("%.0f", uiState.maxFeedRate),
                                onValueChange = { 
                                    it.toFloatOrNull()?.let { v -> settingsViewModel.setMaxFeedRate(v) }
                                },
                                label = { Text("Max Feed Rate (mm/min)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                supportingText = {
                                    Text(
                                        "Range: 1000-20000 mm/min. Sets FluidNC \$11X limit.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
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

// SinglePumpSettingsDialog remains unchanged
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
    
    var showChooser by rememberSaveable { mutableStateOf(false) }
    var showFlowCalibrator by rememberSaveable { mutableStateOf(false) }
    var showKSCalibration by rememberSaveable { mutableStateOf(false) }
    var showPigmentTuner by rememberSaveable { mutableStateOf(false) }
    var showRollerCalibration by rememberSaveable { mutableStateOf(false) }

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
                    // Show color calibration for pigments - use Pigment Strength or K/S based on KM setting
                    if (isPigment) {
                        if (uiState.useKubelkaMunk) {
                            // KM Enabled: K/S Calibration
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
                        } else {
                            // KM Disabled: Pigment Strength
                            Button(
                                onClick = { 
                                    showChooser = false
                                    showPigmentTuner = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                    Text("Color Calibration", fontWeight = FontWeight.Bold)
                                    Text("Visual matching calibration", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    
                    // Roller Position Calibration (Pulse Mode)
                    if (uiState.usePulseMode) {
                        Button(
                            onClick = { 
                                showChooser = false
                                showRollerCalibration = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                Text("Roller Position", fontWeight = FontWeight.Bold)
                                Text("Set home position for pulse mode", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChooser = false }) { Text("Cancel") } }
        )
    }
    
    if (showFlowCalibrator) {
        FlowCalibratorDialog(
            initialPumpIndex = pumpIndex,
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel,
            lockedToPump = true,
            onDismissRequest = { showFlowCalibrator = false }
        )
    }

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
    
    if (showPigmentTuner) {
        PigmentTunerDialog(
            onDismissRequest = { showPigmentTuner = false },
            mixViewModel = mixViewModel,
            lockedColor = pump.name,
            settingsViewModel = settingsViewModel
        )
    }
    
    if (showRollerCalibration) {
        RollerPositionDialog(
            pump = pump,
            onDismiss = { showRollerCalibration = false },
            onSavePosition = { offsetSteps ->
                settingsViewModel.savePumpAngle(pumpIndex, offsetSteps, null)
                showRollerCalibration = false
            }
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
