package com.example.mejustmix.services

import android.graphics.Color
import com.example.mejustmix.data.PaintMix
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * OPTIONAL: Full 31-wavelength spectral implementation with CIE color matching.
 * 
 * Use this if you need higher accuracy or have actual spectrophotometer data.
 * For scanner-based calibration, KubelkaMunkColorMixingCorrected.kt (3-channel) is simpler and sufficient.
 * 
 * This implementation includes:
 * - Proper CIE 1931 color matching functions
 * - D65 illuminant weighting
 * - 31 wavelengths (400-700nm @ 10nm)
 */
object SpectralKubelkaMunk {
    
    // Standard wavelengths: 400-700nm in 10nm steps (31 values)
    val WAVELENGTHS = (400..700 step 10).toList()
    
    // ========================================================================
    // CIE 1931 2° STANDARD OBSERVER COLOR MATCHING FUNCTIONS
    // ========================================================================
    
    // These are the official CIE 1931 x̄(λ), ȳ(λ), z̄(λ) values
    // at 10nm intervals from 400-700nm
    // Source: CIE 15:2004
    
    private val CIE_X = floatArrayOf(
        0.01431f, 0.04351f, 0.13438f, 0.28390f, 0.34828f, // 400-440
        0.33620f, 0.29080f, 0.19536f, 0.09564f, 0.03201f, // 450-490
        0.00490f, 0.00930f, 0.06327f, 0.16550f, 0.29040f, // 500-540
        0.43345f, 0.59450f, 0.76210f, 0.91630f, 1.02630f, // 550-590
        1.06220f, 1.00260f, 0.85445f, 0.64240f, 0.44790f, // 600-640
        0.28350f, 0.16490f, 0.08740f, 0.04677f, 0.02270f, // 650-690
        0.01135f                                           // 700
    )
    
    private val CIE_Y = floatArrayOf(
        0.00040f, 0.00120f, 0.00400f, 0.01160f, 0.02300f, // 400-440
        0.03800f, 0.06000f, 0.09100f, 0.13902f, 0.20802f, // 450-490
        0.32300f, 0.50300f, 0.71000f, 0.86200f, 0.95400f, // 500-540
        0.99495f, 0.99500f, 0.95200f, 0.87000f, 0.75700f, // 550-590
        0.63100f, 0.50300f, 0.38100f, 0.26500f, 0.17500f, // 600-640
        0.10700f, 0.06100f, 0.03200f, 0.01700f, 0.00821f, // 650-690
        0.00410f                                           // 700
    )
    
    private val CIE_Z = floatArrayOf(
        0.06785f, 0.20740f, 0.64560f, 1.38560f, 1.74706f, // 400-440
        1.77211f, 1.66920f, 1.28764f, 0.81295f, 0.46518f, // 450-490
        0.27200f, 0.15820f, 0.07825f, 0.04216f, 0.02030f, // 500-540
        0.00875f, 0.00390f, 0.00210f, 0.00165f, 0.00110f, // 550-590
        0.00080f, 0.00034f, 0.00019f, 0.00005f, 0.00002f, // 600-640
        0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, // 650-690
        0.00000f                                           // 700
    )
    
    // D65 Illuminant relative spectral power distribution (normalized)
    // at 10nm intervals from 400-700nm
    private val D65_SPD = floatArrayOf(
        82.75f, 91.49f, 93.43f, 86.68f, 104.86f,   // 400-440
        117.01f, 117.81f, 114.86f, 115.92f, 108.81f, // 450-490
        109.35f, 107.80f, 104.79f, 107.69f, 104.41f, // 500-540
        104.05f, 100.00f, 96.33f, 95.79f, 88.69f,   // 550-590
        90.01f, 89.60f, 87.70f, 83.29f, 83.70f,    // 600-640
        80.03f, 80.21f, 82.28f, 78.28f, 69.72f,    // 650-690
        71.61f                                       // 700
    )
    
    // ========================================================================
    // SPECTRAL ↔ XYZ ↔ RGB CONVERSION
    // ========================================================================
    
    /**
     * Convert spectral reflectance to CIE XYZ using color matching functions.
     */
    fun spectralToXYZ(reflectance: List<Float>): Triple<Float, Float, Float> {
        require(reflectance.size == 31) { "Reflectance must have 31 values" }
        
        var x = 0f
        var y = 0f
        var z = 0f
        var norm = 0f
        
        for (i in 0 until 31) {
            val illuminated = reflectance[i] * D65_SPD[i]
            x += illuminated * CIE_X[i]
            y += illuminated * CIE_Y[i]
            z += illuminated * CIE_Z[i]
            norm += D65_SPD[i] * CIE_Y[i]
        }
        
        // Normalize so perfect white (R=1.0 everywhere) gives Y=1.0
        if (norm > 0) {
            x /= norm
            y /= norm
            z /= norm
        }
        
        return Triple(x, y, z)
    }
    
    /**
     * Convert CIE XYZ to sRGB.
     * Uses the standard sRGB matrix and gamma correction.
     */
    fun xyzToRGB(xyz: Triple<Float, Float, Float>): Int {
        val (x, y, z) = xyz
        
        // XYZ to linear RGB (sRGB primaries, D65 white point)
        var r = 3.2406f * x - 1.5372f * y - 0.4986f * z
        var g = -0.9689f * x + 1.8758f * y + 0.0415f * z
        var b = 0.0557f * x - 0.2040f * y + 1.0570f * z
        
        // Apply sRGB gamma correction
        fun gammaCorrect(linear: Float): Float {
            return if (linear <= 0.0031308f) {
                12.92f * linear
            } else {
                1.055f * linear.pow(1f / 2.4f) - 0.055f
            }
        }
        
        r = gammaCorrect(r.coerceIn(0f, 1f))
        g = gammaCorrect(g.coerceIn(0f, 1f))
        b = gammaCorrect(b.coerceIn(0f, 1f))
        
        return Color.rgb(
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }
    
    /**
     * Convert RGB to approximate spectral reflectance.
     * 
     * Note: This is an approximation. True RGB→Spectral is not uniquely defined.
     * Uses the spectral.js approach of combining 7 primary reflectance curves.
     */
    fun rgbToSpectralReflectance(colorInt: Int): List<Float> {
        val r = Color.red(colorInt) / 255f
        val g = Color.green(colorInt) / 255f
        val b = Color.blue(colorInt) / 255f
        
        // Simplified approach: interpolate between primary reflectance curves
        // based on RGB values
        return WAVELENGTHS.mapIndexed { i, wavelength ->
            approximateReflectanceAtWavelength(wavelength, r, g, b)
        }
    }
    
    /**
     * Approximate reflectance at a wavelength from RGB.
     * Uses smooth gaussian-like weighting for each primary.
     */
    private fun approximateReflectanceAtWavelength(
        wavelength: Int,
        r: Float,
        g: Float,
        b: Float
    ): Float {
        // Gaussian-like response curves for RGB
        // Blue peaks around 450nm, Green around 540nm, Red around 600nm
        
        fun gaussian(x: Float, mean: Float, sigma: Float): Float {
            return kotlin.math.exp(-((x - mean).pow(2)) / (2f * sigma.pow(2)))
        }
        
        val wl = wavelength.toFloat()
        
        // Weight each primary by its spectral response
        val blueWeight = gaussian(wl, 450f, 40f)
        val greenWeight = gaussian(wl, 540f, 50f)
        val redWeight = gaussian(wl, 600f, 50f)
        
        // Combine primaries
        val totalWeight = blueWeight + greenWeight + redWeight + 0.001f
        val reflectance = (b * blueWeight + g * greenWeight + r * redWeight) / totalWeight
        
        return reflectance.coerceIn(0f, 1f)
    }
    
    // ========================================================================
    // SPECTRAL K/S MIXING
    // ========================================================================
    
    /**
     * Mix spectral K/S values.
     * K/S at each wavelength is additive based on concentration.
     */
    fun mixSpectralKS(
        pigments: List<List<Float>>,  // K/S values for each pigment at each wavelength
        concentrations: List<Float>
    ): List<Float> {
        require(pigments.all { it.size == 31 }) { "All pigments must have 31 K/S values" }
        require(pigments.size == concentrations.size) { "Pigments and concentrations must match" }
        
        return (0 until 31).map { wavelengthIndex ->
            var ksSum = 0f
            pigments.forEachIndexed { pigmentIndex, pigmentKS ->
                ksSum += concentrations[pigmentIndex] * pigmentKS[wavelengthIndex]
            }
            ksSum
        }
    }
    
    /**
     * Convert K/S spectrum to reflectance spectrum.
     */
    fun ksSpectrumToReflectance(ksSpectrum: List<Float>): List<Float> {
        return ksSpectrum.map { ks ->
            val safeKS = ks.coerceAtLeast(0f)
            (1f + safeKS - sqrt(safeKS * safeKS + 2f * safeKS)).coerceIn(0f, 1f)
        }
    }
    
    /**
     * Convert reflectance spectrum to K/S spectrum.
     */
    fun reflectanceToKSSpectrum(reflectance: List<Float>): List<Float> {
        return reflectance.map { r ->
            val safeR = r.coerceIn(0.001f, 0.999f)
            (1f - safeR).pow(2) / (2f * safeR)
        }
    }
    
    /**
     * Full spectral mixing pipeline:
     * 1. Mix K/S spectra
     * 2. Convert to reflectance
     * 3. Convert to XYZ
     * 4. Convert to RGB
     */
    fun mixAndConvertToRGB(
        pigmentKS: List<List<Float>>,
        concentrations: List<Float>
    ): Int {
        val mixedKS = mixSpectralKS(pigmentKS, concentrations)
        val reflectance = ksSpectrumToReflectance(mixedKS)
        val xyz = spectralToXYZ(reflectance)
        return xyzToRGB(xyz)
    }
    
    // ========================================================================
    // SPECTRAL PIGMENT DATABASE
    // ========================================================================
    
    /**
     * Create default spectral K/S values for CMYKW pigments.
     * These are approximations based on typical artist pigments.
     */
    fun createDefaultSpectralDatabase(): SpectralPigmentDatabase {
        return SpectralPigmentDatabase(
            cyan = createCyanKS(),
            magenta = createMagentaKS(),
            yellow = createYellowKS(),
            black = createBlackKS(),
            white = createWhiteKS()
        )
    }
    
    private fun createCyanKS(): List<Float> {
        // Cyan absorbs red (high K/S at long wavelengths)
        return WAVELENGTHS.map { wl ->
            when {
                wl < 500 -> 0.2f + (500 - wl) / 500f * 0.3f  // Low in blue
                wl < 550 -> 0.5f + (wl - 500) / 50f * 2f     // Rising
                else -> 2.5f + (wl - 550) / 150f * 6f        // High in red
            }
        }
    }
    
    private fun createMagentaKS(): List<Float> {
        // Magenta absorbs green (high K/S in middle wavelengths)
        return WAVELENGTHS.map { wl ->
            when {
                wl < 480 -> 0.5f                              // Low in blue
                wl < 600 -> 0.5f + (wl - 480) / 120f * 5f    // High in green
                else -> 5.5f - (wl - 600) / 100f * 4f        // Dropping in red
            }
        }
    }
    
    private fun createYellowKS(): List<Float> {
        // Yellow absorbs blue (high K/S at short wavelengths)
        return WAVELENGTHS.map { wl ->
            when {
                wl < 500 -> 8f - (wl - 400) / 100f * 6f      // High in blue
                wl < 560 -> 2f - (wl - 500) / 60f * 1.5f    // Dropping
                else -> 0.5f                                  // Low in red/green
            }
        }
    }
    
    private fun createBlackKS(): List<Float> {
        // Black absorbs all wavelengths uniformly
        return WAVELENGTHS.map { 20f }
    }
    
    private fun createWhiteKS(): List<Float> {
        // White has minimal absorption at all wavelengths
        return WAVELENGTHS.map { 0.02f }
    }
}

/**
 * Spectral pigment database with K/S values at each of 31 wavelengths.
 */
data class SpectralPigmentDatabase(
    val cyan: List<Float>,    // 31 K/S values
    val magenta: List<Float>,
    val yellow: List<Float>,
    val black: List<Float>,
    val white: List<Float>
)
