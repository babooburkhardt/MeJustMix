package com.example.mejustmix.data

/**
 * Motor and stepper driver configuration.
 * 
 * This data class holds all the parameters needed to calculate motor movements
 * in native steps rather than distance units. Using steps directly eliminates
 * calibration drift and ensures exact pulse alignment.
 * 
 * ## FluidNC Configuration
 * 
 * When using steps-based control, FluidNC should be configured with:
 * - $10X = 1.0 (1 step = 1 unit of travel)
 * - $11X = maxStepsPerSec * 60 (max feed rate in steps/min)
 * - $12X = acceleration in steps/sec²
 * 
 * ## Motor Math
 * 
 * For a typical NEMA 17 with microstepping:
 * - Native steps/rev = 200 (1.8° step angle)
 * - With 16x microstepping: 3200 steps/rev
 * - With 4:1 gear reduction: 12800 steps/rev at output
 * - With 3 rollers: 4266.67 steps/pulse
 */
data class MotorConfig(
    // ==================== STEPPER DRIVER SETTINGS ====================
    
    /**
     * Microstepping divisor (1, 2, 4, 8, 16, 32, 64, 128, 256).
     * Higher values = smoother motion but lower torque.
     * Common values: 16 (silent), 32 (very smooth), 8 (high torque).
     */
    val microsteps: Int = 16,
    
    /**
     * Motor's native step angle in degrees.
     * - 1.8° = 200 steps/rev (most common, NEMA 17)
     * - 0.9° = 400 steps/rev (high precision)
     */
    val stepAngleDegrees: Float = 1.8f,
    
    // ==================== MECHANICAL SETTINGS ====================
    
    /**
     * Gear reduction ratio between motor and pump head.
     * E.g., 4.0 means motor turns 4x for every pump revolution.
     */
    val gearReduction: Float = 4.0f,
    
    /**
     * Number of rollers on the peristaltic pump head.
     * Determines pulses per revolution.
     */
    val rollerCount: Int = 3,
    
    // ==================== SPEED LIMITS ====================
    
    /**
     * Maximum motor speed in steps per second.
     * Limited by stepper driver and controller (ESP32/TinyBee).
     * 
     * MKS TinyBee (ESP32 + FluidNC) limits:
     * - Total across all axes: ~80,000-120,000 steps/sec
     * - Per axis safe limit: ~10,000-40,000 steps/sec depending on microsteps
     * 
     * Higher microstepping requires lower step rates for same RPM!
     */
    val maxStepsPerSec: Int = 6000,
    
    /**
     * Acceleration in steps per second squared.
     * Higher = faster speed changes but more stress on motor.
     * Typical range: 500-3000 steps/sec².
     */
    val accelerationStepsPerSec2: Int = 2000,
    
    /**
     * Jerk limit (rate of change of acceleration) in steps/sec³.
     * Set to 0 to disable jerk limiting.
     * Helps reduce vibration but slows motion profiles.
     */
    val jerkStepsPerSec3: Int = 0,
    
    // ==================== FLUIDNC AXIS MAPPING ====================
    
    /**
     * Whether to invert motor direction.
     * Set true if pump runs backwards.
     */
    val invertDirection: Boolean = false,
    
    /**
     * Enable soft limits (prevents over-travel).
     * Usually disabled for continuous rotation pumps.
     */
    val softLimitsEnabled: Boolean = false,
    
    /**
     * Maximum travel in steps (only used if soft limits enabled).
     */
    val maxTravelSteps: Int = 1000000
) {
    // ==================== CALCULATED PROPERTIES ====================
    
    /**
     * Native motor steps per revolution (before microstepping).
     */
    val nativeStepsPerRev: Int
        get() = (360f / stepAngleDegrees).toInt()
    
    /**
     * Total microsteps per motor revolution.
     */
    val microstepsPerMotorRev: Int
        get() = nativeStepsPerRev * microsteps
    
    /**
     * Total microsteps per pump revolution (after gear reduction).
     */
    val stepsPerPumpRev: Float
        get() = microstepsPerMotorRev * gearReduction
    
    /**
     * Microsteps per pulse (per roller engagement).
     * This is the fundamental unit for pulse-based dispensing.
     */
    val stepsPerPulse: Float
        get() = stepsPerPumpRev / rollerCount
    
    /**
     * Degrees of pump rotation per pulse.
     */
    val degreesPerPulse: Float
        get() = 360f / rollerCount
    
    /**
     * Steps per degree of pump rotation.
     */
    val stepsPerDegree: Float
        get() = stepsPerPumpRev / 360f
    
    /**
     * Maximum feed rate in steps per minute (for FluidNC $11X).
     * Automatically clamped to TinyBee-safe limits based on microstepping.
     */
    val maxFeedRateStepsPerMin: Int
        get() {
            val requested = maxStepsPerSec * 60
            val safeLimit = tinyBeeSafeLimitStepsPerMin
            return minOf(requested, safeLimit)
        }
    
    /**
     * MKS TinyBee safe step rate limit per axis, accounting for all axes running.
     * 
     * Base calculation: 80,000 total / 5 axes = 16,000 per axis
     * But higher microstepping has additional overhead, so we scale down further.
     * 
     * These are conservative limits assuming all 5 axes may run simultaneously.
     */
    val tinyBeeSafeLimitStepsPerSec: Int
        get() {
            // Base per-axis limit when all 5 run together
            // 80,000 total / 5 axes = 16,000 per axis
            val basePerAxis = TINYBEE_TOTAL_CAPACITY_STEPS_PER_SEC / SIMULTANEOUS_AXES
            
            // Scale down for higher microstepping (more CPU overhead per step)
            return when {
                microsteps <= 8 -> minOf(basePerAxis, 12000)   // 12,000 st/s max
                microsteps <= 16 -> minOf(basePerAxis, 10000)  // 10,000 st/s max
                microsteps <= 32 -> minOf(basePerAxis, 8000)   // 8,000 st/s max
                microsteps <= 64 -> minOf(basePerAxis, 6000)   // 6,000 st/s max
                else -> minOf(basePerAxis, 4000)               // 4,000 st/s for 128x/256x
            }
        }
    
    /**
     * TinyBee safe limit in steps per minute.
     */
    val tinyBeeSafeLimitStepsPerMin: Int
        get() = tinyBeeSafeLimitStepsPerSec * 60
    
    /**
     * Whether current maxStepsPerSec exceeds TinyBee safe limit.
     */
    val exceedsTinyBeeLimit: Boolean
        get() = maxStepsPerSec > tinyBeeSafeLimitStepsPerSec
    
    // ==================== FLUIDNC CONFIGURATION ====================
    
    /**
     * Generate FluidNC configuration commands for this motor.
     * 
     * @param axisIndex The axis index (0=X, 1=Y, 2=Z, 3=A, 4=B, 5=C)
     * @return List of FluidNC $ commands to configure the axis
     */
    fun generateFluidNCConfig(axisIndex: Int): List<String> {
        val commands = mutableListOf<String>()
        
        // $10X = steps per unit (1.0 = native steps)
        commands.add("$10$axisIndex=1.0")
        
        // $11X = max feed rate (steps/min)
        commands.add("$11$axisIndex=$maxFeedRateStepsPerMin")
        
        // $12X = acceleration (steps/sec²)
        commands.add("$12$axisIndex=$accelerationStepsPerSec2")
        
        // $13X = max travel (only if soft limits enabled)
        if (softLimitsEnabled) {
            commands.add("$13$axisIndex=$maxTravelSteps")
        }
        
        return commands
    }
    
    companion object {
        /**
         * MKS TinyBee total step rate capacity.
         * ESP32 running FluidNC can reliably handle ~80,000-100,000 total steps/sec
         * across ALL axes combined.
         */
        const val TINYBEE_TOTAL_CAPACITY_STEPS_PER_SEC = 80000
        
        /**
         * Number of axes that may run simultaneously.
         * With 5 pumps potentially dispensing at once (SimulMix), we divide capacity by 5.
         */
        const val SIMULTANEOUS_AXES = 5
        
        /**
         * Default motor configuration for typical peristaltic pump setup.
         * NEMA 17, 16x microstepping, 4:1 gear reduction, 3 rollers.
         */
        val DEFAULT = MotorConfig()
        
        /**
         * High-precision configuration with 32x microstepping.
         */
        val HIGH_PRECISION = MotorConfig(
            microsteps = 32,
            maxStepsPerSec = 4000, // Lower speed due to more steps
            accelerationStepsPerSec2 = 1500
        )
        
        /**
         * High-torque configuration with 8x microstepping.
         */
        val HIGH_TORQUE = MotorConfig(
            microsteps = 8,
            maxStepsPerSec = 8000,
            accelerationStepsPerSec2 = 3000
        )
        
        /**
         * Common microstepping options for UI.
         */
        val MICROSTEP_OPTIONS = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256)
        
        /**
         * Common step angles for UI.
         */
        val STEP_ANGLE_OPTIONS = listOf(0.9f, 1.8f, 3.6f, 7.5f)
    }
}

/**
 * Extension function to calculate steps for a given volume.
 * 
 * @param volumeMl Volume to dispense in milliliters
 * @param mlPerPulse Calibrated milliliters per pulse
 * @return Number of steps to dispense
 */
fun MotorConfig.stepsForVolume(volumeMl: Float, mlPerPulse: Float): Float {
    if (mlPerPulse <= 0f) return 0f
    val pulses = volumeMl / mlPerPulse
    return pulses * stepsPerPulse
}

/**
 * Extension function to calculate volume for a given number of steps.
 * 
 * @param steps Number of motor steps
 * @param mlPerPulse Calibrated milliliters per pulse
 * @return Volume in milliliters
 */
fun MotorConfig.volumeForSteps(steps: Float, mlPerPulse: Float): Float {
    val pulses = steps / stepsPerPulse
    return pulses * mlPerPulse
}
