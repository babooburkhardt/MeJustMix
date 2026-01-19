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
                                    val gcode = GCodeGenerator.generateMixingScript(
                                        mix = pureTestMix,
                                        totalVolumeMl = 5f,
                                        retractionSteps = 15f,
                                        pumps = settingsState.pumps,
                                        flowRateMlPerSec = settingsState.flowRate.toFloatOrNull() ?: 2f
                                    )
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
                        
                        // K/S result
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            KSValueChip("R", pureKS.ksR, Color(0xFFEF5350))
                            KSValueChip("G", pureKS.ksG, Color(0xFF66BB6A))
                            KSValueChip("B", pureKS.ksB, Color(0xFF42A5F5))
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
                                        val gcode = GCodeGenerator.generateMixingScript(
                                            mix = tintTestMix,
                                            totalVolumeMl = 5f,
                                            retractionSteps = 15f,
                                            pumps = settingsState.pumps,
                                            flowRateMlPerSec = settingsState.flowRate.toFloatOrNull() ?: 2f
                                        )
                                        mixViewModel.sendRawGCode(gcode)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Dispense 5mL 50/50 Tint")
                                }
                            }

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
