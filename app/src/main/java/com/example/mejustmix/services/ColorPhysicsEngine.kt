package com.example.mejustmix.services

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.mejustmix.data.PaintMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure Math Engine for Color Physics using Kubelka-Munk (K-M) theory.
 * 
 * ## Overview
 * 
 * This engine handles the mathematical core of subtractive color mixing,
 * which is fundamentally different from additive (RGB) mixing used in displays.
 * When paints mix, they absorb light (subtractive), so mixing cyan and yellow
 * produces green, not white as in additive RGB.
 * 
 * ## Kubelka-Munk Theory
 * 
 * K-M theory models how light interacts with pigmented layers:
 * 
 * - **K (Absorption Coefficient)**: How much light the pigment absorbs
 * - **S (Scattering Coefficient)**: How much light the pigment scatters back
 * - **K/S ratio**: The key parameter that can be calculated from reflectance
 * 
 * The fundamental K-M equation relates reflectance R to K/S:
 * ```
 * K/S = (1 - R)² / (2R)
 * ```
 * 
 * And the inverse:
 * ```
 * R = 1 + K/S - √((K/S)² + 2(K/S))
 * ```
 * 
 * ## Why This Matters for Paint Mixing
 * 
 * RGB mixing assumes light is additive. But paint mixing is subtractive:
 * - RGB: Red + Green = Yellow (light adds up)
 * - Paint: Red + Green = Brown (pigments absorb each other's reflected light)
 * 
 * K-M theory correctly models this by:
 * 1. Converting RGB colors to K/S values (light absorption/scattering ratios)
 * 2. Mixing K/S values linearly (which IS physically additive for absorption)
 * 3. Converting the mixed K/S back to RGB for display
 * 
 * ## Implementation Details
 * 
 * This implementation uses a 3-channel (RGB) approximation of K-M theory,
 * which provides excellent results for typical paint colors. For research-grade
 * accuracy, see [SpectralKubelkaMunk] which uses full 31-wavelength spectra.
 * 
 * Key features:
 * - sRGB ↔ Linear conversion for correct gamma handling
 * - Special handling for white pigment's dominant scattering behavior
 * - Calibration support for determining pigment scattering coefficients
 * 
 * @see KubelkaMunkColorMixing for the high-level mixing API
 * @see SpectralKubelkaMunk for 31-wavelength spectral implementation
 * @see KSColor for the K/S color representation
 */
object ColorPhysicsEngine {
    
    // ========================================================================
    // TUNABLE CONSTANTS
    // ========================================================================
    
    /**
     * White Scattering Boost Factor.
     * 
     * In real Kubelka-Munk theory, white pigments (like TiO2) have:
     * - Very low K (absorption coefficient) ≈ 0.001-0.01
     * - Very HIGH S (scattering coefficient) ≈ 10-50x chromatic pigments
     * 
     * When calibrating via hex codes (masstone/tint method), we only capture
     * the K/S RATIO, not the individual K and S values. This means white's
     * true scattering power is lost.
     * 
     * This boost factor compensates by making white pigment contribute more
     * to the scattering sum during mixing, which properly dilutes the K/S
     * of chromatic pigments and achieves correct lightening.
     * 
     * Tuning guidelines:
     * - Too low (< 5): Pastels will be too dark, not enough white
     * - Too high (> 15): Colors will wash out, white overpowers
     * - Sweet spot for typical dye paints: 6-10
     * 
     * If using spectrometer calibration with true K and S values,
     * set this to 1.0 and let the measured S values handle it.
     */
    var whiteScatteringBoost: Float = 8.0f

    // ========================================================================
    // CORE K-M FORMULAS
    // ========================================================================
    
    /**
     * Converts linear reflectance to K/S ratio using the Kubelka-Munk equation.
     * 
     * The K-M equation: `K/S = (1 - R)² / (2R)`
     * 
     * This converts how much light bounces back (reflectance) into the ratio
     * of absorption to scattering, which is the fundamental quantity for mixing.
     * 
     * @param reflectance Linear reflectance value (0.0 to 1.0, typically from sRGB→Linear conversion)
     * @return K/S ratio. Higher values = more absorbing (darker). 0 = perfect white.
     * 
     * @throws IllegalArgumentException if reflectance is outside 0.01-0.99 (clamped internally)
     * 
     * ## Example
     * ```kotlin
     * // Pure white (R=1.0) → K/S ≈ 0 (no absorption)
     * reflectanceToKS(1.0f) // ≈ 0.0
     * 
     * // Pure black (R=0.0) → K/S ≈ ∞ (total absorption)
     * reflectanceToKS(0.01f) // ≈ 49.0
     * 
     * // Mid-gray (R=0.5) → K/S = 0.25
     * reflectanceToKS(0.5f) // = 0.25
     * ```
     */
    fun reflectanceToKS(reflectance: Float): Float {
        val R = reflectance.coerceIn(0.01f, 0.99f)
        return (1f - R).pow(2f) / (2f * R)
    }
    
    /**
     * Converts K/S ratio back to linear reflectance using the inverse K-M equation.
     * 
     * The inverse equation: `R = 1 + K/S - √((K/S)² + 2(K/S))`
     * 
     * This converts the mixed K/S ratio back to a displayable reflectance value.
     * 
     * @param ks K/S ratio (0 to ∞, clamped to 50 for numerical stability)
     * @return Linear reflectance (0.0 to 1.0), ready for Linear→sRGB conversion
     * 
     * ## Example
     * ```kotlin
     * // K/S = 0 (pure white) → R = 1.0
     * ksToReflectance(0f) // = 1.0
     * 
     * // K/S = 0.25 → R = 0.5 (mid-gray)
     * ksToReflectance(0.25f) // = 0.5
     * 
     * // K/S = 10 (very dark) → R ≈ 0.08
     * ksToReflectance(10f) // ≈ 0.08
     * ```
     */
    fun ksToReflectance(ks: Float): Float {
        val safeKS = ks.coerceIn(0f, 50f)
        return (1f + safeKS - sqrt(safeKS * safeKS + 2f * safeKS)).coerceIn(0f, 1f)
    }

    // ========================================================================
    // GAMMA CORRECTION HELPERS
    // ========================================================================

    fun linearToSrgb(linear: Float): Float {
        val x = linear.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) {
            12.92f * x
        } else {
            1.055f * x.pow(1f / 2.4f) - 0.055f
        }
    }

    fun srgbToLinear(srgb: Float): Float {
        val x = srgb.coerceIn(0f, 1f)
        return if (x <= 0.04045f) {
            x / 12.92f
        } else {
            ((x + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    // ========================================================================
    // MIXING ALGORITHMS
    // ========================================================================

    /**
     * Optical Mixing: Additive mixing of K and S coefficients.
     * 
     * IMPROVED: White pigment is now handled specially to account for its
     * dominant scattering behavior. In K-M theory, white lightens by contributing
     * high S (scattering) with low K (absorption), which dilutes the K/S ratio
     * of the mixture more than standard pigments.
     * 
     * The white scattering boost factor compensates for the fact that hex-based
     * calibration underestimates white's true lightening power.
     * 
     * returns: The resulting K/S color of the mix.
     */
    fun mixPigments(pigments: List<KSColor>, concentrations: List<Float>): KSColor {
        var totalS = 0f
        var sumK_R = 0f
        var sumK_G = 0f
        var sumK_B = 0f
        
        pigments.forEachIndexed { i, pigment ->
            val c = concentrations.getOrElse(i) { 0f }.coerceAtLeast(0f)
            val S = pigment.s
            
            // Detect white pigment: very low K/S across all channels
            val isWhitePigment = pigment.ksR < 0.05f && pigment.ksG < 0.05f && pigment.ksB < 0.05f
            
            if (isWhitePigment) {
                // White's scattering contribution is boosted to match real-world behavior.
                // Real TiO2 has S values 10-50x higher than chromatic pigments.
                // This boost factor compensates for hex-calibration limitations.
                // See whiteScatteringBoost documentation for tuning guidelines.
                val effectiveS = S * whiteScatteringBoost
                
                totalS += c * effectiveS
                sumK_R += c * pigment.ksR * effectiveS
                sumK_G += c * pigment.ksG * effectiveS
                sumK_B += c * pigment.ksB * effectiveS
            } else {
                // Chromatic pigments: use calibrated K/S values directly
                totalS += c * S
                sumK_R += c * pigment.ksR * S
                sumK_G += c * pigment.ksG * S
                sumK_B += c * pigment.ksB * S
            }
        }

        if (totalS < 0.0001f) return KSColor(0f, 0f, 0f, 1f)

        return KSColor(
            ksR = sumK_R / totalS,
            ksG = sumK_G / totalS,
            ksB = sumK_B / totalS,
            s = totalS
        )
    }
    
    // ========================================================================
    // CALIBRATION SOLVER (Finding S)
    // ========================================================================
    
    fun solveScattering(pureKS: KSColor, mixKS: KSColor, sWhite: Float = 1.0f): Float {
        val sR = solveSingleChannel(pureKS.ksR, mixKS.ksR, sWhite)
        val sG = solveSingleChannel(pureKS.ksG, mixKS.ksG, sWhite)
        val sB = solveSingleChannel(pureKS.ksB, mixKS.ksB, sWhite)
        
        // Weight by signal strength (absorption)
        val wR = pureKS.ksR
        val wG = pureKS.ksG
        val wB = pureKS.ksB
        val totalWeight = wR + wG + wB
        
        if (totalWeight < 0.001f) return 1.0f
        
        val weightedS = (sR * wR + sG * wG + sB * wB) / totalWeight
        return weightedS.coerceIn(0.1f, 5.0f)
    }
    
    private fun solveSingleChannel(ksPure: Float, ksMix: Float, sWhite: Float): Float {
        // Based on K-M Mix Law: (K/S)_mix = [c_p * (K/S)_p * S_p + c_w * (K/S)_w * S_w] / [c_p * S_p + c_w * S_w]
        // This function reverses that to find S_p.
        val denominator = ksPure - ksMix
        if (denominator <= 0.01f) return 3.0f // Fallback for opaque/similar colors
        
        val result = (ksMix * sWhite) / denominator
        return result.coerceIn(0.1f, 5.0f)
    }
}
