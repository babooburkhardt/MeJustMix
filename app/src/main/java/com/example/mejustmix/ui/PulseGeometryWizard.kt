package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.util.Locale

/**
 * Pulse Geometry Wizard - Visual calibration for taper zones.
 * Modified to work primarily in DEGREES for universal compatibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseGeometryWizard(
    pump: PumpConfig,
    pumpIndex: Int,
    stepsPerPulse: Float,
    pillowLengthMm: Float, // Still passed for secondary display, but not critical for logic
    onJog: (steps: Float) -> Unit,
    onSave: (taperStartSteps: Float, taperEndSteps: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var currentPositionSteps by remember { mutableFloatStateOf(0f) }
    var taperStartSteps by remember { mutableStateOf<Float?>(null) }
    var taperEndSteps by remember { mutableStateOf<Float?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Convert steps to degrees (Primary Unit)
    fun stepsToDegrees(steps: Float) = (steps / stepsPerPulse) * 360f
    
    // Convert steps to mm (Secondary Reference)
    fun stepsToMm(steps: Float) = (steps / stepsPerPulse) * pillowLengthMm
    
    // Jog with backlash compensation
    fun jogWithBacklash(steps: Float) {
        scope.launch {
            // Note: Backlash logic is handled by the ViewModel/Repository, 
            // but we track position locally for the UI.
            // Wait, previous file had logic for overshoot here?
            // "jogWithBacklash" passed in from parent actually calls the repository which does the overshoot.
            // So we just call onJog. But wait, checking usage in SettingsSections, it passes { settingsViewModel.jogPumpWithBacklash(...) }
            // So we don't need to reimplement overshoot here, just call onJog.
            onJog(steps)
            currentPositionSteps += steps
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "📐 Geometry Wizard (Angular)",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${pump.name} • ${String.format(Locale.US, "%.0f", stepsPerPulse)} steps/rev",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // Progress indicator
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / 4f },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "Step ${currentStep + 1} of 4",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Step content
                when (currentStep) {
                    0 -> WelcomeStep(
                        onNext = { currentStep = 1 }
                    )
                    1 -> FindTaperStartStep(
                        currentPositionSteps = currentPositionSteps,
                        stepsPerPulse = stepsPerPulse,
                        onJog = ::jogWithBacklash,
                        onMark = { 
                            taperStartSteps = currentPositionSteps
                            currentStep = 2
                        },
                        onBack = { currentStep = 0 }
                    )
                    2 -> FindTaperEndStep(
                        currentPositionSteps = currentPositionSteps,
                        taperStartSteps = taperStartSteps ?: 0f,
                        stepsPerPulse = stepsPerPulse,
                        onJog = ::jogWithBacklash,
                        onMark = {
                            taperEndSteps = currentPositionSteps
                            currentStep = 3
                        },
                        onBack = { currentStep = 1 }
                    )
                    3 -> ReviewStep(
                        taperStartSteps = taperStartSteps ?: 0f,
                        taperEndSteps = taperEndSteps ?: 0f,
                        stepsPerPulse = stepsPerPulse,
                        pillowLengthMm = pillowLengthMm,
                        onSave = {
                            onSave(taperStartSteps ?: 0f, taperEndSteps ?: 0f)
                            onDismiss()
                        },
                        onBack = { currentStep = 2 }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Welcome to Angular Calibration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "This wizard defines the roller geometry using rotational angles. This allows for universal calibration independent of tube length.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📚 What You'll Do:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("1️⃣ Find Angle A: Roller STARTS to lift off", style = MaterialTheme.typography.bodySmall)
                Text("2️⃣ Find Angle B: Roller COMPLETELY releases", style = MaterialTheme.typography.bodySmall)
                Text("3️⃣ Save the calculated Taper Angle & Phase Offset", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Calibration")
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FindTaperStartStep(
    currentPositionSteps: Float,
    stepsPerPulse: Float,
    onJog: (Float) -> Unit,
    onMark: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Step 1: Find Taper Start",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "🎯 Goal:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Jog the pump until the roller BEGINS to lift off the flat section. This is the start of the pressure release.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        JogControls(
            currentPositionSteps = currentPositionSteps,
            stepsPerPulse = stepsPerPulse,
            onJog = onJog
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("Back", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onMark,
                modifier = Modifier.weight(1f)
            ) {
                Text("Mark Angle A")
                Icon(Icons.Default.Check, null, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FindTaperEndStep(
    currentPositionSteps: Float,
    taperStartSteps: Float,
    stepsPerPulse: Float,
    onJog: (Float) -> Unit,
    onMark: () -> Unit,
    onBack: () -> Unit
) {
    val degreesFromStart = ((currentPositionSteps - taperStartSteps) / stepsPerPulse) * 360f
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Step 2: Find Taper End",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "🎯 Goal:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Continue jogging until the roller COMPLETELY releases the tube. The tube should be fully round.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Angle from Point A:", style = MaterialTheme.typography.bodySmall)
                Text(
                    String.format(Locale.US, "%.1f°", degreesFromStart),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        JogControls(
            currentPositionSteps = currentPositionSteps,
            stepsPerPulse = stepsPerPulse,
            onJog = onJog
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("Back", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onMark,
                modifier = Modifier.weight(1f)
            ) {
                Text("Mark Angle B")
                Icon(Icons.Default.Check, null, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ReviewStep(
    taperStartSteps: Float,
    taperEndSteps: Float,
    stepsPerPulse: Float,
    pillowLengthMm: Float,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val taperLengthSteps = abs(taperEndSteps - taperStartSteps)
    val taperAngleDegrees = (taperLengthSteps / stepsPerPulse) * 360f
    
    // Derived values for reference
    val taperFraction = taperAngleDegrees / 360f
    val taperLengthMm = taperFraction * pillowLengthMm
    val fullDiameterMm = pillowLengthMm * (1f - (2f * taperFraction))
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Step 3: Review & Save",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "📊 Calculated Geometry",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                HorizontalDivider()
                
                MeasurementRow("Taper Angle", String.format(Locale.US, "%.1f°", taperAngleDegrees))

                // Optional: Show derived MM values for sanity check if pillow length is roughly correct
                Text("Reference Values (based on ${String.format(Locale.US, "%.0f", pillowLengthMm)}mm tube):", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top=8.dp))
                MeasurementRow("Est. Taper Length", String.format(Locale.US, "%.1f mm", taperLengthMm))
                MeasurementRow("Est. Full Diameter", String.format(Locale.US, "%.1f mm", fullDiameterMm.coerceAtLeast(0f)))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("Back", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, null)
                Text("Save Geometry", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun JogControls(
    currentPositionSteps: Float,
    stepsPerPulse: Float,
    onJog: (Float) -> Unit
) {
    val currentDegrees = (currentPositionSteps / stepsPerPulse) * 360f
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Position display
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Current Position",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    String.format(Locale.US, "%.1f°", currentDegrees % 360f), // Modulo 360 for display niceness
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    String.format(Locale.US, "%.1f steps", currentPositionSteps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Coarse controls
        Text(
            "Coarse (±10 steps)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onJog(-10f) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("−10", modifier = Modifier.padding(start = 4.dp))
            }
            FilledTonalButton(
                onClick = { onJog(10f) },
                modifier = Modifier.weight(1f)
            ) {
                Text("+10", modifier = Modifier.padding(end = 4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
        
        // Fine controls
        Text(
            "Fine (±1 step)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onJog(-1f) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("−1", modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = { onJog(1f) },
                modifier = Modifier.weight(1f)
            ) {
                Text("+1", modifier = Modifier.padding(end = 4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
        
        // Micro controls
        Text(
            "Micro (±0.1 step)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onJog(-0.1f) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("−0.1", modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = { onJog(0.1f) },
                modifier = Modifier.weight(1f)
            ) {
                Text("+0.1", modifier = Modifier.padding(end = 4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun MeasurementRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
