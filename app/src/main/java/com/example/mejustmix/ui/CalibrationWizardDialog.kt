package com.example.mejustmix.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.services.SpectralMath

@Composable
fun CalibrationWizardDialog(
    pigmentName: String,
    onDismissRequest: () -> Unit,
    onCalibrated: (KSColor) -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    var currentStep by remember { mutableStateOf(0) }
    
    // Calibration Data
    var whiteReference by remember { mutableStateOf<List<Float>?>(null) }
    var pigmentSpectrum by remember { mutableStateOf<List<Float>?>(null) }
    var calculatedKS by remember { mutableStateOf<KSColor?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Auto-advance logic: Watch for sensor data updates
    LaunchedEffect(uiState.spectralData) {
        val data = uiState.spectralData
        if (data != null) {
            when (currentStep) {
                1 -> { // Measuring White
                    if (whiteReference == null) { // Only capture once per button press logic ideally, but here we capture latest
                        whiteReference = data
                        // Auto save white ref to global settings too as a convenience
                        settingsViewModel.setWhiteReference(data)
                    }
                }
                2 -> { // Measuring Pigment
                    if (pigmentSpectrum == null) {
                         pigmentSpectrum = data
                         // Auto calculate
                         whiteReference?.let { white ->
                             val ks = SpectralMath.calculateKSFromSpectral(data, white)
                             if (ks != null) {
                                 calculatedKS = ks
                                 errorMessage = null
                             } else {
                                 errorMessage = "Error calculating K/S: Invalid Data"
                             }
                         }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, "Close")
                    }
                    Text(
                        "Calibration Wizard: $pigmentName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / 4f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )

                // Content
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(targetState = currentStep) { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            when (step) {
                                0 -> StepIntro(pigmentName)
                                1 -> StepMeasureWhite(
                                    isConnected = uiState.spectralConnectionStatus.startsWith("Ready") || uiState.spectralConnectionStatus.startsWith("Data"),
                                    onScan = { 
                                        whiteReference = null // Reset for new read
                                        settingsViewModel.triggerSpectralScan() 
                                    },
                                    capturedData = whiteReference
                                )
                                2 -> StepMeasurePigment(
                                    pigmentName = pigmentName,
                                    isConnected = uiState.spectralConnectionStatus.startsWith("Ready") || uiState.spectralConnectionStatus.startsWith("Data"),
                                    onScan = { 
                                        pigmentSpectrum = null // Reset
                                        settingsViewModel.triggerSpectralScan() 
                                    },
                                    capturedData = pigmentSpectrum
                                )
                                3 -> StepResults(
                                    pigmentName = pigmentName,
                                    ksColor = calculatedKS,
                                    error = errorMessage
                                )
                            }
                        }
                    }
                }

                // Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(onClick = { currentStep-- }) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Default.ArrowBack, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp)) // Placeholder
                    }

                    Button(
                        onClick = {
                            if (currentStep < 3) {
                                currentStep++
                            } else {
                                // Finish
                                calculatedKS?.let { onCalibrated(it) }
                                onDismissRequest()
                            }
                        },
                        enabled = when (currentStep) {
                            1 -> whiteReference != null
                            2 -> pigmentSpectrum != null
                            3 -> calculatedKS != null
                            else -> true
                        }
                    ) {
                        Text(if (currentStep == 3) "Save & Close" else "Next")
                        Spacer(Modifier.width(8.dp))
                         @Suppress("DEPRECATION")
                        Icon(if (currentStep == 3) Icons.Default.Check else Icons.Default.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
fun StepIntro(pigmentName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.Science, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Welcome to Guided Calibration", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This wizard will help you create an accurate Kubelka-Munk profile for $pigmentName.",
            textAlign = TextAlign.Center
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("You will need:", fontWeight = FontWeight.Bold)
                Text("• The Spectral Sensor (Connected)")
                Text("• A White Calibration Standard (Tile)")
                Text("• A dry, opaque sample of Pure $pigmentName")
            }
        }
    }
}

@Composable
fun StepMeasureWhite(isConnected: Boolean, onScan: () -> Unit, capturedData: List<Float>?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Step 1: White Reference", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Place the sensor flat on the White Calibration Tile.",
            textAlign = TextAlign.Center
        )
        
        if (capturedData != null) {
            Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(48.dp))
            Text("Reference Captured!", fontWeight = FontWeight.Bold)
        } else {
            Button(onClick = onScan, enabled = isConnected, modifier = Modifier.size(120.dp), shape = MaterialTheme.shapes.extraLarge) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Icon(Icons.Default.Science, null)
                     Text(if (isConnected) "SCAN\nWHITE" else "Connect\nSensor")
                 }
            }
        }
    }
}

@Composable
fun StepMeasurePigment(pigmentName: String, isConnected: Boolean, onScan: () -> Unit, capturedData: List<Float>?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Step 2: Measure Pigment", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Place the sensor flat on the Pure $pigmentName sample.",
            textAlign = TextAlign.Center
        )
        
        if (capturedData != null) {
            Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(48.dp))
            Text("Pigment Data Captured!", fontWeight = FontWeight.Bold)
        } else {
            Button(onClick = onScan, enabled = isConnected, modifier = Modifier.size(120.dp), shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Icon(Icons.Default.Science, null)
                     Text(if (isConnected) "SCAN\n$pigmentName" else "Connect\nSensor")
                 }
            }
        }
    }
}

@Composable
fun StepResults(pigmentName: String, ksColor: KSColor?, error: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Step 3: Results", style = MaterialTheme.typography.headlineSmall)
        
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        } else if (ksColor != null) {
            Text("Calibration Successful!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Calculated K/S Values:")
                    Text("Red (Cyan Abs): %.4f".format(ksColor.ksR))
                    Text("Green (Magenta Abs): %.4f".format(ksColor.ksG))
                    Text("Blue (Yellow Abs): %.4f".format(ksColor.ksB))
                }
            }
            Text("Click 'Save & Close' to apply this profile.", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        } else {
            CircularProgressIndicator()
            Text("Calculating...")
        }
    }
}
