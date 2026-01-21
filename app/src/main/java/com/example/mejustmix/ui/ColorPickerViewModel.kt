package com.example.mejustmix.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ColorPickerViewModel : ViewModel() {

    private val _selectedColor = MutableStateFlow(Color.White)
    val selectedColor = _selectedColor.asStateFlow()

    private val _imageUris = MutableStateFlow<List<Uri>>(emptyList())
    val imageUris = _imageUris.asStateFlow()

    private val _activeImageUri = MutableStateFlow<Uri?>(null)
    val activeImageUri = _activeImageUri.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap = _bitmap.asStateFlow()

    fun addImage(context: Context, uri: Uri) {
        _imageUris.update { it + uri }
        setActiveImage(context, uri)
    }

    fun removeImage(context: Context, uri: Uri) {
        _imageUris.update { it - uri }
        if (_activeImageUri.value == uri) {
            if (_imageUris.value.isNotEmpty()) {
                setActiveImage(context, _imageUris.value.first())
            } else {
                _activeImageUri.value = null
                _bitmap.value = null
            }
        }
    }

    fun setActiveImage(context: Context, uri: Uri) {
        _activeImageUri.value = uri
        loadBitmap(context, uri)
    }

    private fun loadBitmap(context: Context, uri: Uri) {
        viewModelScope.launch {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .target { _bitmap.value = it.toBitmap() }
                .build()
            context.imageLoader.execute(request)
        }
    }

    /**
     * Corrected sampling using RMS (Root Mean Square) for Gamma accuracy.
     */
    fun sampleColor(x: Float, y: Float) {
        _bitmap.value?.let {
            val xCoord = x.toInt().coerceIn(0, it.width - 1)
            val yCoord = y.toInt().coerceIn(0, it.height - 1)

            // Use Long or Double to prevent overflow during squaring
            var sumSqRed = 0.0
            var sumSqGreen = 0.0
            var sumSqBlue = 0.0
            var count = 0

            for (i in -5..5) {
                for (j in -5..5) {
                    val currentX = (xCoord + i).coerceIn(0, it.width - 1)
                    val currentY = (yCoord + j).coerceIn(0, it.height - 1)
                    
                    val pixel = it.getPixel(currentX, currentY)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)

                    // Square the values
                    sumSqRed += r * r
                    sumSqGreen += g * g
                    sumSqBlue += b * b
                    count++
                }
            }

            // Average the squares, then square root
            val avgR = sqrt(sumSqRed / count).toInt()
            val avgG = sqrt(sumSqGreen / count).toInt()
            val avgB = sqrt(sumSqBlue / count).toInt()

            _selectedColor.value = Color(avgR, avgG, avgB)
        }
    }
}