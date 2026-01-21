package com.example.mejustmix.utils

import com.example.mejustmix.ui.PumpConfig
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Utility object for pulse mode calculations.
 * Handles conversions between steps, pulses, and milliliters.
 */
object PulseModeCalculator {
    
    /**
     * Motor specifications for the peristaltic pump system
     */
    object MotorSpecs {
        const val STEP_ANGLE_DEGREES = 1.8f       // NEMA 17 typical
        const val GEAR_REDUCTION = 4f              // 1:4 reduction
        const val ROLLER_COUNT = 3                 // 3-roller peristaltic pump
        
        // Calculated values
        val STEPS_PER_MOTOR_REV = 360f / STEP_ANGLE_DEGREES  // 200 steps
        val STEPS_PER_PUMP_REV = STEPS_PER_MOTOR_REV * GEAR_REDUCTION  // 800 steps
        val STEPS_PER_PULSE = STEPS_PER_PUMP_REV / ROLLER_COUNT  // ~266.67 steps
    }
    
    /**
     * Calculate steps per pulse based on motor specifications.
     * This determines how many motor steps equal one complete roller rotation.
     * 
     * @param stepAngle Motor step angle in degrees (default: 1.8° for NEMA 17)
     * @param gearReduction Gear reduction ratio (default: 4 for 1:4)
     * @param rollerCount Number of pump rollers (default: 3)
     * @return Steps required for one complete pulse
     */
    fun calculateStepsPerPulse(
        stepAngle: Float = MotorSpecs.STEP_ANGLE_DEGREES,
        gearReduction: Float = MotorSpecs.GEAR_REDUCTION,
        rollerCount: Int = MotorSpecs.ROLLER_COUNT
    ): Float {
        val stepsPerMotorRevolution = 360f / stepAngle
        val stepsPerPumpRevolution = stepsPerMotorRevolution * gearReduction
        return stepsPerPumpRevolution / rollerCount
    }
    
    /**
     * Calculate milliliters from pulse count.
     * 
     * @param pulseCount Number of pulses to dispense
     * @param mlPerPulse Calibrated mL per pulse value
     * @return Volume in milliliters
     */
    fun pulsesToMilliliters(pulseCount: Int, mlPerPulse: Float): Float {
        return pulseCount * mlPerPulse
    }
    
    /**
     * Calculate pulse count needed for target volume.
     * Rounds to nearest whole pulse.
     * 
     * @param targetMl Target volume in milliliters
     * @param mlPerPulse Calibrated mL per pulse value
     * @return Number of whole pulses required
     */
    fun millilitersToPulses(targetMl: Float, mlPerPulse: Float): Int {
        if (mlPerPulse <= 0) return 0
        return (targetMl / mlPerPulse).roundToInt()
    }
    
    /**
     * Calculate steps needed for a given number of pulses.
     * 
     * @param pulseCount Number of pulses
     * @param stepsPerPulse Steps per pulse (from motor specs or calibration)
     * @return Total steps required
     */
    fun pulsesToSteps(pulseCount: Int, stepsPerPulse: Float): Int {
        return (pulseCount * stepsPerPulse).roundToInt()
    }
    
    /**
     * Calculate pulse count from steps.
     * 
     * @param steps Total motor steps
     * @param stepsPerPulse Steps per pulse (from motor specs or calibration)
     * @return Number of whole pulses
     */
    fun stepsToPulses(steps: Int, stepsPerPulse: Float): Int {
        if (stepsPerPulse <= 0) return 0
        return (steps / stepsPerPulse).roundToInt()
    }
    
    /**
     * Round steps to nearest pulse boundary.
     * Ensures steps always align with complete pulses.
     * 
     * @param steps Current step count
     * @param stepsPerPulse Steps per pulse
     * @return Steps rounded to nearest pulse boundary
     */
    fun snapToPulseBoundary(steps: Float, stepsPerPulse: Float): Float {
        val pulses = (steps / stepsPerPulse).roundToInt()
        return pulses * stepsPerPulse
    }
    
    /**
     * Calculate how many steps away from nearest pulse boundary.
     * Returns negative if before boundary, positive if after.
     * 
     * @param steps Current step position
     * @param stepsPerPulse Steps per pulse
     * @return Steps offset from nearest boundary
     */
    fun stepsFromPulseBoundary(steps: Float, stepsPerPulse: Float): Float {
        val remainder = steps % stepsPerPulse
        return if (remainder > stepsPerPulse / 2) {
            remainder - stepsPerPulse
        } else {
            remainder
        }
    }
    
    /**
     * Check if current position is at a pulse boundary.
     * 
     * @param steps Current step position
     * @param stepsPerPulse Steps per pulse
     * @param tolerance Acceptable deviation in steps (default: 1.0)
     * @return True if within tolerance of a pulse boundary
     */
    fun isAtPulseBoundary(
        steps: Float,
        stepsPerPulse: Float,
        tolerance: Float = 1f
    ): Boolean {
        val offset = abs(stepsFromPulseBoundary(steps, stepsPerPulse))
        return offset < tolerance
    }
    
    /**
     * Calculate minimum volume dispensable in pulse mode.
     * 
     * @param mlPerPulse Calibrated mL per pulse
     * @param minimumPulses Minimum pulse count allowed (typically 1-3)
     * @return Minimum dispensable volume in mL
     */
    fun minimumDispensableVolume(mlPerPulse: Float, minimumPulses: Int = 1): Float {
        return mlPerPulse * minimumPulses
    }
    
    /**
     * Validate if target volume meets minimum pulse requirement.
     * 
     * @param targetMl Target volume in milliliters
     * @param mlPerPulse Calibrated mL per pulse
     * @param minimumPulses Minimum pulse count allowed
     * @return True if target meets minimum
     */
    fun meetsMinimumPulseRequirement(
        targetMl: Float,
        mlPerPulse: Float,
        minimumPulses: Int
    ): Boolean {
        val pulses = millilitersToPulses(targetMl, mlPerPulse)
        return pulses >= minimumPulses
    }
    
    /**
     * Calculate actual volume that will be dispensed after rounding to whole pulses.
     * 
     * @param requestedMl Requested volume
     * @param mlPerPulse Calibrated mL per pulse
     * @return Actual volume that will be dispensed
     */
    fun actualDispensedVolume(requestedMl: Float, mlPerPulse: Float): Float {
        val pulses = millilitersToPulses(requestedMl, mlPerPulse)
        return pulsesToMilliliters(pulses, mlPerPulse)
    }
    
    /**
     * Calculate dispensing error introduced by pulse rounding.
     * 
     * @param requestedMl Requested volume
     * @param mlPerPulse Calibrated mL per pulse
     * @return Absolute error in mL
     */
    fun dispensingError(requestedMl: Float, mlPerPulse: Float): Float {
        val actual = actualDispensedVolume(requestedMl, mlPerPulse)
        return abs(actual - requestedMl)
    }
    
    /**
     * Calculate dispensing error as percentage.
     * 
     * @param requestedMl Requested volume
     * @param mlPerPulse Calibrated mL per pulse
     * @return Error as percentage (0-100)
     */
    fun dispensingErrorPercent(requestedMl: Float, mlPerPulse: Float): Float {
        if (requestedMl == 0f) return 0f
        val error = dispensingError(requestedMl, mlPerPulse)
        return (error / requestedMl) * 100f
    }
}

/**
 * Extension functions for PumpConfig to add pulse mode utilities
 */

/**
 * Calculate steps needed to dispense target volume using this pump's calibration.
 */
fun stepsForVolume(targetMl: Float, pump: PumpConfig): Int {
    val pulses = PulseModeCalculator.millilitersToPulses(targetMl, pump.mlPerPulse)
    return PulseModeCalculator.pulsesToSteps(pulses, pump.stepsPerPulse)
}

/**
 * Calculate actual volume that will be dispensed for this pump.
 */
fun actualVolumeForPump(requestedMl: Float, pump: PumpConfig): Float {
    return PulseModeCalculator.actualDispensedVolume(requestedMl, pump.mlPerPulse)
}

/**
 * Check if this pump is currently at a pulse boundary (homed).
 */
fun isAtHome(pump: PumpConfig): Boolean {
    return PulseModeCalculator.isAtPulseBoundary(
        pump.pulseHomeOffset,
        pump.stepsPerPulse
    )
}

/**
 * Get steps needed to move this pump to nearest home position.
 */
fun stepsToHome(pump: PumpConfig): Int {
    val currentOffset = pump.pulseHomeOffset
    val nearestHome = PulseModeCalculator.snapToPulseBoundary(
        currentOffset,
        pump.stepsPerPulse
    )
    return (nearestHome - currentOffset).roundToInt()
}
