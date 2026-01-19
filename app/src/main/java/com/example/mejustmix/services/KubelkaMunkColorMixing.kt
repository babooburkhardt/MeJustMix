package com.example.mejustmix.services

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.mejustmix.data.PaintMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Kubelka-Munk inspired color mixing with Two-Constant optimization.
 * * UPDATED: 
 * 1. Fixed 'pow' compilation error (2 -> 2f)
 * 2. Added Gamma Correction (linearToSrgb) so colors aren't dark/muddy
 */
object KubelkaMunkColorMixing {

    // ========================================================================
    // CORE K-M FORMULAS
    // ========================================================================
    
    fun reflectanceToKS(reflectance: Float): Float {
        val R = reflectance.coerceIn(0.01f, 0.99f)
        // FIX: .pow(2) -> .pow(2f) because Kotlin requires a Float exponent
        return (1f - R).pow(2f) / (2f * R)
    }
    
    fun ksToReflectance(ks: Float): Float {
        val safeKS = ks.coerceIn(0f, 50f)
        return (1f + safeKS - sqrt(safeKS * safeKS + 2f * safeKS)).coerceIn(0f, 1f)
    }
    
    // ========================================================================
    // RGB <-> K/S CONVERSION
    // ========================================================================
    
    fun rgbToKS(colorInt: Int): KSColor {
        // We convert sRGB (screen) to Linear RGB before doing physics math
        val r = srgbToLinear(Color.red(colorInt) / 255f).coerceIn(0.01f, 0.99f)
        val g = srgbToLinear(Color.green(colorInt) / 255f).coerceIn(0.01f, 0.99f)
        val b = srgbToLinear(Color.blue(colorInt) / 255f).coerceIn(0.01f, 0.99f)
        
        return KSColor(
            ksR = reflectanceToKS(r),
            ksG = reflectanceToKS(g),
            ksB = reflectanceToKS(b),
            s = 1.0f 
        )
    }
    
    fun ksToRGB(ks: KSColor): Int {
        // 1. Get Linear Reflectance (Physics world)
        val rLinear = ksToReflectance(ks.ksR)
        val gLinear = ksToReflectance(ks.ksG)
        val bLinear = ksToReflectance(ks.ksB)
        
        // 2. Convert to sRGB (Screen world)
        // This was missing! Without this, 50% gray looks like 20% gray.
        val r = linearToSrgb(rLinear)
        val g = linearToSrgb(gLinear)
        val b = linearToSrgb(bLinear)

        return Color.rgb(
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }

    // ========================================================================
    // GAMMA CORRECTION HELPERS (CRITICAL FOR ACCURACY)
    // ========================================================================

    private fun linearToSrgb(linear: Float): Float {
        val x = linear.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) {
            12.92f * x
        } else {
            1.055f * x.pow(1f / 2.4f) - 0.055f
        }
    }

    private fun srgbToLinear(srgb: Float): Float {
        val x = srgb.coerceIn(0f, 1f)
        return if (x <= 0.04045f) {
            x / 12.92f
        } else {
            ((x + 0.055f) / 1.055f).pow(2.4f)
        }
    }
    
    // ========================================================================
    // CALIBRATION SOLVER
    // ========================================================================
    
    fun solveScattering(
        pureKS: KSColor,
        mixKS: KSColor,
        sWhite: Float = 1.0f
    ): Float {
        // Calculate for each channel
        val sR = solveSingleChannel(pureKS.ksR, mixKS.ksR, sWhite)
        val sG = solveSingleChannel(pureKS.ksG, mixKS.ksG, sWhite)
        val sB = solveSingleChannel(pureKS.ksB, mixKS.ksB, sWhite)
        
        // Weight by which channel has the strongest pigment signal
        val wR = pureKS.ksR
        val wG = pureKS.ksG
        val wB = pureKS.ksB
        val totalWeight = wR + wG + wB
        
        if (totalWeight < 0.001f) return 1.0f
        
        val weightedS = (sR * wR + sG * wG + sB * wB) / totalWeight
        return weightedS.coerceIn(0.1f, 5.0f)
    }
    
    private fun solveSingleChannel(ksPure: Float, ksMix: Float, sWhite: Float): Float {
        val denominator = ksPure - ksMix
        if (denominator <= 0.01f) return 3.0f // Very opaque
        
        val result = (ksMix * sWhite) / denominator
        return result.coerceIn(0.1f, 5.0f)
    }

    // ========================================================================
    // PAINT MIX CALCULATION
    // ========================================================================
    
    fun calculateMixRatios(
        targetColorInt: Int,
        pigmentDatabase: KSPigmentDatabase
    ): PaintMix {
        val targetLab = DoubleArray(3)
        ColorUtils.colorToLAB(targetColorInt, targetLab)
        
        var bestMix = calculateInitialGuess(targetColorInt)
        var bestError = calculateLabError(bestMix, targetLab, pigmentDatabase)
        
        var stepSize = 0.1f
        val minStep = 0.002f
        var noImprovementCount = 0
        
        repeat(150) {
            var improved = false
            
            val adjustments = listOf(
                { m: PaintMix, d: Float -> m.copy(cyan = (m.cyan + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(magenta = (m.magenta + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(yellow = (m.yellow + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(black = (m.black + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(white = (m.white + d).coerceIn(0f, 1f)) }
            )
            
            for (adjust in adjustments) {
                // Try increase
                val testPos = normalize(adjust(bestMix, stepSize))
                val errorPos = calculateLabError(testPos, targetLab, pigmentDatabase)
                if (errorPos < bestError - 0.01f) {
                    bestMix = testPos
                    bestError = errorPos
                    improved = true
                }
                
                // Try decrease
                val testNeg = normalize(adjust(bestMix, -stepSize))
                val errorNeg = calculateLabError(testNeg, targetLab, pigmentDatabase)
                if (errorNeg < bestError - 0.01f) {
                    bestMix = testNeg
                    bestError = errorNeg
                    improved = true
                }
            }
            
            if (!improved) {
                noImprovementCount++
                if (noImprovementCount >= 3) {
                    stepSize *= 0.6f
                    noImprovementCount = 0
                    if (stepSize < minStep) return@repeat
                }
            } else {
                noImprovementCount = 0
            }
        }
        
        return bestMix
    }
    
    private fun calculateInitialGuess(targetColorInt: Int): PaintMix {
        // Use sRGB to Linear for better physics guess
        val r = srgbToLinear(Color.red(targetColorInt) / 255f)
        val g = srgbToLinear(Color.green(targetColorInt) / 255f)
        val b = srgbToLinear(Color.blue(targetColorInt) / 255f)
        
        val c = 1f - r
        val m = 1f - g
        val y = 1f - b
        
        val maxRGB = max(r, max(g, b))
        val minRGB = min(r, min(g, b))
        val lightness = (maxRGB + minRGB) / 2f
        
        val darkness = 1f - maxRGB
        val k = if (darkness > 0.1f && lightness < 0.4f) {
            darkness * 0.5f
        } else {
            0f
        }
        
        val w = when {
            lightness > 0.7f -> (lightness - 0.5f) * 0.8f
            minRGB > 0.3f -> minRGB * 0.3f
            else -> 0f
        }
        
        val saturation = if (maxRGB > 0) (maxRGB - minRGB) / maxRGB else 0f
        val pigmentScale = 0.3f + saturation * 0.5f
        
        return normalize(PaintMix(
            cyan = c * pigmentScale,
            magenta = m * pigmentScale,
            yellow = y * pigmentScale,
            black = k,
            white = w
        ))
    }
    
    private fun calculateLabError(
        mix: PaintMix,
        targetLab: DoubleArray,
        database: KSPigmentDatabase
    ): Float {
        val mixedKS = calculateMixedKS(mix, database)
        val mixedRGB = ksToRGB(mixedKS)
        
        val mixedLab = DoubleArray(3)
        ColorUtils.colorToLAB(mixedRGB, mixedLab)
        
        val dL = (targetLab[0] - mixedLab[0]).toFloat()
        val dA = (targetLab[1] - mixedLab[1]).toFloat()
        val dB = (targetLab[2] - mixedLab[2]).toFloat()
        
        return sqrt(dL * dL + dA * dA + dB * dB)
    }
    
    fun calculateMixedKS(mix: PaintMix, database: KSPigmentDatabase): KSColor {
        val pigments = listOf(database.cyan, database.magenta, database.yellow, database.black, database.white)
        val concentrations = listOf(mix.cyan, mix.magenta, mix.yellow, mix.black, mix.white)
        
        var totalS = 0f
        var sumK_R = 0f
        var sumK_G = 0f
        var sumK_B = 0f
        
        pigments.forEachIndexed { i, pigment ->
            val c = concentrations[i]
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
    
    fun mixPigments(pigments: List<KSColor>, concentrations: List<Float>): KSColor {
        var totalS = 0f
        var sumK_R = 0f
        var sumK_G = 0f
        var sumK_B = 0f
        
        pigments.forEachIndexed { i, pigment ->
            val c = concentrations[i].coerceAtLeast(0f)
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
    
    private fun normalize(mix: PaintMix): PaintMix {
        val total = mix.cyan + mix.magenta + mix.yellow + mix.black + mix.white
        
        return when {
            total < 0.001f -> PaintMix(0f, 0f, 0f, 0f, 1f)
            else -> PaintMix(
                cyan = mix.cyan / total,
                magenta = mix.magenta / total,
                yellow = mix.yellow / total,
                black = mix.black / total,
                white = mix.white / total
            )
        }
    }
    
    // ========================================================================
    // DEFAULT PIGMENT DATABASE
    // ========================================================================
    
    fun createDefaultPigmentDatabase(): KSPigmentDatabase {
        return KSPigmentDatabase(
            cyan = KSColor(ksR = 5.0f, ksG = 0.3f, ksB = 0.1f, s = 1.0f),
            magenta = KSColor(ksR = 0.1f, ksG = 4.0f, ksB = 0.8f, s = 1.0f),
            yellow = KSColor(ksR = 0.02f, ksG = 0.5f, ksB = 4.0f, s = 1.0f),
            black = KSColor(ksR = 20.0f, ksG = 20.0f, ksB = 20.0f, s = 1.0f),
            white = KSColor(ksR = 0.01f, ksG = 0.01f, ksB = 0.01f, s = 2.0f)
        )
    }
    
    fun scalePigmentDatabase(
        database: KSPigmentDatabase,
        strengths: PigmentStrengths
    ): KSPigmentDatabase {
        return KSPigmentDatabase(
            cyan = database.cyan.scaleKS(strengths.cyan),
            magenta = database.magenta.scaleKS(strengths.magenta),
            yellow = database.yellow.scaleKS(strengths.yellow),
            black = database.black.scaleKS(strengths.black),
            white = database.white.scaleKS(strengths.white)
        )
    }
    
    fun previewMixedColor(mix: PaintMix, database: KSPigmentDatabase): Int {
        val mixedKS = calculateMixedKS(mix, database)
        return ksToRGB(mixedKS)
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

data class KSColor(
    val ksR: Float,
    val ksG: Float,
    val ksB: Float,
    val s: Float = 1.0f
) {
    fun scaleKS(factor: Float): KSColor = KSColor(
        ksR = ksR * factor,
        ksG = ksG * factor,
        ksB = ksB * factor,
        s = s 
    )
    
    fun scale(factor: Float): KSColor = KSColor(
        ksR = ksR * factor,
        ksG = ksG * factor,
        ksB = ksB * factor,
        s = s * factor
    )
}

data class KSPigmentDatabase(
    val cyan: KSColor,
    val magenta: KSColor,
    val yellow: KSColor,
    val black: KSColor,
    val white: KSColor
)
