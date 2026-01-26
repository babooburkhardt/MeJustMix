package com.example.mejustmix.services

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.mejustmix.data.PaintMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-level API for Kubelka-Munk color mixing calculations.
 * 
 * ## What This Does
 * 
 * This object provides the main entry point for calculating paint mix ratios
 * that will produce a target color. It uses Kubelka-Munk (K-M) theory to model
 * how pigments actually mix in the real world (subtractive mixing), which is
 * fundamentally different from how colors mix on screens (additive RGB).
 * 
 * ## Why Not Just RGB?
 * 
 * If you mix red and green paint in equal parts, you get brown - not yellow
 * like RGB would predict. K-M theory correctly models this because:
 * 
 * - RGB assumes light adds up (screens emit light)
 * - Paint absorbs light (pigments subtract wavelengths)
 * - K-M models the physics of light absorption and scattering
 * 
 * ## Key Features
 * 
 * 1. **Accurate Earth Tones**: Browns, grays, and muted colors mix correctly
 * 2. **White Handling**: Properly models how white pigment lightens colors
 * 3. **Calibration Support**: Can be tuned for specific pigments via hex codes
 * 4. **Optimization**: Iteratively finds the best CMYKW ratios for any target
 * 
 * ## Architecture
 * 
 * This facade delegates to specialized components:
 * - [ColorPhysicsEngine]: Core K-M math (K/S conversions, gamma, mixing)
 * - [SpectralMath]: Spectral sensor data processing
 * - [KSPigmentRepository]: Pigment database and calibration
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * // Get the default pigment database
 * val database = KubelkaMunkColorMixing.createDefaultPigmentDatabase()
 * 
 * // Calculate mix ratios for coral pink (#FF7F7F)
 * val targetColor = Color.parseColor("#FF7F7F")
 * val mix = KubelkaMunkColorMixing.calculateMixRatios(targetColor, database)
 * 
 * // Result: PaintMix(cyan=0.0, magenta=0.25, yellow=0.15, black=0.0, white=0.60)
 * // The mix correctly includes ~60% white to achieve the pastel tone!
 * 
 * // Preview what the mixed color will look like
 * val preview = KubelkaMunkColorMixing.previewMixedColor(mix, database)
 * ```
 * 
 * ## Calibration Workflow
 * 
 * For best results, calibrate your specific pigments:
 * 
 * 1. Paint a swatch of each pure pigment (masstone)
 * 2. Use a color picker or scanner to get the hex code
 * 3. Call [rgbToKS] to convert to K/S values
 * 4. Store in a [KSPigmentDatabase]
 * 
 * For tinted calibration (more accurate):
 * 1. Mix each pigment 50/50 with white
 * 2. Scan both masstone and tint
 * 3. Use [solveScattering] to determine the scattering coefficient
 * 
 * @see ColorPhysicsEngine for the underlying K-M math
 * @see SpectralKubelkaMunk for 31-wavelength spectral implementation
 * @see PaintMix for the output mix ratio format
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
        // Actively scans for the "sweet spot" of white pigment before starting the main loop.
        // IMPROVED: Expanded range to catch pastels that need 40-80% white.
        // Also runs even if initial guess has some white, to escape local minima.
        
        // Detect if this target likely needs white:
        // - L* > 50 means mid-tone or lighter
        // - OR high RGB values (bright saturated colors) even if LAB L* is low
        //   (because saturated reds have low L* but still need white to achieve brightness)
        val r = Color.red(targetColorInt) / 255f
        val g = Color.green(targetColorInt) / 255f  
        val b = Color.blue(targetColorInt) / 255f
        val maxRgb = maxOf(r, g, b)
        val isBrightSaturated = maxRgb > 0.85f // Very bright primary channel
        
        val isLightTarget = targetLab[0] > 50 || isBrightSaturated
        
        // Wider candidate range for light/bright colors
        val whiteCandidates = if (isLightTarget) {
            listOf(0.05f, 0.1f, 0.15f, 0.2f, 0.25f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
        } else {
            listOf(0.005f, 0.01f, 0.025f, 0.05f, 0.1f)
        }
        
        for (w in whiteCandidates) {
            // Scale down chromatic pigments proportionally when testing more white
            val chromaticScale = 1f - w * 0.5f // Reduce chromatics as white increases
            val testMix = normalize(PaintMix(
                cyan = bestMix.cyan * chromaticScale,
                magenta = bestMix.magenta * chromaticScale,
                yellow = bestMix.yellow * chromaticScale,
                black = bestMix.black * chromaticScale,
                white = w
            ))
            val testError = calculateLabError(testMix, targetLab, pigmentDatabase)
            if (testError < bestError) {
                bestMix = testMix
                bestError = testError
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
        
        // Also get LAB for perceptual lightness
        val targetLab = DoubleArray(3)
        ColorUtils.colorToLAB(targetColorInt, targetLab)
        val labLightness = targetLab[0].toFloat() / 100f // Normalize to 0-1
        
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
        
        // IMPROVED White Guess: Use LAB L* for perceptually accurate lightness assessment
        // Real-world pastels typically need 50-80% white by volume.
        // The key insight: LAB L* > 70 means the color WILL need significant white.
        val saturation = if (maxRGB > 0) (maxRGB - minRGB) / maxRGB else 0f
        
        val w = when {
            // Very light colors (pastels): L* > 80 → aggressive white (60-85%)
            labLightness > 0.80f -> 0.6f + (labLightness - 0.8f) * 1.25f
            
            // Light colors: L* 65-80 → moderate white (35-60%)
            labLightness > 0.65f -> 0.35f + (labLightness - 0.65f) * 1.67f
            
            // Mid-tones: L* 50-65 → some white needed (15-35%), especially if desaturated
            labLightness > 0.50f -> {
                val baseWhite = 0.15f + (labLightness - 0.5f) * 1.33f
                // Desaturated colors need more white
                val desatBoost = if (saturation < 0.3f) (0.3f - saturation) * 0.3f else 0f
                baseWhite + desatBoost
            }
            
            // Darker colors: L* 35-50 → minimal white, more if desaturated
            labLightness > 0.35f -> {
                if (saturation < 0.2f) (0.2f - saturation) * 0.3f else 0f
            }
            
            // Very dark: no white
            else -> 0f
        }
        
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
        
        // IMPROVED: Lightness-weighted error for pastel accuracy
        // At high target lightness (L* > 70), lightness errors are weighted more heavily.
        // This forces the optimizer to add more white for light/pastel colors.
        // Without this, the optimizer finds "close enough" dark matches and stops.
        val lightnessWeight = if (targetLab[0] > 70) {
            // Scale from 1.0 at L*=70 to 2.5 at L*=100
            1.0f + (targetLab[0].toFloat() - 70f) / 30f * 1.5f
        } else {
            1.0f
        }
        
        // Also penalize being TOO DARK more than being too light
        // (since underestimating white is the common failure mode)
        val darknessPenalty = if (dL > 0) 1.2f else 1.0f // Target is lighter than mix = penalty
        
        val weightedDL = dL * lightnessWeight * darknessPenalty
        
        return sqrt(weightedDL * weightedDL + dA * dA + dB * dB)
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


