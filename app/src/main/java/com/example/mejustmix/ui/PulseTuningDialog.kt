package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PulseTuningDialog(
    pump: PumpConfig,
    stepsPerPulse: Float,
    isTuning: Boolean,
    tuningPhaseOffset: Float,
    tuningStrength: Float,
    tuningPulseWidth: Float?,
    pillowLengthMm: Float,
    currentHomeOffset: Float,
    onToggleTuning: (Boolean) -> Unit,
    onOffsetChange: (Float) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onWidthChange: (Float) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    // Calculate derived values in real-time
    // val stepsPerPulse is now passed in
    val phaseShiftSteps = (tuningPhaseOffset / 360f) * stepsPerPulse
    val newHomeOffset = currentHomeOffset - phaseShiftSteps
    
    val newFullDiameterMm = if (tuningPulseWidth != null) {
        val fraction = tuningPulseWidth / 360f
        (pillowLengthMm - (2f * pillowLengthMm * fraction)).coerceAtLeast(1f)
    } else null
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f) // Constrain height to 90% of screen
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()), // Make content scrollable
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Live Pulse Tuning",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Pump: ${pump.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(pump.colorArgb)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // Warning
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Use WATER only! Pump will run continuously while tuning.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // Controls
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    
                    // Timing Slider
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Timing (Phase Offset)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${if (tuningPhaseOffset > 0) "+" else ""}${tuningPhaseOffset.toInt()}°",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = tuningPhaseOffset,
                            onValueChange = onOffsetChange,
                            valueRange = -90f..90f,
                            steps = 35, // 5 degree steps
                            enabled = isTuning // Only allow tuning while running for real-time feedback? Or always?
                        )
                        Text(
                            "Adjust if the speed boost happens too early or too late.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    
                    // Strength Slider
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Smoothing Strength", fontWeight = FontWeight.SemiBold)
                            Text(
                                String.format("%.1fx", tuningStrength),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = tuningStrength,
                            onValueChange = onStrengthChange,
                            valueRange = 0f..5f,
                            steps = 49
                        )
                        Text(
                            "Higher = Stronger dip correction & surge suppression.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    
                    // Width Slider (Taper Duration)
                    if (tuningPulseWidth != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pulse Width (Sharpness)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${tuningPulseWidth.toInt()}°",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = tuningPulseWidth,
                                onValueChange = onWidthChange,
                                valueRange = 5f..45f,
                                steps = 39,
                            )
                            Text(
                                "Adjust taper duration. Small = Sharp, Large = Smooth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                // Derived Values Display (for the nerds!)
                HorizontalDivider()
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "📊 Calculated Values",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Phase shift in steps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Phase Shift:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                String.format("%.1f steps", phaseShiftSteps),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // New home offset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "New Home Offset:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                String.format("%.1f steps", newHomeOffset),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Full diameter section (if pulse width is being adjusted)
                        if (newFullDiameterMm != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Full Diameter Section:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    String.format("%.2f mm", newFullDiameterMm),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Start/Stop
                    Button(
                        onClick = { onToggleTuning(!isTuning) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTuning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(if (isTuning) Icons.Default.Close else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isTuning) "Stop Pump" else "Start Pump")
                    }
                    
                    // Save
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = !isTuning && tuningPhaseOffset != 0f, // Only save if stopped and changed? Or allow save while running? Safest if stopped.
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, null) // Checkmark better?
                        Spacer(Modifier.width(8.dp))
                        Text("Save Offset")
                    }
                }
            }
        }
    }
}
