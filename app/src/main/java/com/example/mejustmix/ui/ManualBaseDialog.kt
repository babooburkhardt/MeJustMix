package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mejustmix.ui.components.BlurManagedDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBaseDialog(
    mixViewModel: MixViewModel,
    totalVolume: Float,
    onDismissRequest: () -> Unit,
    onConfirm: (String, Float, Boolean) -> Unit // UPDATED: Now expects 3 parameters
) {
    var baseName by remember { mutableStateOf("Clear Base") }
    var transparency by remember { mutableStateOf(0f) } // 0.0 to 0.95
    var includeWhite by remember { mutableStateOf(true) }

    BlurManagedDialog(
        mixViewModel = mixViewModel,
        onDismissRequest = onDismissRequest
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Manual Base Mix", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Create glazes or use custom mediums.", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- BASE TYPE SELECTOR ---
                Text("Base Type", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Clear", "Matte", "Gloss").forEach { name ->
                        FilterChip(
                            selected = baseName.contains(name),
                            onClick = { baseName = "$name Base" },
                            label = { Text(name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- TRANSPARENCY SLIDER ---
                val paintVol = totalVolume * (1f - transparency)
                val baseVol = totalVolume * transparency

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ratio", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${(transparency * 100).toInt()}% Base / ${(100 - (transparency * 100)).toInt()}% Paint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = transparency,
                    onValueChange = { transparency = it },
                    valueRange = 0f..0.95f,
                    steps = 19
                )
                
                // Visual Volume Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Machine: ${String.format("%.1f", paintVol)}ml", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("You Add: ${String.format("%.1f", baseVol)}ml", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- INCLUDE WHITE TOGGLE ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use White Ink?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (includeWhite) "Machine will mix White + Colors." 
                            else "Machine will dispense pure transparent dyes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = includeWhite,
                        onCheckedChange = { includeWhite = it }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- ACTIONS ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Button(onClick = { 
                        // Passing all 3 values back to VisualizerCard
                        onConfirm(baseName, transparency, includeWhite) 
                    }) {
                        Text("Set Manual Mix")
                    }
                }
            }
        }
    }
}
