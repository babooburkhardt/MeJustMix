package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.sqrt

/**
 * Shows side-by-side comparison of screen color vs predicted paint output.
 * Uses K-M theory prediction for accurate results.
 */
@Composable
fun RealPaintPreviewComparison(
    screenColor: Color,
    predictedColor: Color,  // K-M predicted output
    modifier: Modifier = Modifier
) {
    // Calculate Delta E between screen and predicted colors
    val deltaE = calculateDeltaE(screenColor, predictedColor)
    val matchQuality = (1f - (deltaE / 100f).coerceIn(0f, 1f))
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Reality Check: Screen vs Paint",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Screen color
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(screenColor, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Screen Color",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "(Selected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Arrow
                Column(
                    modifier = Modifier.width(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "→",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Predicted paint color (from K-M)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(predictedColor, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Paint Output",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "(K-M Prediction)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Match quality indicator
            HorizontalDivider()
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Match Quality:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                
                LinearProgressIndicator(
                    progress = { matchQuality },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = when {
                        matchQuality > 0.85f -> MaterialTheme.colorScheme.primary
                        matchQuality > 0.7f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                
                Text(
                    "${(matchQuality * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                getMatchDescription(matchQuality),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            // Tips for poor matches
            if (matchQuality < 0.7f) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        "💡 Tip: Very bright/saturated colors are hard to achieve with paint. Try selecting a slightly darker shade.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Calculate Delta E (color difference) between two colors in LAB space.
 */
private fun calculateDeltaE(color1: Color, color2: Color): Float {
    val lab1 = DoubleArray(3)
    val lab2 = DoubleArray(3)
    ColorUtils.colorToLAB(color1.toArgb(), lab1)
    ColorUtils.colorToLAB(color2.toArgb(), lab2)
    
    val dL = lab1[0] - lab2[0]
    val dA = lab1[1] - lab2[1]
    val dB = lab1[2] - lab2[2]
    
    return sqrt((dL * dL + dA * dA + dB * dB).toFloat())
}

/**
 * Get description based on match quality.
 */
private fun getMatchDescription(matchQuality: Float): String {
    return when {
        matchQuality > 0.95f -> "Excellent - Paint will closely match your selection"
        matchQuality > 0.85f -> "Good - Minor differences, paint will look very similar"
        matchQuality > 0.7f -> "Fair - Noticeable difference, but close"
        matchQuality > 0.5f -> "Moderate - Paint will be noticeably different"
        else -> "Significant difference - Consider selecting a darker shade"
    }
}

/**
 * Compact inline preview showing real paint appearance.
 */
@Composable
fun InlineRealPaintPreview(
    screenColor: Color,
    predictedColor: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Screen
        Box(
            modifier = Modifier
                .size(size)
                .background(screenColor, RoundedCornerShape(4.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        )
        
        Text("→", style = MaterialTheme.typography.labelSmall)
        
        // Predicted
        Box(
            modifier = Modifier
                .size(size)
                .background(predictedColor, RoundedCornerShape(4.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        )
    }
}
