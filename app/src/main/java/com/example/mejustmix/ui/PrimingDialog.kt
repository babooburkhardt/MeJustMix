package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimingDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var volumeText by remember { mutableStateOf("5.0") }
    var sliderValue by remember { mutableStateOf(5f) }
    
    // Sync slider to text field
    LaunchedEffect(volumeText) {
        volumeText.toFloatOrNull()?.let { value ->
            if (value in 0.1f..50f) {
                sliderValue = value
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Prime Pump") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Dispense fluid to prime the pump and remove air bubbles.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Divider()
                
                // Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume:", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${String.format("%.1f", sliderValue)} mL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Slider(
                        value = sliderValue,
                        onValueChange = { 
                            sliderValue = it
                            volumeText = String.format("%.1f", it)
                        },
                        valueRange = 0.1f..50f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.1 mL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("50 mL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                // Manual input field
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { 
                        volumeText = it
                        it.toFloatOrNull()?.let { value ->
                            if (value in 0.1f..50f) {
                                sliderValue = value
                            }
                        }
                    },
                    label = { Text("Volume (mL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2f, 5f, 10f, 20f).forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                sliderValue = preset
                                volumeText = String.format("%.1f", preset)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${preset.toInt()}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val volume = volumeText.toFloatOrNull() ?: 5f
                    onConfirm(volume.coerceIn(0.1f, 50f))
                }
            ) {
                Text("Prime")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
