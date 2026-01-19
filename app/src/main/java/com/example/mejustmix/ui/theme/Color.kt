package com.example.mejustmix.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val Indigo = Color(0xFF3F51B5)
val SlateGray = Color(0xFF708090)
val LightCoolGray = Color(0xFFf0f2f5)

/**
 * Extension function to calculate the perceived brightness of a color.
 * Returns a value between 0.0 (black) and 1.0 (white).
 * Useful for determining if text on top of this color should be dark or light.
 */
fun Color.getBrightness(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
