package com.example.mejustmix.services

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.mejustmix.data.PaintMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pigment strength multipliers for color mixing.
 * Higher values = stronger pigment = less volume needed.
 */
data class PigmentStrengths(
    val cyan: Float = 3.5f,
    val magenta: Float = 2.5f,
    val yellow: Float = 1.0f,
    val black: Float = 5.0f,
    val white: Float = 1.0f,
    val gamma: Float = 1.0f
)

/**
 * Mixing algorithm mode.
 */
enum class MixingMode {
    /** Simple RGB-based mixing (fast, less accurate) */
    SIMPLE_RGB,
    
    /** 3-channel K-M mixing (recommended for scanner calibration) */
    KUBELKA_MUNK_3CHANNEL,
    
    /** Full 31-wavelength spectral K-M (for spectrophotometer data) */
    KUBELKA_MUNK_SPECTRAL
}

/**
 * Unified color mixing service.
 * 
 * Supports:
 * - Simple RGB-based mixing (fast, original algorithm)
 * - Corrected 3-channel Kubelka-Munk (recommended for scanner calibration)
 * - Full 31-wavelength spectral K-M (for spectrophotometer data)
 */
object ColorMixingService {

    private const val PIGMENT_MASSTONE_LIGHTNESS = 0.25f
    private const val MIN_TOTAL_VOLUME = 0.0001f

    // Cached databases
    private var ksDatabase: KSPigmentDatabase? = null
    private var spectralDatabase: SpectralPigmentDatabase? = null

    // ========================================================================
    // MAIN API (with backward compatibility)
    // ========================================================================

    /**
     * Main entry point for color mixing calculation.
     * 
     * @param colorInt Android color integer (ARGB)
     * @param strengths Pigment strength multipliers
     * @param useKubelkaMunk If true, use Kubelka-Munk; if false, use simple RGB
     * @return Normalized paint mix ratios
     */
    fun calculateMixRatios(
        colorInt: Int,
        strengths: PigmentStrengths,
        useKubelkaMunk: Boolean = false
    ): PaintMix {
        return if (useKubelkaMunk) {
            calculateKM3ChannelMix(colorInt, strengths)
        } else {
            calculateSimpleMix(colorInt, strengths)
        }
    }

    /**
     * Calculate mix with explicit mixing mode selection.
     */
    fun calculateMixRatios(
        colorInt: Int,
        strengths: PigmentStrengths,
        mixingMode: MixingMode
    ): PaintMix {
        return when (mixingMode) {
            MixingMode.SIMPLE_RGB -> calculateSimpleMix(colorInt, strengths)
            MixingMode.KUBELKA_MUNK_3CHANNEL -> calculateKM3ChannelMix(colorInt, strengths)
            MixingMode.KUBELKA_MUNK_SPECTRAL -> calculateKMSpectralMix(colorInt, strengths)
        }
    }

    /**
     * Preview the resulting color from a paint mix.
     */
    fun previewMixedColor(
        mix: PaintMix,
        useKubelkaMunk: Boolean = false
    ): Int {
        return if (useKubelkaMunk) {
            previewKM3Channel(mix)
        } else {
            previewSimple(mix)
        }
    }

    /**
     * Preview with explicit mixing mode.
     */
    fun previewMixedColor(
        mix: PaintMix,
        mixingMode: MixingMode
    ): Int {
        return when (mixingMode) {
            MixingMode.SIMPLE_RGB -> previewSimple(mix)
            MixingMode.KUBELKA_MUNK_3CHANNEL -> previewKM3Channel(mix)
            MixingMode.KUBELKA_MUNK_SPECTRAL -> previewKMSpectral(mix)
        }
    }

    // ========================================================================
    // SIMPLE RGB MIXING (Original algorithm)
    // ========================================================================

    private fun calculateSimpleMix(
        colorInt: Int,
        strengths: PigmentStrengths
    ): PaintMix {
        // 1. Normalize RGB to 0.0 - 1.0 and apply Gamma Correction
        val r = (Color.red(colorInt) / 255f).pow(strengths.gamma)
        val g = (Color.green(colorInt) / 255f).pow(strengths.gamma)
        val b = (Color.blue(colorInt) / 255f).pow(strengths.gamma)

        // 2. Calculate HSL for Perceptual Lightness
        val hsl = FloatArray(3)
        ColorUtils.RGBToHSL(
            Color.red(colorInt),
            Color.green(colorInt),
            Color.blue(colorInt),
            hsl
        )
        val lightness = hsl[2]

        // 3. Determine "Optical" Components (CMYK separation)
        val maxVal = max(r, max(g, b))
        val opticalC = (maxVal - r)
        val opticalM = (maxVal - g)
        val opticalY = (maxVal - b)
        val opticalK = 1f - maxVal

        // 4. White Calculation
        val neededWhite = if (lightness > PIGMENT_MASSTONE_LIGHTNESS) {
            (lightness - PIGMENT_MASSTONE_LIGHTNESS) / (1f - PIGMENT_MASSTONE_LIGHTNESS)
        } else {
            0f
        }

        val baseWhite = min(r, min(g, b))
        val opticalW = max(baseWhite, neededWhite)

        // 5. Apply Tunable Strengths
        val volC = opticalC / strengths.cyan
        val volM = opticalM / strengths.magenta
        val volY = opticalY / strengths.yellow
        val volK = opticalK / strengths.black
        val volW = opticalW / strengths.white

        // 6. Normalization
        val totalVolume = volC + volM + volY + volK + volW

        return if (totalVolume > MIN_TOTAL_VOLUME) {
            PaintMix(
                cyan = volC / totalVolume,
                magenta = volM / totalVolume,
                yellow = volY / totalVolume,
                black = volK / totalVolume,
                white = volW / totalVolume
            )
        } else {
            PaintMix(0f, 0f, 0f, 0f, 1f)
        }
    }

    private fun previewSimple(mix: PaintMix): Int {
        // Simple CMY to RGB
        val r = ((1f - mix.cyan) * (1f - mix.black) * 255).toInt().coerceIn(0, 255)
        val g = ((1f - mix.magenta) * (1f - mix.black) * 255).toInt().coerceIn(0, 255)
        val b = ((1f - mix.yellow) * (1f - mix.black) * 255).toInt().coerceIn(0, 255)

        // Add white influence
        val w = mix.white
        val finalR = (r + w * (255 - r)).toInt().coerceIn(0, 255)
        val finalG = (g + w * (255 - g)).toInt().coerceIn(0, 255)
        val finalB = (b + w * (255 - b)).toInt().coerceIn(0, 255)

        return Color.rgb(finalR, finalG, finalB)
    }

    // ========================================================================
    // 3-CHANNEL KUBELKA-MUNK (Corrected algorithm)
    // ========================================================================

    private fun calculateKM3ChannelMix(
        colorInt: Int,
        strengths: PigmentStrengths
    ): PaintMix {
        if (ksDatabase == null) {
            ksDatabase = KubelkaMunkColorMixing.createDefaultPigmentDatabase()
        }

        val scaledDatabase = KubelkaMunkColorMixing.scalePigmentDatabase(
            ksDatabase!!,
            strengths
        )

        return KubelkaMunkColorMixing.calculateMixRatios(colorInt, scaledDatabase)
    }

    private fun previewKM3Channel(mix: PaintMix): Int {
        if (ksDatabase == null) {
            ksDatabase = KubelkaMunkColorMixing.createDefaultPigmentDatabase()
        }
        return KubelkaMunkColorMixing.previewMixedColor(mix, ksDatabase!!)
    }

    // ========================================================================
    // SPECTRAL KUBELKA-MUNK (31-wavelength)
    // ========================================================================

    private fun calculateKMSpectralMix(
        colorInt: Int,
        strengths: PigmentStrengths
    ): PaintMix {
        if (spectralDatabase == null) {
            spectralDatabase = SpectralKubelkaMunk.createDefaultSpectralDatabase()
        }

        val targetReflectance = SpectralKubelkaMunk.rgbToSpectralReflectance(colorInt)
        val targetKS = SpectralKubelkaMunk.reflectanceToKSSpectrum(targetReflectance)

        return optimizeSpectralMix(targetKS, spectralDatabase!!, strengths)
    }

    private fun optimizeSpectralMix(
        targetKS: List<Float>,
        database: SpectralPigmentDatabase,
        strengths: PigmentStrengths
    ): PaintMix {
        val scaledC = database.cyan.map { it * strengths.cyan }
        val scaledM = database.magenta.map { it * strengths.magenta }
        val scaledY = database.yellow.map { it * strengths.yellow }
        val scaledK = database.black.map { it * strengths.black }
        val scaledW = database.white.map { it * strengths.white }

        var mix = PaintMix(0.2f, 0.2f, 0.2f, 0.1f, 0.3f)
        var bestError = Float.MAX_VALUE
        var bestMix = mix

        repeat(100) {
            val pigments = listOf(scaledC, scaledM, scaledY, scaledK, scaledW)
            val concs = listOf(mix.cyan, mix.magenta, mix.yellow, mix.black, mix.white)

            val mixedKS = SpectralKubelkaMunk.mixSpectralKS(pigments, concs)
            val error = targetKS.zip(mixedKS) { t, m -> (t - m) * (t - m) }.sum()

            if (error < bestError) {
                bestError = error
                bestMix = mix
            }

            mix = adjustMixSpectral(mix, targetKS, pigments, 0.02f)
        }

        return normalizeMix(bestMix)
    }

    private fun adjustMixSpectral(
        mix: PaintMix,
        targetKS: List<Float>,
        pigments: List<List<Float>>,
        step: Float
    ): PaintMix {
        val concs = listOf(mix.cyan, mix.magenta, mix.yellow, mix.black, mix.white)
        val mixedKS = SpectralKubelkaMunk.mixSpectralKS(pigments, concs)

        val gradients = pigments.mapIndexed { i, pigmentKS ->
            targetKS.zip(mixedKS).mapIndexed { w, (target, current) ->
                (target - current) * pigmentKS[w]
            }.sum()
        }

        return PaintMix(
            cyan = (mix.cyan + step * gradients[0]).coerceIn(0f, 1f),
            magenta = (mix.magenta + step * gradients[1]).coerceIn(0f, 1f),
            yellow = (mix.yellow + step * gradients[2]).coerceIn(0f, 1f),
            black = (mix.black + step * gradients[3]).coerceIn(0f, 1f),
            white = (mix.white + step * gradients[4]).coerceIn(0f, 1f)
        )
    }

    private fun previewKMSpectral(mix: PaintMix): Int {
        if (spectralDatabase == null) {
            spectralDatabase = SpectralKubelkaMunk.createDefaultSpectralDatabase()
        }

        val pigments = listOf(
            spectralDatabase!!.cyan,
            spectralDatabase!!.magenta,
            spectralDatabase!!.yellow,
            spectralDatabase!!.black,
            spectralDatabase!!.white
        )
        val concs = listOf(mix.cyan, mix.magenta, mix.yellow, mix.black, mix.white)

        return SpectralKubelkaMunk.mixAndConvertToRGB(pigments, concs)
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    /**
     * Normalize mix - only if sum > 1.0 (corrected behavior)
     */
    private fun normalizeMix(mix: PaintMix): PaintMix {
        val total = mix.cyan + mix.magenta + mix.yellow + mix.black + mix.white

        return when {
            total > 1.0f -> PaintMix(
                cyan = mix.cyan / total,
                magenta = mix.magenta / total,
                yellow = mix.yellow / total,
                black = mix.black / total,
                white = mix.white / total
            )
            total < 0.001f -> PaintMix(0f, 0f, 0f, 0f, 1f)
            else -> mix
        }
    }

    /**
     * Update the K/S database with calibrated values.
     */
    fun updatePigmentCalibration(pigment: String, ksColor: KSColor) {
        val current = ksDatabase ?: KubelkaMunkColorMixing.createDefaultPigmentDatabase()

        ksDatabase = when (pigment.lowercase()) {
            "cyan" -> current.copy(cyan = ksColor)
            "magenta" -> current.copy(magenta = ksColor)
            "yellow" -> current.copy(yellow = ksColor)
            "black" -> current.copy(black = ksColor)
            "white" -> current.copy(white = ksColor)
            else -> current
        }
    }

    /**
     * Get current pigment K/S values.
     */
    fun getPigmentKS(pigment: String): KSColor {
        val db = ksDatabase ?: KubelkaMunkColorMixing.createDefaultPigmentDatabase()
        return when (pigment.lowercase()) {
            "cyan" -> db.cyan
            "magenta" -> db.magenta
            "yellow" -> db.yellow
            "black" -> db.black
            "white" -> db.white
            else -> KSColor(1f, 1f, 1f)
        }
    }

    /**
     * Reset databases to defaults.
     */
    fun resetToDefaults() {
        ksDatabase = null
        spectralDatabase = null
    }
}
