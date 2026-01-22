package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.utils.PulseGeometryUtils
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Angle-based pump homing dialog with drift tracking.
 * 
 * No priming step - just tell the app where the roller is currently pointing,
 * and it will calculate the offset. Includes drift tracking to learn pump behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AngleHomingDialog(
    pump: PumpConfig,
    onDismiss: () -> Unit,
    onSaveAngle: (currentAngle: Float, driftDegrees: Float?) -> Unit
) {
    // Safe fallbacks for new fields (in case loading old pump configs)
    val safeLastKnownAngle = pump.lastKnownAngle ?: 225f
    val safeDriftHistory = pump.driftHistory ?: emptyList()
    val safeDriftCompensation = pump.driftCompensation ?: 0f
    
    var selectedAngle by remember { mutableStateOf(safeLastKnownAngle) }
    var showDriftInfo by remember { mutableStateOf(false) }
    
    // Calculate drift analysis
    val driftAnalysis = remember(safeDriftHistory) {
        PulseGeometryUtils.analyzeDriftPattern(safeDriftHistory)
    }
    
    // Calculate expected angle based on previous calibration + drift
    val expectedAngle = remember(safeLastKnownAngle, safeDriftCompensation) {
        (safeLastKnownAngle + safeDriftCompensation) % 360f
    }
    
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
                        Text("🎯 Pump Position", style = MaterialTheme.typography.titleLarge)
                        Text(pump.name, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                HorizontalDivider()
                
                // Instruction card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Where is the roller pointing?", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Look at the pump and tap the clock position that matches where any roller is currently pointing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                
                // Clock selector
                ClockPositionSelector(
                    pumpColor = Color(pump.colorArgb),
                    selectedAngle = selectedAngle,
                    onAngleSelected = { selectedAngle = it }
                )
                
                // Selected angle display
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(pump.colorArgb).copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                PulseGeometryUtils.degreesToDirection(selectedAngle),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${selectedAngle.roundToInt()}°",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color(pump.colorArgb)
                            )
                        }
                        Text(
                            "${PulseGeometryUtils.stepsToNextBoundary(selectedAngle).roundToInt()} steps to home",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
                
                // Drift tracking section
                if (safeDriftHistory.isNotEmpty()) {
                    DriftTrackingCard(
                        driftAnalysis = driftAnalysis,
                        expectedAngle = expectedAngle,
                        observedAngle = selectedAngle,
                        expanded = showDriftInfo,
                        onToggle = { showDriftInfo = !showDriftInfo }
                    )
                }
                
                // Reminder card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "💡 Tip: If pulses return or worsen, come back and update this position. After 5 updates, the app will learn your pump's drift pattern!",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            // Calculate drift if we have a previous calibration
                            val drift = if (safeDriftHistory.isNotEmpty() || safeLastKnownAngle != 225f) {
                                PulseGeometryUtils.calculateDrift(expectedAngle, selectedAngle)
                            } else {
                                null
                            }
                            onSaveAngle(selectedAngle, drift)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(pump.colorArgb)
                        )
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Position")
                    }
                }
            }
        }
    }
}

/**
 * Visual clock position selector for angle input.
 */
@Composable
fun ClockPositionSelector(
    pumpColor: Color,
    selectedAngle: Float,
    onAngleSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(2.dp, pumpColor.copy(alpha = 0.5f), CircleShape)
        )
        
        // Clock hour markers
        val hours = listOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        
        hours.forEach { hour ->
            val angle = ((hour % 12) * 30f - 90f) * (Math.PI / 180f)
            val radius = 0.38f
            
            val hourAngle = PulseGeometryUtils.clockPositionToDegrees(hour)
            val isSelected = kotlin.math.abs(selectedAngle - hourAngle) < 15f
            
            val offsetX = (cos(angle) * radius).toFloat()
            val offsetY = (sin(angle) * radius).toFloat()
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(
                        x = (offsetX * 140).dp,
                        y = (offsetY * 140).dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { onAngleSelected(hourAngle) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) pumpColor else MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isSelected) 4.dp else 1.dp
                    )
                ) {
                    Text(
                        "$hour",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        // Center indicator with direction arrow
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Arrow pointing to selected direction
            val arrowRotation = selectedAngle - 90f
            Icon(
                Icons.Default.Navigation,
                contentDescription = null,
                tint = pumpColor,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
            Text(
                "Roller",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        
        // Key positions labels
        // North label - Pulse boundary
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
        ) {
            if (selectedAngle < 15f || selectedAngle > 345f) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Pulse Boundary",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        // SW label - Also near boundary (225°)
        if (selectedAngle in 210f..240f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = (-16).dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Near Boundary (225°)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Drift tracking information card.
 */
@Composable
fun DriftTrackingCard(
    driftAnalysis: PulseGeometryUtils.DriftAnalysis,
    expectedAngle: Float,
    observedAngle: Float,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val currentDrift = PulseGeometryUtils.calculateDrift(expectedAngle, observedAngle)
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (driftAnalysis.recommendedCompensation != null)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (driftAnalysis.recommendedCompensation != null) 
                            Icons.Default.TrendingUp 
                        else 
                            Icons.Default.Analytics,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Drift Tracking (${driftAnalysis.sampleCount} samples)",
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Expand"
                )
            }
            
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                // Current drift
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current drift:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${if (currentDrift >= 0) "+" else ""}${String.format("%.1f", currentDrift)}°",
                        fontWeight = FontWeight.Bold,
                        color = if (kotlin.math.abs(currentDrift) > 10f) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
                
                // Analysis message
                Spacer(Modifier.height(4.dp))
                Text(
                    driftAnalysis.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                // If recommendation available
                if (driftAnalysis.recommendedCompensation != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Auto-compensation: ${String.format("%.1f", driftAnalysis.recommendedCompensation)}° per session",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
