package com.example.mejustmix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mejustmix.data.ColorFolder
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FolderDetailsDialog(
    folder: ColorFolder,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var newName by remember { mutableStateOf(folder.name) }
    val formattedDate = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault()).format(Date(folder.createdAt))

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
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )

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
                        Icon(Icons.Default.Delete, contentDescription = "Delete Folder", tint = MaterialTheme.colorScheme.error)
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
