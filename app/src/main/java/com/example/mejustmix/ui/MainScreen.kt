package com.example.mejustmix.ui

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mejustmix.data.ConnectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()
    
    val factory = MixViewModelFactory(context.applicationContext as Application, settingsViewModel)
    val mixViewModel: MixViewModel = viewModel(factory = factory)

    val settingsState by settingsViewModel.uiState.collectAsState()
    val historyItems by mixViewModel.mixHistory.collectAsState()
    val pumpWarning by mixViewModel.pumpDepletionWarning.collectAsState()

    // NEW: Observe the sending state
    val isSending by mixViewModel.isSending.collectAsState()
    
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showSinglePumpDialogIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    
    // Connection mode selection dialogs
    var showConnectionModeDialog by rememberSaveable { mutableStateOf(false) }
    var showBLEPermissionDialog by rememberSaveable { mutableStateOf(false) }
    
    // Spectral Data Import Launcher
    val spectralImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { settingsViewModel.importSpectralData(it) }
    }
    
    // Unified permission launcher for BLE
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        // Check if we have what we need based on Android version
        val isGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_SCAN] == true && perms[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (isGranted) {
            settingsViewModel.startBLEScan()
            Toast.makeText(context, "Bluetooth enabled. Scanning...", Toast.LENGTH_SHORT).show()
        } else {
            settingsViewModel.setConnectionMode(ConnectionType.WIFI)
            Toast.makeText(context, "Permissions denied. Using WiFi.", Toast.LENGTH_LONG).show()
        }
    }
    
    // First launch check - show connection mode selection if not configured
    LaunchedEffect(Unit) {
        if (settingsState.connectionMode == null) {
            showConnectionModeDialog = true
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Connection mode selection dialog
    if (showConnectionModeDialog) {
        ConnectionModeSelectionDialog(
            onModeSelected = { mode ->
                when (mode) {
                    ConnectionType.BLE -> {
                        // Show permission explanation before requesting
                        showBLEPermissionDialog = true
                    }
                    ConnectionType.WIFI -> {
                        // Set WiFi mode directly, no permission needed
                        settingsViewModel.setConnectionMode(ConnectionType.WIFI)
                        Toast.makeText(context, "WiFi mode selected", Toast.LENGTH_SHORT).show()
                    }
                }
                showConnectionModeDialog = false
            },
            onDismiss = {
                // If dismissed without selection, default to WiFi
                if (settingsState.connectionMode == null) {
                    settingsViewModel.setConnectionMode(ConnectionType.WIFI)
                }
                showConnectionModeDialog = false
            }
        )
    }
    
    // BLE permission explanation dialog
    if (showBLEPermissionDialog) {
        BLEPermissionExplanationDialog(
            onRequestPermission = {
                showBLEPermissionDialog = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    multiplePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    )
                } else {
                    multiplePermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    )
                }
            },
            onUseWiFiInstead = {
                // User chose WiFi instead
                settingsViewModel.setConnectionMode(ConnectionType.WIFI)
                Toast.makeText(context, "WiFi mode selected", Toast.LENGTH_SHORT).show()
                showBLEPermissionDialog = false
            },
            onDismiss = {
                // Dismissed - default to WiFi
                settingsViewModel.setConnectionMode(ConnectionType.WIFI)
                showBLEPermissionDialog = false
            }
        )
    }
    
    LaunchedEffect(pumpWarning) {
        pumpWarning?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "Dismiss"
            )
            mixViewModel.clearPumpWarning()
        }
    }
    
    // Show calibration warning for first-time users
    var showCalibrationWarning by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(settingsState.hasSeenCalibrationWarning) {
        if (!settingsState.hasSeenCalibrationWarning && settingsState.connectionMode != null) {
            // Only show after connection mode is set
            showCalibrationWarning = true
        }
    }
    
    if (showCalibrationWarning) {
        CalibrationWarningDialog(
            onDismiss = { showCalibrationWarning = false },
            onAcknowledge = {
                settingsViewModel.markCalibrationWarningSeen()
                showCalibrationWarning = false
            }
        )
    }

    // NEW: The Sending Popup
    if (isSending) {
        Dialog(onDismissRequest = { /* Prevent dismissal by clicking outside */ }) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Sending G-Code...",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text(
                "Recent Mixes", 
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), 
                style = MaterialTheme.typography.titleLarge
            )
            HistoryGridSheet(
                historyItems = historyItems,
                onSelectMix = { item ->
                    mixViewModel.restoreFromHistory(item)
                    showHistory = false 
                }
            )
        }
    }

    if (showSettingsDialog) {
        SettingsModal(
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel,
            onDismissRequest = { showSettingsDialog = false }
        )
    }

    if (showSinglePumpDialogIndex != null) {
        SinglePumpSettingsDialog(
            pumpIndex = showSinglePumpDialogIndex!!,
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel,
            onDismissRequest = { showSinglePumpDialogIndex = null }
        )
    }

    val visualizerContent = remember(mixViewModel) {
        movableContentOf<Boolean> { showDispenseControls ->
            VisualizerCard(
                mixViewModel = mixViewModel, 
                fillHeight = !showDispenseControls, // Fill height only when controls are hidden (Tablet)
                showDispenseControls = showDispenseControls,
                onImportSpectralData = { spectralImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }
            )
        }
    }

    val controlsContent = remember(mixViewModel, settingsState.showTerminal) { 
        movableContentOf { 
            ControlPanel(
                mixViewModel = mixViewModel,
                onSettingsClick = { showSettingsDialog = true },
                onPumpLongClick = { index -> showSinglePumpDialogIndex = index }
            )
            Library(mixViewModel = mixViewModel)
            
            if (settingsState.showTerminal) {
                Terminal(mixViewModel = mixViewModel)
            }
        }
    }

    val isModalActive by mixViewModel.isModalActive.collectAsState()
    
    // --- BLUR LOGIC ---
    val isAnyDialogOpen = isModalActive || showHistory || showSettingsDialog || 
                        showSinglePumpDialogIndex != null || showConnectionModeDialog || 
                        showBLEPermissionDialog || showCalibrationWarning || isSending
    
    val blurRadius by animateDpAsState(
        targetValue = if (isAnyDialogOpen) 6.dp else 0.dp,
        label = "BackgroundBlur"
    )

    Scaffold(
        modifier = Modifier.blur(blurRadius),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    actionOnNewLine = false
                )
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            val isWideScreen = maxWidth > maxHeight

            if (isWideScreen) {
                // --- LANDSCAPE / TABLET LAYOUT ---
                Column {
                    Header(
                        mixViewModel = mixViewModel,
                        onSettingsClick = { showSettingsDialog = true },
                        onHistoryClick = { showHistory = true },
                        onUndoClick = { mixViewModel.undo() },
                        onRedoClick = { mixViewModel.redo() },
                        onExportClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, mixViewModel.exportMixRecipe())
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    )
                    
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1.4f)
                                .fillMaxHeight() 
                                .padding(end = 8.dp) 
                        ) {
                            // HIDE dispense controls inside the card
                            visualizerContent(false) 
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 8.dp)
                        ) {
                            // SHOW dispense controls here at the top
                            Column(modifier = Modifier.padding(start = 16.dp, end = 24.dp, top = 24.dp)) {
                                DispenseInterface(mixViewModel, settingsViewModel)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            controlsContent()
                        }
                    }
                }
            } else {
                // --- PORTRAIT / PHONE LAYOUT ---
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Header(
                        mixViewModel = mixViewModel,
                        onSettingsClick = { showSettingsDialog = true },
                        onHistoryClick = { showHistory = true },
                        onUndoClick = { mixViewModel.undo() },
                        onRedoClick = { mixViewModel.redo() },
                        onExportClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, mixViewModel.exportMixRecipe())
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    )
                    visualizerContent(true)
                    
                    controlsContent()
                }
            }
        }
    }
}
