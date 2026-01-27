package com.example.mejustmix.services

import com.example.mejustmix.data.MotorConfig
import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.ui.PumpConfig
import com.example.mejustmix.utils.PulseCompensationCalculator
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Result of step-based dispense calculation.
 * 
 * @property commands List of G-code commands to execute
 * @property actualVolumesMl Map of pump name to actual volume that will be dispensed
 * @property stepCounts Map of pump name to step count
 * @property pulseCounts Map of pump name to pulse count (fractional)
 * @property endPhases Map of pump name to ending phase position in steps
 */
data class StepDispenseResult(
    val commands: List<String>,
    val actualVolumesMl: Map<String, Float>,
    val stepCounts: Map<String, Float>,
    val pulseCounts: Map<String, Float>,
    val endPhases: Map<String, Float>
)

/**
 * G-code generator that works in native motor steps.
 * 
 * ## Why Steps Instead of Distance?
 * 
 * Traditional CNC machines use distance units (mm or inches) and rely on
 * $10X (steps/mm) calibration. This works well for linear motion but has
 * problems for peristaltic pumps:
 * 
 * 1. **No Physical Distance**: Pumps don't move linearly - they rotate
 * 2. **Calibration Drift**: mL/mm conversion introduces rounding errors
 * 3. **Pulse Alignment**: We need exact step counts for whole pulses
 * 4. **Phase Tracking**: Steps are the native unit for phase calculations
 * 
 * By configuring FluidNC with $10X=1.0 (1 step = 1 unit), we get:
 * - Exact step counts in G-code
 * - No conversion errors
 * - Direct pulse alignment
 * - Clean phase tracking
 * 
 * ## FluidNC Configuration Required
 * 
 * For each axis, set:
 * ```
 * $10X = 1.0      ; 1 step = 1 unit
 * $11X = 360000   ; Max rate (steps/min) = maxStepsPerSec * 60
 * $12X = 2000     ; Acceleration (steps/sec²)
 * ```
 */
object StepBasedGCodeGenerator {
    
    /**
     * Maximum feed rate clamp (steps/min).
     * Prevents accidental over-speed from bad config.
     */
    private const val ABSOLUTE_MAX_FEED_RATE = 720000 // 12000 steps/sec * 60
    
    /**
     * Generate FluidNC initialization commands to configure axes for step-based control.
     * 
     * @param pumps List of pump configurations
     * @param motorConfig Motor configuration with timing parameters
     * @return List of FluidNC $ commands
     */
    fun generateAxisConfigCommands(
        pumps: List<PumpConfig>,
        motorConfig: MotorConfig
    ): List<String> {
        val commands = mutableListOf<String>()
        
        for (pump in pumps) {
            // $10X = steps per unit (1.0 = native steps mode)
            commands.add("$10${pump.axisIndex}=1.0")
            
            // $11X = max feed rate (steps/min)
            commands.add("$11${pump.axisIndex}=${motorConfig.maxFeedRateStepsPerMin}")
            
            // $12X = acceleration (steps/sec²)
            commands.add("$12${pump.axisIndex}=${motorConfig.accelerationStepsPerSec2}")
        }
        
        return commands
    }
    
    /**
     * Generate G-code for dispensing a paint mix using step-based control.
     * 
     * @param mix Paint mix ratios (normalized to sum = 1.0)
     * @param totalVolumeMl Total volume to dispense
     * @param pumps Pump configurations with mlPerPulse calibration
     * @param motorConfig Motor configuration for step calculations
     * @param retractionSteps Steps to retract after dispensing
     * @param flowRateStepsPerSec Target flow rate in steps/second
     * @param currentPhases Current phase positions for each pump (steps from pulse boundary)
     * @param useCompensation Whether to use pulse velocity compensation
     * @param compensationProfile Optional pulse compensation profile
     * @return StepDispenseResult with commands and tracking data
     */
    fun generateMixDispense(
        mix: PaintMix,
        totalVolumeMl: Float,
        pumps: List<PumpConfig>,
        motorConfig: MotorConfig,
        retractionSteps: Float = 0f,
        flowRateStepsPerSec: Float = 1000f,
        currentPhases: Map<String, Float> = emptyMap(),
        useCompensation: Boolean = false,
        compensationProfile: PulseCompensationCalculator.PulseProfile? = null
    ): StepDispenseResult {
        require(pumps.size >= 5) { "Requires at least 5 pumps (CMYKW), got ${pumps.size}" }
        
        val commands = mutableListOf<String>()
        val actualVolumes = mutableMapOf<String, Float>()
        val stepCounts = mutableMapOf<String, Float>()
        val pulseCounts = mutableMapOf<String, Float>()
        val endPhases = mutableMapOf<String, Float>()
        val activeAxes = mutableListOf<String>()
        
        // Helper to get pump by name
        fun getPump(name: String, defaultIndex: Int): PumpConfig {
            return pumps.find { it.name.equals(name, ignoreCase = true) }
                ?: pumps.getOrElse(defaultIndex) { pumps[0] }
        }
        
        // Map mix ratios to pumps
        val pumpData = listOf(
            "Cyan" to (getPump("Cyan", 0) to mix.cyan),
            "Magenta" to (getPump("Magenta", 1) to mix.magenta),
            "Yellow" to (getPump("Yellow", 2) to mix.yellow),
            "Black" to (getPump("Black", 3) to mix.black),
            "White" to (getPump("White", 4) to mix.white)
        )
        
        // Calculate steps for each pump using motor config
        for ((name, data) in pumpData) {
            val (pump, ratio) = data
            val volumeNeeded = ratio * totalVolumeMl
            
            if (volumeNeeded > 0.001f && pump.mlPerPulse > 0f) {
                // Calculate pulses needed
                val pulses = volumeNeeded / pump.mlPerPulse
                // Convert to steps using motor config
                val steps = pulses * motorConfig.stepsPerPulse
                
                stepCounts[name] = steps
                pulseCounts[name] = pulses
                actualVolumes[name] = pulses * pump.mlPerPulse
                activeAxes.add(pump.axis)
            }
        }
        
        if (activeAxes.isEmpty()) {
            // Nothing to dispense
            return StepDispenseResult(
                commands = listOf("G91", "G90"),
                actualVolumesMl = emptyMap(),
                stepCounts = emptyMap(),
                pulseCounts = emptyMap(),
                endPhases = currentPhases
            )
        }
        
        // Reset position tracking
        val allAxes = pumps.map { it.axis }.take(5).joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes")
        commands.add("G91")  // Relative mode
        
        // Calculate feed rate (steps/min)
        val baseFeedRate = (flowRateStepsPerSec * 60f).toInt()
            .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
            .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
        
        if (useCompensation && compensationProfile != null) {
            // Compensated dispensing - each pump gets individual segments
            for ((name, data) in pumpData) {
                val (pump, _) = data
                val totalSteps = stepCounts[name] ?: continue
                
                if (totalSteps > 0.01f) {
                    val initialPhase = currentPhases[name] ?: 0f
                    
                    // Generate compensated segments using motor config's stepsPerPulse
                    val segments = PulseCompensationCalculator.generateCompensatedSegments(
                        totalSteps = totalSteps,
                        stepsPerPillow = motorConfig.stepsPerPulse,
                        baseFeedRate = baseFeedRate,
                        profile = compensationProfile,
                        initialPhaseSteps = initialPhase
                    )
                    
                    // Merge consecutive same-speed segments
                    val mergedSegments = PulseCompensationCalculator.mergeConsecutiveSegments(segments)
                    
                    // Generate G-code for each segment
                    for (segment in mergedSegments) {
                        if (segment.steps > 0.01f) {
                            val feedClamped = segment.feedRate
                                .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
                                .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
                            
                            commands.add(
                                "G1 ${pump.axis}${formatSteps(segment.steps)} F$feedClamped"
                            )
                        }
                    }
                    
                    // Calculate ending phase
                    val endPhase = (initialPhase + totalSteps) % motorConfig.stepsPerPulse
                    endPhases[name] = endPhase
                }
            }
        } else {
            // Simple dispensing - all pumps in one command
            val dispenseLine = StringBuilder("G1 ")
            var hasMovement = false
            
            for ((name, data) in pumpData) {
                val (pump, _) = data
                val steps = stepCounts[name] ?: continue
                
                if (steps > 0.01f) {
                    dispenseLine.append("${pump.axis}${formatSteps(steps)} ")
                    hasMovement = true
                    
                    // Calculate ending phase
                    val initialPhase = currentPhases[name] ?: 0f
                    val endPhase = (initialPhase + steps) % motorConfig.stepsPerPulse
                    endPhases[name] = endPhase
                }
            }
            
            if (hasMovement) {
                dispenseLine.append("F$baseFeedRate")
                commands.add(dispenseLine.toString().trim())
            }
        }
        
        // Retraction
        if (retractionSteps > 0f && activeAxes.isNotEmpty()) {
            val retractFeed = baseFeedRate.coerceAtMost(motorConfig.maxFeedRateStepsPerMin / 2)
            val retractCmd = StringBuilder("G1 ")
            
            for (axis in activeAxes.distinct()) {
                retractCmd.append("${axis}${formatSteps(-retractionSteps)} ")
            }
            retractCmd.append("F$retractFeed")
            commands.add(retractCmd.toString().trim())
        }
        
        commands.add("G90")  // Back to absolute
        
        return StepDispenseResult(
            commands = commands,
            actualVolumesMl = actualVolumes,
            stepCounts = stepCounts,
            pulseCounts = pulseCounts,
            endPhases = endPhases
        )
    }
    
    /**
     * Generate G-code to prime a single pump.
     * 
     * @param pump Pump configuration
     * @param motorConfig Motor configuration
     * @param volumeMl Volume to prime in milliliters
     * @param flowRateStepsPerSec Flow rate in steps/second
     * @param currentPhase Current phase position in steps
     * @param useCompensation Whether to use pulse compensation
     * @param compensationProfile Optional compensation profile
     * @return Pair of (commands, endingPhase)
     */
    fun generatePrime(
        pump: PumpConfig,
        motorConfig: MotorConfig,
        volumeMl: Float,
        flowRateStepsPerSec: Float = 1000f,
        currentPhase: Float = 0f,
        useCompensation: Boolean = false,
        compensationProfile: PulseCompensationCalculator.PulseProfile? = null
    ): Pair<List<String>, Float> {
        if (pump.mlPerPulse <= 0f || volumeMl <= 0f) {
            return Pair(emptyList(), currentPhase)
        }
        
        val commands = mutableListOf<String>()
        val pulses = volumeMl / pump.mlPerPulse
        val totalSteps = pulses * motorConfig.stepsPerPulse
        
        val baseFeedRate = (flowRateStepsPerSec * 60f).toInt()
            .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
            .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
        
        commands.add("G92 ${pump.axis}0")
        commands.add("G91")
        
        if (useCompensation && compensationProfile != null) {
            val segments = PulseCompensationCalculator.generateCompensatedSegments(
                totalSteps = totalSteps,
                stepsPerPillow = motorConfig.stepsPerPulse,
                baseFeedRate = baseFeedRate,
                profile = compensationProfile,
                initialPhaseSteps = currentPhase
            )
            
            val mergedSegments = PulseCompensationCalculator.mergeConsecutiveSegments(segments)
            
            for (segment in mergedSegments) {
                if (segment.steps > 0.01f) {
                    val feedClamped = segment.feedRate
                        .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
                        .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
                    
                    commands.add(
                        "G1 ${pump.axis}${formatSteps(segment.steps)} F$feedClamped"
                    )
                }
            }
        } else {
            commands.add("G1 ${pump.axis}${formatSteps(totalSteps)} F$baseFeedRate")
        }
        
        commands.add("G90")
        
        val endPhase = (currentPhase + totalSteps) % motorConfig.stepsPerPulse
        return Pair(commands, endPhase)
    }
    
    /**
     * Generate G-code to dispense exact number of pulses.
     * 
     * @param pump Pump configuration
     * @param motorConfig Motor configuration
     * @param pulseCount Number of complete pulses to dispense
     * @param flowRateStepsPerSec Flow rate in steps/second
     * @param currentPhase Current phase position in steps
     * @param useCompensation Whether to use pulse compensation
     * @param compensationProfile Optional compensation profile
     * @return Pair of (commands, endingPhase)
     */
    fun generatePulseDispense(
        pump: PumpConfig,
        motorConfig: MotorConfig,
        pulseCount: Int,
        flowRateStepsPerSec: Float = 1000f,
        currentPhase: Float = 0f,
        useCompensation: Boolean = false,
        compensationProfile: PulseCompensationCalculator.PulseProfile? = null
    ): Pair<List<String>, Float> {
        val totalSteps = pulseCount * motorConfig.stepsPerPulse
        
        val commands = mutableListOf<String>()
        val baseFeedRate = (flowRateStepsPerSec * 60f).toInt()
            .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
            .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
        
        commands.add("G92 ${pump.axis}0")
        commands.add("G91")
        
        if (useCompensation && compensationProfile != null) {
            val segments = PulseCompensationCalculator.generateCompensatedSegments(
                totalSteps = totalSteps,
                stepsPerPillow = motorConfig.stepsPerPulse,
                baseFeedRate = baseFeedRate,
                profile = compensationProfile,
                initialPhaseSteps = currentPhase
            )
            
            val mergedSegments = PulseCompensationCalculator.mergeConsecutiveSegments(segments)
            
            for (segment in mergedSegments) {
                if (segment.steps > 0.01f) {
                    val feedClamped = segment.feedRate
                        .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
                        .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
                    
                    commands.add(
                        "G1 ${pump.axis}${formatSteps(segment.steps)} F$feedClamped"
                    )
                }
            }
        } else {
            commands.add("G1 ${pump.axis}${formatSteps(totalSteps)} F$baseFeedRate")
        }
        
        commands.add("G90")
        
        // For exact pulse count, ending phase equals starting phase
        // (integer pulses = full rotations within phase cycle)
        val endPhase = (currentPhase + totalSteps) % motorConfig.stepsPerPulse
        return Pair(commands, endPhase)
    }
    
    /**
     * Generate G-code to move pump to nearest pulse boundary (home position).
     * 
     * @param pump Pump configuration
     * @param motorConfig Motor configuration
     * @param currentPhase Current phase position in steps [0, stepsPerPulse)
     * @param flowRateStepsPerSec Flow rate in steps/second
     * @return Pair of (commands, volumeDispensed)
     */
    fun generateHomeToNextBoundary(
        pump: PumpConfig,
        motorConfig: MotorConfig,
        currentPhase: Float,
        flowRateStepsPerSec: Float = 1000f
    ): Pair<List<String>, Float> {
        // Calculate steps to next boundary (always move forward)
        val stepsToHome = if (currentPhase > 0.01f) {
            motorConfig.stepsPerPulse - currentPhase
        } else {
            0f
        }
        
        if (stepsToHome < 0.5f) {
            // Already at home
            return Pair(emptyList(), 0f)
        }
        
        val baseFeedRate = (flowRateStepsPerSec * 60f).toInt()
            .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
            .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
        
        val commands = listOf(
            "G92 ${pump.axis}0",
            "G91",
            "G1 ${pump.axis}${formatSteps(stepsToHome)} F$baseFeedRate",
            "G90"
        )
        
        val volumeDispensed = (stepsToHome / motorConfig.stepsPerPulse) * pump.mlPerPulse
        return Pair(commands, volumeDispensed)
    }
    
    /**
     * Generate G-code to jog a pump by specified steps.
     * 
     * @param pump Pump configuration
     * @param motorConfig Motor configuration
     * @param steps Number of steps (positive = forward, negative = backward)
     * @param feedRate Feed rate in steps/second
     * @return List of G-code commands
     */
    fun generateJog(
        pump: PumpConfig,
        motorConfig: MotorConfig,
        steps: Float,
        feedRate: Float = 500f
    ): List<String> {
        val feedRateStepsPerMin = (feedRate * 60f).toInt()
            .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
            .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
        
        return listOf(
            "G91",
            "G1 ${pump.axis}${formatSteps(steps)} F$feedRateStepsPerMin",
            "G90"
        )
    }
    
    /**
     * Generate G-code to retract all active pumps.
     * 
     * @param pumps List of pump configurations
     * @param motorConfig Motor configuration
     * @param retractionSteps Steps to retract
     * @param feedRate Feed rate in steps/second
     * @return List of G-code commands
     */
    fun generateRetractAll(
        pumps: List<PumpConfig>,
        motorConfig: MotorConfig,
        retractionSteps: Float,
        feedRate: Float = 1000f
    ): List<String> {
        if (retractionSteps <= 0f) return emptyList()
        
        val feedRateStepsPerMin = (feedRate * 60f).toInt()
            .coerceAtMost(motorConfig.maxFeedRateStepsPerMin)
            .coerceAtMost(ABSOLUTE_MAX_FEED_RATE)
        
        val retractParts = pumps.joinToString(" ") { pump ->
            "${pump.axis}${formatSteps(-retractionSteps)}"
        }
        
        return listOf(
            "G91",
            "G1 $retractParts F$feedRateStepsPerMin",
            "G90"
        )
    }
    
    /**
     * Format steps for G-code (consistent decimal places).
     */
    private fun formatSteps(steps: Float): String {
        return String.format(Locale.US, "%.2f", steps)
    }
}
