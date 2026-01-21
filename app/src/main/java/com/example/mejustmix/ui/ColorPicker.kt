package com.example.mejustmix.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor(): Color {
        return Color.hsv(hue, saturation, value)
    }
}

@Composable
fun CircularColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    // Initialize state
    val initialHsv = remember(color) {
        val hsvArray = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsvArray)
        HsvColor(hsvArray[0], hsvArray[1], hsvArray[2])
    }

    var hsvState by remember { mutableStateOf(initialHsv) }

    LaunchedEffect(color) {
        // FIX 1: Check if the incoming color matches our current state to prevent jumping
        val currentStateColor = hsvState.toColor()
        val dist = sqrt(
            (color.red - currentStateColor.red).let { it * it } +
            (color.green - currentStateColor.green).let { it * it } +
            (color.blue - currentStateColor.blue).let { it * it }
        )
        // Increased threshold to reduce drift during rapid brightness changes
        if (dist < 0.05f) return@LaunchedEffect

        // FIX 2: Prevent Thumb reset when color is near black
        val hsvArray = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsvArray)

        val incomingHue = hsvArray[0]
        val incomingSat = hsvArray[1]
        val incomingVal = hsvArray[2]

        val isLowLight = incomingVal < 0.1f
        val isSaturationLost = incomingSat < 0.05f 
        val shouldPreserve = isLowLight && isSaturationLost

        val newHue = if (shouldPreserve) hsvState.hue else incomingHue
        val newSat = if (shouldPreserve) hsvState.saturation else incomingSat
        val newValue = incomingVal

        val newHsv = HsvColor(newHue, newSat, newValue)

        if (abs(newHsv.hue - hsvState.hue) > 1f ||
            abs(newHsv.saturation - hsvState.saturation) > 0.01f ||
            abs(newHsv.value - hsvState.value) > 0.01f
        ) {
            hsvState = newHsv
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ColorWheel(
            hsv = hsvState,
            onHsvChanged = { newHsv ->
                hsvState = newHsv
                onColorChanged(newHsv.toColor())
            },
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        )

        BrightnessSlider(
            hsv = hsvState,
            onValueChanged = { newValue ->
                hsvState = hsvState.copy(value = newValue)
                onColorChanged(hsvState.toColor())
            }
        )
    }
}

@Composable
private fun ColorWheel(
    hsv: HsvColor,
    onHsvChanged: (HsvColor) -> Unit,
    modifier: Modifier = Modifier,
    thumbSize: Dp = 36.dp
) {
    val density = LocalDensity.current
    val thumbSizePx = with(density) { thumbSize.toPx() }
    
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    // Calculate Target Position
    val targetOffset = remember(hsv, componentSize) {
        if (componentSize == IntSize.Zero) {
            Offset.Zero
        } else {
            val center = Offset(componentSize.width / 2f, componentSize.height / 2f)
            val radius = minOf(componentSize.width, componentSize.height) / 2f
            
            val hueRadians = Math.toRadians(hsv.hue.toDouble())
            val saturationRadius = hsv.saturation * radius
            
            Offset(
                x = (center.x + saturationRadius * cos(hueRadians)).toFloat(),
                y = (center.y + saturationRadius * sin(hueRadians)).toFloat()
            )
        }
    }

    // Animate Thumb ("Puck on Ice" physics)
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, 
            stiffness = Spring.StiffnessLow 
        ),
        label = "Thumb Animation"
    )

    // Calculate Transient Color
    val animatedColor = remember(animatedOffset, componentSize) {
        if (componentSize == IntSize.Zero) hsv.toColor()
        else {
             val center = Offset(componentSize.width / 2f, componentSize.height / 2f)
             val dx = animatedOffset.x - center.x
             val dy = animatedOffset.y - center.y
             var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
             if (angle < 0) angle += 360f
             
             val radius = minOf(componentSize.width, componentSize.height) / 2f
             val dist = sqrt(dx * dx + dy * dy)
             
             // Fixed: Use Float (0f, 1f) for coerceIn
             val sat = (dist / radius).coerceIn(0f, 1f)
             
             Color.hsv(angle, sat, 1f) 
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { componentSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateHsvFromOffset(offset, size, hsv, onHsvChanged)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateHsvFromOffset(change.position, size, hsv, onHsvChanged)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Draw Wheel Background
            val hueColors = listOf(
                Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(hueColors.reversed(), center),
                radius = radius,
                center = center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Draw Animated Thumb
            if (componentSize != IntSize.Zero) {
                drawCircle(
                    color = Color.White,
                    radius = thumbSizePx / 2,
                    center = animatedOffset
                )
                drawCircle(
                    color = animatedColor, 
                    radius = thumbSizePx / 2 - 4.dp.toPx(),
                    center = animatedOffset
                )
                drawCircle(
                    color = Color.LightGray,
                    radius = thumbSizePx / 2,
                    center = animatedOffset,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

private fun updateHsvFromOffset(
    offset: Offset,
    size: IntSize,
    currentHsv: HsvColor,
    onHsvChanged: (HsvColor) -> Unit
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = minOf(size.width, size.height) / 2f

    val dx = offset.x - center.x
    val dy = offset.y - center.y

    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0) angle += 360f

    val distance = sqrt(dx * dx + dy * dy)
    val saturation = (distance / radius).coerceIn(0f, 1f)

    onHsvChanged(currentHsv.copy(hue = angle, saturation = saturation))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessSlider(
    hsv: HsvColor,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorWithFullBrightness = HsvColor(hsv.hue, hsv.saturation, 1f).toColor()

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)) 
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0.001f, 0.001f, 0.001f), colorWithFullBrightness)
                    )
                )
                .border(
                    width = 1.dp, 
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), 
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Slider(
            value = hsv.value,
            onValueChange = onValueChanged,
            valueRange = 0.001f..1f, 
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.LightGray, CircleShape)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}