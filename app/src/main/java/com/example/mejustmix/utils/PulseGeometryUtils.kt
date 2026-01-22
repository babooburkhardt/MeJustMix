package com.example.mejustmix.utils

import kotlin.math.abs

/**
 * Peristaltic Pump Geometry Calculator
 * 
 * Converts between roller angle and pulse timing based on measured reference points.
 * Includes drift tracking to learn the pump's mechanical behavior over time.
 */
object PulseGeometryUtils {
    
    // ==================== HARDWARE CONSTANTS ====================
    
    /** Number of rollers on the pump head */
    const val ROLLERS = 3
    
    /** Motor steps per full revolution (1.8° stepper × 4:1 gear ratio) */
    const val STEPS_PER_REV = 800f
    
    /** Degrees of rotation per pulse */
    const val DEGREES_PER_PULSE = 360f / ROLLERS  // 120°
    
    /** Steps per pulse */
    const val STEPS_PER_PULSE = STEPS_PER_REV / ROLLERS  // 266.67
    
    /** Steps per degree */
    const val STEPS_PER_DEGREE = STEPS_PER_REV / 360f  // 2.222
    
    // ==================== MEASURED GEOMETRY ====================
    // Based on eyeballed measurements - adjustable for fine-tuning
    
    /** Roller angle at peak compression (fully engaged) */
    const val ANGLE_FULL_ENGAGED = 210f
    
    /** Roller angle at zero compression (pulse boundary) */
    const val ANGLE_DISENGAGED = 225f
    
    /** Width of the taper zone in degrees */
    const val TAPER_ARC = ANGLE_DISENGAGED - ANGLE_FULL_ENGAGED  // 15°
    
    // ==================== FLOW ZONES ====================
    
    enum class FlowZone {
        ENTRY_TAPER,      // Flow ramping up (tube compressing)
        FULL_COMPRESSION, // Steady maximum flow
        EXIT_TAPER,       // Flow ramping down (tube expanding)
        PASSIVE           // This roller not compressing
    }
    
    // ==================== CALCULATIONS ====================
    
    /**
     * Calculate steps needed to reach the next pulse boundary (225°).
     * 
     * @param currentAngleDegrees Current roller angle (0-360, 0° = North)
     * @return Steps to move to reach pulse boundary
     */
    fun stepsToNextBoundary(currentAngleDegrees: Float): Float {
        val targetAngle = ANGLE_DISENGAGED
        val degreesToBoundary = if (currentAngleDegrees <= targetAngle) {
            targetAngle - currentAngleDegrees
        } else {
            (360f - currentAngleDegrees) + targetAngle
        }
        return degreesToBoundary * STEPS_PER_DEGREE
    }
    
    /**
     * Calculate current pulse phase.
     * 
     * @param currentAngleDegrees Current roller angle
     * @return Phase from 0.0 (at boundary) to 1.0 (approaching next boundary)
     */
    fun pulsePhase(currentAngleDegrees: Float): Float {
        // Normalize angle relative to pulse boundary
        val normalizedAngle = (currentAngleDegrees - ANGLE_DISENGAGED + 360f) % DEGREES_PER_PULSE
        return normalizedAngle / DEGREES_PER_PULSE
    }
    
    /**
     * Determine which flow zone the roller is currently in.
     */
    fun currentZone(currentAngleDegrees: Float): FlowZone {
        val phase = pulsePhase(currentAngleDegrees)
        val taperFraction = TAPER_ARC / DEGREES_PER_PULSE  // 15/120 = 0.125
        
        return when {
            phase < taperFraction -> FlowZone.ENTRY_TAPER
            phase < 0.5f -> FlowZone.FULL_COMPRESSION
            phase < 0.5f + taperFraction -> FlowZone.EXIT_TAPER
            else -> FlowZone.PASSIVE
        }
    }
    
    /**
     * Convert a clock position (1-12) to degrees.
     * Useful for quick user input: "The roller is pointing at 7 o'clock"
     */
    fun clockPositionToDegrees(hour: Int): Float {
        // 12 o'clock = 0° (North), 3 o'clock = 90° (East), etc.
        return ((12 - hour) % 12) * 30f
    }
    
    /**
     * Convert degrees to a human-readable direction.
     */
    fun degreesToDirection(degrees: Float): String {
        val normalized = degrees % 360
        return when {
            normalized < 22.5f || normalized >= 337.5f -> "↑ North"
            normalized < 67.5f -> "↗ NE"
            normalized < 112.5f -> "→ East"
            normalized < 157.5f -> "↘ SE"
            normalized < 202.5f -> "↓ South"
            normalized < 247.5f -> "↙ SW"
            normalized < 292.5f -> "← West"
            else -> "↖ NW"
        }
    }
    
    // ==================== DRIFT TRACKING ====================
    
    /**
     * Calculate drift from a new calibration.
     * 
     * @param expectedAngle Where the roller SHOULD be (based on step count)
     * @param observedAngle Where the roller ACTUALLY is (user observation)
     * @return Drift in degrees (positive = ahead, negative = behind)
     */
    fun calculateDrift(expectedAngle: Float, observedAngle: Float): Float {
        var drift = observedAngle - expectedAngle
        // Normalize to -180 to +180 range
        while (drift > 180) drift -= 360
        while (drift < -180) drift += 360
        return drift
    }
    
    /**
     * Analyze drift history and return compensation value if pattern is consistent.
     * 
     * @param driftHistory List of drift measurements (degrees)
     * @param minSamples Minimum samples before recommending compensation (default 5)
     * @param maxVariance Maximum allowed variance to consider drift "consistent"
     * @return Recommended drift compensation in degrees, or null if not enough data/too variable
     */
    fun analyzeDriftPattern(
        driftHistory: List<Float>,
        minSamples: Int = 5,
        maxVariance: Float = 5f
    ): DriftAnalysis {
        if (driftHistory.size < minSamples) {
            return DriftAnalysis(
                hasEnoughData = false,
                sampleCount = driftHistory.size,
                averageDrift = 0f,
                variance = 0f,
                recommendedCompensation = null,
                message = "Need ${minSamples - driftHistory.size} more calibrations to detect drift pattern"
            )
        }
        
        val avgDrift = driftHistory.average().toFloat()
        val variance = driftHistory.map { abs(it - avgDrift) }.average().toFloat()
        
        return if (variance <= maxVariance && abs(avgDrift) > 1f) {
            DriftAnalysis(
                hasEnoughData = true,
                sampleCount = driftHistory.size,
                averageDrift = avgDrift,
                variance = variance,
                recommendedCompensation = avgDrift,
                message = "Consistent drift detected: ${String.format("%.1f", avgDrift)}° per session. Apply compensation?"
            )
        } else if (variance > maxVariance) {
            DriftAnalysis(
                hasEnoughData = true,
                sampleCount = driftHistory.size,
                averageDrift = avgDrift,
                variance = variance,
                recommendedCompensation = null,
                message = "Drift is inconsistent (variance: ${String.format("%.1f", variance)}°). No auto-compensation recommended."
            )
        } else {
            DriftAnalysis(
                hasEnoughData = true,
                sampleCount = driftHistory.size,
                averageDrift = avgDrift,
                variance = variance,
                recommendedCompensation = null,
                message = "Minimal drift detected (${String.format("%.1f", avgDrift)}°). No compensation needed."
            )
        }
    }
    
    /**
     * Convert drift in degrees to steps.
     */
    fun driftDegreesToSteps(driftDegrees: Float): Float {
        return driftDegrees * STEPS_PER_DEGREE
    }
    
    /**
     * Result of drift pattern analysis.
     */
    data class DriftAnalysis(
        val hasEnoughData: Boolean,
        val sampleCount: Int,
        val averageDrift: Float,
        val variance: Float,
        val recommendedCompensation: Float?,
        val message: String
    )
}
