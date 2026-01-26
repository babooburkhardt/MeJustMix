package com.example.mejustmix.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
    }
}

// 1. CONNECTION SETTINGS
@Composable
fun ConnectionSection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "🔧",
            title = "Connection Settings",
            subtitle = "FluidNC IP and web control",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Connection Mode: Currently only WiFi is supported for FluidNC
                // If we want to support BLE for the printer later, we can add it back.
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                         Text("FluidNC Connection (WiFi)", style = MaterialTheme.typography.labelMedium)
                         Spacer(Modifier.height(8.dp))
                         OutlinedTextField(
                            value = uiState.ipAddress,
                            onValueChange = { settingsViewModel.updateIpAddress(it) },
                            label = { Text("IP Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://${uiState.ipAddress}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.ipAddress.isNotEmpty()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open FluidNC WebDash")
                }
            }
        }
    }
}

// 2. DISPENSING SETTINGS
@Composable
fun DispensingSection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    mixViewModel: MixViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onOpenRetractionTuner: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "🎯",
            title = "Dispensing Settings",
            subtitle = "Flow rate and retraction",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
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
                    OutlinedButton(onClick = onOpenRetractionTuner) {
                        Text("Tune")
                    }
                }
            }
        }
    }
}

// 3. PUMP CONFIGURATION
@Composable
fun PumpSection(
    uiState: SettingsUiState,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onShowAxisSelector: (Int) -> Unit,
    onCalibrate: (Int) -> Unit,
    onPrime: (String) -> Unit,
    onRefill: (Int) -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "💧",
            title = "Pump Configuration",
            subtitle = "${uiState.pumps.size} pumps configured",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.pumps.forEachIndexed { index, pump ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(modifier = Modifier.size(16.dp)) {
                                    drawCircle(color = Color(pump.colorArgb))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    pump.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))

                                TextButton(onClick = { onShowAxisSelector(index) }) {
                                    Text("Axis ${pump.axis}", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onCalibrate(index) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Calibrate")
                                }
                                OutlinedButton(onClick = { onPrime(pump.axis) }) {
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(pump.colorArgb),
                                    trackColor = Color.LightGray.copy(alpha = 0.3f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { onRefill(index) }) {
                                    Text("Refill")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 1.5 SPECTRAL SENSOR
@Composable
fun SpectralSection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    permissionsLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onImportSpectralData: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "🌈",
            title = "Spectral Sensor",
            subtitle = uiState.spectralConnectionStatus,
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Status: ${uiState.spectralConnectionStatus}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // Sensor Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sensor Model", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.spectralSensorType == "AS7341",
                            onClick = { settingsViewModel.setSpectralSensorType("AS7341") },
                            label = { Text("AS7341 (10-ch)") }
                        )
                        FilterChip(
                            selected = uiState.spectralSensorType == "AS7265x",
                            onClick = { settingsViewModel.setSpectralSensorType("AS7265x") },
                            label = { Text("AS7265x (18-ch)") }
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            permissionsLauncher.launch(arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ))
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
                    onClick = { settingsViewModel.triggerSpectralWarmup() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.spectralConnectionStatus == "Ready" || uiState.spectralConnectionStatus.startsWith("Data")
                ) {
                    Text("Warmup Sensor (LED)")
                }

                Button(
                    onClick = { settingsViewModel.triggerSpectralAutoGain() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.spectralConnectionStatus == "Ready" || uiState.spectralConnectionStatus.startsWith("Data")
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Optimize Signal Gain")
                }

                Button(
                    onClick = { settingsViewModel.triggerSpectralScan() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.spectralConnectionStatus == "Ready" || uiState.spectralConnectionStatus.startsWith("Data")
                ) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Trigger Reading")
                }
                
                OutlinedButton(
                    onClick = onImportSpectralData,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Spectral Data (JSON)")
                }
                
                uiState.spectralData?.let { data ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Sensor Data (${data.size} Channels / ${uiState.spectralSensorType})", fontWeight = FontWeight.Bold)
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

// 4. COLOR MIXING ALGORITHM
@Composable
fun ColorMixingSection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onEditKSValues: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "🎨",
            title = "Color Mixing Algorithm",
            subtitle = if (uiState.useKubelkaMunk) "Kubelka-Munk (Spectral)" else "RGB-based (Simple)",
            expanded = expanded,
            onClick = onHeaderClick
        )
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
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
                            onClick = onEditKSValues,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📊 Edit K/S Values")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text("Manual Pigment Weights", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Fine-tune strength without re-calibrating. Higher = Stronger pigment.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )
                        
                        val strengths = uiState.pigmentStrengths
                        
                        StrengthSlider("Cyan", strengths.cyan, Color(0xFF00BCD4), settingsViewModel)
                        StrengthSlider("Magenta", strengths.magenta, Color(0xFFE91E63), settingsViewModel)
                        StrengthSlider("Yellow", strengths.yellow, Color(0xFFFFC107), settingsViewModel)
                        StrengthSlider("Black", strengths.black, Color.Black, settingsViewModel)
                        StrengthSlider("White", strengths.white, Color.Gray, settingsViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun StrengthSlider(name: String, value: Float, color: Color, settingsViewModel: SettingsViewModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, fontWeight = FontWeight.Bold, color = color)
            Text(String.format("%.2f x", value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = { settingsViewModel.updatePigmentStrength(name, it) },
            valueRange = 0.5f..3.0f,
            steps = 24, // 0.1 steps
            modifier = Modifier.height(30.dp)
        )
    }
}

// 5. PULSE MODE
@Composable
fun PulseSection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onCalibratePump: (Int) -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "🔄",
            title = "Pulse Mode",
            subtitle = if (uiState.usePulseMode) "Enabled" else "Disabled",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // We need to manage some state here for the sub-dialogs trigger from within the card
                // But the caller passed onCalibratePump.
                // The card triggers onOpenTuningTool... which we might need to handle or pass up.
                // Let's pass a local handler or assume the View Model handles it?
                // The original code had local state showPumpSelector etc.
                // I'll keep the local state inside this section for the things that are purely internal to this section.
                
                var showPumpSelector by remember { mutableStateOf(false) }
                var showTuningDialogForPumpIndex by remember { mutableStateOf<Int?>(null) }
                
                PulseModeSettingsCard(
                    usePulseMode = uiState.usePulseMode,
                    pulseMinimum = uiState.pulseMinimum,
                    onTogglePulseMode = { settingsViewModel.togglePulseMode(it) },
                    onPulseMinimumChange = { settingsViewModel.updatePulseMinimum(it) },
                    onCalibratePump = onCalibratePump,
                    onSnapAllToHome = { settingsViewModel.snapAllPumpsToHome() },
                    pulseSmoothingStrength = uiState.pulseSmoothingStrength,
                    onPulseSmoothingChange = { settingsViewModel.updatePulseSmoothingStrength(it) },
                    onOpenTuningTool = {
                        if (uiState.pumps.size > 1) {
                            showPumpSelector = true
                        } else {
                            showTuningDialogForPumpIndex = 0
                        }
                    },
                    pumps = uiState.pumps
                )
                
                if (showPumpSelector) {
                     AlertDialog(
                        onDismissRequest = { showPumpSelector = false },
                        title = { Text("Select Pump to Tune") },
                        text = {
                            Column {
                                uiState.pumps.forEachIndexed { index, pump ->
                                    TextButton(
                                        onClick = {
                                            showPumpSelector = false
                                            showTuningDialogForPumpIndex = index
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(pump.colorArgb))
                                    ) {
                                        Text(pump.name, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                if (showTuningDialogForPumpIndex != null) {
                    val index = showTuningDialogForPumpIndex!!
                    val pump = uiState.pumps.getOrNull(index)
                        if (pump != null) {
                            PulseTuningDialog(
                                pump = pump,
                                isTuning = uiState.isTuning,
                                tuningPhaseOffset = uiState.tuningPhaseOffset,
                                tuningStrength = uiState.pulseSmoothingStrength,
                                tuningPulseWidth = uiState.tuningPulseWidthDegrees,
                                pillowLengthMm = uiState.pillowLengthMm,
                                currentHomeOffset = pump.pulseHomeOffset,
                                onToggleTuning = { // We need the index. 'index' variable is available from previous block? 
                                    // Wait, 'index' was defined in the block above: val index = showTuningDialogForPumpIndex!!
                                    // So we can capture it.
                                    settingsViewModel.toggleTuning(index, it) 
                                },
                                onOffsetChange = { settingsViewModel.updateTuningOffset(it) },
                                onStrengthChange = { settingsViewModel.updatePulseSmoothingStrength(it) },
                                onWidthChange = { settingsViewModel.updateTuningWidth(it) },
                                onSave = {
                                    settingsViewModel.saveTuningOffset(index)
                                    showTuningDialogForPumpIndex = null
                                },
                                onDismiss = { 
                                    settingsViewModel.stopTuning()
                                    showTuningDialogForPumpIndex = null 
                                }
                            )
                        }
                }

                if (uiState.usePulseMode) {
                    Text(
                        "💡 Tip: Before dispensing, visually check that each pump roller is aligned to its home position (just past the compression point).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
            
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
                                // Local state for Pillow Length
                                var pillowText by remember { mutableStateOf(String.format("%.1f", uiState.pillowLengthMm)) }
                                if (pillowText.toFloatOrNull() != uiState.pillowLengthMm) {
                                    pillowText = String.format("%.1f", uiState.pillowLengthMm)
                                }

                                OutlinedTextField(
                                    value = pillowText,
                                    onValueChange = { 
                                        pillowText = it
                                        it.toFloatOrNull()?.let { v -> settingsViewModel.setPillowLengthMm(v) }
                                    },
                                    label = { Text("Pillow (mm)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                // Local state for Tube ID
                                var tubeIdText by remember { mutableStateOf(String.format("%.1f", uiState.tubeInnerDiameterMm)) }
                                if (tubeIdText.toFloatOrNull() != uiState.tubeInnerDiameterMm) {
                                    tubeIdText = String.format("%.1f", uiState.tubeInnerDiameterMm)
                                }

                                OutlinedTextField(
                                    value = tubeIdText,
                                    onValueChange = { 
                                        tubeIdText = it
                                        it.toFloatOrNull()?.let { v -> settingsViewModel.setTubeInnerDiameterMm(v) }
                                    },
                                    label = { Text("Tube ID (mm)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                            
                            // Local state to prevent "fighting" the formatter while typing
                            var fullDiameterText by remember { mutableStateOf(String.format("%.1f", uiState.fullDiameterSectionMm)) }
                            // Update local text only if external value changes significantly
                            if (fullDiameterText.toFloatOrNull() != uiState.fullDiameterSectionMm) {
                                fullDiameterText = String.format("%.1f", uiState.fullDiameterSectionMm)
                            }

                            OutlinedTextField(
                                value = fullDiameterText,
                                onValueChange = { 
                                    fullDiameterText = it
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
                            
                            // Geometry Wizard Button
                            var showPumpSelectorForWizard by remember { mutableStateOf(false) }
                            var showGeometryWizard by remember { mutableStateOf(false) }
                            var wizardPumpIndex by remember { mutableStateOf(0) }
                            
                            OutlinedButton(
                                onClick = { showPumpSelectorForWizard = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📐 Launch Geometry Wizard")
                            }
                            
                            if (showPumpSelectorForWizard) {
                                AlertDialog(
                                    onDismissRequest = { showPumpSelectorForWizard = false },
                                    title = { Text("Select Pump to Calibrate") },
                                    text = {
                                        Column {
                                            Text(
                                                "Choose which pump to use for geometry calibration:",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )
                                            uiState.pumps.forEachIndexed { index, pump ->
                                                OutlinedButton(
                                                    onClick = {
                                                        wizardPumpIndex = index
                                                        showPumpSelectorForWizard = false
                                                        showGeometryWizard = true
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Text(pump.name)
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {},
                                    dismissButton = {
                                        TextButton(onClick = { showPumpSelectorForWizard = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                            
                            if (showGeometryWizard) {
                                PulseGeometryWizard(
                                    pump = uiState.pumps.getOrNull(wizardPumpIndex) ?: uiState.pumps.first(),
                                    pumpIndex = wizardPumpIndex,
                                    pillowLengthMm = uiState.pillowLengthMm,
                                    onJog = { steps ->
                                        settingsViewModel.jogPumpWithBacklash(wizardPumpIndex, steps)
                                    },
                                    onSave = { taperStartSteps, taperLengthMm, fullDiameterMm ->
                                        settingsViewModel.saveGeometryFromWizard(
                                            wizardPumpIndex,
                                            taperStartSteps,
                                            taperLengthMm,
                                            fullDiameterMm
                                        )
                                    },
                                    onDismiss = { showGeometryWizard = false }
                                )
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
}

// 6. DISPLAY SETTINGS
@Composable
fun DisplaySection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "📊",
            title = "Display Settings",
            subtitle = "Preview options and warnings",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
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
}

// 7. DATA MANAGEMENT
@Composable
fun DataSection(
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "💾",
            title = "Data Management",
            subtitle = "Export and import backups",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export Backup")
                    }

                    OutlinedButton(
                        onClick = onImport,
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
}

// 8. DEBUG OPTIONS
@Composable
fun DebugSection(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit
) {
    Column {
        SettingsSectionHeader(
            icon = "🐛",
            title = "Debug Options",
            subtitle = "Developer and experimental features",
            expanded = expanded,
            onClick = onHeaderClick
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
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

                // --- Rotation Calibration ---
                var showRotationCalibration by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showRotationCalibration = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔄 Calibrate Motor Rotation (Steps/Rev)")
                }

                if (showRotationCalibration) {
                    PulseRotationCalibrationDialog(
                        pumps = uiState.pumps,
                        onJog = { pumpIndex, steps ->
                            settingsViewModel.jogPumpWithBacklash(pumpIndex, steps)
                        },
                        onSave = { pumpIndex, stepsPerPulse ->
                            settingsViewModel.updateStepsPerPulse(pumpIndex, stepsPerPulse)
                        },
                        onDismiss = { showRotationCalibration = false }
                    )
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

// 9. SINGLE PUMP SETTINGS DIALOG (Moved from SettingsModal.kt)
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
