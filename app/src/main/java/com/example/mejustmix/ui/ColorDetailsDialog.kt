package com.example.mejustmix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mejustmix.data.SavedColor
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ColorDetailsDialog(
    savedColor: SavedColor,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var newName by remember { mutableStateOf(savedColor.name) }
    val hexCode = "#${savedColor.color.toArgb().toUInt().toString(16).substring(2).uppercase()}"
    val formattedDate = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault()).format(Date(savedColor.createdAt))

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(savedColor.color, shape = MaterialTheme.shapes.medium)
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Color Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Hex: $hexCode", style = MaterialTheme.typography.bodyMedium)
                Text("Created: $formattedDate", style = MaterialTheme.typography.bodyMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onDelete()
                        onDismissRequest()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Color", tint = MaterialTheme.colorScheme.error)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         TextButton(onClick = onDismissRequest) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            onRename(newName)
                            onDismissRequest()
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
