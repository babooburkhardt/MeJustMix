package com.example.mejustmix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mejustmix.ui.components.BlurManagedDialog
import com.example.mejustmix.ui.components.BlurManagedAlertDialog

@Composable
fun EditColorDialog(
    mixViewModel: MixViewModel,
    colorName: String,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(colorName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        BlurManagedAlertDialog(
            mixViewModel = mixViewModel,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Color?") },
            text = { Text("Are you sure you want to delete this color? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismissRequest()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BlurManagedDialog(
        mixViewModel = mixViewModel,
        onDismissRequest = onDismissRequest
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Edit Color",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Color Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onRename(name)
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }

                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun EditPhotoDialog(
    mixViewModel: MixViewModel,
    photoName: String,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(photoName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        BlurManagedAlertDialog(
            mixViewModel = mixViewModel,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Photo?") },
            text = { Text("Are you sure you want to delete this photo? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismissRequest()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BlurManagedDialog(
        mixViewModel = mixViewModel,
        onDismissRequest = onDismissRequest
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Edit Photo",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Photo Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onRename(name)
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }

                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun EditFolderDialog(
    mixViewModel: MixViewModel,
    folderName: String,
    isPhotoFolder: Boolean = false,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(folderName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val itemType = if (isPhotoFolder) "Photo Folder" else "Color Folder"

    if (showDeleteConfirm) {
        BlurManagedAlertDialog(
            mixViewModel = mixViewModel,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $itemType?") },
            text = { Text("Are you sure you want to delete this folder and all its contents? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismissRequest()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BlurManagedDialog(
        mixViewModel = mixViewModel,
        onDismissRequest = onDismissRequest
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Edit $itemType",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onRename(name)
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }

                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
