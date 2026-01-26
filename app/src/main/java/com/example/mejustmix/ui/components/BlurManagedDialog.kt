package com.example.mejustmix.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mejustmix.ui.MixViewModel

/**
 * A wrapper around standard Dialog that automatically tracks its presence
 * in the MixViewModel to trigger app-wide background blur.
 */
@Composable
fun BlurManagedDialog(
    mixViewModel: MixViewModel,
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    DisposableEffect(Unit) {
        mixViewModel.incrementModalCount()
        onDispose {
            mixViewModel.decrementModalCount()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        content = content
    )
}

/**
 * A wrapper around standard AlertDialog that automatically tracks its presence
 * in the MixViewModel to trigger app-wide background blur.
 */
@Composable
fun BlurManagedAlertDialog(
    mixViewModel: MixViewModel,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.AlertDialogDefaults.shape,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.AlertDialogDefaults.containerColor,
    iconContentColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.AlertDialogDefaults.iconContentColor,
    titleContentColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.AlertDialogDefaults.titleContentColor,
    textContentColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.AlertDialogDefaults.textContentColor,
    tonalElevation: androidx.compose.ui.unit.Dp = androidx.compose.material3.AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    DisposableEffect(Unit) {
        mixViewModel.incrementModalCount()
        onDispose {
            mixViewModel.decrementModalCount()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}
