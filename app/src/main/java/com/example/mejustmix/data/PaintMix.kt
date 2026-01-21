package com.example.mejustmix.data

import androidx.compose.ui.graphics.Color

data class PaintMix(
    val cyan: Float,
    val magenta: Float,
    val yellow: Float,
    val black: Float,
    val white: Float
)

// Moved here so it can be shared between ViewModel and UI
data class HistoryItem(
    val color: Color,
    val paintMix: PaintMix, 
    val timestamp: Long = System.currentTimeMillis()
)