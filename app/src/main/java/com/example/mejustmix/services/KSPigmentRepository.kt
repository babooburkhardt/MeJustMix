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
    
    fun createDefaultPigmentDatabase(): KSPigmentDatabase {
        return KSPigmentDatabase(
            cyan = KSColor(ksR = 5.0f, ksG = 0.3f, ksB = 0.1f, s = 1.0f),
            magenta = KSColor(ksR = 0.1f, ksG = 4.0f, ksB = 0.8f, s = 1.0f),
            yellow = KSColor(ksR = 0.02f, ksG = 0.5f, ksB = 4.0f, s = 1.0f),
            black = KSColor(ksR = 20.0f, ksG = 20.0f, ksB = 20.0f, s = 1.0f),
            white = KSColor(ksR = 0.01f, ksG = 0.01f, ksB = 0.01f, s = 0.8f) // Adjusted to 0.8 per user request
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
