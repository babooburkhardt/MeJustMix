package com.example.mejustmix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun ColorWheel(mixViewModel: MixViewModel, modifier: Modifier = Modifier) {
    val currentColor by mixViewModel.color.collectAsState()

    CircularColorPicker(
        color = currentColor,
        onColorChanged = { color ->
            mixViewModel.setColor(color)
        },
        modifier = modifier
    )
}
