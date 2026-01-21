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
 * Improved pulse calibration dialog with validation, feedback, and multi-run averaging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseCalibrationDialog(
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
    var testPulseCount by remember { mutableStateOf(10) }
    var isDispensing by remember { mutableStateOf(false) }
    var hasDispensed by remember { mutableStateOf(false) }
    var measuredMlText by remember { mutableStateOf("") }
    var calibrationRuns by remember { mutableStateOf(listOf<CalibrationRun>()) }
    var showHelp by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var showMotorConfigDialog by remember { mutableStateOf(false) }
    var customStepsPerPulse by remember { mutableStateOf<Float?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Use custom steps per pulse if set, otherwise calculate from motor specs
    val stepsPerPulse = customStepsPerPulse ?: PulseModeCalculator.calculateStepsPerPulse(
        stepAngle = 1.8f,      // Default NEMA 17
        gearReduction = 4f,    // Default 1:4
        rollerCount = 3        // Default 3 rollers
    )
    
    val calculatedMlPerPulse = remember(measuredMlText, testPulseCount) {
        val measured = measuredMlText.toFloatOrNull() ?: 0f
        if (testPulseCount > 0 && measured > 0) measured / testPulseCount else null
    }
    
    // Validation
    val isValidMeasurement = remember(measuredMlText, testPulseCount) {
        val measured = measuredMlText.toFloatOrNull()
        measured != null && measured > 0 && measured < (testPulseCount * 2.5f)
    }
    
    // Calculate average from all runs
    val averageMlPerPulse = remember(calibrationRuns) {
        if (calibrationRuns.isEmpty()) null
        else calibrationRuns.map { it.mlPerPulse }.average().toFloat()
    }
    
    val stdDev = remember(calibrationRuns) {
        if (calibrationRuns.size < 2) null
        else {
            val avg = calibrationRuns.map { it.mlPerPulse }.average()
            val variance = calibrationRuns.map { (it.mlPerPulse - avg) * (it.mlPerPulse - avg) }.average()
            kotlin.math.sqrt(variance).toFloat()
        }
    }
    
    // Save confirmation dialog
    if (showSaveConfirmation) {
        val valueToSave = averageMlPerPulse ?: calculatedMlPerPulse ?: 0.5f
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Save Calibration?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will update ${pump.name}:")
                    Text(
                        "New value: ${String.format("%.3f", valueToSave)} mL/pulse",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (calibrationRuns.size >= 2) {
                        Text("Based on ${calibrationRuns.size} runs (averaged)", 
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("What this means:", style = MaterialTheme.typography.labelMedium)
                    Text("• 1 pulse = ${String.format("%.2f", valueToSave)} mL", 
                        style = MaterialTheme.typography.bodySmall)
                    Text("• 10 mL requires ~${(10f / valueToSave).roundToInt()} pulses",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(valueToSave)
                    showSaveConfirmation = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with restart button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pump Homing", style = MaterialTheme.typography.titleLarge)
                        Text(pump.name, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentStep > 1) {
                            IconButton(onClick = {
                                currentStep = 1
                                hasPrimed = false
                                hasDispensed = false
                                isDispensing = false
                                measuredMlText = ""
                                calibrationRuns = emptyList()
                            }) {
                                Icon(Icons.Default.Refresh, "Start Over")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }
                
                // Motor specs info
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                )) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pump Homing:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Align pump rollers to home position before dispensing",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            "1.8° motor, 1:4 gear, 3 rollers = ${stepsPerPulse.toInt()} steps/pulse",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Visual stepper
                VisualStepper(currentStep)
                
                // Help section
                TextButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showHelp) "Hide Tips" else "Show Tips")
                }
                
                AnimatedVisibility(visible = showHelp) {
                    HelpCard()
                }
                
                HorizontalDivider()
                
                when (currentStep) {
                    1 -> Step1VisualHoming(
                        pump, stepsPerPulse, trackedOffsetSteps,
                        onOffsetChange = { trackedOffsetSteps = it },
                        onNext = { currentStep = 2 }
                    )
                    2 -> Step2Prime(
                        pump, trackedOffsetSteps, stepsPerPulse, hasPrimed,
                        onPrime = { onPrimeToPulseHome(); hasPrimed = true },
                        onBack = { currentStep = 1 },
                        onNext = { currentStep = 3 }
                    )
                    3 -> Step3Dispense(
                        pump, testPulseCount, isDispensing, hasDispensed,
                        onPulseCountChange = { testPulseCount = it },
                        onDispense = {
                            isDispensing = true
                            hasDispensed = false
                            onDispensePulses(testPulseCount)
                            // Estimate duration (2 seconds per pulse)
                            scope.launch {
                                delay((testPulseCount * 2000L))
                                isDispensing = false
                                hasDispensed = true
                            }
                        },
                        onBack = { currentStep = 2 },
                        onNext = { currentStep = 4 }
                    )
                    4 -> Step4Measure(
                        testPulseCount, measuredMlText, calculatedMlPerPulse,
                        isValidMeasurement, calibrationRuns, averageMlPerPulse, stdDev,
                        onMeasuredMlChange = { measuredMlText = it },
                        onAddRun = {
                            val measured = measuredMlText.toFloatOrNull()!!
                            val mlPerPulse = measured / testPulseCount
                            calibrationRuns = calibrationRuns + CalibrationRun(
                                testPulseCount, measured, mlPerPulse
                            )
                            // Reset for next run
                            hasDispensed = false
                            isDispensing = false
                            measuredMlText = ""
                            currentStep = 3
                        },
                        onBack = { currentStep = 3 },
                        onSave = { showSaveConfirmation = true }
                    )
                }
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
                Text("Next: Prime to Home")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
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
    // Always moves forward to avoid air gaps
    val currentPhase = ((trackedOffsetSteps % stepsPerPulse) + stepsPerPulse) % stepsPerPulse
    val stepsToMove = if (currentPhase == 0f) {
        // Already aligned at home - move forward one full pulse
        stepsPerPulse.toInt()
    } else {
        // Not aligned - move forward to next home position
        (stepsPerPulse - currentPhase).toInt()
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 2: Prime to Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Prime pump forward to home position.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Will move $stepsToMove steps forward (removes any air).", color = MaterialTheme.colorScheme.primary)
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
                    Text("Ready for test dispense!")
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Button(onClick = onNext, enabled = hasPrimed) {
                Text("Next: Dispense")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Button(onClick = onNext, enabled = hasDispensed && !isDispensing) {
                Text("Next: Measure")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { min ->
                        FilterChip(
                            selected = pulseMinimum == min,
                            onClick = { onPulseMinimumChange(min) },
                            label = { Text("$min") }
                        )
                    }
                }
                Text(
                    "Higher = more accurate ratios, larger minimum volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                HorizontalDivider()
                
                Text("Pump Homing:", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Align rollers to home position before dispensing",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                pumps.forEachIndexed { index, pump ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(pump.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${String.format("%.2f", pump.mlPerPulse)} mL/pulse",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        TextButton(onClick = { onCalibratePump(index) }) {
                            Icon(Icons.Default.Home, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Home")
                        }
                    }
                }
            }
        }
    }
}
