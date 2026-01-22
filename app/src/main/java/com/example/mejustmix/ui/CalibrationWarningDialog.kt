package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * First-time setup dialog that informs users about calibration requirements.
 */
@Composable
fun CalibrationWarningDialog(
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit
) {
    var hasAcknowledged by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = { /* Prevent dismissal without acknowledgment */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with warning icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "Welcome to MeJustMix!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider()
                
                Text(
                    text = "⚠️ Calibration Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "For accurate color mixing, you MUST calibrate the following:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Calibration checklist
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CalibrationItem(
                            title = "1. Flow Rate Calibration",
                            description = "Measure actual flow rate for each pump to ensure accurate volumes."
                        )
                        
                        CalibrationItem(
                            title = "2. Pigment Strength",
                            description = "Calibrate the tinting strength of each pigment for color accuracy."
                        )
                        
                        CalibrationItem(
                            title = "3. Roller Positions (Pulse Mode)",
                            description = "If using pulse mode, set the home position for each pump's rollers."
                        )
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "💡 Tip: Access calibration tools in the Settings menu. Start with flow rate calibration for best results.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                HorizontalDivider()
                
                // Acknowledgment checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasAcknowledged,
                        onCheckedChange = { hasAcknowledged = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "I understand that calibration is required for accurate results",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Action button
                Button(
                    onClick = {
                        onAcknowledge()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasAcknowledged
                ) {
                    Text("Got it! Let's mix some paint")
                }
            }
        }
    }
}

@Composable
private fun CalibrationItem(
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
