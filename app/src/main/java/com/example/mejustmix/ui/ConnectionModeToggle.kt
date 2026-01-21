package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mejustmix.data.ConnectionType

/**
 * Simple toggle to switch between BLE and WiFi modes.
 * Alternative to per-machine connection types.
 */
@Composable
fun ConnectionModeToggle(
    currentMode: ConnectionType,
    onModeChanged: (ConnectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Connection Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // BLE Button
                FilterChip(
                    selected = currentMode == ConnectionType.BLE,
                    onClick = { onModeChanged(ConnectionType.BLE) },
                    label = { Text("Bluetooth") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                
                // WiFi Button
                FilterChip(
                    selected = currentMode == ConnectionType.WIFI,
                    onClick = { onModeChanged(ConnectionType.WIFI) },
                    label = { Text("WiFi") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Show current connection info
            Text(
                text = when (currentMode) {
                    ConnectionType.BLE -> "Using Bluetooth connection"
                    ConnectionType.WIFI -> "Using WiFi connection"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Connection mode section with explanation.
 */
@Composable
fun ConnectionModeSection(
    currentMode: ConnectionType,
    bleDeviceName: String?,
    wifiIpAddress: String?,
    onModeChanged: (ConnectionType) -> Unit,
    onConfigureBLE: () -> Unit,
    onConfigureWiFi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Connection",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Mode toggle
        ConnectionModeToggle(
            currentMode = currentMode,
            onModeChanged = onModeChanged
        )
        
        // Configuration card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (currentMode) {
                    ConnectionType.BLE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Bluetooth Device",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    bleDeviceName ?: "Not configured",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onConfigureBLE) {
                                Text(if (bleDeviceName == null) "Setup" else "Change")
                            }
                        }
                    }
                    ConnectionType.WIFI -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "IP Address",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    wifiIpAddress ?: "Not configured",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onConfigureWiFi) {
                                Text(if (wifiIpAddress == null) "Setup" else "Change")
                            }
                        }
                    }
                }
            }
        }
    }
}
