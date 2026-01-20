package com.example.mejustmix.services

import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.ui.PumpConfig
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Result of pulse calculation - includes actual volume that will be dispensed
 */
data class PulseDispenseResult(
    val commands: List<String>,
    val actualVolumeMl: Float,
    val pulseCounts: Map<String, Int>,  // Pump name -> pulse count
    val scaleFactor: Float              // How much we scaled up (1.0 = no scaling)
)

object GCodeGenerator {

    // LIMITATION FIX: Safe upper limit for stepper motors.
    private const val MAX_SAFE_FEED_RATE = 12000

    /**
     * Generates G-code for dispensing a paint mixture.
     * 
     * @param usePulseMode If true, dispense in whole pulses only
     * @param pulseMinimum Minimum pulses for any non-zero component (default 1)
     */
    fun generateMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float,
        usePulseMode: Boolean = false,
        pulseMinimum: Int = 1
    ): List<String> {
        return if (usePulseMode) {
            generatePulseMixingScript(mix, totalVolumeMl, retractionSteps, pumps, flowRateMlPerSec, pulseMinimum).commands
        } else {
            generateStandardMixingScript(mix, totalVolumeMl, retractionSteps, pumps, flowRateMlPerSec)
        }
    }

    /**
     * Standard mL-based dispensing (original behavior).
     */
    private fun generateStandardMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float
    ): List<String> {
        if (pumps.size < 5) {
            throw IllegalArgumentException("Requires at least 5 pumps (CMYK+W), got ${pumps.size}")
        }

        val commands = mutableListOf<String>()
        val activeAxes = mutableListOf<String>() 
        
        val allAxes = pumps.map { it.axis }.take(5).joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes")
        commands.add("G91")

        fun getPump(name: String, defaultIndex: Int): PumpConfig {
            return pumps.find { it.name.equals(name, ignoreCase = true) } 
                ?: pumps.getOrElse(defaultIndex) { pumps[0] }
        }

        val dispenseLine = StringBuilder("G1 ")
        var hasMovement = false
        
        val pumpData = listOf(
            getPump("Cyan", 0) to mix.cyan,
            getPump("Magenta", 1) to mix.magenta,
            getPump("Yellow", 2) to mix.yellow,
            getPump("Black", 3) to mix.black,
            getPump("White", 4) to mix.white
        )

        for ((pump, ratio) in pumpData) {
            val vol = ratio * totalVolumeMl
            if (vol > 0) {
                val stepsPerMl = pump.calibration.toFloatOrNull() ?: 100f
                val steps = (vol * stepsPerMl).toInt()
                if (steps > 0) {
                    dispenseLine.append("${pump.axis}$steps ")
                    activeAxes.add(pump.axis)
                    hasMovement = true
                }
            }
        }

        if (hasMovement) {
            val avgCal = pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
            val feedRate = (flowRateMlPerSec * avgCal * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            dispenseLine.append("F$feedRate")
            commands.add(dispenseLine.toString().trim())
        }

        if (retractionSteps > 0 && activeAxes.isNotEmpty()) {
            val avgCal = pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
            val retractFeed = (flowRateMlPerSec * avgCal * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            val retractCmd = StringBuilder("G1 ")
            for (axis in activeAxes.distinct()) {
                retractCmd.append("${axis}-${retractionSteps.toInt()} ")
            }
            retractCmd.append("F$retractFeed")
            commands.add(retractCmd.toString().trim())
        }

        commands.add("G90")
        return commands
    }

    /**
     * Pulse-based dispensing - ensures whole pulses only.
     * 
     * Algorithm:
     * 1. Calculate raw pulses needed for each pump
     * 2. Find the smallest non-zero pulse count
     * 3. If smallest < minimum, scale everything up so smallest = minimum
     * 4. Round all to whole pulses
     * 5. Generate G-code using steps = pulses * stepsPerPulse
     */
    fun generatePulseMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float,
        pulseMinimum: Int = 1
    ): PulseDispenseResult {
        if (pumps.size < 5) {
            throw IllegalArgumentException("Requires at least 5 pumps (CMYK+W), got ${pumps.size}")
        }

        fun getPump(name: String, defaultIndex: Int): PumpConfig {
            return pumps.find { it.name.equals(name, ignoreCase = true) } 
                ?: pumps.getOrElse(defaultIndex) { pumps[0] }
        }

        val pumpData = listOf(
            "Cyan" to (getPump("Cyan", 0) to mix.cyan),
            "Magenta" to (getPump("Magenta", 1) to mix.magenta),
            "Yellow" to (getPump("Yellow", 2) to mix.yellow),
            "Black" to (getPump("Black", 3) to mix.black),
            "White" to (getPump("White", 4) to mix.white)
        )

        // Step 1: Calculate raw pulses needed
        val rawPulses = pumpData.associate { (name, data) ->
            val (pump, ratio) = data
            val volumeNeeded = ratio * totalVolumeMl
            val pulsesNeeded = if (pump.mlPerPulse > 0) volumeNeeded / pump.mlPerPulse else 0f
            name to pulsesNeeded
        }

        // Step 2: Find smallest non-zero pulse count
        val nonZeroPulses = rawPulses.values.filter { it > 0.001f }
        
        if (nonZeroPulses.isEmpty()) {
            // No movement needed
            return PulseDispenseResult(
                commands = listOf("G91", "G90"),
                actualVolumeMl = 0f,
                pulseCounts = emptyMap(),
                scaleFactor = 1f
            )
        }

        val smallestPulses = nonZeroPulses.minOrNull() ?: 1f

        // Step 3: Calculate scale factor to ensure minimum pulses
        val scaleFactor = if (smallestPulses < pulseMinimum) {
            pulseMinimum.toFloat() / smallestPulses
        } else {
            1f
        }

        // Step 4: Round all to whole pulses
        val wholePulses = rawPulses.mapValues { (_, raw) ->
            if (raw > 0.001f) {
                (raw * scaleFactor).roundToInt().coerceAtLeast(pulseMinimum)
            } else {
                0
            }
        }

        // Step 5: Generate G-code
        val commands = mutableListOf<String>()
        val activeAxes = mutableListOf<String>()

        val allAxes = pumps.map { it.axis }.take(5).joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes")
        commands.add("G91")

        val dispenseLine = StringBuilder("G1 ")
        var hasMovement = false
        var actualTotalVolume = 0f

        for ((name, data) in pumpData) {
            val (pump, _) = data
            val pulses = wholePulses[name] ?: 0
            
            if (pulses > 0) {
                val steps = (pulses * pump.stepsPerPulse).toInt()
                val volume = pulses * pump.mlPerPulse
                
                dispenseLine.append("${pump.axis}$steps ")
                activeAxes.add(pump.axis)
                hasMovement = true
                actualTotalVolume += volume
            }
        }

        if (hasMovement) {
            // Use stepsPerPulse for feed rate calculation
            val avgStepsPerPulse = pumps.map { it.stepsPerPulse }.average().toFloat()
            val avgMlPerPulse = pumps.map { it.mlPerPulse }.average().toFloat()
            val stepsPerMl = if (avgMlPerPulse > 0) avgStepsPerPulse / avgMlPerPulse else 100f
            val feedRate = (flowRateMlPerSec * stepsPerMl * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            
            dispenseLine.append("F$feedRate")
            commands.add(dispenseLine.toString().trim())
        }

        // Retraction
        if (retractionSteps > 0 && activeAxes.isNotEmpty()) {
            val avgStepsPerPulse = pumps.map { it.stepsPerPulse }.average().toFloat()
            val avgMlPerPulse = pumps.map { it.mlPerPulse }.average().toFloat()
            val stepsPerMl = if (avgMlPerPulse > 0) avgStepsPerPulse / avgMlPerPulse else 100f
            val retractFeed = (flowRateMlPerSec * stepsPerMl * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            
            val retractCmd = StringBuilder("G1 ")
            for (axis in activeAxes.distinct()) {
                retractCmd.append("${axis}-${retractionSteps.toInt()} ")
            }
            retractCmd.append("F$retractFeed")
            commands.add(retractCmd.toString().trim())
        }

        commands.add("G90")

        return PulseDispenseResult(
            commands = commands,
            actualVolumeMl = actualTotalVolume,
            pulseCounts = wholePulses,
            scaleFactor = scaleFactor
        )
    }

    /**
     * Generate G-code to home a pump to the nearest pulse boundary.
     * 
     * This moves the pump forward to complete the current partial pulse,
     * ensuring the roller is at a consistent starting position.
     * 
     * @param pump The pump to home
     * @return G-code commands and the volume that will be dispensed during homing
     */
    fun generatePulseHomeScript(
        pump: PumpConfig,
        flowRateMlPerSec: Float
    ): Pair<List<String>, Float> {
        val stepsToHome = if (pump.pulseHomeOffset > 0) {
            // Move forward to complete the pulse
            pump.stepsPerPulse - pump.pulseHomeOffset
        } else {
            0f
        }
        
        if (stepsToHome <= 0) {
            return Pair(emptyList(), 0f)
        }

        val stepsPerMl = if (pump.mlPerPulse > 0) pump.stepsPerPulse / pump.mlPerPulse else 100f
        val feedRate = (flowRateMlPerSec * stepsPerMl * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
        val volumeDispensed = stepsToHome / pump.stepsPerPulse * pump.mlPerPulse

        val commands = listOf(
            "G92 ${pump.axis}0",
            "G91",
            "G1 ${pump.axis}${stepsToHome.toInt()} F$feedRate",
            "G90"
        )

        return Pair(commands, volumeDispensed)
    }

    /**
     * Generate G-code to home ALL pumps to their pulse boundaries.
     */
    fun generateAllPumpsHomeScript(
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float
    ): List<String> {
        val commands = mutableListOf<String>()
        
        // Reset positions
        val allAxes = pumps.map { it.axis }.joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes")
        commands.add("G91")
        
        // Build single command for all pumps that need homing
        val homeLine = StringBuilder("G1 ")
        var hasMovement = false
        
        for (pump in pumps) {
            if (pump.pulseHomeOffset > 0) {
                val stepsToHome = (pump.stepsPerPulse - pump.pulseHomeOffset).toInt()
                if (stepsToHome > 0) {
                    homeLine.append("${pump.axis}$stepsToHome ")
                    hasMovement = true
                }
            }
        }
        
        if (hasMovement) {
            val avgStepsPerPulse = pumps.map { it.stepsPerPulse }.average().toFloat()
            val avgMlPerPulse = pumps.map { it.mlPerPulse }.average().toFloat()
            val stepsPerMl = if (avgMlPerPulse > 0) avgStepsPerPulse / avgMlPerPulse else 100f
            val feedRate = (flowRateMlPerSec * stepsPerMl * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            
            homeLine.append("F$feedRate")
            commands.add(homeLine.toString().trim())
        }
        
        commands.add("G90")
        return commands
    }

    /**
     * Generates G-code for priming a single pump.
     */
    fun generatePrimeOnlyScript(
        axis: String,
        volumeMl: Float,
        stepsPerMl: Float,
        flowRateMlPerSec: Float
    ): List<String> {
        val steps = (volumeMl * stepsPerMl).toInt()
        val calculatedFeed = (flowRateMlPerSec * stepsPerMl * 60).toInt()
        val feedRate = calculatedFeed.coerceAtMost(MAX_SAFE_FEED_RATE)
        
        return listOf(
            "G92 ${axis}0", 
            "G91",
            "G1 $axis$steps F$feedRate",
            "G90"
        )
    }

    /**
     * Generate G-code to prime by whole pulses.
     */
    fun generatePulsePrimeScript(
        pump: PumpConfig,
        pulseCount: Int,
        flowRateMlPerSec: Float
    ): List<String> {
        val steps = (pulseCount * pump.stepsPerPulse).toInt()
        val stepsPerMl = if (pump.mlPerPulse > 0) pump.stepsPerPulse / pump.mlPerPulse else 100f
        val feedRate = (flowRateMlPerSec * stepsPerMl * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
        
        return listOf(
            "G92 ${pump.axis}0",
            "G91",
            "G1 ${pump.axis}$steps F$feedRate",
            "G90"
        )
    }

    fun generateRetractAllScript(
        pumps: List<PumpConfig>,
        retractionSteps: Float,
        flowRateMlPerSec: Float
    ): List<String> {
        val avgCal = pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
        val calculatedFeed = (flowRateMlPerSec * avgCal * 60).toInt()
        val feedRate = calculatedFeed.coerceAtMost(MAX_SAFE_FEED_RATE)

        val axesString = pumps.joinToString(" ") { "${it.axis}-${retractionSteps.toInt()}" }

        return listOf(
            "G91",
            "G1 $axesString F$feedRate",
            "G90"
        )
    }
}
