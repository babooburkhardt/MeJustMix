package com.example.mejustmix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefillDialog(
    pumpName: String,
    currentLevel: Float,
    onDismissRequest: () -> Unit,
    onConfirm: (Float, Boolean) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var isAdding by rememberSaveable { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Update $pumpName Level", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Current Level: ${String.format("%.1f", currentLevel)} ml", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = isAdding,
                        onClick = { isAdding = true },
                        label = { Text("Add / Top Off") },
                        leadingIcon = { if (isAdding) Icon(Icons.Filled.Add, null) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !isAdding,
                        onClick = { isAdding = false },
                        label = { Text("Replace Bottle") },
                        leadingIcon = { if (!isAdding) Icon(Icons.Filled.Refresh, null) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (isAdding) "Amount Added" else "New Bottle Size") },
                    suffix = { Text("ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val amount = amountText.toFloatOrNull() ?: 0f
                        if (amount > 0) onConfirm(amount, isAdding)
                    }) {
                        Text("Update Level")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpSettingsGroup(
    pumpIndex: Int,
    pump: PumpConfig, // CHANGED from PumpSettings to PumpConfig
    availableAxes: List<String>,
    viewModel: SettingsViewModel,
    onPrimeClick: () -> Unit,
    onRefillClick: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val pumpColor = Color(pump.colorArgb) // Convert Int to Color

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(24.dp)) { drawCircle(color = pumpColor) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(pump.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    OutlinedButton(
                        onClick = { isDropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Axis ${pump.axis}")
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = isDropdownExpanded, onDismissRequest = { isDropdownExpanded = false }) {
                        availableAxes.forEach { axis ->
                            DropdownMenuItem(text = { Text("Axis $axis") }, onClick = {
                                viewModel.onPumpAxisChanged(pumpIndex, axis)
                                isDropdownExpanded = false
                            })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pump.calibration,
                    onValueChange = { viewModel.onPumpCalibrationChanged(pumpIndex, it) },
                    label = { Text("Cal") },
                    trailingIcon = { Text("step/ml", modifier = Modifier.padding(end = 8.dp)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = onPrimeClick) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Text("Prime")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${String.format("%.0f", pump.currentVolumeMl)}ml", 
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(40.dp)
                )
                LinearProgressIndicator(
                    progress = (pump.currentVolumeMl / pump.maxVolumeMl).coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = pumpColor,
                    trackColor = Color.LightGray.copy(alpha = 0.3f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRefillClick) { Text("Refill") }
            }
        }
    }
}