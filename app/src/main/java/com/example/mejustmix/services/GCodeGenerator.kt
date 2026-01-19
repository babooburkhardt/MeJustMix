package com.example.mejustmix.services

import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.ui.PumpConfig

object GCodeGenerator {

    // LIMITATION FIX: Safe upper limit for stepper motors.
    private const val MAX_SAFE_FEED_RATE = 12000

    /**
     * Generates G-code for dispensing a paint mixture.
     */
    fun generateMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float
    ): List<String> {
        // We still want at least 5 pumps to be safe, but we will look them up by name now
        if (pumps.size < 5) {
            throw IllegalArgumentException("Requires at least 5 pumps (CMYK+W), got ${pumps.size}")
        }

        val commands = mutableListOf<String>()
        val activeAxes = mutableListOf<String>() 
        
        // Reset position to 0,0,0,0,0 without moving motors (fixes soft limit errors)
        val allAxes = pumps.map { it.axis }.take(5).joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes") // Set current position as 0
        
        commands.add("G91") // Relative mode

        // Helper to find pump by name (from our previous fix)
        fun getPump(name: String, defaultIndex: Int): PumpConfig {
            return pumps.find { it.name.equals(name, ignoreCase = true) } 
                ?: pumps.getOrElse(defaultIndex) { pumps[0] }
        }

        // --- THE UNISON FIX: Build one single G1 line ---
        val dispenseLine = StringBuilder("G1 ")
        var hasMovement = false
        
        // We'll calculate the steps for each pump
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
            // Calculate a feed rate. Since they move in unison, FluidNC handles the 
            // timing so they all finish at the exact same time.
            val avgCal = pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
            val feedRate = (flowRateMlPerSec * avgCal * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            
            dispenseLine.append("F$feedRate")
            commands.add(dispenseLine.toString().trim())
        }

        // 2. Retraction (Already in unison, but we'll keep it clean)
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

        commands.add("G90") // Back to absolute
        return commands
    }

    // ... (Keep generatePrimeOnlyScript and generateRetractAllScript as they were) ...
    
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

    fun generateRetractAllScript(
        pumps: List<PumpConfig>,
        retractionSteps: Float,
        flowRateMlPerSec: Float
    ): List<String> {
        val avgCal = pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
        val calculatedFeed = (flowRateMlPerSec * avgCal * 60).toInt()
        val feedRate = calculatedFeed.coerceAtMost(MAX_SAFE_FEED_RATE)

        // Retract everyone
        val axesString = pumps.joinToString(" ") { "${it.axis}-${retractionSteps.toInt()}" }

        return listOf(
            "G91",
            "G1 $axesString F$feedRate",
            "G90"
        )
    }
}