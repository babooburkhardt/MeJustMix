package com.example.mejustmix.services

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

/**
 * Repository for Pigment Data and Defaults.
 */
object KSPigmentRepository {
    
    /**
     * Default pigment database calibrated from:
     * - Masstone hex codes (visual appearance of pure pigments)
     * - Real mix measurement: 56% Magenta + 44% Yellow = #A00320
     * 
     * The masstone hex alone underestimates absorption strength because
     * it shows pigment diluted on paper. The real mix measurement reveals
     * that absorption is ~5x stronger when pigments are concentrated.
     * 
     * Source hex codes:
     * - Magenta: #8D2931 (deep berry/wine)
     * - Cyan: #2A2853 (deep blue-violet)  
     * - Yellow: #F9AB07 (golden yellow)
     * - Black: #373532 (warm dark gray)
     * - Black+White tint: #5D6063
     * 
     * These values are tuned for typical dye-based paints.
     * For best results, recalibrate with your actual pigments.
     */
    fun createDefaultPigmentDatabase(): KSPigmentDatabase {
        return KSPigmentDatabase(
            // Cyan: Absorbs red strongly, passes green and blue (deep blue-violet)
            // Derived from #2A2853
            cyan = KSColor(ksR = 60.0f, ksG = 10.0f, ksB = 2.0f, s = 1.0f),

            // Magenta: Passes red, absorbs green very strongly, absorbs some blue
            // Calibrated from real mix: 56%M + 44%Y = #A00320
            // Green absorption is 5x higher than masstone hex suggests
            // IMPROVED: Reduced ksB from 15.0 to 4.0 to stop it from looking like "Red"
            magenta = KSColor(ksR = 1.0f, ksG = 110.0f, ksB = 4.0f, s = 1.0f),
            
            // Yellow: Passes red, passes most green, absorbs blue strongly
            // Derived from #F9AB07
            yellow = KSColor(ksR = 0.0f, ksG = 0.5f, ksB = 50.0f, s = 1.0f),
            
            // Black: Absorbs all channels strongly and fairly evenly
            // Derived from #373532, boosted for real absorption
            black = KSColor(ksR = 60.0f, ksG = 65.0f, ksB = 70.0f, s = 1.0f),
            
            // White: Very low K/S (minimal absorption), scattering handled by
            // ColorPhysicsEngine.whiteScatteringBoost factor
            white = KSColor(ksR = 0.01f, ksG = 0.01f, ksB = 0.01f, s = 1.0f)
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
}
