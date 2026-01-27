package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog wrapper for the scroll wheel roller position calibration.
 * Uses the nice PulseHomeScrollWheel UI that the user prefers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollerPositionDialog(
    pump: PumpConfig,
    stepsPerPulse: Float,
    onDismiss: () -> Unit,
    onSavePosition: (offsetSteps: Float) -> Unit
) {
    // Initialize with current known position (convert angle to steps)
    // steps = (angle / 360) * stepsPerPulse
    val initialSteps = ((pump.lastKnownAngle ?: 0f) / 360f) * stepsPerPulse
    var currentOffset by remember { mutableFloatStateOf(initialSteps) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🎯 Roller Position", style = MaterialTheme.typography.titleLarge)
                        Text(pump.name, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // The scroll wheel UI
                PulseHomeScrollWheel(
                    pumpName = pump.name,
                    pumpColor = Color(pump.colorArgb),
                    currentOffsetSteps = currentOffset,
                    onOffsetChange = { steps ->
                        currentOffset = steps
                    },
                    onMarkHome = {
                        onSavePosition(currentOffset)
                        onDismiss()
                    }
                )
            }
        }
    }
}
