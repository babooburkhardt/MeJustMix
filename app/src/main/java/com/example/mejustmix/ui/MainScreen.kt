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
    
    // Location permission launcher for BLE
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - start BLE scan
            settingsViewModel.startBLEScan()
            Toast.makeText(context, "Bluetooth enabled. Scanning for devices...", Toast.LENGTH_SHORT).show()
        } else {
            // Permission denied - fall back to WiFi
            settingsViewModel.setConnectionMode(ConnectionType.WIFI)
            Toast.makeText(
                context,
                "Location permission denied. Using WiFi mode.",
                Toast.LENGTH_LONG
            ).show()
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
                // User agreed - request location permission
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                showBLEPermissionDialog = false
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
        movableContentOf<Boolean> { fillHeight ->
            VisualizerCard(
                mixViewModel = mixViewModel, 
                fillHeight = fillHeight,
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

    Scaffold(
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
                            visualizerContent(true)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 8.dp)
                        ) {
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
                    visualizerContent(false)
                    
                    controlsContent()
                }
            }
        }
    }
}
