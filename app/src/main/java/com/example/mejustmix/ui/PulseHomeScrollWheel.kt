package com.example.mejustmix.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Calculate steps per pulse based on motor and gear specifications.
 * 
 * @param stepAngle Motor step angle in degrees (typically 1.8° for NEMA steppers)
 * @param gearReduction Gear reduction ratio (e.g., 4.0 for 1:4 reduction)
 * @param rollerCount Number of rollers on the peristaltic pump (typically 3)
 * @return Steps per complete pulse (one roller rotation)
 */
fun calculateStepsPerPulse(
    stepAngle: Float = 1.8f,
    gearReduction: Float = 4f,
    rollerCount: Int = 3
): Float {
    val stepsPerMotorRevolution = 360f / stepAngle
    val stepsPerPumpRevolution = stepsPerMotorRevolution * gearReduction
    val stepsPerPulse = stepsPerPumpRevolution / rollerCount
    return stepsPerPulse
}

/**
 * Interactive scroll wheel for visually homing a peristaltic pump.
 * Allows user to rotate a virtual representation of the pump rollers
 * and aligns them to a home position.
 * 
 * Physical calculations:
 * - 1.8° stepper = 200 steps/motor revolution
 * - 1:4 gear reduction = 800 steps/pump revolution  
 * - 3 rollers = 800/3 ≈ 266.67 steps/pulse
 * 
 * @param pumpName Name of the pump being homed
 * @param pumpColor Color associated with this pump
 * @param stepsPerPulse Calculated steps for one complete pulse (default: auto-calculated)
 * @param currentOffsetSteps Current offset from home in steps
 * @param onOffsetChange Callback when user rotates the wheel (returns step offset)
 * @param onMarkHome Callback when user marks current position as home
 * @param modifier Modifier for the component
 */
@Composable
fun PulseHomeScrollWheel(
    pumpName: String,
    pumpColor: Color,
    stepsPerPulse: Float = calculateStepsPerPulse(),
    currentOffsetSteps: Float = 0f,
    onOffsetChange: (steps: Float) -> Unit,
    onMarkHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    // State for smooth animations
    var dragAngle by remember { mutableFloatStateOf(0f) }
    var accumulatedSteps by remember { mutableFloatStateOf(currentOffsetSteps) }
    
    // Calculate current angle based on accumulated steps
    val currentAngle = (accumulatedSteps / stepsPerPulse) * 360f
    
    // Calculate how many steps to nearest pulse boundary
    val stepsToHome = accumulatedSteps % stepsPerPulse
    val stepsFromHome = if (stepsToHome > stepsPerPulse / 2) {
        stepsToHome - stepsPerPulse
    } else {
        stepsToHome
    }
    
    val isAtHome = abs(stepsFromHome) < 1f
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "Home $pumpName Pump",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Drag to rotate the rollers to align with home position",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Visual scroll wheel
        Box(
            modifier = Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragAngle = calculateAngle(offset, size.width / 2f, size.height / 2f)
                        },
                        onDrag = { change, _ ->
                            val newAngle = calculateAngle(
                                change.position,
                                size.width / 2f,
                                size.height / 2f
                            )
                            
                            // Calculate delta angle (handle wrapping)
                            var deltaAngle = newAngle - dragAngle
                            if (deltaAngle > 180) deltaAngle -= 360
                            if (deltaAngle < -180) deltaAngle += 360
                            
                            // Convert angle to steps
                            val deltaSteps = (deltaAngle / 360f) * stepsPerPulse
                            
                            accumulatedSteps += deltaSteps
                            onOffsetChange(accumulatedSteps)
                            
                            dragAngle = newAngle
                            change.consume()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Render the pump wheel
            PumpWheelCanvas(
                angle = currentAngle,
                pumpColor = pumpColor,
                isAtHome = isAtHome,
                stepsFromHome = stepsFromHome,
                stepsPerPulse = stepsPerPulse
            )
        }
        
        // Status information
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAtHome) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current Position:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${accumulatedSteps.roundToInt()} steps",
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Distance from Home:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${abs(stepsFromHome).roundToInt()} steps",
                        fontWeight = FontWeight.Bold,
                        color = if (isAtHome) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
                
                if (isAtHome) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Aligned at pulse boundary!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // Snap to home button
        Button(
            onClick = {
                // Snap to nearest home position
                val nearestHome = (accumulatedSteps / stepsPerPulse).roundToInt() * stepsPerPulse
                accumulatedSteps = nearestHome
                onOffsetChange(nearestHome)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAtHome
        ) {
            Text("Snap to Nearest Home")
        }
        
        // Mark as home button
        Button(
            onClick = onMarkHome,
            modifier = Modifier.fillMaxWidth(),
            enabled = isAtHome,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text("Mark as Home Position")
        }
        
        // Technical info
        Text(
            "Steps per pulse: ${stepsPerPulse.roundToInt()} (calculated from motor specs)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PumpWheelCanvas(
    angle: Float,
    pumpColor: Color,
    isAtHome: Boolean,
    stepsFromHome: Float,
    stepsPerPulse: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.8f
        
        // Draw outer circle (pump body)
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Draw home position indicator (fixed at top)
        drawLine(
            color = if (isAtHome) Color.Green else Color.Red,
            start = Offset(center.x, center.y - radius - 20.dp.toPx()),
            end = Offset(center.x, center.y - radius + 10.dp.toPx()),
            strokeWidth = 6.dp.toPx()
        )
        
        // Draw home position triangle
        val triangleSize = 15.dp.toPx()
        val trianglePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y - radius - 20.dp.toPx() - triangleSize)
            lineTo(center.x - triangleSize / 2, center.y - radius - 20.dp.toPx())
            lineTo(center.x + triangleSize / 2, center.y - radius - 20.dp.toPx())
            close()
        }
        drawPath(
            path = trianglePath,
            color = if (isAtHome) Color.Green else Color.Red
        )
        
        // Draw 3 rollers rotating around center
        rotate(angle, center) {
            for (i in 0 until 3) {
                val rollerAngle = (i * 120f) * (PI.toFloat() / 180f)
                val rollerX = center.x + radius * 0.7f * kotlin.math.cos(rollerAngle)
                val rollerY = center.y + radius * 0.7f * kotlin.math.sin(rollerAngle)
                
                // Draw roller
                drawCircle(
                    color = pumpColor,
                    radius = radius * 0.2f,
                    center = Offset(rollerX, rollerY)
                )
                
                // Draw roller border
                drawCircle(
                    color = Color.Black,
                    radius = radius * 0.2f,
                    center = Offset(rollerX, rollerY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        
        // Draw center dot
        drawCircle(
            color = Color.Gray,
            radius = 8.dp.toPx(),
            center = center
        )
        
        // Draw compression zone indicator (where tube is squeezed)
        val compressionAngle = 30f // degrees
        drawArc(
            color = Color.Red.copy(alpha = 0.2f),
            startAngle = -compressionAngle / 2f - 90f,
            sweepAngle = compressionAngle,
            useCenter = true,
            topLeft = Offset(center.x - radius * 0.85f, center.y - radius * 0.85f),
            size = androidx.compose.ui.geometry.Size(radius * 1.7f, radius * 1.7f)
        )
    }
}

/**
 * Calculate angle in degrees from center point to touch position
 */
private fun calculateAngle(position: Offset, centerX: Float, centerY: Float): Float {
    val deltaX = position.x - centerX
    val deltaY = position.y - centerY
    val angleRad = atan2(deltaY, deltaX)
    val angleDeg = angleRad * (180f / PI.toFloat())
    return (angleDeg + 360f) % 360f
}

/**
 * Compact version for use in calibration dialog
 */
@Composable
fun CompactPulseHomeWheel(
    pumpColor: Color,
    stepsPerPulse: Float = calculateStepsPerPulse(),
    currentOffsetSteps: Float = 0f,
    onOffsetChange: (steps: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedSteps by remember { mutableFloatStateOf(currentOffsetSteps) }
    var dragAngle by remember { mutableFloatStateOf(0f) }
    
    val currentAngle = (accumulatedSteps / stepsPerPulse) * 360f
    val stepsToHome = accumulatedSteps % stepsPerPulse
    val stepsFromHome = if (stepsToHome > stepsPerPulse / 2) {
        stepsToHome - stepsPerPulse
    } else {
        stepsToHome
    }
    val isAtHome = abs(stepsFromHome) < 1f
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragAngle = calculateAngle(offset, size.width / 2f, size.height / 2f)
                        },
                        onDrag = { change, _ ->
                            val newAngle = calculateAngle(
                                change.position,
                                size.width / 2f,
                                size.height / 2f
                            )
                            
                            var deltaAngle = newAngle - dragAngle
                            if (deltaAngle > 180) deltaAngle -= 360
                            if (deltaAngle < -180) deltaAngle += 360
                            
                            val deltaSteps = (deltaAngle / 360f) * stepsPerPulse
                            
                            accumulatedSteps += deltaSteps
                            onOffsetChange(accumulatedSteps)
                            
                            dragAngle = newAngle
                            change.consume()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            PumpWheelCanvas(
                angle = currentAngle,
                pumpColor = pumpColor,
                isAtHome = isAtHome,
                stepsFromHome = stepsFromHome,
                stepsPerPulse = stepsPerPulse
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Steps", style = MaterialTheme.typography.labelSmall)
                Text(
                    "${accumulatedSteps.roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("From Home", style = MaterialTheme.typography.labelSmall)
                Text(
                    "${abs(stepsFromHome).roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAtHome) Color.Green else Color.Red
                )
            }
        }
    }
}
