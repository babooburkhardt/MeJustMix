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
        val minStep = 0.0005f // High precision
        var noImprovementCount = 0
        
        // 1. "Zero-Tolerance" White Check (Multi-Point Initialization)
        // actively scans for the "sweet spot" of white pigment before starting the main loop.
        // We test 0.5%, 1%, 2.5%, and 5% white to see if any of them beat the "pure" guess.
        if (bestMix.white < 0.005f) {
            val whiteCandidates = listOf(0.005f, 0.01f, 0.025f, 0.05f)
            for (w in whiteCandidates) {
                val testMix = normalize(bestMix.copy(white = w))
                val testError = calculateLabError(testMix, targetLab, pigmentDatabase)
                if (testError < bestError) {
                    bestMix = testMix
                    bestError = testError
                }
            }
        }
        
        // 2. Main Optimization Loop with Micro-Stepping
        repeat(300) { 
            var improved = false
            
            // White gets a "Micro-Step" (1/20th) for extreme sensitivity
            val microStep = stepSize * 0.05f
            
            val adjustments = listOf(
                { m: PaintMix, d: Float -> m.copy(cyan = (m.cyan + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(magenta = (m.magenta + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(yellow = (m.yellow + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(black = (m.black + d).coerceIn(0f, 1f)) },
                { m: PaintMix, d: Float -> m.copy(white = (m.white + d).coerceIn(0f, 1f)) }
            )
            
            for ((index, adjust) in adjustments.withIndex()) {
                val isWhite = index == 4
                
                // Standard Step
                val testPos = normalize(adjust(bestMix, stepSize))
                val errorPos = calculateLabError(testPos, targetLab, pigmentDatabase)
                
                // MICRO-STEPPING (White Only)
                if (isWhite && errorPos >= bestError) {
                    val testMicro = normalize(adjust(bestMix, microStep))
                    val errorMicro = calculateLabError(testMicro, targetLab, pigmentDatabase)
                    if (errorMicro < bestError - 0.0001f) { // Lower threshold
                        bestMix = testMicro
                        bestError = errorMicro
                        improved = true
                        continue 
                    }
                } else if (errorPos < bestError - 0.0001f) {
                    bestMix = testPos
                    bestError = errorPos
                    improved = true
                }
                
                // Standard Negative Step
                val testNeg = normalize(adjust(bestMix, -stepSize))
                val errorNeg = calculateLabError(testNeg, targetLab, pigmentDatabase)
                
                if (isWhite && errorNeg >= bestError) {
                    val testMicroNeg = normalize(adjust(bestMix, -microStep))
                    val errorMicroNeg = calculateLabError(testMicroNeg, targetLab, pigmentDatabase)
                    if (errorMicroNeg < bestError - 0.0001f) {
                        bestMix = testMicroNeg
                        bestError = errorMicroNeg
                        improved = true
                        continue
                    }
                } else if (errorNeg < bestError - 0.0001f) {
                    bestMix = testNeg
                    bestError = errorNeg
                    improved = true
                }
            }
            
            if (!improved) {
                noImprovementCount++
                if (noImprovementCount >= 3) {
                    stepSize *= 0.5f // Gentler decay
                    noImprovementCount = 0
                    if (stepSize < minStep) return@repeat
                }
            } else {
                noImprovementCount = 0
            }
        }
        
        // 3. Last-Mile "White Polish"
        // Dedicated pass to wiggle White by tiny amounts (0.0005) to shave off the last bit of Delta E
        // This ensures we never say "legit unnecessary" unless it TRULY is.
        var polishStep = 0.002f
        repeat(50) {
            val wUp = normalize(bestMix.copy(white = (bestMix.white + polishStep).coerceIn(0f, 1f)))
            val errUp = calculateLabError(wUp, targetLab, pigmentDatabase)
            
            if (errUp < bestError) {
                bestMix = wUp
                bestError = errUp
            } else {
                val wDown = normalize(bestMix.copy(white = (bestMix.white - polishStep).coerceIn(0f, 1f)))
                val errDown = calculateLabError(wDown, targetLab, pigmentDatabase)
                if (errDown < bestError) {
                    bestMix = wDown
                    bestError = errDown
                } else {
                    polishStep *= 0.5f
                    if (polishStep < 0.0001f) return@repeat
                }
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
        
        val darkness = 1f - maxRGB // Estimation of black content
        val k = if (darkness > 0.1f && lightness < 0.4f) {
            darkness * 0.5f
        } else {
            0f
        }
        
        // 3. Improved White Guess Logic
        // Old: lightness > 0.7f (Too strict, missed mid-tones)
        // New: lightness > 0.4f OR significantly desaturated colors (high minRGB)
        // "Tad light" colors often fall in the 0.5-0.7 range.
        val w = when {
            lightness > 0.4f -> (lightness - 0.3f) * 0.8f // Aggressively suggest white for anything not dark
            minRGB > 0.2f -> minRGB * 0.5f // If the darkest channel is lit, we probably need white
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


