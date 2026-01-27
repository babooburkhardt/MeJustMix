package com.example.mejustmix.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pulse Geometry Wizard - Visual calibration for taper zones.
 * Users eyeball physical roller positions to define compensation geometry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseGeometryWizard(
    pump: PumpConfig,
    pumpIndex: Int,
    stepsPerPulse: Float,
    pillowLengthMm: Float,
    onJog: (steps: Float) -> Unit,
    onSave: (taperStartSteps: Float, taperLengthMm: Float, fullDiameterMm: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    var currentPositionSteps by remember { mutableStateOf(0f) }
    var taperStartSteps by remember { mutableStateOf<Float?>(null) }
    var taperEndSteps by remember { mutableStateOf<Float?>(null) }
    
    // stepsPerPulse is now passed in
    val scope = rememberCoroutineScope()
    
    // Convert steps to degrees
    fun stepsToDegrees(steps: Float) = (steps / stepsPerPulse) * 360f
    
    // Convert steps to mm (approximate, assuming linear relationship)
    fun stepsToMm(steps: Float) = (steps / stepsPerPulse) * pillowLengthMm
    
    // Jog with backlash compensation
    fun jogWithBacklash(steps: Float) {
        scope.launch {
            if (steps < 0) {
                // Reverse: overshoot by 5 steps, then return
                val overshoot = 5f
                onJog(steps - overshoot)
                kotlinx.coroutines.delay(200)
                onJog(overshoot)
            } else {
                onJog(steps)
            }
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
                            "📐 Geometry Wizard",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            pump.name,
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
                        pillowLengthMm = pillowLengthMm,
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
                        pillowLengthMm = pillowLengthMm,
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
                            val taperLengthSteps = abs((taperEndSteps ?: 0f) - (taperStartSteps ?: 0f))
                            val taperLengthMm = stepsToMm(taperLengthSteps)
                            val fullDiameterMm = pillowLengthMm - (2f * taperLengthMm)
                            onSave(taperStartSteps ?: 0f, taperLengthMm, fullDiameterMm.coerceAtLeast(0f))
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
            "Welcome to Geometry Calibration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "This wizard will help you define the exact physical geometry of your pump's taper zones.",
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
                Text("1️⃣ Find where the roller STARTS to lift off", style = MaterialTheme.typography.bodySmall)
                Text("2️⃣ Find where the roller COMPLETELY releases", style = MaterialTheme.typography.bodySmall)
                Text("3️⃣ Review and save your measurements", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "⚙️ Backlash Compensation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "When you jog backwards, the motor will automatically overshoot and return. This ensures accurate positioning despite gear play.",
                    style = MaterialTheme.typography.bodySmall
                )
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
    pillowLengthMm: Float,
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
                    "Jog the pump until the roller STARTS to lift off the flat section of the tube. This is where the taper begins.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        JogControls(
            currentPositionSteps = currentPositionSteps,
            stepsPerPulse = stepsPerPulse,
            pillowLengthMm = pillowLengthMm,
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
                Text("Mark Point A")
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
    pillowLengthMm: Float,
    onJog: (Float) -> Unit,
    onMark: () -> Unit,
    onBack: () -> Unit
) {
    val distanceFromStart = currentPositionSteps - taperStartSteps
    val distanceMm = (distanceFromStart / stepsPerPulse) * pillowLengthMm
    
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
                    "Continue jogging until the roller COMPLETELY releases the tube. This is where the taper ends.",
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
                Text("Distance from Point A:", style = MaterialTheme.typography.bodySmall)
                Text(
                    String.format("%.1f mm", distanceMm),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        JogControls(
            currentPositionSteps = currentPositionSteps,
            stepsPerPulse = stepsPerPulse,
            pillowLengthMm = pillowLengthMm,
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
                Text("Mark Point B")
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
    val taperLengthMm = (taperLengthSteps / stepsPerPulse) * pillowLengthMm
    val taperLengthDegrees = (taperLengthSteps / stepsPerPulse) * 360f
    val fullDiameterMm = pillowLengthMm - (2f * taperLengthMm)
    val taperFraction = (taperLengthMm / pillowLengthMm) * 100f
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Step 3: Review & Save",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Here are the calculated values based on your measurements:",
            style = MaterialTheme.typography.bodyMedium
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
                
                MeasurementRow("Taper Length", String.format("%.1f mm", taperLengthMm))
                MeasurementRow("Taper Angle", String.format("%.1f°", taperLengthDegrees))
                MeasurementRow("Full Diameter Section", String.format("%.1f mm", fullDiameterMm.coerceAtLeast(0f)))
                MeasurementRow("Taper Fraction", String.format("%.1f%%", taperFraction))
            }
        }
        
        if (fullDiameterMm < 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        "Warning: Taper length exceeds pillow length. The tube may be very soft or measurements need adjustment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
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
                Text("Save & Apply", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun JogControls(
    currentPositionSteps: Float,
    stepsPerPulse: Float,
    pillowLengthMm: Float,
    onJog: (Float) -> Unit
) {
    val currentDegrees = (currentPositionSteps / stepsPerPulse) * 360f
    val currentMm = (currentPositionSteps / stepsPerPulse) * pillowLengthMm
    
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
                    String.format("%.1f°", currentDegrees),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    String.format("%.1f mm | %.1f steps", currentMm, currentPositionSteps),
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
