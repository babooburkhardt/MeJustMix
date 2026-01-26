package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.services.KubelkaMunkColorMixing
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload

/**
 * Dialog for managing Kubelka-Munk K/S values (3-channel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KubelkaMunkSettingsDialog(
    settingsViewModel: SettingsViewModel,
    onDismissRequest: () -> Unit,
    mixViewModel: MixViewModel? = null,
    onImportRequest: () -> Unit = {}
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val database = uiState.kmDatabase
    
    var selectedPigment by remember { mutableStateOf("Cyan") }
    var showSimpleCalibration by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    
    // Pigment definitions - realistic pigment colors
    // Based on actual artist pigments:
    // Cyan = Phthalocyanine Blue (deep teal-blue)
    // Magenta = Quinacridone Magenta (deep berry/wine)
    // Yellow = Cadmium Yellow Medium (warm golden)
    // Black = Carbon Black
    // White = Titanium White
    val pigmentColors = mapOf(
        "Cyan" to Color(0xFF0074A2),      // Phthalo Blue - deep teal
        "Magenta" to Color(0xFF9F005D),   // Quinacridone - deep berry
        "Yellow" to Color(0xFFE8B800),    // Cadmium Yellow - warm gold
        "Black" to Color(0xFF1A1A1A),     // Carbon Black
        "White" to Color(0xFFF5F5F0)      // Titanium White - slight cream
    )
    
    val pigmentTextColors = mapOf(
        "Cyan" to Color.White,
        "Magenta" to Color.White,
        "Yellow" to Color(0xFF3D2E00),    // Dark brown for contrast
        "Black" to Color.White,
        "White" to Color(0xFF666666)
    )
    
    // Simple calibration dialog
    if (showSimpleCalibration != null) {
        val pigmentName = showSimpleCalibration!!
        val currentKS = settingsViewModel.getPigmentKS(pigmentName)
        
        SimpleKMCalibrationDialog(
            pigmentName = pigmentName,
            currentKSColor = currentKS,
            onCalibrated = { newKSColor ->
                settingsViewModel.updatePigmentKS(pigmentName, newKSColor)
            },
            onDismissRequest = { showSimpleCalibration = null },
            mixViewModel = mixViewModel,
            settingsViewModel = settingsViewModel
        )
    }
    
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Header
                Text(
                    text = "Kubelka-Munk Values",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Tap a pigment to edit its absorption & scattering properties.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Pigment Selector - Colored tiles like main menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pigmentColors.forEach { (pigment, bgColor) ->
                        val isSelected = selectedPigment == pigment
                        val textColor = pigmentTextColors[pigment] ?: Color.Black
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .then(
                                    if (isSelected) Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp)
                                    ) else if (pigment == "White") Modifier.border(
                                        1.dp,
                                        Color.LightGray,
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                                .clickable { selectedPigment = pigment },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (pigment) {
                                    "Cyan" -> "C"
                                    "Magenta" -> "M"
                                    "Yellow" -> "Y"
                                    "Black" -> "K"
                                    "White" -> "W"
                                    else -> pigment.first().toString()
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = textColor
                            )
                            Text(
                                text = pigment,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Quick calibration button
                Button(
                    onClick = { showSimpleCalibration = selectedPigment },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pigmentColors[selectedPigment] ?: MaterialTheme.colorScheme.primary,
                        contentColor = pigmentTextColors[selectedPigment] ?: Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Calibrate $selectedPigment", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // K/S Values Display
                if (database != null) {
                    val ksColor = when (selectedPigment) {
                        "Cyan" -> database.cyan
                        "Magenta" -> database.magenta
                        "Yellow" -> database.yellow
                        "Black" -> database.black
                        "White" -> database.white
                        else -> database.cyan
                    }
                    
                    // K/S Editor Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header with preview
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "$selectedPigment Parameters",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                // Preview color
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            Color(KubelkaMunkColorMixing.ksToRGB(ksColor)),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                )
                            }
                            
                            HorizontalDivider()
                            
                            // Absorption section
                            Text(
                                "Absorption (K/S ratio)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Red channel
                                KSValueEditor(
                                    label = "Red",
                                    value = ksColor.ksR,
                                    color = Color(0xFFEF5350),
                                    onValueChange = { newR ->
                                        settingsViewModel.updatePigmentKS(selectedPigment, ksColor.copy(ksR = newR))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Green channel
                                KSValueEditor(
                                    label = "Green",
                                    value = ksColor.ksG,
                                    color = Color(0xFF66BB6A),
                                    onValueChange = { newG ->
                                        settingsViewModel.updatePigmentKS(selectedPigment, ksColor.copy(ksG = newG))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Blue channel
                                KSValueEditor(
                                    label = "Blue",
                                    value = ksColor.ksB,
                                    color = Color(0xFF42A5F5),
                                    onValueChange = { newB ->
                                        settingsViewModel.updatePigmentKS(selectedPigment, ksColor.copy(ksB = newB))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            HorizontalDivider()
                            
                            // Scattering section
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        "Scattering (S)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        when {
                                            ksColor.s > 2.0f -> "Very Opaque"
                                            ksColor.s > 1.0f -> "Opaque"
                                            ksColor.s > 0.5f -> "Semi-Opaque"
                                            else -> "Transparent"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                OutlinedTextField(
                                    value = String.format(Locale.US, "%.2f", ksColor.s),
                                    onValueChange = { newText ->
                                        newText.toFloatOrNull()?.let { newS ->
                                            val safeS = newS.coerceAtLeast(0.01f)
                                            settingsViewModel.updatePigmentKS(selectedPigment, ksColor.copy(s = safeS))
                                        }
                                    },
                                    modifier = Modifier.width(100.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "K-M database not loaded. Toggle K-M mode off and on in settings.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Data management buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { settingsViewModel.exportKSDatabase() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export KS")
                    }
                    
                    OutlinedButton(
                        onClick = onImportRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import KS")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { settingsViewModel.resetKMDatabaseToDefaults() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset All")
                    }
                    
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun KSValueEditor(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) { mutableStateOf(String.format(Locale.US, "%.2f", value)) }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Color indicator bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color)
        )
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
                newText.toFloatOrNull()?.let { newValue ->
                    if (newValue >= 0f) onValueChange(newValue)
                }
            },
            label = { Text(label, fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        )
    }
}
