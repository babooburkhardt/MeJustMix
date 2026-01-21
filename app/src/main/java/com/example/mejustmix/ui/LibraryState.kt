package com.example.mejustmix.ui

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Represents a single color saved by the user.
 * @param id A unique identifier for the color.
 * @param name The user-defined name of the color.
 * @param color The actual color value.
 * @param createdAt The timestamp when the color was saved.
 */
data class SavedColor(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val color: Color,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Represents a folder containing saved colors.
 * @param id A unique identifier for the folder.
 * @param name The user-defined name of the folder.
 * @param colors A list of colors contained within this folder.
 * @param isExpanded Whether the folder is currently expanded in the UI.
 */
data class ColorFolder(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val colors: List<SavedColor> = emptyList(),
    val isExpanded: Boolean = true
)
