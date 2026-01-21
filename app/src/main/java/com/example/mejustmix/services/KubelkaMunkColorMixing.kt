package com.example.mejustmix.services

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.mejustmix.data.PaintMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * [DEPRECATED FACADE]
 * This object now delegates to:
 * - ColorPhysicsEngine (Math)
 * - SpectralMath (Sensor Conversions)
 * - KSPigmentRepository (Data)
 */
object KubelkaMunkColorMixing {

    // ========================================================================
    // DELEGATES
    // ========================================================================
    
    fun reflectanceToKS(reflectance: Float): Float = ColorPhysicsEngine.reflectanceToKS(reflectance)
    
    fun ksToReflectance(ks: Float): Float = ColorPhysicsEngine.ksToReflectance(ks)
    
    fun rgbToKS(colorInt: Int): KSColor {
        val r = ColorPhysicsEngine.srgbToLinear(Color.red(colorInt) / 255f).coerceIn(0.01f, 0.99f)
        val g = ColorPhysicsEngine.srgbToLinear(Color.green(colorInt) / 255f).coerceIn(0.01f, 0.99f)
        val b = ColorPhysicsEngine.srgbToLinear(Color.blue(colorInt) / 255f).coerceIn(0.01f, 0.99f)
        
        return KSColor(
            ksR = ColorPhysicsEngine.reflectanceToKS(r),
            ksG = ColorPhysicsEngine.reflectanceToKS(g),
            ksB = ColorPhysicsEngine.reflectanceToKS(b),
            s = 1.0f 
        )
    }
    
    fun ksToRGB(ks: KSColor): Int {
        val rLinear = ColorPhysicsEngine.ksToReflectance(ks.ksR)
        val gLinear = ColorPhysicsEngine.ksToReflectance(ks.ksG)
        val bLinear = ColorPhysicsEngine.ksToReflectance(ks.ksB)
        
        val r = ColorPhysicsEngine.linearToSrgb(rLinear)
        val g = ColorPhysicsEngine.linearToSrgb(gLinear)
        val b = ColorPhysicsEngine.linearToSrgb(bLinear)

        return Color.rgb(
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }

    // ========================================================================
    // CALIBRATION SOLVER
    // ========================================================================
    
    fun solveScattering(pureKS: KSColor, mixKS: KSColor, sWhite: Float = 1.0f): Float {
        return ColorPhysicsEngine.solveScattering(pureKS, mixKS, sWhite)
    }

    // ========================================================================
    // PAINT MIX CALCULATION
    // ========================================================================
    
    // NOTE: This complex logic still lives here for now, but uses the new Engine
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
        val r = ColorPhysicsEngine.srgbToLinear(Color.red(targetColorInt) / 255f)
        val g = ColorPhysicsEngine.srgbToLinear(Color.green(targetColorInt) / 255f)
        val b = ColorPhysicsEngine.srgbToLinear(Color.blue(targetColorInt) / 255f)
        
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
        return ColorPhysicsEngine.mixPigments(pigments, concentrations)
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
    // DELEGATED REPOSITORY CALLS
    // ========================================================================
    
    fun createDefaultPigmentDatabase(): KSPigmentDatabase = KSPigmentRepository.createDefaultPigmentDatabase()
    
    fun scalePigmentDatabase(db: KSPigmentDatabase, s: PigmentStrengths) = KSPigmentRepository.scalePigmentDatabase(db, s)
    
    fun previewMixedColor(mix: PaintMix, database: KSPigmentDatabase): Int {
        val mixedKS = calculateMixedKS(mix, database)
        return ksToRGB(mixedKS)
    }

    // ========================================================================
    // DELEGATED SPECTRAL CALLS
    // ========================================================================
    
    fun calculateKSFromSpectral(sample: List<Float>, whiteRef: List<Float>): KSColor? {
        return SpectralMath.calculateKSFromSpectral(sample, whiteRef)
    }
    
    fun calculateRGBFromSpectral(sample: List<Float>, whiteRef: List<Float>): Int {
        return SpectralMath.calculateRGBFromSpectral(sample, whiteRef)
    }
}


