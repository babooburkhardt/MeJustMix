package com.example.mejustmix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun VolumeSelection(
    mixViewModel: MixViewModel,
    settingsViewModel: SettingsViewModel
) {
    val totalVolume by mixViewModel.totalVolume.collectAsState()

    var showCustomDialog by remember { mutableStateOf(false) }
    var customVolume by rememberSaveable { mutableStateOf<Float?>(null) }

    if (showCustomDialog) {
        CustomVolumeDialog(
            mixViewModel = mixViewModel,
            onDismissRequest = { showCustomDialog = false },
            onConfirm = { vol ->
                customVolume = vol
                mixViewModel.setTotalVolume(vol)
                showCustomDialog = false
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val buttonPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
        val commonModifier = Modifier
            .weight(1f)
            .height(64.dp)
        
        val buttonShape = RoundedCornerShape(12.dp)

        val items = listOf(
            "Small" to 3f,
            "Medium" to 10f,
            "Large" to 25f
        )

        items.forEach { (label, volume) ->
            val isSelected = totalVolume == volume
            val isEnabled = mixViewModel.isMixPossible(volume)

            val content: @Composable () -> Unit = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label, 
                        fontWeight = FontWeight.Black, 
                        style = MaterialTheme.typography.titleMedium, 
                        maxLines = 1
                    )
                    Text(
                        text = volume.toMlString(0), 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall, 
                        maxLines = 1
                    )
                }
            }

            if (isSelected) {
                Button(
                    onClick = { mixViewModel.setTotalVolume(volume) },
                    shape = buttonShape,
                    contentPadding = buttonPadding,
                    modifier = commonModifier,
                    enabled = isEnabled
                ) { content() }
            } else {
                OutlinedButton(
                    onClick = { mixViewModel.setTotalVolume(volume) },
                    shape = buttonShape,
                    contentPadding = buttonPadding,
                    modifier = commonModifier,
                    enabled = isEnabled
                ) { content() }
            }
        }

        // --- Custom Button ---
        val currentCustom = customVolume
        val isCustomSelected = currentCustom != null && totalVolume == currentCustom
        val isCustomAmountPossible = currentCustom?.let { mixViewModel.isMixPossible(it) } ?: true

        val customContent: @Composable () -> Unit = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Custom", 
                    fontWeight = FontWeight.Black, 
                    style = MaterialTheme.typography.titleMedium, 
                    maxLines = 1
                )
                if (currentCustom != null) {
                    Text(
                        text = currentCustom.toMlString(1), 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall, 
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Set", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall, 
                        maxLines = 1
                    )
                }
            }
        }

        if (isCustomSelected) {
            Button(
                onClick = { showCustomDialog = true },
                shape = buttonShape,
                contentPadding = buttonPadding,
                modifier = commonModifier,
                enabled = isCustomAmountPossible
            ) { customContent() }
        } else {
            OutlinedButton(
                onClick = { showCustomDialog = true },
                shape = buttonShape,
                contentPadding = buttonPadding,
                modifier = commonModifier,
                enabled = true
            ) { customContent() }
        }
    }
}
