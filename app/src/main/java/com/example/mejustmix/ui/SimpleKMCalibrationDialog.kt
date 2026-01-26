package com.example.mejustmix.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.services.GCodeGenerator
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.services.KubelkaMunkColorMixing

/**
 * K/S Calibration dialog with dispense buttons for real-world calibration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleKMCalibrationDialog(
    pigmentName: String,
    currentKSColor: KSColor,
    onCalibrated: (KSColor) -> Unit,
    onDismissRequest: () -> Unit,
    // Optional: for dispensing test samples
    mixViewModel: MixViewModel? = null,
    settingsViewModel: SettingsViewModel? = null
) {
    // STATE: Pure Color (Step 1)
    val currentRGB = KubelkaMunkColorMixing.ksToRGB(currentKSColor)
    var hexInputPure by remember { mutableStateOf(String.format("#%06X", currentRGB and 0xFFFFFF)) }
    var previewColorPure by remember { mutableStateOf(currentRGB) }
    var pureKS by remember { mutableStateOf(currentKSColor) }
    
    // STATE: Mix Color (Step 2 - Optional)
    var useMixCalibration by remember { mutableStateOf(false) }
    var hexInputMix by remember { mutableStateOf("#FFFFFF") }
    var previewColorMix by remember { mutableStateOf(AndroidColor.WHITE) }
    var calculatedS by remember { mutableFloatStateOf(currentKSColor.s) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // STATE: Spectral Sensor (Shared between steps)
    var whiteReference by remember { mutableStateOf<List<Float>?>(null) }
    var spectralKSVals by remember { mutableStateOf<List<Float>?>(null) }
    val spectralEnabled = settingsViewModel?.uiState?.collectAsState()?.value?.spectralSensorEnabled == true

    // Re-calculate S whenever mix input changes
    LaunchedEffect(pureKS.ksR, pureKS.ksG, pureKS.ksB, hexInputMix, useMixCalibration) {
        if (useMixCalibration) {
            try {
                val mixColorInt = AndroidColor.parseColor(hexInputMix)
                previewColorMix = mixColorInt
                val mixKS = KubelkaMunkColorMixing.rgbToKS(mixColorInt)
                calculatedS = KubelkaMunkColorMixing.solveScattering(pureKS, mixKS)
            } catch (e: Exception) {
                // Ignore invalid hex while typing
            }
        } else {
            calculatedS = 1.0f
        }
    }

    // Get pump index for this pigment
    val pumpIndex = remember(pigmentName) {
        when (pigmentName.lowercase()) {
            "cyan" -> 0
            "magenta" -> 1
            "yellow" -> 2
            "black" -> 3
            "white" -> 4
            else -> -1
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Calibrate $pigmentName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    // Color chip
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(previewColorPure), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                }

                // ================= STEP 1: PURE COLOR =================
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Step 1: Pure Masstone", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Check if Spectral Sensor is Enabled
                        if (spectralEnabled && settingsViewModel != null) {
                            // --- SPECTRAL SENSOR UI ONLY ---
                            val settingsState by settingsViewModel.uiState.collectAsState()
                            
                            Text(
                                "Use the Spectral Sensor to calibrate this pigment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌈 Spectral Sensor", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(settingsState.spectralConnectionStatus, style = MaterialTheme.typography.labelSmall)
                                }
                            
                                if (settingsState.spectralData != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { whiteReference = settingsState.spectralData },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = if(whiteReference!=null) Color.Gray else MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text(if (whiteReference != null) "White Ref Set" else "Set White Ref")
                                        }
                                        
                                        Button(
                                            onClick = {
                                                val sample = settingsState.spectralData
                                                if (whiteReference != null && sample != null && whiteReference!!.size == 18 && sample.size == 18) {
                                                // 1. Calculate K/S from Spectral Data
                                                val resultKS = KubelkaMunkColorMixing.calculateKSFromSpectral(sample, whiteReference!!)
                                                spectralKSVals = com.example.mejustmix.services.SpectralMath.calculateFullKSSpectrum(sample, whiteReference!!, settingsState.darkReference)
                                                
                                                if (resultKS != null) {
                                                    // 2. Set the pure KS
                                                    pureKS = KSColor(resultKS.ksR, resultKS.ksG, resultKS.ksB, pureKS.s)
                                                    
                                                    // 3. Update Preview
                                                    val previewInt = KubelkaMunkColorMixing.ksToRGB(pureKS)
                                                    previewColorPure = previewInt
                                                    hexInputPure = String.format("#%06X", previewInt and 0xFFFFFF)
                                                    errorMessage = null
                                                }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = whiteReference != null
                                        ) {
                                            Text("Capture Data")
                                        }
                                    }
                                    if (whiteReference == null) {
                                        Text("1. Scan White Reference first", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    OutlinedButton(onClick = { settingsViewModel.triggerSpectralScan() }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Trigger Sensor Reading")
                                    }
                                }
                            }
                        } else {
                            // --- MANUAL UI ONLY ---
                            Text(
                                "Dispense pure $pigmentName onto white paper, let dry, then scan and enter the hex code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Dispense button (if viewmodels provided)
                            if (mixViewModel != null && settingsViewModel != null && pumpIndex >= 0) {
                                val settingsState by settingsViewModel.uiState.collectAsState()
                                
                                OutlinedButton(
                                    onClick = {
                                        // Dispense 5mL of pure pigment
                                        val pureTestMix = when (pumpIndex) {
                                            0 -> PaintMix(1f, 0f, 0f, 0f, 0f)
                                            1 -> PaintMix(0f, 1f, 0f, 0f, 0f)
                                            2 -> PaintMix(0f, 0f, 1f, 0f, 0f)
                                            3 -> PaintMix(0f, 0f, 0f, 1f, 0f)
                                            4 -> PaintMix(0f, 0f, 0f, 0f, 1f)
                                            else -> PaintMix(0f, 0f, 0f, 0f, 1f)
                                        }
                                        val result = GCodeGenerator.generateMixingScript(
                                            mix = pureTestMix,
                                            totalVolumeMl = 5f,
                                            retractionSteps = 15f,
                                            pumps = settingsState.pumps,
                                            flowRateMlPerSec = settingsState.flowRate.toFloatOrNull() ?: 2f
                                        )
                                        val gcode = result.first
                                        mixViewModel.sendRawGCode(gcode)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Dispense 5mL Pure $pigmentName")
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically, 
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = hexInputPure,
                                    onValueChange = { input ->
                                        hexInputPure = input
                                        try {
                                            val color = AndroidColor.parseColor(input)
                                            previewColorPure = color
                                            val tempKS = KubelkaMunkColorMixing.rgbToKS(color)
                                            pureKS = KSColor(tempKS.ksR, tempKS.ksG, tempKS.ksB, pureKS.s)
                                            errorMessage = null
                                        } catch (e: Exception) {
                                            errorMessage = "Invalid hex"
                                        }
                                    },
                                    label = { Text("Scanned Hex") },
                                    modifier = Modifier.weight(1f),
                                    isError = errorMessage != null,
                                    singleLine = true
                                )
                                ColorPreviewBox(previewColorPure)
                            }
                        }
                        
                        // K/S Table Result
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Text("Current K/S Values (Absorption)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Spacer(Modifier.height(8.dp))
                                
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Channel", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("Value", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("Controls", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                }
                                Divider(Modifier.padding(vertical = 4.dp))
                                
                                val rowStyle = MaterialTheme.typography.bodyMedium
                                
                                @Composable
                                fun KSRow(channel: String, value: Float, controls: String, color: Color) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(channel, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("%.4f".format(value), style = rowStyle, modifier = Modifier.weight(1f))
                                        Text(controls, style = rowStyle, color = Color.Gray, modifier = Modifier.weight(1.5f))
                                    }
                                }
                                
                                KSRow("Red", pureKS.ksR, "Cyan Amount", Color(0xFFEF5350))
                                KSRow("Green", pureKS.ksG, "Magenta Amount", Color(0xFF66BB6A))
                                KSRow("Blue", pureKS.ksB, "Yellow Amount", Color(0xFF42A5F5))
                            }
                        }
                    }
                }

                        // Spectral Data Grid (If Available)
                        if (spectralKSVals != null) {
                            Spacer(Modifier.height(16.dp))
                            Text("Spectral K/S Profile", style = MaterialTheme.typography.titleSmall)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    val wavelengths = if (spectralKSVals!!.size <= 10) 
                                        listOf(415, 445, 480, 515, 555, 590, 630, 680)
                                    else 
                                        listOf(410, 435, 460, 485, 510, 535, 560, 585, 610, 645, 680, 705, 730, 760, 810, 860, 900, 940)
                                    
                                    val dataPairs = spectralKSVals!!.zip(wavelengths)
                                    
                                    dataPairs.chunked(4).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            rowItems.forEach { (ks, wl) ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(4.dp), 
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text("${wl}nm", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                                                        Text("%.2f".format(ks), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                            // Fill empty space if row is incomplete
                                            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }

                // ================= STEP 2: TINT STRENGTH =================
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (useMixCalibration) 
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Step 2: Tint Strength", 
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (useMixCalibration) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Optional - improves pastel accuracy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = useMixCalibration, onCheckedChange = { useMixCalibration = it })
                        }

                        if (useMixCalibration) {
                            Text(
                                "Mix 50% $pigmentName + 50% White by volume, apply, dry, scan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Dispense 50/50 mix button
                            if (mixViewModel != null && settingsViewModel != null && pumpIndex >= 0 && pumpIndex != 4) {
                                val settingsState by settingsViewModel.uiState.collectAsState()
                                
                                OutlinedButton(
                                    onClick = {
                                        // Dispense 5mL of 50% pigment + 50% white
                                        val tintTestMix = when (pumpIndex) {
                                            0 -> PaintMix(0.5f, 0f, 0f, 0f, 0.5f)
                                            1 -> PaintMix(0f, 0.5f, 0f, 0f, 0.5f)
                                            2 -> PaintMix(0f, 0f, 0.5f, 0f, 0.5f)
                                            3 -> PaintMix(0f, 0f, 0f, 0.5f, 0.5f)
                                            else -> PaintMix(0f, 0f, 0f, 0f, 1f)
                                        }
                                        val result = GCodeGenerator.generateMixingScript(
                                            mix = tintTestMix,
                                            totalVolumeMl = 10f,
                                            retractionSteps = 15f,
                                            pumps = settingsState.pumps,
                                            flowRateMlPerSec = settingsState.flowRate.toFloatOrNull() ?: 2f
                                        )
                                        val gcode = result.first
                                        mixViewModel.sendRawGCode(gcode)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Dispense 10mL 50/50 Tint")
                                }
                            }

                            if (spectralEnabled && settingsViewModel != null) {
                                val settingsState by settingsViewModel.uiState.collectAsState()
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (whiteReference == null) {
                                       Text(
                                           "⚠️ You must capture a 'White Reference' first.\nGo back to Step 1, place sensor on white paper, and click 'Set White Ref'.",
                                           style = MaterialTheme.typography.bodySmall,
                                           color = MaterialTheme.colorScheme.error
                                       )
                                    }
                                    
                                    Button(
                                        onClick = {
                                            val sample = settingsState.spectralData
                                            if (whiteReference != null && sample != null && whiteReference!!.size == 18 && sample.size == 18) {
                                                // 1. Calculate K/S (Mix) using Helper
                                                val mixKS = KubelkaMunkColorMixing.calculateKSFromSpectral(sample, whiteReference!!)
                                                
                                                if (mixKS != null) {
                                                    // 3. Solve for S
                                                    calculatedS = KubelkaMunkColorMixing.solveScattering(pureKS, mixKS, 1.0f) // Assuming White Ref S=1 relative
                                                    
                                                    // Update preview
                                                    val previewInt = KubelkaMunkColorMixing.ksToRGB(mixKS)
                                                    previewColorMix = previewInt
                                                    hexInputMix = String.format("#%06X", previewInt and 0xFFFFFF)
                                                }
                                                

                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = whiteReference != null && settingsState.spectralConnectionStatus.startsWith("Data")
                                    ) {
                                        Text("Capture Tint Data & Solve S")
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = hexInputMix,
                                        onValueChange = { hexInputMix = it },
                                        label = { Text("Tint Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    ColorPreviewBox(previewColorMix)
                                }
                            }
                            
                            // Scattering result
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                    Text(
                                        "Scattering (S): %.2f".format(calculatedS), 
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        when {
                                            calculatedS > 2.5f -> "Very Opaque"
                                            calculatedS > 1.5f -> "Opaque"
                                            calculatedS > 0.8f -> "Semi-Opaque"
                                            calculatedS > 0.4f -> "Semi-Transparent"
                                            else -> "Transparent"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        errorMessage!!, 
                        color = MaterialTheme.colorScheme.error, 
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.weight(1f))

                // ================= ACTIONS =================
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismissRequest, 
                        modifier = Modifier.weight(1f)
                    ) { 
                        Text("Cancel") 
                    }
                    Button(
                        onClick = {
                            if (errorMessage == null) {
                                val finalKS = KSColor(
                                    ksR = pureKS.ksR,
                                    ksG = pureKS.ksG,
                                    ksB = pureKS.ksB,
                                    s = calculatedS
                                )
                                onCalibrated(finalKS)
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = errorMessage == null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun KSValueChip(channel: String, value: Float, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                channel, 
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                "%.2f".format(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun InstructionCard(steps: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
fun ColorPreviewBox(color: Int) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(Color(color), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    )
}
