package com.example.mejustmix.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.services.GCodeGenerator
import com.example.mejustmix.ui.components.ModernPillTab
import com.example.mejustmix.ui.components.ModernPillTabRow

@Composable
fun PigmentTunerDialog(
    onDismissRequest: () -> Unit,
    mixViewModel: MixViewModel,
    lockedColor: String? = null, 
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val strengths = settingsState.pigmentStrengths

    // If lockedColor is provided, use it. Otherwise default to Cyan.
    var selectedColor by remember { mutableStateOf(lockedColor ?: "Cyan") }
    // ADDED: "White" to the list of tunable colors
    val colors = listOf("Cyan", "Magenta", "Yellow", "Black", "White")

    val currentStrength = when(selectedColor) {
        "Cyan" -> strengths.cyan
        "Magenta" -> strengths.magenta
        "Yellow" -> strengths.yellow
        "Black" -> strengths.black
        "White" -> strengths.white
        else -> 1.0f
    }

    // --- UPDATED TARGETS (Subtractive/Physical Standards) ---
    val targetColorObj = when(selectedColor) {
        "Cyan" -> Color(0xFF00B7EB)     // Process Cyan
        "Magenta" -> Color(0xFFFF0090)  // Process Magenta
        "Yellow" -> Color(0xFFFFE600)   // Process Yellow
        "Black" -> Color(0xFF2D2926)    // Rich Black
        "White" -> Color(0xFFBBBBBB)    // Light Gray (Target for White Calibration)
        else -> Color.White
    }

    // Help Dialog State
    var showHelp by remember { mutableStateOf(false) }

    // --- VISUAL CALIBRATION STATE ---
    // 1.0 = Match, < 1.0 = Lighter, > 1.0 = Darker
    var visualFactor by remember { mutableStateOf(1.0f) }

    // Calculate the "Observed" color based on the slider
    val observedColorObj = remember(targetColorObj, visualFactor) {
        if (visualFactor == 1.0f) {
            targetColorObj
        } else if (visualFactor < 1.0f) {
            // Blend towards White (Lighter)
            val blended = ColorUtils.blendARGB(targetColorObj.toArgb(), Color.White.toArgb(), 1f - visualFactor)
            Color(blended)
        } else {
            // Blend towards Black (Darker)
            val darkRatio = (visualFactor - 1.0f) * 0.5f 
            val blended = ColorUtils.blendARGB(targetColorObj.toArgb(), Color.Black.toArgb(), darkRatio)
            Color(blended)
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Pigment Strength Tuning") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Select the color you want to calibrate.")
                    Text("2. Click 'Dispense Test Dot' to print a 5mL sample.")
                    Text("3. Compare the real dot to the 'Expected' circle.")
                    Text("4. Use the slider to match reality:")
                    Text("• Left (Lighter): Your dot is paler/whiter than the screen.")
                    Text("• Right (Darker): Your dot is deeper/stronger than the screen.")
                    Text("• Note for White: We use a Gray test dot. If your dot is too dark, it means you need MORE white.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Got it") }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with Info Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val title = if (lockedColor != null) "Tune $lockedColor Pigment" else "Pigment Tuner"
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Tab selection (only if not locked)
                if (lockedColor == null) {
                    ModernPillTabRow(
                        selectedTabIndex = colors.indexOf(selectedColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colors.forEach { color ->
                            ModernPillTab(
                                selected = selectedColor == color,
                                onClick = { 
                                    selectedColor = color
                                    visualFactor = 1.0f // Reset slider on change
                                },
                                text = color
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Wrap content in AnimatedContent for horizontal slide
                AnimatedContent(
                    targetState = selectedColor,
                    transitionSpec = {
                        val duration = 300
                        val targetIndex = colors.indexOf(targetState)
                        val initialIndex = colors.indexOf(initialState)
                        if (targetIndex > initialIndex) {
                            (slideInHorizontally(animationSpec = tween(duration)) { it } + fadeIn(animationSpec = tween(duration)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(duration)) { -it } + fadeOut(animationSpec = tween(duration)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(duration)) { -it } + fadeIn(animationSpec = tween(duration)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(duration)) { it } + fadeOut(animationSpec = tween(duration)))
                        }
                    },
                    label = "PigmentTunerTransition"
                ) { currentSelection ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // --- STEP 1: DISPENSE ---
                        Text("Step 1: Create Sample", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        
                        Button(
                            onClick = {
                                // Create a mix based on selection
                                val testMix = when(currentSelection) {
                                    "Cyan" -> PaintMix(2.5f / strengths.cyan, 0f, 0f, 0f, 2.5f / strengths.white)
                                    "Magenta" -> PaintMix(0f, 2.5f / strengths.magenta, 0f, 0f, 2.5f / strengths.white)
                                    "Yellow" -> PaintMix(0f, 0f, 2.5f / strengths.yellow, 0f, 2.5f / strengths.white)
                                    "Black" -> PaintMix(0f, 0f, 0f, 2.5f / strengths.black, 2.5f / strengths.white)
                                    // White Test: Mix with Black to create Gray (approx 25% Black / 75% White for visibility)
                                    "White" -> PaintMix(0f, 0f, 0f, 1.25f / strengths.black, 3.75f / strengths.white)
                                    else -> PaintMix(0f,0f,0f,0f,5f)
                                }
                                
                                // Normalize
                                val total = testMix.cyan + testMix.magenta + testMix.yellow + testMix.black + testMix.white
                                val normalizedMix = if (total > 0) PaintMix(
                                    testMix.cyan/total, testMix.magenta/total, testMix.yellow/total, testMix.black/total, testMix.white/total
                                ) else PaintMix(0f,0f,0f,0f,1f)
                                
                                val flowRate = settingsState.flowRate.toFloatOrNull() ?: 2.0f

                                val result = GCodeGenerator.generateMixingScript(
                                    mix = normalizedMix,
                                    totalVolumeMl = 5.0f,
                                    retractionSteps = 15f,
                                    pumps = settingsState.pumps, 
                                    flowRateMlPerSec = flowRate
                                )
                                val gcode = result.first
                                mixViewModel.sendRawGCode(gcode)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            val label = if (currentSelection == "White") "Dispense Gray Test" else "Dispense 5mL Test Dot"
                            Text(label)
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                        // --- STEP 2: VISUAL MATCH ---
                        Text("Step 2: Match Reality", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Does the dot match the Target?", fontSize = 14.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Visual Comparison Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // LEFT: TARGET
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(CircleShape)
                                        .background(targetColorObj)
                                        .border(2.dp, Color.LightGray, CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Expected", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Text("VS", fontWeight = FontWeight.ExtraBold, color = Color.Gray)

                            // RIGHT: OBSERVED (Adjustable)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(CircleShape)
                                        .background(observedColorObj)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Actual", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Slider Control
                        Text(
                            text = when {
                                visualFactor < 0.95f -> "My paint is Lighter"
                                visualFactor > 1.05f -> "My paint is Darker"
                                else -> "Perfect Match"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Slider(
                            value = visualFactor,
                            onValueChange = { visualFactor = it },
                            valueRange = 0.5f..2.0f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Lighter", style = MaterialTheme.typography.labelSmall)
                            Text("Darker", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- CALCULATION PREVIEW & SAVE ---
                // For Pigments (CMYK): Darker = Too Strong = Need Higher Strength Number (to dispense less)
                // For Base (White): Darker = Not Enough White = Need Lower Strength Number (to dispense more)
                val suggestedStrength = if (selectedColor == "White") {
                    currentStrength / visualFactor // Invert logic for white
                } else {
                    currentStrength * visualFactor
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                   Column {
                       Text("Current Strength", style = MaterialTheme.typography.labelSmall)
                       Text(String.format("%.2f", currentStrength), fontWeight = FontWeight.Bold)
                   }
                   
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "becomes",
                        tint = Color.Gray
                    )
                   Column(horizontalAlignment = Alignment.End) {
                       Text("New Strength", style = MaterialTheme.typography.labelSmall)
                       Text(
                           String.format("%.2f", suggestedStrength), 
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
                            settingsViewModel.updatePigmentStrength(selectedColor, suggestedStrength)
                            visualFactor = 1.0f // Reset for next pass
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
}