package com.example.mejustmix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Header(
    mixViewModel: MixViewModel,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onExportClick: () -> Unit
) {
    val fluidNCStatus by mixViewModel.fluidNCStatus.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MeJustMix",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp
            )
            
            // Only show green wifi when actually connected (Idle, Run, Hold, etc.)
            // Show red for: null, Disconnected, Error, Connecting, Reconnecting
            val isConnected = fluidNCStatus?.state?.let { state ->
                state != "Disconnected" && 
                state != "Error" && 
                state != "Connecting..." && 
                state != "Reconnecting"
            } ?: false
            
            Icon(
                imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = if (isConnected) "Online" else "Offline",
                tint = if (isConnected) Color.Green else Color.Red,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row {
            IconButton(onClick = onUndoClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedoClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onExportClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Export Recipe")
            }
            IconButton(onClick = onHistoryClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.History, contentDescription = "History")
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }
}