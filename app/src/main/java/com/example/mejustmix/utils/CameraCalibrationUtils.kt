package com.example.mejustmix.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Valid targets for calibration.
 */
enum class CalibrationTarget(val label: String, val isMasstone: Boolean, val pigmentName: String) {
    WHITE_REF("White Reference", false, "White"),
    
    CYAN_MASSTONE("Cyan Masstone", true, "Cyan"),
    CYAN_TINT("Cyan Tint (50/50)", false, "Cyan"),
    
    MAGENTA_MASSTONE("Magenta Masstone", true, "Magenta"),
    MAGENTA_TINT("Magenta Tint (50/50)", false, "Magenta"),
    
    YELLOW_MASSTONE("Yellow Masstone", true, "Yellow"),
    YELLOW_TINT("Yellow Tint (50/50)", false, "Yellow"),
    
    BLACK_MASSTONE("Black Masstone", true, "Black"),
    BLACK_TINT("Black Tint (50/50)", false, "Black");

    fun next(): CalibrationTarget {
        val nextOrdinal = (this.ordinal + 1) % values().size
        return values()[nextOrdinal]
    }
}

object CameraCalibrationUtils {

    /**
     * Sample average color from a rectangular region of the bitmap.
     * Used for manual "loupe" sampling.
     */
    fun sampleAverageColor(bitmap: Bitmap, sampleRect: Rect): Int {
        // Clamp rect to image bounds
        val safeLeft = sampleRect.left.coerceIn(0, bitmap.width - 1)
        val safeTop = sampleRect.top.coerceIn(0, bitmap.height - 1)
        val safeRight = sampleRect.right.coerceIn(safeLeft + 1, bitmap.width)
        val safeBottom = sampleRect.bottom.coerceIn(safeTop + 1, bitmap.height)
        
        val width = safeRight - safeLeft
        val height = safeBottom - safeTop
        
        if (width <= 0 || height <= 0) return Color.TRANSPARENT
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, safeLeft, safeTop, width, height)
        
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        
        for (pixel in pixels) {
            rSum += Color.red(pixel)
            gSum += Color.green(pixel)
            bSum += Color.blue(pixel)
            count++
        }
        
        if (count == 0) return Color.TRANSPARENT
        
        return Color.rgb(
            (rSum / count).toInt(),
            (gSum / count).toInt(),
            (bSum / count).toInt()
        )
    }
}
