package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.ui.theme.getBrightness

@Composable
fun FlowCalibratorDialog(
    initialPumpIndex: Int,
    mixViewModel: MixViewModel,
    settingsViewModel: SettingsViewModel,
    lockedToPump: Boolean = false,
    onDismissRequest: () -> Unit
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    var selectedPumpIndex by rememberSaveable { mutableStateOf(initialPumpIndex) }

    // Calibration State
    var testAmountStr by rememberSaveable { mutableStateOf("10") }
    var actualAmountStr by rememberSaveable { mutableStateOf("") }

    val currentPump = uiState.pumps.getOrNull(selectedPumpIndex) ?: return
    val currentCal = currentPump.calibration.toFloatOrNull() ?: 100f
    val pumpColor = Color(currentPump.colorArgb)

    // Calculate New Calibration
    val actualAmount = actualAmountStr.toFloatOrNull()
    val testAmount = testAmountStr.toFloatOrNull()

    val newCalibration = if (actualAmount != null && actualAmount > 0 && testAmount != null) {
        (testAmount / actualAmount) * currentCal
    } else {
        null
    }

    // Help Dialog State
    var showHelp by remember { mutableStateOf(false) }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Flow Rate Calibration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Place a clean measuring cup under the nozzle.")
                    Text("2. Enter a Target amount (e.g. 10ml) and click Dispense.")
                    Text("3. Wait for the dispense to finish completely.")
                    Text("4. Measure the EXACT volume of liquid in the cup.")
                    Text("5. Enter that value in 'Actual Amount'.")
                    Text("6. Click Save to update the pumps steps/ml.")
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
        val configuration = LocalConfiguration.current
        val isTablet = configuration.smallestScreenWidthDp >= 600
        val cardModifier = if (isTablet) {
            Modifier.width(500.dp)
        } else {
            Modifier.fillMaxWidth(0.95f)
        }

        Card(
            modifier = cardModifier.padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    val title = if (lockedToPump) "Calibrate ${currentPump.name} Flow" else "Calibrate Flow Rate"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
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

                // 1. Pump Tabs (Hidden if locked)
                if (!lockedToPump) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedPumpIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = pumpColor,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedPumpIndex]),
                                color = pumpColor
                            )
                        }
                    ) {
                        uiState.pumps.forEachIndexed { index, pump ->
                            Tab(
                                selected = selectedPumpIndex == index,
                                onClick = {
                                    selectedPumpIndex = index
                                    actualAmountStr = ""
                                },
                                text = {
                                    Text(
                                        pump.name,
                                        color = if (selectedPumpIndex == index) Color(pump.colorArgb) else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selectedPumpIndex == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    // Small divider if no tabs
                    HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp))
                }

                // 2. Step 1: Dispense
                Text("Step 1: Dispense", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Place a cup under the ${currentPump.name} nozzle.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = testAmountStr,
                        onValueChange = { testAmountStr = it },
                        label = { Text("Target (ml)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = testAmountStr.toFloatOrNull() ?: 0f
                            if (amount > 0) {
                                mixViewModel.primePump(currentPump.axis, amount)
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = pumpColor)
                    ) {
                        val textColor = if (pumpColor.getBrightness() > 0.5f) Color.Black else Color.White
                        Text("DISPENSE", color = textColor)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                // 3. Step 2: Measure
                Text("Step 2: Measure", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Enter the exact volume collected.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = actualAmountStr,
                    onValueChange = { actualAmountStr = it },
                    label = { Text("Actual Amount (ml)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // 4. Results Preview
                if (newCalibration != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Current Steps/mL", style = MaterialTheme.typography.labelSmall)
                                Text(String.format("%.2f", currentCal), style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("New Steps/mL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(String.format("%.2f", newCalibration), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Close") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newCalibration != null) {
                                settingsViewModel.updatePumpCalibration(currentPump.axis, String.format("%.2f", newCalibration))
                                actualAmountStr = ""
                            }
                        },
                        enabled = newCalibration != null
                    ) {
                        Text("Save Calibration")
                    }
                }
            }
        }
    }
}
