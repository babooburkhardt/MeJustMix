package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RetractionCalibratorDialog(
    onDismissRequest: () -> Unit,
    mixViewModel: MixViewModel,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    
    // Parse current retraction (steps)
    val currentRetraction = settingsState.retractionSteps.toFloatOrNull() ?: 15.0f
    
    // We use the first pump to test (usually Cyan/X)
    val testPump = settingsState.pumps.firstOrNull()
    
    // Help Dialog State
    var showHelp by remember { mutableStateOf(false) }

    // --- VISUAL CALIBRATION STATE ---
    // 0.0 = Severe Oozing (Needs MORE retraction)
    // 1.0 = Perfect
    // 2.0 = Sucking Air (Needs LESS retraction)
    var visualFactor by remember { mutableStateOf(1.0f) }

    // --- HELP DIALOG ---
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Retraction Tuning") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Place a cup under the ${testPump?.name ?: "Nozzle"}.")
                    Text("2. Click 'Dispense & Retract'.")
                    Text("3. Watch the nozzle tip immediately after it stops.")
                    Text("4. Use the slider to match what you see:")
                    Text("• Left (Oozing): Use this if a drop forms or falls.")
                    Text("• Right (Air Gap): Use this if paint is sucked back too far.")
                    Text("• Center: Use this if the cut is clean.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Got it") }
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retraction Tuner", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- STEP 1: TEST ---
                Text("Step 1: Test Behavior", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                Button(
                    onClick = {
                        if (testPump != null) {
                            val axis = testPump.axis
                            // FIXED: Send as a List<String> to match MixViewModel signature
                            // G91 (Relative) -> Extrude 0.5 -> Pause -> Retract -> G90 (Absolute)
                            val gcode = listOf(
                                "G91",
                                "G1 ${axis}0.5 F300",
                                "G4 P0.5",
                                "G1 ${axis}-${currentRetraction} F1000",
                                "G90"
                            )
                            mixViewModel.sendRawGCode(gcode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = testPump != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                ) {
                    Text("Dispense & Retract")
                }
                
                if (testPump == null) {
                    Text("No pumps configured!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Divider(modifier = Modifier.padding(vertical = 24.dp))

                // --- STEP 2: VISUAL MATCH ---
                Text("Step 2: Match Reality", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("What does the nozzle tip look like?", fontSize = 14.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(16.dp))

                // Slider Control
                Slider(
                    value = visualFactor,
                    onValueChange = { visualFactor = it },
                    valueRange = 0.0f..2.0f,
                    steps = 19, // 0.1 increments
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // LEFT: Oozing
                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(80.dp)) {
                        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Oozing", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("(Increase)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    
                    // CENTER
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (visualFactor in 0.9f..1.1f) MaterialTheme.colorScheme.primary else Color.LightGray)
                        )
                        Text("Perfect", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    // RIGHT: Air Gap
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(80.dp)) {
                        // Custom empty circle drawing
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(2.dp, MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Text("Air Gap", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("(Decrease)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- CALCULATION LOGIC ---
                // If Factor < 1.0 (Oozing), we need MORE retraction.
                val multiplier = 2.0f - visualFactor // Invert logic for the math
                
                // Dampen the multiplier so it's not too aggressive (0.5x to 1.5x range)
                val dampenedMult = 1.0f + ((multiplier - 1.0f) * 0.5f)
                
                val suggestedRetraction = (currentRetraction * dampenedMult).coerceAtLeast(0f)

                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                   Column {
                       Text("Current Steps", style = MaterialTheme.typography.labelSmall)
                       Text(String.format("%.1f", currentRetraction), fontWeight = FontWeight.Bold)
                   }
                   
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "becomes",
                        tint = Color.Gray
                    )
                   Column(horizontalAlignment = Alignment.End) {
                       Text("New Steps", style = MaterialTheme.typography.labelSmall)
                       Text(
                           String.format("%.1f", suggestedRetraction), 
                           fontWeight = FontWeight.Bold,
                           color = MaterialTheme.colorScheme.primary
                       )
                   }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            settingsViewModel.updateRetractionSteps(String.format("%.1f", suggestedRetraction))
                            visualFactor = 1.0f // Reset
                        },
                        enabled = visualFactor != 1.0f
                    ) {
                        Text("Calibrate")
                    }
                }
            }
        }
    }
}