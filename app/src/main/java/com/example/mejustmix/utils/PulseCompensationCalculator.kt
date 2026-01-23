package com.example.mejustmix.utils

import kotlin.math.PI
import kotlin.math.roundToInt
import java.util.Locale

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
    
    private const val MAX_SAFE_FEED_RATE = 12000 // Safe upper limit for stepper motors
    
    /**
     * Represents a pulse compensation profile with zones and speed modifiers.
     */
    data class PulseProfile(
        val taperFraction: Float,           // 0.0-0.5 (fraction of pillow that is taper on each side)
        val entrySpeedMultiplier: Float,    // Speed multiplier for taper entry (surge suppression, typ 0.8-1.0)
        val exitSpeedMultiplier: Float,     // Speed multiplier for taper exit (dip compensation, typ 1.5-3.0)
        val nominalSpeedFraction: Float,    // Fraction of pillow at nominal speed
        val taperLengthMm: Float,           // Calculated taper length in mm
        val volumePerPillowMl: Float,       // Estimated volume per pillow in mL
        val optimalTaperAcceleration: Float // Recommended acceleration for taper zones (mm/s²)
    ) {
        // Legacy property for backward compatibility or summary
        val taperSpeedMultiplier: Float get() = exitSpeedMultiplier 
    }
    
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
        baseFeedRate: Int = 1000,
        strengthFactor: Float = 1.0f
    ): PulseProfile {
        // Calculate taper length (each side)
        // Calculate taper length (each side)
        val taperLength = ((pillowLengthMm - fullDiameterSectionMm) / 2f).coerceAtLeast(0f)
        
        // Safety check for division by zero if pillowLength is 0
        val taperFraction = if (pillowLengthMm > 0.1f) {
            (taperLength / pillowLengthMm).coerceIn(0.01f, 0.49f)
        } else {
            0.01f // Fallback safe value
        }
        
        // Asymmetric Compensation Strategy:
        // 1. Entry Taper (Touch-down): This inherently causes a surge (positive displacement).
        //    Ideally we would SLOW down.
        //    If smoothing strength is high (>1.2), we start suppressing the surge.
        //    Formula: starts at 1.0, drops to 0.7 as strength goes to 2.5
        val entryMultiplier = if (strengthFactor > 1.2f) {
            (1.0f - (0.23f * (strengthFactor - 1.2f))).coerceAtLeast(0.7f)
        } else {
            1.0f
        } 
        
        // 2. Exit Taper (Lift-off): This inherently causes a dip (vacuum/backflow).
        //    We must SPEED UP significantly to fill the void.
        //    Strength factor directly controls the exit speed multiplier (0x = no compensation, 5x = extreme)
        //    Minimum of 1.0x to prevent slowdown
        val exitMultiplier = strengthFactor.coerceAtLeast(1.0f)
        
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
        // We calculate based on the massive acceleration needed for the Exit (Lift-off) phase
        val vFastMmPerSec = (baseFeedRate * exitMultiplier) / 60f
        val vNominalMmPerSec = baseFeedRate / 60f
        val taperLengthMeters = taperLength / 1000f 
        
        // a = (v² - u²) / (2s)
        val optimalAcceleration = if (taperLength > 0.1f) {
            val deltaVSquared = (vFastMmPerSec * vFastMmPerSec) - (vNominalMmPerSec * vNominalMmPerSec)
            val accelMmPerSecSquared = deltaVSquared / (2f * taperLength)
            kotlin.math.abs(accelMmPerSecSquared).coerceIn(100f, 2500f) // Increased max cap for aggressive compensation
        } else {
            500f 
        }
        
        return PulseProfile(
            taperFraction = taperFraction,
            entrySpeedMultiplier = entryMultiplier,
            exitSpeedMultiplier = exitMultiplier.coerceIn(1.2f, 5.0f),
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
        val entryFeed = (baseFeedRate * profile.entrySpeedMultiplier).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
        val exitFeed = (baseFeedRate * profile.exitSpeedMultiplier).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
        
        return listOf(
            GCodeSegment(taperSteps, entryFeed, "Entry taper"),
            GCodeSegment(nominalSteps, baseFeedRate, "Full flow"),
            GCodeSegment(taperSteps, exitFeed, "Exit taper")
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
        profile: PulseProfile,
        initialPhaseSteps: Float = 0f
    ): List<GCodeSegment> {
        if (totalSteps <= 0 || stepsPerPillow <= 0) {
            return emptyList()
        }
        
        val segments = mutableListOf<GCodeSegment>()
        var remainingToDispense = totalSteps
        var currentPhase = initialPhaseSteps % stepsPerPillow
        
        val taperSteps = stepsPerPillow * profile.taperFraction
        val fullFlowSteps = stepsPerPillow * profile.nominalSpeedFraction
        val exitStart = stepsPerPillow - taperSteps
        
        val entryFeed = (baseFeedRate * profile.entrySpeedMultiplier).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
        val exitFeed = (baseFeedRate * profile.exitSpeedMultiplier).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
        
        while (remainingToDispense > 0.01f) {
            // Determine current zone and steps remaining in it
            val (zoneStepsRemaining, zoneFeed, zoneDesc) = when {
                currentPhase < taperSteps -> {
                    // Entry Taper
                    Triple(taperSteps - currentPhase, entryFeed, "Entry taper")
                }
                currentPhase < exitStart -> {
                    // Full Flow
                    Triple(exitStart - currentPhase, baseFeedRate, "Full flow")
                }
                else -> {
                    // Exit Taper
                    Triple(stepsPerPillow - currentPhase, exitFeed, "Exit taper")
                }
            }
            
            // How much can we dispense in this zone?
            val stepsToDo = remainingToDispense.coerceAtMost(zoneStepsRemaining)
            
            segments.add(GCodeSegment(stepsToDo, zoneFeed, zoneDesc))
            
            remainingToDispense -= stepsToDo
            currentPhase = (currentPhase + stepsToDo) % stepsPerPillow
            
            // Floating point correction: if we just finished a zone exactly, force phase alignment
            // to avoid getting stuck in microscopic remnants of zones
            if (kotlin.math.abs(remainingToDispense - 0f) > 0.01f && 
                kotlin.math.abs(zoneStepsRemaining - stepsToDo) < 0.01f) {
                 // We finished the zone exactly, moving to next zone logic automatically via loop
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
        return "Taper: ${taperPercent}% (${String.format(Locale.US, "%.1f", profile.taperLengthMm)}mm), " +
               "Speed Boost: ${String.format(Locale.US, "%.1f", profile.taperSpeedMultiplier)}x, " +
               "Vol/Pillow: ${String.format(Locale.US, "%.3f", profile.volumePerPillowMl)}mL"
    }
}
