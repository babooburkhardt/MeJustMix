package com.example.mejustmix.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class SortOption {
    DATE_DESC, // Newest first
    NAME_ASC,  // A-Z
    HUE        // Rainbow (Colors only)
}

@Serializable
data class SavedColor(
    val id: String,
    @Serializable(with = ColorSerializer::class)
    val color: Color,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ColorFolder(
    val id: String,
    val name: String,
    val colors: List<SavedColor>,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOption: SortOption = SortOption.DATE_DESC // Added field
)

// Photo library models
@Serializable
data class SavedPhoto(
    val id: String,
    val uriString: String,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class PhotoFolder(
    val id: String,
    val name: String,
    val photos: List<SavedPhoto>,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOption: SortOption = SortOption.DATE_DESC // Added field
)