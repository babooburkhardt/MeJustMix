package com.example.mejustmix.services

import android.graphics.Color

/**
 * Math for converting raw spectral sensor data into usable Color objects.
 */
object SpectralMath {

    /**
     * Converts raw 18-channel spectral data into a K/S Color.
     * Use this when you want to use the physics engine to mix this color.
     */
    fun calculateKSFromSpectral(sample: List<Float>, whiteRef: List<Float>): KSColor? {
        if (sample.size != 18 && sample.size != 10) return null
        if (whiteRef.size != sample.size) return null
        
        // 1. Calculate Reflectance R = Sample / White
        val reflectance = sample.zip(whiteRef) { s, w -> 
            if (w == 0f) 0.01f else (s / w).coerceIn(0.01f, 0.99f) 
        }
        
        // 2. Calculate K/S = (1-R)^2 / 2R
        val ks = reflectance.map { r -> ColorPhysicsEngine.reflectanceToKS(r) }
        
        // 3. Map Channels to RGB
        val ksRed: Float
        val ksGreen: Float
        val ksBlue: Float
        
        if (sample.size == 10) {
            // AS7341 Mapping (10 Channels)
            // F1: 415nm, F2: 445nm, F3: 480nm, F4: 515nm
            // F5: 555nm, F6: 590nm, F7: 630nm, F8: 680nm
            
            // Red: F7 (630nm) matches sRGB Red (~612nm) well
            ksRed = ks[6]
            
            // Green: F5 (555nm) is peak human sensitivity
            ksGreen = ks[4]
            
            // Blue: F2 (445nm) matches sRGB Blue (~435-450nm) well
            ksBlue = ks[1]
        } else {
            // AS7265x Mapping (18 Channels) - Legacy
            // Red ~ 610nm -> Index 8 (I)
            // Green ~ 560nm -> Index 6 (G)
            // Blue ~ 460nm -> Index 14 (C)
            ksRed = ks[8]
            ksGreen = ks[6]
            ksBlue = ks[14]
        }
        
        return KSColor(ksRed, ksGreen, ksBlue, 1.0f)
    }
    
    /**
     * Converts raw 18-channel spectral data directly to an RGB Int.
     * Use this when you just want to Show the color on screen.
     */
    fun calculateRGBFromSpectral(sample: List<Float>, whiteRef: List<Float>): Int {
        val ksColor = calculateKSFromSpectral(sample, whiteRef) ?: return Color.GRAY
        
        // Convert K/S back to Reflectance, then to sRGB (Gamma Corrected)
        val rLinear = ColorPhysicsEngine.ksToReflectance(ksColor.ksR)
        val gLinear = ColorPhysicsEngine.ksToReflectance(ksColor.ksG)
        val bLinear = ColorPhysicsEngine.ksToReflectance(ksColor.ksB)
        
        val r = ColorPhysicsEngine.linearToSrgb(rLinear)
        val g = ColorPhysicsEngine.linearToSrgb(gLinear)
        val b = ColorPhysicsEngine.linearToSrgb(bLinear)

        return Color.rgb(
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }
}
