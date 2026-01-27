package com.example.mejustmix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.data.MotorConfig
import java.util.Locale

/**
 * Motor Settings Section for the Settings screen.
 * Allows configuration of stepper motor parameters and FluidNC settings.
 */
@Composable
fun MotorSettingsSection(
    motorConfig: MotorConfig,
    useStepBasedGCode: Boolean,
    onMotorConfigChange: (MotorConfig) -> Unit,
    onToggleStepBasedGCode: (Boolean) -> Unit,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onSyncToFluidNC: () -> Unit = {},  // Optional - not used without UART
    onReadFromFluidNC: () -> Unit = {},  // Optional - not used without UART
    fluidNCConfigSynced: Boolean = false  // Optional with default
) {
    Column {
        SettingsSectionHeader(
            icon = Icons.Default.Settings,
            title = "Motor & FluidNC Settings",
            subtitle = if (useStepBasedGCode) "Step-based control (${motorConfig.microsteps}x µsteps)" else "Distance-based control",
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
                // Step-based G-code toggle
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (useStepBasedGCode) 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Step-Based G-Code",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "Send commands in native motor steps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = useStepBasedGCode,
                                onCheckedChange = onToggleStepBasedGCode
                            )
                        }
                        
                        if (useStepBasedGCode) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "FluidNC will be configured with \$10X=1.0 (1 step = 1 unit). " +
                                        "This eliminates calibration drift and ensures exact pulse alignment.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (useStepBasedGCode) {
                    HorizontalDivider()
                    
                    // Microstepping Selection
                    Text(
                        "Microstepping",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Set this to match your stepper driver DIP switches",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MotorConfig.MICROSTEP_OPTIONS.filter { it <= 32 }.forEach { microsteps ->
                            FilterChip(
                                selected = motorConfig.microsteps == microsteps,
                                onClick = { 
                                    onMotorConfigChange(motorConfig.copy(microsteps = microsteps))
                                },
                                label = { Text("${microsteps}x") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MotorConfig.MICROSTEP_OPTIONS.filter { it > 32 }.forEach { microsteps ->
                            FilterChip(
                                selected = motorConfig.microsteps == microsteps,
                                onClick = { 
                                    onMotorConfigChange(motorConfig.copy(microsteps = microsteps))
                                },
                                label = { Text("${microsteps}x") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    // Calculated values display
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Calculated Values",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            CalculatedValueRow(
                                "Steps/Motor Rev",
                                "${motorConfig.microstepsPerMotorRev}"
                            )
                            CalculatedValueRow(
                                "Steps/Pump Rev",
                                String.format(Locale.US, "%.0f", motorConfig.stepsPerPumpRev)
                            )
                            CalculatedValueRow(
                                "Steps/Pulse",
                                String.format(Locale.US, "%.2f", motorConfig.stepsPerPulse)
                            )
                            CalculatedValueRow(
                                "Max Feed Rate",
                                "${motorConfig.maxFeedRateStepsPerMin} steps/min"
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Motor Parameters
                    Text(
                        "Motor Parameters",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step Angle
                        var stepAngleExpanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = stepAngleExpanded,
                            onExpandedChange = { stepAngleExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = "${motorConfig.stepAngleDegrees}°",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Step Angle") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stepAngleExpanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = stepAngleExpanded,
                                onDismissRequest = { stepAngleExpanded = false }
                            ) {
                                MotorConfig.STEP_ANGLE_OPTIONS.forEach { angle ->
                                    DropdownMenuItem(
                                        text = { Text("$angle° (${(360f/angle).toInt()} steps/rev)") },
                                        onClick = {
                                            onMotorConfigChange(motorConfig.copy(stepAngleDegrees = angle))
                                            stepAngleExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Gear Reduction
                        var gearText by remember { mutableStateOf(motorConfig.gearReduction.toString()) }
                        LaunchedEffect(motorConfig.gearReduction) {
                            gearText = motorConfig.gearReduction.toString()
                        }
                        OutlinedTextField(
                            value = gearText,
                            onValueChange = { 
                                gearText = it
                                it.toFloatOrNull()?.let { v -> 
                                    if (v > 0) onMotorConfigChange(motorConfig.copy(gearReduction = v))
                                }
                            },
                            label = { Text("Gear Ratio") },
                            suffix = { Text(":1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Roller Count
                        var rollerText by remember { mutableStateOf(motorConfig.rollerCount.toString()) }
                        LaunchedEffect(motorConfig.rollerCount) {
                            rollerText = motorConfig.rollerCount.toString()
                        }
                        OutlinedTextField(
                            value = rollerText,
                            onValueChange = { 
                                rollerText = it
                                it.toIntOrNull()?.let { v -> 
                                    if (v in 1..6) onMotorConfigChange(motorConfig.copy(rollerCount = v))
                                }
                            },
                            label = { Text("Rollers") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        Spacer(Modifier.weight(1f))
                    }
                    
                    HorizontalDivider()
                    
                    // Speed Limits
                    Text(
                        "Speed & Acceleration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "TinyBee limit: ${motorConfig.tinyBeeSafeLimitStepsPerSec} st/s per axis (for 5 axes @ ${motorConfig.microsteps}x)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    // Warning if exceeding TinyBee limit
                    if (motorConfig.exceedsTinyBeeLimit) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Speed clamped to ${motorConfig.tinyBeeSafeLimitStepsPerSec} st/s to protect TinyBee",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var maxSpeedText by remember { mutableStateOf(motorConfig.maxStepsPerSec.toString()) }
                        LaunchedEffect(motorConfig.maxStepsPerSec) {
                            maxSpeedText = motorConfig.maxStepsPerSec.toString()
                        }
                        OutlinedTextField(
                            value = maxSpeedText,
                            onValueChange = { 
                                maxSpeedText = it
                                it.toIntOrNull()?.let { v -> 
                                    if (v in 100..20000) onMotorConfigChange(motorConfig.copy(maxStepsPerSec = v))
                                }
                            },
                            label = { Text("Max Speed") },
                            suffix = { Text("st/s") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = motorConfig.exceedsTinyBeeLimit,
                            supportingText = { 
                                Text(
                                    if (motorConfig.exceedsTinyBeeLimit) 
                                        "Max: ${motorConfig.tinyBeeSafeLimitStepsPerSec}" 
                                    else 
                                        "100-${motorConfig.tinyBeeSafeLimitStepsPerSec}"
                                ) 
                            }
                        )
                        
                        var accelText by remember { mutableStateOf(motorConfig.accelerationStepsPerSec2.toString()) }
                        LaunchedEffect(motorConfig.accelerationStepsPerSec2) {
                            accelText = motorConfig.accelerationStepsPerSec2.toString()
                        }
                        OutlinedTextField(
                            value = accelText,
                            onValueChange = { 
                                accelText = it
                                it.toIntOrNull()?.let { v -> 
                                    if (v in 50..10000) onMotorConfigChange(motorConfig.copy(accelerationStepsPerSec2 = v))
                                }
                            },
                            label = { Text("Accel") },
                            suffix = { Text("st/s²") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            supportingText = { Text("50-10000") }
                        )
                    }
                    
                    // Apply to FluidNC button
                    Button(
                        onClick = onSyncToFluidNC,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Speed & Acceleration to FluidNC")
                    }
                    
                    Text(
                        "Sends \$11X (max rate) and \$12X (accel) commands for all axes",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    HorizontalDivider()
                    
                    // FluidNC Commands Preview (for reference)
                    Text(
                        "FluidNC Commands Reference",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "\$10X (steps/unit) must be set in config.yaml manually",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    var showCommandPreview by remember { mutableStateOf(false) }
                    
                    TextButton(
                        onClick = { showCommandPreview = !showCommandPreview },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showCommandPreview) "Hide Commands" else "Show FluidNC Commands")
                        Icon(
                            if (showCommandPreview) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            null
                        )
                    }
                    
                    AnimatedVisibility(visible = showCommandPreview) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E1E1E)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Commands sent by 'Apply' button:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                
                                // Show speed/accel commands for axis 0
                                Text(
                                    "\$110=${motorConfig.maxFeedRateStepsPerMin}  ; Max feed rate (steps/min)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFF4EC9B0)
                                )
                                Text(
                                    "\$120=${motorConfig.accelerationStepsPerSec2}  ; Acceleration (steps/s\u00b2)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFF4EC9B0)
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "(Sent for each axis: X=0, Y=1, Z=2, A=3, B=4)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = Color.DarkGray)
                                Spacer(Modifier.height(12.dp))
                                
                                Text(
                                    "For config.yaml (manual edit):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "\$100=1.0  ; 1 step = 1 unit (required for step-mode)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFFCE9178)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatedValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Dialog for advanced motor configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedMotorConfigDialog(
    motorConfig: MotorConfig,
    onConfigChange: (MotorConfig) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Advanced Motor Configuration",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // Direction Inversion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Invert Direction", fontWeight = FontWeight.Bold)
                        Text(
                            "Reverse motor rotation direction",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = motorConfig.invertDirection,
                        onCheckedChange = { 
                            onConfigChange(motorConfig.copy(invertDirection = it))
                        }
                    )
                }
                
                HorizontalDivider()
                
                // Soft Limits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Soft Limits", fontWeight = FontWeight.Bold)
                        Text(
                            "Prevent over-travel (usually OFF for pumps)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = motorConfig.softLimitsEnabled,
                        onCheckedChange = { 
                            onConfigChange(motorConfig.copy(softLimitsEnabled = it))
                        }
                    )
                }
                
                if (motorConfig.softLimitsEnabled) {
                    var maxTravelText by remember { mutableStateOf(motorConfig.maxTravelSteps.toString()) }
                    OutlinedTextField(
                        value = maxTravelText,
                        onValueChange = { 
                            maxTravelText = it
                            it.toIntOrNull()?.let { v -> 
                                if (v > 0) onConfigChange(motorConfig.copy(maxTravelSteps = v))
                            }
                        },
                        label = { Text("Max Travel") },
                        suffix = { Text("steps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                HorizontalDivider()
                
                // Jerk Limiting
                Text("Jerk Control", fontWeight = FontWeight.Bold)
                Text(
                    "Limits rate of acceleration change (0 = disabled)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                var jerkText by remember { mutableStateOf(motorConfig.jerkStepsPerSec3.toString()) }
                OutlinedTextField(
                    value = jerkText,
                    onValueChange = { 
                        jerkText = it
                        it.toIntOrNull()?.let { v -> 
                            if (v >= 0) onConfigChange(motorConfig.copy(jerkStepsPerSec3 = v))
                        }
                    },
                    label = { Text("Jerk Limit") },
                    suffix = { Text("steps/s³") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("0 = disabled, typical: 1000-5000") }
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
