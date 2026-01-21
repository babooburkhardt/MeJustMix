package com.example.mejustmix.services

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.mejustmix.data.PaintMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure Math Engine for Color Physics.
 * Handles K/S Conversions, Gamma Correction, and Optical Mixing.
 */
object ColorPhysicsEngine {

    // ========================================================================
    // CORE K-M FORMULAS
    // ========================================================================
    
    fun reflectanceToKS(reflectance: Float): Float {
        val R = reflectance.coerceIn(0.01f, 0.99f)
        return (1f - R).pow(2f) / (2f * R)
    }
    
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
            
            totalS += c * S
            sumK_R += c * pigment.ksR * S
            sumK_G += c * pigment.ksG * S
            sumK_B += c * pigment.ksB * S
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
