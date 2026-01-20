package com.example.mejustmix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Full-screen demo/test screen for the pulse home scroll wheel.
 * Allows testing the scroll wheel on a per-pump basis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseHomeTestScreen(
    pumps: List<PumpConfig>,
    onNavigateBack: () -> Unit,
    onJogPump: (pumpIndex: Int, steps: Int) -> Unit,
    onMarkHome: (pumpIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track which pump is currently selected
    var selectedPumpIndex by remember { mutableIntStateOf(0) }
    val selectedPump = pumps[selectedPumpIndex]
    
    // Calculate steps per pulse using motor specifications
    // 1.8° stepper, 1:4 gear reduction, 3 rollers
    val stepsPerPulse = calculateStepsPerPulse(
        stepAngle = 1.8f,
        gearReduction = 4f,
        rollerCount = 3
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pulse Mode Homing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Home Each Pump",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Use the scroll wheel below to rotate each pump's rollers to the home position. " +
                        "This ensures dispensing always starts and ends at the same point for accurate pulse-based mixing.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Motor specifications card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Motor Specifications",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Step Angle:", style = MaterialTheme.typography.bodySmall)
                        Text("1.8°", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gear Reduction:", style = MaterialTheme.typography.bodySmall)
                        Text("1:4", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Roller Count:", style = MaterialTheme.typography.bodySmall)
                        Text("3", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Steps/Pulse:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${stepsPerPulse.toInt()} steps",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Text(
                        "Formula: (360°/${1.8f}°) × ${4f} ÷ ${3} = ${stepsPerPulse.toInt()} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            HorizontalDivider()
            
            // Pump selector
            Text(
                "Select Pump to Home:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            pumps.forEachIndexed { index, pump ->
                PumpSelectorCard(
                    pump = pump,
                    isSelected = selectedPumpIndex == index,
                    onClick = { selectedPumpIndex = index },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            HorizontalDivider()
            
            // Scroll wheel for selected pump
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(selectedPump.colorArgb).copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PulseHomeScrollWheel(
                        pumpName = selectedPump.name,
                        pumpColor = Color(selectedPump.colorArgb),
                        stepsPerPulse = stepsPerPulse,
                        currentOffsetSteps = selectedPump.pulseHomeOffset,
                        onOffsetChange = { steps ->
                            onJogPump(selectedPumpIndex, steps.toInt())
                        },
                        onMarkHome = {
                            onMarkHome(selectedPumpIndex)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Additional helpful info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "💡 Tip: Home Position",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Align a roller just PAST the compression point where it releases the tube. " +
                        "This ensures consistent dispensing as each pulse starts and ends at the same mechanical position.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PumpSelectorCard(
    pump: PumpConfig,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(pump.colorArgb).copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                Color(pump.colorArgb)
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(4.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color(pump.colorArgb))
                    }
                }
                
                Column {
                    Text(
                        pump.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Axis: ${pump.axis}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(pump.colorArgb)
                )
            }
        }
    }
}

/**
 * Simple preview composable for testing
 */
@Composable
fun PulseHomeTestScreenPreview() {
    MaterialTheme {
        val samplePumps = listOf(
            PumpConfig("Cyan", "X", colorArgb = Color.Cyan.toArgb()),
            PumpConfig("Magenta", "Y", colorArgb = Color.Magenta.toArgb()),
            PumpConfig("Yellow", "Z", colorArgb = Color.Yellow.toArgb()),
        )
        
        PulseHomeTestScreen(
            pumps = samplePumps,
            onNavigateBack = {},
            onJogPump = { _, _ -> },
            onMarkHome = {}
        )
    }
}
