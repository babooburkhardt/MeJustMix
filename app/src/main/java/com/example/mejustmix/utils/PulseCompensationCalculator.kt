package com.example.mejustmix.utils

import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Calculates velocity compensation profiles for peristaltic pump pulse damping.
 * 
 * A peristaltic pump creates fluid flow by squeezing "pillows" through a tube.
 * The flow rate is NOT constant during a single roller cycle due to the tapered
 * entry/exit zones where the tube expands/compresses.
 * 
 * This calculator generates a velocity profile to achieve constant volumetric flow.
 */
object PulseCompensationCalculator {
    
    /**
     * Represents a pulse compensation profile with zones and speed modifiers.
     */
    data class PulseProfile(
        val taperFraction: Float,           // 0.0-0.5 (fraction of pillow that is taper on each side)
        val taperSpeedMultiplier: Float,    // Speed multiplier for taper zones (typically 1.5-3.0)
        val nominalSpeedFraction: Float,    // Fraction of pillow at nominal speed (1.0 - 2*taperFraction)
        val taperLengthMm: Float,           // Calculated taper length in mm
        val volumePerPillowMl: Float,       // Estimated volume per pillow in mL
        val optimalTaperAcceleration: Float // Recommended acceleration for taper zones (mm/s²)
    )
    
    /**
     * Calculate the compensation profile from tube geometry.
     * 
     * @param pillowLengthMm Total length of one pillow (roller to roller)
     * @param fullDiameterSectionMm Length where tube is at full expansion
     * @param tubeInnerDiameterMm Tube inner diameter (for volume calculation)
     * @param baseFeedRate Base feed rate in mm/min (for acceleration calculation)
     * @return Computed pulse profile
     */
    fun calculateProfile(
        pillowLengthMm: Float,
        fullDiameterSectionMm: Float,
        tubeInnerDiameterMm: Float = 3f,
        baseFeedRate: Int = 1000
    ): PulseProfile {
        // Calculate taper length (each side)
        val taperLength = ((pillowLengthMm - fullDiameterSectionMm) / 2f).coerceAtLeast(0f)
        val taperFraction = (taperLength / pillowLengthMm).coerceIn(0.01f, 0.49f)
        
        // Speed multiplier: Inverse of average volume flow in taper zone
        // At taper entry, effective area = ~0%; at taper exit, effective area = 100%
        // Average effective area in taper = 50%
        // To match volume flow, speed must be: 1 / 0.5 = 2x
        // However, we use a slightly more conservative multiplier
        val taperSpeedMultiplier = 2.0f
        
        // Volume calculation using cylinder approximation
        // V = π * r² * L (for full section)
        // Plus approximate taper volume (cone sections)
        val radiusMm = tubeInnerDiameterMm / 2f
        val fullSectionVolume = PI.toFloat() * radiusMm * radiusMm * fullDiameterSectionMm
        // Taper is roughly 1/3 of cylinder (cone volume = 1/3 * base * height)
        val taperVolume = (PI.toFloat() * radiusMm * radiusMm * taperLength) * 0.5f // Average section
        val totalVolumeMm3 = fullSectionVolume + (2 * taperVolume)
        val volumeMl = totalVolumeMm3 / 1000f // Convert mm³ to mL
        
        // Calculate optimal acceleration for taper zones
        // Goal: Match the volume gradient dV/dx in the taper
        // 
        // Physics:
        // - Volume flow rate Q = A(x) * v(x) where A(x) is cross-sectional area, v(x) is velocity
        // - For constant Q, if A increases linearly, v must decrease linearly
        // - Linear velocity change over distance requires constant acceleration
        // 
        // Calculation:
        // - Velocity change: Δv = v_fast - v_nominal = baseFeedRate * (taperSpeedMultiplier - 1)
        // - Distance: taperLength (in mm)
        // - Convert feed rate from mm/min to mm/s: baseFeedRate / 60
        // - Acceleration a = (v_final² - v_initial²) / (2 * distance)
        // 
        // For entry taper (deceleration from fast to nominal):
        val vFastMmPerSec = (baseFeedRate * taperSpeedMultiplier) / 60f
        val vNominalMmPerSec = baseFeedRate / 60f
        val taperLengthMeters = taperLength / 1000f // Convert mm to meters for calculation
        
        // Using kinematic equation: v² = u² + 2as, solve for a
        // a = (v² - u²) / (2s)
        val optimalAcceleration = if (taperLength > 0.1f) {
            val deltaVSquared = (vNominalMmPerSec * vNominalMmPerSec) - (vFastMmPerSec * vFastMmPerSec)
            val accelMmPerSecSquared = deltaVSquared / (2f * taperLength)
            // Take absolute value and convert to positive (we want magnitude)
            kotlin.math.abs(accelMmPerSecSquared).coerceIn(100f, 1500f)
        } else {
            500f // Default for very short tapers
        }
        
        return PulseProfile(
            taperFraction = taperFraction,
            taperSpeedMultiplier = taperSpeedMultiplier.coerceIn(1.2f, 4.0f),
            nominalSpeedFraction = (1f - (2f * taperFraction)).coerceAtLeast(0.1f),
            taperLengthMm = taperLength,
            volumePerPillowMl = volumeMl,
            optimalTaperAcceleration = optimalAcceleration
        )
    }
    
    /**
     * Data class representing a single G-code segment with steps and feed rate.
     */
    data class GCodeSegment(
        val steps: Float,
        val feedRate: Int,
        val description: String
    )
    
    /**
     * Generate G-code segments for ONE compensated pillow cycle.
     * Returns 3 segments: entry taper, full flow, exit taper.
     * 
     * @param stepsPerPillow Total steps for one pillow
     * @param baseFeedRate Nominal feed rate (mm/min or steps/min depending on config)
     * @param profile The calculated pulse profile
     * @return List of (steps, feedRate, description) for the 3 zones
     */
    fun generatePillowSegments(
        stepsPerPillow: Float,
        baseFeedRate: Int,
        profile: PulseProfile
    ): List<GCodeSegment> {
        val taperSteps = stepsPerPillow * profile.taperFraction
        val nominalSteps = stepsPerPillow * profile.nominalSpeedFraction
        val fastFeed = (baseFeedRate * profile.taperSpeedMultiplier).toInt()
        
        return listOf(
            GCodeSegment(taperSteps, fastFeed, "Entry taper"),
            GCodeSegment(nominalSteps, baseFeedRate, "Full flow"),
            GCodeSegment(taperSteps, fastFeed, "Exit taper")
        )
    }
    
    /**
     * Generate G-code segments for multiple pulses of a single pump.
     * Useful when you want to modulate across several pulses.
     * 
     * @param totalSteps Total steps to dispense
     * @param stepsPerPillow Steps per pillow (pump calibration)
     * @param baseFeedRate Nominal feed rate
     * @param profile The calculated pulse profile
     * @return Flattened list of all segments for the entire dispense
     */
    fun generateCompensatedSegments(
        totalSteps: Float,
        stepsPerPillow: Float,
        baseFeedRate: Int,
        profile: PulseProfile
    ): List<GCodeSegment> {
        if (totalSteps <= 0 || stepsPerPillow <= 0) {
            return emptyList()
        }
        
        val completePillows = (totalSteps / stepsPerPillow).toInt()
        val remainderSteps = totalSteps % stepsPerPillow
        
        val segments = mutableListOf<GCodeSegment>()
        
        // Add segments for each complete pillow
        repeat(completePillows) { pillowIndex ->
            segments.addAll(generatePillowSegments(stepsPerPillow, baseFeedRate, profile))
        }
        
        // Handle remainder (partial pillow at the end)
        if (remainderSteps > 0.01f) {
            // For partial pillows, determine which zone we're in
            val taperSteps = stepsPerPillow * profile.taperFraction
            
            when {
                remainderSteps <= taperSteps -> {
                    // Still in entry taper
                    val fastFeed = (baseFeedRate * profile.taperSpeedMultiplier).toInt()
                    segments.add(GCodeSegment(remainderSteps, fastFeed, "Partial entry taper"))
                }
                remainderSteps <= stepsPerPillow - taperSteps -> {
                    // Entry taper + partial full flow
                    val fastFeed = (baseFeedRate * profile.taperSpeedMultiplier).toInt()
                    segments.add(GCodeSegment(taperSteps, fastFeed, "Entry taper"))
                    segments.add(GCodeSegment(remainderSteps - taperSteps, baseFeedRate, "Partial full flow"))
                }
                else -> {
                    // Entry taper + full flow + partial exit taper
                    val fastFeed = (baseFeedRate * profile.taperSpeedMultiplier).toInt()
                    val nominalSteps = stepsPerPillow * profile.nominalSpeedFraction
                    segments.add(GCodeSegment(taperSteps, fastFeed, "Entry taper"))
                    segments.add(GCodeSegment(nominalSteps, baseFeedRate, "Full flow"))
                    segments.add(GCodeSegment(remainderSteps - taperSteps - nominalSteps, fastFeed, "Partial exit taper"))
                }
            }
        }
        
        return segments
    }
    
    /**
     * Merge consecutive segments with the same feed rate to reduce G-code size.
     */
    fun mergeConsecutiveSegments(segments: List<GCodeSegment>): List<GCodeSegment> {
        if (segments.isEmpty()) return emptyList()
        
        val merged = mutableListOf<GCodeSegment>()
        var current = segments.first()
        
        for (i in 1 until segments.size) {
            val next = segments[i]
            if (next.feedRate == current.feedRate) {
                // Merge
                current = current.copy(
                    steps = current.steps + next.steps,
                    description = "${current.description} + ${next.description}"
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return merged
    }
    
    /**
     * Format a brief summary of the profile for display.
     */
    fun formatProfileSummary(profile: PulseProfile): String {
        val taperPercent = (profile.taperFraction * 100).roundToInt()
        return "Taper: ${taperPercent}% (${String.format("%.1f", profile.taperLengthMm)}mm), " +
               "Speed Boost: ${String.format("%.1f", profile.taperSpeedMultiplier)}x, " +
               "Vol/Pillow: ${String.format("%.3f", profile.volumePerPillowMl)}mL"
    }
}
