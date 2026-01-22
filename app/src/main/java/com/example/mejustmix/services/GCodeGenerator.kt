package com.example.mejustmix.services

import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.ui.PumpConfig
import com.example.mejustmix.utils.PulseCompensationCalculator
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
     * Generate FluidNC initialization commands to configure speed and acceleration limits.
     * These commands set the $11X (max rate) and $12X (acceleration) values for each axis.
     * 
     * @param pumps List of pump configurations with axis indices
     * @param maxFeedRate Maximum feed rate per axis (mm/min) - sets $110-$115
     * @param nominalAcceleration Default acceleration per axis (mm/s²) - sets $120-$125
     * @return List of FluidNC configuration commands
     */
    fun generateFluidNCInitCommands(
        pumps: List<PumpConfig>,
        maxFeedRate: Float = 5000f,
        nominalAcceleration: Float = 1000f
    ): List<String> {
        val commands = mutableListOf<String>()
        
        // Set max rate and acceleration for each active pump axis
        for (pump in pumps.take(5)) {  // Limit to 5 pumps (CMYKW)
            // $11X = max rate (mm/min)
            commands.add("$$11${pump.axisIndex}=${maxFeedRate.toInt()}")
            // $12X = acceleration (mm/s²)
            commands.add("$$12${pump.axisIndex}=${nominalAcceleration.toInt()}")
        }
        
        return commands
    }

    /**
     * Generates G-code for dispensing a paint mixture.
     * 
     * @param usePulseMode If true, dispense with velocity compensation based on pillow geometry
     * @param pulseMinimum Minimum pulses for any non-zero component (default 1)
     * @param pulseProfile Compensation profile (required if usePulseMode is true)
     * @param useDynamicAcceleration If true, adjust FluidNC acceleration for taper zones
     * @param taperAcceleration Custom acceleration for taper zones (mm/s²), or use profile's optimal value if null
     * @param nominalAcceleration Acceleration for full-flow zones (mm/s²)
     * @param maxFeedRate Maximum feed rate limit (mm/min)
     */
    fun generateMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float,
        usePulseMode: Boolean = false,
        pulseMinimum: Int = 1,
        pulseProfile: PulseCompensationCalculator.PulseProfile? = null,
        useDynamicAcceleration: Boolean = false,
        taperAcceleration: Float? = null,
        nominalAcceleration: Float = 1000f,
        maxFeedRate: Float = 5000f
    ): List<String> {
        return if (usePulseMode && pulseProfile != null) {
            // Pulse mode with velocity compensation
            generateCompensatedMixingScript(mix, totalVolumeMl, retractionSteps, pumps, flowRateMlPerSec, pulseProfile, useDynamicAcceleration, taperAcceleration, nominalAcceleration, maxFeedRate)
        } else if (usePulseMode) {
            // Fallback to simple pulse mode if no profile provided
            generatePulseMixingScript(mix, totalVolumeMl, retractionSteps, pumps, flowRateMlPerSec, pulseMinimum).commands
        } else {
            // Standard mL-based dispensing
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
                val steps = vol * stepsPerMl
                if (steps > 0.01f) {
                    dispenseLine.append("${pump.axis}${String.format("%.2f", steps)} ")
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
     * Compensated pulse-based dispensing - modulates speed to counteract pump pulsation.
     * 
     * Algorithm:
     * 1. Calculate total steps needed for each pump
     * 2. For each pump, generate segmented G-code based on the pillow profile:
     *    - Entry taper zone: Higher speed (compensates for reduced flow)
     *    - Full flow zone: Nominal speed
     *    - Exit taper zone: Higher speed (compensates for reduced flow)
     * 3. Merge consecutive segments where possible to reduce G-code size
     * 4. If dynamic acceleration is enabled, inject $12X= commands to adjust acceleration
     */
    private fun generateCompensatedMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float,
        profile: PulseCompensationCalculator.PulseProfile,
        useDynamicAcceleration: Boolean = false,
        taperAcceleration: Float? = null,
        nominalAcceleration: Float = 1000f,
        maxFeedRate: Float = 5000f
    ): List<String> {
        if (pumps.size < 5) {
            throw IllegalArgumentException("Requires at least 5 pumps (CMYK+W), got ${pumps.size}")
        }

        val commands = mutableListOf<String>()
        val activeAxes = mutableListOf<String>()

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

        // Calculate steps for each pump
        val pumpSteps = mutableMapOf<String, Float>()
        for ((name, data) in pumpData) {
            val (pump, ratio) = data
            val volumeNeeded = ratio * totalVolumeMl
            val stepsPerMl = pump.calibration.toFloatOrNull() ?: 100f
            pumpSteps[name] = volumeNeeded * stepsPerMl
        }

        // Reset positions
        val allAxes = pumps.map { it.axis }.take(5).joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes")
        commands.add("G91")

        // For simplicity, we'll process pumps one at a time with compensated segments
        // This is because different pumps may have different total steps
        for ((name, data) in pumpData) {
            val (pump, _) = data
            val totalSteps = pumpSteps[name] ?: 0f

            if (totalSteps > 0.01f) {
                activeAxes.add(pump.axis)

                // Calculate base feed rate for this pump
                val stepsPerMl = pump.calibration.toFloatOrNull() ?: 100f
                val baseFeedRate = (flowRateMlPerSec * stepsPerMl * 60).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)

                // Generate compensated segments
                val segments = PulseCompensationCalculator.generateCompensatedSegments(
                    totalSteps = totalSteps,
                    stepsPerPillow = pump.stepsPerPulse,
                    baseFeedRate = baseFeedRate,
                    profile = profile
                )

                // Merge consecutive segments with same feed rate
                val mergedSegments = PulseCompensationCalculator.mergeConsecutiveSegments(segments)

                // Determine acceleration values
                val effectiveTaperAccel = taperAcceleration ?: profile.optimalTaperAcceleration

                // Generate G-code for each segment
                var previousFeedRate = baseFeedRate
                for (segment in mergedSegments) {
                    if (segment.steps > 0.01f) {
                        // Clamp feed rate to user-configured max
                        val feedClamped = segment.feedRate.coerceAtMost(maxFeedRate.toInt()).coerceAtMost(MAX_SAFE_FEED_RATE)
                        
                        // Inject acceleration command if dynamic acceleration is enabled
                        // and we're transitioning between taper and nominal zones
                        if (useDynamicAcceleration) {
                            val isTaperZone = segment.feedRate > baseFeedRate
                            val wasTaperZone = previousFeedRate > baseFeedRate
                            
                            if (isTaperZone != wasTaperZone) {
                                val targetAccel = if (isTaperZone) effectiveTaperAccel else nominalAcceleration
                                // FluidNC command: $12X=value where X is axis index (0=X, 1=Y, 2=Z, 3=A, 4=B)
                                commands.add("$$12${pump.axisIndex}=${targetAccel.toInt()}")
                            }
                        }
                        
                        commands.add("G1 ${pump.axis}${String.format("%.2f", segment.steps)} F$feedClamped")
                        previousFeedRate = segment.feedRate
                    }
                }
                
                // Reset to nominal acceleration after pump finishes (if dynamic accel was used)
                if (useDynamicAcceleration) {
                    commands.add("$$12${pump.axisIndex}=${nominalAcceleration.toInt()}")
                }
            }
        }

        // Retraction
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
     * Generates G-code for parallel dispensing (Simul-Mix).
     * All pumps run simultaneously at the same volumetric flow rate.
     * 
     * @param flowRateMlPerSec Target flow rate per pump in mL/s
     */
    fun generateParallelMixingScript(
        mix: PaintMix,
        totalVolumeMl: Float,
        retractionSteps: Float,
        pumps: List<PumpConfig>,
        flowRateMlPerSec: Float,
        maxFeedRate: Float = 5000f
    ): List<String> {
        if (pumps.size < 5) throw IllegalArgumentException("Requires at least 5 pumps")

        val commands = mutableListOf<String>()
        val activeAxes = mutableSetOf<String>()

        fun getPump(name: String, defaultIndex: Int): PumpConfig {
            return pumps.find { it.name.equals(name, ignoreCase = true) } 
                ?: pumps.getOrElse(defaultIndex) { pumps[0] }
        }

        // 1. Calculate required volume & steps for each pump
        data class PumpPlan(
            val pump: PumpConfig,
            val volumeMl: Float,
            val totalSteps: Float,
            val durationSec: Float
        )

        val plans = mutableListOf<PumpPlan>()
        val pumpData = listOf(
            "Cyan" to (getPump("Cyan", 0) to mix.cyan),
            "Magenta" to (getPump("Magenta", 1) to mix.magenta),
            "Yellow" to (getPump("Yellow", 2) to mix.yellow),
            "Black" to (getPump("Black", 3) to mix.black),
            "White" to (getPump("White", 4) to mix.white)
        )

        for ((name, data) in pumpData) {
            val (pump, ratio) = data
            val volumeNeeded = ratio * totalVolumeMl
            if (volumeNeeded > 0.001f) {
                val stepsPerMl = pump.calibration.toFloatOrNull() ?: 100f
                val totalSteps = volumeNeeded * stepsPerMl
                val duration = volumeNeeded / flowRateMlPerSec
                
                plans.add(PumpPlan(pump, volumeNeeded, totalSteps, duration))
                activeAxes.add(pump.axis)
            }
        }

        if (plans.isEmpty()) return listOf("G91", "G90")

        // Reset positions
        val allAxes = pumps.map { it.axis }.take(5).joinToString(" ") { "${it}0" }
        commands.add("G92 $allAxes")
        commands.add("G91")

        // 2. Create sorted list of "Stop Events"
        // We sort by duration ascending to handle pumps dropping out one by one
        val sortedPlans = plans.sortedBy { it.durationSec }
        
        // 3. Generate Time Segments
        var currentTime = 0f
        
        for (i in sortedPlans.indices) {
            val plan = sortedPlans[i]
            val segmentDuration = plan.durationSec - currentTime
            
            if (segmentDuration > 0.001f) {
                // Find all pumps active in this segment (duration > currentTime)
                val activePlans = sortedPlans.filter { it.durationSec > currentTime }
                val activeCount = activePlans.size
                
                // Calculate moves for this segment
                val moveCmd = StringBuilder("G1")
                var hasMove = false
                
                // Calculate feed rate for this segment
                var sumSquares = 0f
                for (active in activePlans) {
                    val stepsPerMl = active.pump.calibration.toFloatOrNull() ?: 100f
                    val axisSpeedStepsPerSec = flowRateMlPerSec * stepsPerMl
                    val stepsInSegment = axisSpeedStepsPerSec * segmentDuration
                    
                    moveCmd.append(" ${active.pump.axis}${String.format("%.2f", stepsInSegment)}")
                    sumSquares += stepsInSegment * stepsInSegment
                    hasMove = true
                }
                
                if (hasMove) {
                    val euclideanDistance = kotlin.math.sqrt(sumSquares)
                    val durationMin = segmentDuration / 60f
                    val feedRate = (euclideanDistance / durationMin).toInt().coerceAtMost(maxFeedRate.toInt()).coerceAtMost(MAX_SAFE_FEED_RATE)
                    
                    moveCmd.append(" F$feedRate")
                    commands.add(moveCmd.toString())
                }
            }
            
            currentTime = plan.durationSec
        }

        // 4. Simultaneous Retraction
        if (retractionSteps > 0 && activeAxes.isNotEmpty()) {
            val retractCmd = StringBuilder("G1")
            var sumSquares = 0f
            
            for (axis in activeAxes.distinct()) {
                retractCmd.append(" ${axis}-${retractionSteps.toInt()}")
                sumSquares += retractionSteps * retractionSteps
            }
            
            // Retract speed (fast)
            val avgCal = pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
            val retractDurationSec = 0.5f // Target 0.5s for retraction
            val euclideanDistance = kotlin.math.sqrt(sumSquares)
            val retractFeed = (euclideanDistance / (retractDurationSec / 60f)).toInt().coerceAtMost(MAX_SAFE_FEED_RATE)
            
            retractCmd.append(" F$retractFeed")
            commands.add(retractCmd.toString())
        }

        commands.add("G90")
        return commands
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
