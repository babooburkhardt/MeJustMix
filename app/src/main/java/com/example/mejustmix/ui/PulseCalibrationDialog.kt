package com.example.mejustmix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.utils.PulseModeCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Data class for calibration run.
 */
data class CalibrationRun(
    val pulseCount: Int,
    val measuredMl: Float,
    val mlPerPulse: Float
)

/**
 * Pulse calibration dialog - visual scroll wheel for position input.
 * Single step: just set the roller position, no priming required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseCalibrationDialog(
    pump: PumpConfig,
    pumpIndex: Int,
    onDismiss: () -> Unit,
    onSave: (mlPerPulse: Float) -> Unit,
    onDispensePulses: (pulseCount: Int) -> Unit,
    onPrimeToPulseHome: () -> Unit,
    onSaveAngle: ((angle: Float, drift: Float?) -> Unit)? = null
) {
    // Initialize from last known angle if available, otherwise use offset
    // This ensures the GUI matches the actual known roller position
    var trackedOffsetSteps by remember { 
        mutableStateOf(
            run {
                val stepsPerPulse = PulseModeCalculator.calculateStepsPerPulse(1.8f, 4f, 3)
                (pump.lastKnownAngle / 360f) * stepsPerPulse
            }
        ) 
    }
    
    val stepsPerPulse = PulseModeCalculator.calculateStepsPerPulse(
        stepAngle = 1.8f,
        gearReduction = 4f,
        rollerCount = 3
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pump Homing", style = MaterialTheme.typography.titleLarge)
                        Text(pump.name, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // Single step visual homing with save
                Step1VisualHomingSingleStep(
                    pump = pump,
                    stepsPerPulse = stepsPerPulse,
                    trackedOffsetSteps = trackedOffsetSteps,
                    onOffsetChange = { trackedOffsetSteps = it },
                    onSave = {
                        // Save the offset and close
                        onSaveAngle?.invoke(
                            com.example.mejustmix.utils.PulseGeometryUtils.ANGLE_DISENGAGED - 
                            (trackedOffsetSteps / stepsPerPulse * 120f),  // Convert steps to angle
                            null
                        )
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * DEPRECATED - Old 2-step dialog kept for reference.
 * Use AngleHomingDialog instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseCalibrationDialogOld(
    pump: PumpConfig,
    pumpIndex: Int,
    onDismiss: () -> Unit,
    onSave: (mlPerPulse: Float) -> Unit,
    onDispensePulses: (pulseCount: Int) -> Unit,
    onPrimeToPulseHome: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    var trackedOffsetSteps by remember { mutableStateOf(pump.pulseHomeOffset) }
    var hasPrimed by remember { mutableStateOf(false) }
    
    val stepsPerPulse = PulseModeCalculator.calculateStepsPerPulse(
        stepAngle = 1.8f,
        gearReduction = 4f,
        rollerCount = 3
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pump Homing", style = MaterialTheme.typography.titleLarge)
                        Text(pump.name, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                // Simple single-step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Home,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Set Position",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                HorizontalDivider()
                
                Step1VisualHoming(
                    pump, stepsPerPulse, trackedOffsetSteps,
                    onOffsetChange = { trackedOffsetSteps = it },
                    onNext = { onDismiss() }  // Just close, no priming
                )
            }
        }
    }
}

@Composable
fun VisualStepper(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val steps = listOf("Home", "Prime", "Dispense", "Measure")
        
        steps.forEachIndexed { index, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            color = when {
                                index + 1 < currentStep -> MaterialTheme.colorScheme.primary
                                index + 1 == currentStep -> MaterialTheme.colorScheme.secondary
                                else -> Color.LightGray.copy(alpha = 0.3f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index + 1 < currentStep) {
                        Icon(
                            Icons.Default.Check, 
                            null, 
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (index + 1 == currentStep) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index + 1 == currentStep) FontWeight.Bold else FontWeight.Normal,
                    color = if (index + 1 == currentStep) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
            
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(2.dp)
                        .background(
                            if (index + 1 < currentStep) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                Color.LightGray.copy(alpha = 0.3f)
                        )
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
fun HelpCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("💡 Best Practices:", fontWeight = FontWeight.Bold)
            Text("• Use a graduated syringe for accuracy", style = MaterialTheme.typography.bodySmall)
            Text("• Dispense into a container, then measure", style = MaterialTheme.typography.bodySmall)
            Text("• Run calibration 2-3 times and average", style = MaterialTheme.typography.bodySmall)
            Text("• Recalibrate if you change tubing", style = MaterialTheme.typography.bodySmall)
            Text("• Higher pulse counts = more accurate", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Step1VisualHoming(
    pump: PumpConfig,
    stepsPerPulse: Float,
    trackedOffsetSteps: Float,
    onOffsetChange: (Float) -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 1: Visual Homing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Align scroll wheel with where the rollers currently are.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("(The bottom is where the tubes enter and exit the pump)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        CompactPulseHomeWheel(
            pumpColor = Color(pump.colorArgb),
            stepsPerPulse = stepsPerPulse,
            currentOffsetSteps = trackedOffsetSteps,
            onOffsetChange = onOffsetChange,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onNext) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done")
            }
        }
    }
}

/**
 * Single-step visual homing - saves directly without priming.
 */
@Composable
fun Step1VisualHomingSingleStep(
    pump: PumpConfig,
    stepsPerPulse: Float,
    trackedOffsetSteps: Float,
    onOffsetChange: (Float) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Set Roller Position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Align scroll wheel with where the rollers currently are.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("(The bottom is where the tubes enter and exit the pump)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        CompactPulseHomeWheel(
            pumpColor = Color(pump.colorArgb),
            stepsPerPulse = stepsPerPulse,
            currentOffsetSteps = trackedOffsetSteps,
            onOffsetChange = onOffsetChange,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Reminder tip
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "💡 If pulses return, come back and update this position!",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onSave) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save Position")
            }
        }
    }
}

@Composable
fun Step2Prime(
    pump: PumpConfig,
    trackedOffsetSteps: Float,
    stepsPerPulse: Float,
    hasPrimed: Boolean,
    onPrime: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    // Calculate forward movement to next home position
    val currentPhase = ((trackedOffsetSteps % stepsPerPulse) + stepsPerPulse) % stepsPerPulse
    val stepsToMove = if (currentPhase == 0f) {
        stepsPerPulse.toInt()
    } else {
        (stepsPerPulse - currentPhase).toInt()
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 2: Prime to Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Prime pump forward to home position.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Will move $stepsToMove steps forward.", color = MaterialTheme.colorScheme.primary)
            }
        }
        
        // Warning card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "⚠️ Warning: Priming will dispense paint. Have a container ready!",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Button(
            onClick = onPrime,
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasPrimed,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasPrimed) MaterialTheme.colorScheme.primary else Color(pump.colorArgb)
            )
        ) {
            Icon(if (hasPrimed) Icons.Default.CheckCircle else Icons.Default.Home, null)
            Spacer(Modifier.width(8.dp))
            Text(if (hasPrimed) "✓ Primed" else "Prime Pump ($stepsToMove steps)")
        }
        
        if (hasPrimed) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Pump is homed and ready!")
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Button(onClick = onNext, enabled = hasPrimed) {
                Text("Done")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun Step3Dispense(
    pump: PumpConfig,
    testPulseCount: Int,
    isDispensing: Boolean,
    hasDispensed: Boolean,
    onPulseCountChange: (Int) -> Unit,
    onDispense: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 3: Test Dispense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Dispense pulses into measuring container.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("More pulses = larger volume = more accurate.", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Text("Pulse count:", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20, 50).forEach { count ->
                FilterChip(
                    selected = testPulseCount == count,
                    onClick = { onPulseCountChange(count) },
                    label = { Text("$count") },
                    enabled = !isDispensing
                )
            }
        }
        
        Button(
            onClick = onDispense,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDispensing && !hasDispensed,
            colors = ButtonDefaults.buttonColors(containerColor = Color(pump.colorArgb))
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Dispense $testPulseCount Pulses")
        }
        
        // Dispensing progress
        if (isDispensing) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Dispensing $testPulseCount pulses...", fontWeight = FontWeight.Medium)
                        Text(
                            "Please wait (~${testPulseCount * 2} seconds)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        if (hasDispensed) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Dispensed! Now measure the output.")
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, enabled = !isDispensing) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Button(onClick = onNext, enabled = hasDispensed && !isDispensing) {
                Text("Next: Measure")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun Step4Measure(
    testPulseCount: Int,
    measuredMlText: String,
    calculatedMlPerPulse: Float?,
    isValidMeasurement: Boolean,
    calibrationRuns: List<CalibrationRun>,
    averageMlPerPulse: Float?,
    stdDev: Float?,
    onMeasuredMlChange: (String) -> Unit,
    onAddRun: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 4: Measure & Calculate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Measure dispensed volume with syringe.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Expected range: 0.5-${testPulseCount * 2.5f} mL", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        OutlinedTextField(
            value = measuredMlText,
            onValueChange = onMeasuredMlChange,
            label = { Text("Measured volume") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            suffix = { Text("mL") },
            isError = measuredMlText.isNotBlank() && !isValidMeasurement,
            supportingText = {
                if (measuredMlText.isNotBlank() && !isValidMeasurement) {
                    Text(
                        "⚠️ Value seems unrealistic. Expected 0.1-${testPulseCount * 2.5f} mL",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
        
        // Current measurement result
        if (calculatedMlPerPulse != null && isValidMeasurement) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This Run", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${String.format("%.3f", calculatedMlPerPulse)} mL/pulse",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$measuredMlText mL ÷ $testPulseCount pulses",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
        
        // Previous runs
        if (calibrationRuns.isNotEmpty()) {
            HorizontalDivider()
            Text("Previous Runs:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            
            calibrationRuns.forEachIndexed { index, run ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Run ${index + 1}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${String.format("%.3f", run.mlPerPulse)} mL/pulse",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "(${run.measuredMl} mL ÷ ${run.pulseCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        // Average if multiple runs
        if (calibrationRuns.size >= 2 && averageMlPerPulse != null) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Average (Recommended)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${String.format("%.3f", averageMlPerPulse)} mL/pulse",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (stdDev != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "±${String.format("%.4f", stdDev)} (${calibrationRuns.size} runs)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isValidMeasurement && calculatedMlPerPulse != null && calibrationRuns.size < 5) {
                OutlinedButton(
                    onClick = onAddRun,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Run")
                }
            }
            
            Button(
                onClick = onSave,
                enabled = (calibrationRuns.isNotEmpty() && averageMlPerPulse != null) || 
                         (isValidMeasurement && calculatedMlPerPulse != null),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (calibrationRuns.size >= 2) "Save Average" else "Save")
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
        }
    }
}

/**
 * Settings card for pulse mode.
 */
@Composable
fun PulseModeSettingsCard(
    usePulseMode: Boolean,
    pulseMinimum: Int,
    onTogglePulseMode: (Boolean) -> Unit,
    onPulseMinimumChange: (Int) -> Unit,
    onCalibratePump: (Int) -> Unit,
    onSnapAllToHome: () -> Unit,
    pumps: List<PumpConfig>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (usePulseMode) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Pulse Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Dispense in whole pulses only",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "⚠️ Only needed for tubes\n>3mm inner diameter.\nIf unsure, leave as is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = usePulseMode,
                    onCheckedChange = onTogglePulseMode
                )
            }
            
            if (usePulseMode) {
                HorizontalDivider()
                
                Text("Minimum pulses per component:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(0, 1, 2, 3).forEach { min ->
                        FilterChip(
                            selected = pulseMinimum == min,
                            onClick = { onPulseMinimumChange(min) },
                            label = { Text("$min") },
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp)
                        )
                    }
                }
                Text(
                    "Higher = more accurate ratios, larger minimum volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onSnapAllToHome,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Home, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Snap All to Nearest Pulse Boundary")
                }
            }
        }
    }
}
