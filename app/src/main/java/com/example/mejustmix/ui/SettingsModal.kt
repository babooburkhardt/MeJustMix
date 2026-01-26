package com.example.mejustmix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

import com.example.mejustmix.ui.RetractionCalibratorDialog

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

    // Permissions for BLE
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
             settingsViewModel.connectSpectralSensor()
        }
    }
    
    // --- DIALOG STATES ---
    var showPrimeDialogForAxis by rememberSaveable { mutableStateOf<String?>(null) }
    var showRefillDialogForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAxisSelectorForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    
    // Calibration States
    var calibrationChooserIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showFlowCalibratorForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // Note: KSCalibration and PigmentTuner logic is also handled inside PulseSection/PumpSection/Dialogs?
    // The previous implementation had them at top level triggered by the chooser.
    var showKSCalibrationForColor by rememberSaveable { mutableStateOf<String?>(null) }
    var showCalibrationWizardForColor by rememberSaveable { mutableStateOf<String?>(null) }
    var showPigmentTunerForColor by rememberSaveable { mutableStateOf<String?>(null) }
    
    var showRetractionCalibrator by rememberSaveable { mutableStateOf(false) }
    var showKubelkaMunkSettings by rememberSaveable { mutableStateOf(false) }
    
    // Pulse Mode States
    var showPulseCalibrationForIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRollerCalibrationForIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // Expanded Section State (Shared across tabs, or we can reset on tab switch)
    var expandedSection by rememberSaveable { mutableStateOf("") }
    
    // Tab State
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Machine", "Mixing", "System")

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

    // --- DIALOGS ---

    // 1. Chooser Dialog
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
                            if (uiState.useKubelkaMunk) {
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
                                            Text("Manual Entry / View K/S", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
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

    // 2. Sub-Dialogs
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
                onPrimeToPulseHome = { settingsViewModel.primePumpToHome(index) },
                onSaveAngle = { angle, drift -> settingsViewModel.savePumpAngle(index, angle, drift) }
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
                    settingsViewModel.savePumpAngle(index, offsetSteps, null)
                    showRollerCalibrationForIndex = null
                }
            )
        } else {
            showRollerCalibrationForIndex = null
        }
    }
    
    // Axis Selector
    if (showAxisSelectorForIndex != null) {
        val index = showAxisSelectorForIndex!!
        val pump = uiState.pumps.getOrNull(index)
        if (pump != null) {
             val axes = listOf("X", "Y", "Z", "A", "B")
             AlertDialog(
                onDismissRequest = { showAxisSelectorForIndex = null },
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
                                        settingsViewModel.onPumpAxisChanged(index, axis)
                                        showAxisSelectorForIndex = null
                                    }
                                )
                                Text("Axis $axis", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAxisSelectorForIndex = null }) { Text("Cancel") } }
             )
        }
    }

    // Refill Dialog
    if (showRefillDialogForIndex != null) {
        val index = showRefillDialogForIndex!!
        val pump = uiState.pumps.getOrNull(index)
        if (pump != null) {
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
    }
    
    // Prime Dialog
    if (showPrimeDialogForAxis != null) {
         PrimingDialog(
            onDismissRequest = { showPrimeDialogForAxis = null },
            onConfirm = { amount ->
                showPrimeDialogForAxis?.let { axis -> mixViewModel.primePump(axis, amount) }
                showPrimeDialogForAxis = null
            }
        )
    }

    // --- MAIN SETTINGS UI ---
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                // Use a fixed height or weight for the scrollable area
                // Content
                // Use a fixed height or weight for the scrollable area
                Box(modifier = Modifier
                    .weight(1f, fill = false)
                    .animateContentSize(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                ) {
                     AnimatedContent(
                         targetState = selectedTabIndex,
                         transitionSpec = {
                             val exitDuration = 200
                             val enterDuration = 300
                             val enterDelay = 150 // Wait for exit/resize to mostly finish
                             
                             (fadeIn(tween(enterDuration, delayMillis = enterDelay)) + 
                                 slideInHorizontally(tween(enterDuration, delayMillis = enterDelay, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) { width -> if (targetState > initialState) width else -width })
                                 .togetherWith(
                                     fadeOut(tween(exitDuration)) + 
                                     slideOutHorizontally(tween(exitDuration, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { width -> if (targetState > initialState) -width else width }
                                 )
                         },
                         label = "TabContent"
                     ) { page ->
                        // Use Column + verticalScroll for stable intrinsic height measurement during animation
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (page) {
                                0 -> { // Machine
                                    ConnectionSection(
                                        uiState = uiState,
                                        settingsViewModel = settingsViewModel, 
                                        expanded = expandedSection == "connection",
                                        onHeaderClick = { expandedSection = if (expandedSection == "connection") "" else "connection" }
                                    )
                                    DispensingSection(
                                        uiState = uiState,
                                        mixViewModel = mixViewModel,
                                        settingsViewModel = settingsViewModel,
                                        expanded = expandedSection == "dispensing",
                                        onHeaderClick = { expandedSection = if (expandedSection == "dispensing") "" else "dispensing" },
                                        onOpenRetractionTuner = { showRetractionCalibrator = true }
                                    )
                                    PumpSection(
                                        uiState = uiState,
                                        expanded = expandedSection == "pumps",
                                        onHeaderClick = { expandedSection = if (expandedSection == "pumps") "" else "pumps" },
                                        onShowAxisSelector = { showAxisSelectorForIndex = it },
                                        onCalibrate = { calibrationChooserIndex = it },
                                        onPrime = { showPrimeDialogForAxis = it },
                                        onRefill = { showRefillDialogForIndex = it }
                                    )
                                }
                                1 -> { // Mixing
                                    SpectralSection(
                                        uiState = uiState,
                                        settingsViewModel = settingsViewModel,
                                        expanded = expandedSection == "spectral",
                                        onHeaderClick = { expandedSection = if (expandedSection == "spectral") "" else "spectral" },
                                        permissionsLauncher = permissionsLauncher,
                                        onImportSpectralData = { spectralImportLauncher.launch(arrayOf("application/json")) }
                                    )
                                    ColorMixingSection(
                                        uiState = uiState,
                                        settingsViewModel = settingsViewModel,
                                        expanded = expandedSection == "colormix",
                                        onHeaderClick = { expandedSection = if (expandedSection == "colormix") "" else "colormix" },
                                        onEditKSValues = { showKubelkaMunkSettings = true }
                                    )
                                }
                                2 -> { // System
                                    PulseSection(
                                        uiState = uiState,
                                        settingsViewModel = settingsViewModel,
                                        expanded = expandedSection == "pulse",
                                        onHeaderClick = { expandedSection = if (expandedSection == "pulse") "" else "pulse" },
                                        onCalibratePump = { showPulseCalibrationForIndex = it }
                                    )
                                    DisplaySection(
                                        uiState = uiState,
                                        settingsViewModel = settingsViewModel,
                                        expanded = expandedSection == "display",
                                        onHeaderClick = { expandedSection = if (expandedSection == "display") "" else "display" }
                                    )
                                    DataSection(
                                        expanded = expandedSection == "data",
                                        onHeaderClick = { expandedSection = if (expandedSection == "data") "" else "data" },
                                        onExport = { exportLauncher.launch("MeJustMix_Backup.zip") },
                                        onImport = { importLauncher.launch(arrayOf("application/zip")) }
                                    )
                                    DebugSection(
                                        uiState = uiState,
                                        settingsViewModel = settingsViewModel,
                                        expanded = expandedSection == "debug",
                                        onHeaderClick = { expandedSection = if (expandedSection == "debug") "" else "debug" }
                                    )
                                }
                            }
                        }
                     }

                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    )
}
