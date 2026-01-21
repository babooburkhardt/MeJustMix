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
import androidx.lifecycle.viewmodel.compose.viewModel

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
    
    val snackbarHostState = remember { SnackbarHostState() }
    
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
            VisualizerCard(mixViewModel = mixViewModel, fillHeight = fillHeight)
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
