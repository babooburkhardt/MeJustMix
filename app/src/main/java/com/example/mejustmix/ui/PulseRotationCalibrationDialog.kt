package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Rotation Calibration Dialog - Verify motor steps per revolution.
 * Essential for Pulse Compensation to work correctly regardless of microstepping/gearing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseRotationCalibrationDialog(
    pumps: List<PumpConfig>,
    onJog: (pumpIndex: Int, steps: Float) -> Unit,
    onSave: (pumpIndex: Int, stepsPerPulse: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPumpIndex by remember { mutableStateOf(0) }
    val selectedPump = pumps.getOrNull(selectedPumpIndex) ?: pumps.first()
    
    // Default assumption: 3 rollers per pump
    // If we know steps per pulse, steps per rev is * 3
    var currentStepsPerRev by remember(selectedPump) { 
        mutableStateOf(selectedPump.stepsPerPulse * 3f) 
    }
    
    val scope = rememberCoroutineScope()
    
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
                            "🔄 Rotation Calibration",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Verify hardware steps per revolution",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // Pump Selector
                Text("Select Pump:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pumps.forEachIndexed { index, pump ->
                        FilterChip(
                            selected = index == selectedPumpIndex,
                            onClick = { selectedPumpIndex = index },
                            label = { Text(pump.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🎯 The Goal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Pulse Compensation needs to know exactly how many G-code units equal ONE full revolution. This depends on your motor microstepping settings (16x, 32x) which the app cannot guess.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Current Value Display
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
                        Text("Current Calibration", style = MaterialTheme.typography.labelMedium)
                        Text(
                            String.format("%.0f Steps / Rev", currentStepsPerRev),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "= ${String.format("%.1f", currentStepsPerRev / 3f)} steps per pulse (120°)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Test Actions
                Text("Test Rotation:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onJog(selectedPumpIndex, currentStepsPerRev) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Text("Spin 1 Turn", modifier = Modifier.padding(start = 8.dp))
                    }
                    
                    OutlinedButton(
                        onClick = { onJog(selectedPumpIndex, currentStepsPerRev * 10f) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Spin 10 Turns")
                    }
                }
                
                Text(
                    "Did it spin EXACTLY 360°?", 
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Adjustments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Decrease
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { currentStepsPerRev *= 0.9f }, // -10%
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Too Far (-10%)") }
                        
                        OutlinedButton(
                            onClick = { currentStepsPerRev *= 0.99f }, // -1%
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Tiny Bit (-1%)") }
                    }
                    
                    // Increase
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { currentStepsPerRev *= 1.1f }, // +10%
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Too Short (+10%)") }
                        
                        OutlinedButton(
                            onClick = { currentStepsPerRev *= 1.01f }, // +1%
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Tiny Bit (+1%)") }
                    }
                }
                
                // Manual Override
                Text("Manual Multipliers:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { currentStepsPerRev = 800f }, // 200 * 4
                        label = { Text("Reset (1x)") }
                    )
                    SuggestionChip(
                        onClick = { currentStepsPerRev = 800f * 16f }, // 16x Micro
                        label = { Text("16x Micro") }
                    )
                    SuggestionChip(
                        onClick = { currentStepsPerRev = 800f * 32f }, // 32x Micro
                        label = { Text("32x Micro") }
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Save
                Button(
                    onClick = { 
                        // Save steps per pulse (Rev / 3)
                        onSave(selectedPumpIndex, currentStepsPerRev / 3f) 
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, null)
                    Text("Save Calibration", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
