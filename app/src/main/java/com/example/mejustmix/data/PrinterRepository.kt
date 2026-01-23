package com.example.mejustmix.data

import android.content.Context
import java.util.Locale
import com.example.mejustmix.services.FluidNCService
import com.example.mejustmix.services.FluidNCBLEManager
import com.example.mejustmix.services.FluidNCStatus
import com.example.mejustmix.services.GCodeGenerator
import com.example.mejustmix.ui.PumpConfig
import com.example.mejustmix.ui.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay

/**
 * Repository for handling interactions with the FluidNC Printer.
 * Supports both BLE and WiFi connection modes.
 * Manages connection, GCode generation, and sending.
 */
class PrinterRepository private constructor(context: Context) {
    
    // Store application context to avoid memory leaks
    private val appContext: Context = context.applicationContext

    companion object {
        @Volatile
        private var instance: PrinterRepository? = null

        fun getInstance(context: Context): PrinterRepository {
            return instance ?: synchronized(this) {
                instance ?: PrinterRepository(context).also { instance = it }
            }
        }
    }

    // WiFi connection (HTTP)
    private var fluidNCService: FluidNCService? = null
    
    // BLE connection
    private var bleManager: FluidNCBLEManager? = null
    
    // Current connection type
    private var currentConnectionType: ConnectionType? = null
    
    private val _connectionStatus = MutableStateFlow<FluidNCStatus?>(null)
    val connectionStatus = _connectionStatus.asStateFlow()
    
    private val _gcodeHistory = MutableStateFlow<List<String>>(emptyList())
    val gcodeHistory = _gcodeHistory.asStateFlow()
    
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()
    
    // Phase Tracking for Pulse Compensation
    // Maps pump axis (e.g., "X", "Y") or name to its current phase offset in steps [0, stepsPerPillow)
    // This ensures smooth velocity transitions between consecutive dispense operations
    private val _pumpPhaseSteps = mutableMapOf<String, Float>()

    /**
     * Resets all pump phase tracking to zero.
     * Call this when physically homing pumps.
     */
    fun resetAllPhases() {
        _pumpPhaseSteps.clear()
        // Initialize all known pumps to 0 if needed, or just let them start at 0
    }

    /**
     * Connect to a machine using its profile.
     */
    fun connectToMachine(machine: MachineProfile) {
        disconnect()
        
        when (machine.connectionType) {
            ConnectionType.BLE -> connectBLE(machine)
            ConnectionType.WIFI -> connectWiFi(machine)
        }
        
        currentConnectionType = machine.connectionType
    }
    
    /**
     * Connect via BLE.
     */
    private fun connectBLE(machine: MachineProfile) {
        bleManager = FluidNCBLEManager(appContext)
        
        // Monitor BLE connection state and convert to FluidNCStatus
        // TODO: Collect bleManager.connectionState and map to FluidNCStatus
        
        if (!machine.bleAddress.isNullOrBlank()) {
            bleManager?.connectByAddress(machine.bleAddress)
        } else if (!machine.bleDeviceName.isNullOrBlank()) {
            bleManager?.connect(machine.bleDeviceName)
        }
    }
    
    /**
     * Connect via WiFi (legacy HTTP mode).
     */
    private fun connectWiFi(machine: MachineProfile) {
        if (machine.ipAddress.isNullOrBlank()) return
        
        fluidNCService = FluidNCService(
            context = appContext,
            onStatusChange = { status -> _connectionStatus.value = status },
            onGCodeSent = { gcode -> _gcodeHistory.update { it + ">> $gcode" } }
        )
        fluidNCService?.connect(machine.ipAddress, 23)
    }

    /**
     * Legacy WiFi-only connect (for backward compatibility).
     */
    fun connect(ipAddress: String) {
        disconnect()
        fluidNCService = FluidNCService(
            context = appContext,
            onStatusChange = { status -> _connectionStatus.value = status },
            onGCodeSent = { gcode -> _gcodeHistory.update { it + ">> $gcode" } }
        )
        fluidNCService?.connect(ipAddress, 23)
        currentConnectionType = ConnectionType.WIFI
    }

    fun disconnect() {
        fluidNCService?.disconnect()
        fluidNCService = null
        
        bleManager?.disconnect()
        bleManager = null
        
        _connectionStatus.value = null
        currentConnectionType = null
    }

    fun addToHistory(msg: String) {
        _gcodeHistory.update { it + msg }
    }
    
    /**
     * Ensure FluidNC settings are appropriate for aggressive pulse tuning.
     * Sets max feed rate to 15000 mm/min and acceleration to 2500 mm/s² for the specified pump.
     * This allows 5x speed multipliers without hitting limits.
     */
    suspend fun ensureTuningSettings(pumpIndex: Int, pumps: List<PumpConfig>) {
        val pump = pumps.getOrNull(pumpIndex) ?: return
        
        // For 5x multiplier with base rate ~2000-3000, we need max rate of 15000+
        val tuningMaxRate = 15000
        // For aggressive speed changes, we need high acceleration
        val tuningAcceleration = 2500
        
        val commands = listOf(
            "$11${pump.axisIndex}=$tuningMaxRate",  // Set max rate
            "$12${pump.axisIndex}=$tuningAcceleration"  // Set acceleration
        )
        
        addToHistory(">> Configuring FluidNC for tuning (Max: ${tuningMaxRate}, Accel: ${tuningAcceleration})")
        sendGCodeCommands(commands)
        delay(100) // Give FluidNC time to process
    }
    
    /**
     * Jog a pump by a relative number of steps.
     * Used by geometry wizard for precise positioning.
     */
    suspend fun jogPump(pumpIndex: Int, steps: Float, pumps: List<PumpConfig>) {
        val pump = pumps.getOrNull(pumpIndex) ?: return
        
        val commands = listOf(
            "G91",  // Relative positioning
            "G1 ${pump.axis}${String.format(java.util.Locale.US, "%.2f", steps)} F1000",
            "G90"   // Back to absolute
        )
        
        addToHistory(">> Jog ${pump.name}: ${String.format("%.1f", steps)} steps")
        sendGCodeCommands(commands)
        delay(50)
    }

    // --- High Level Operations ---

    suspend fun dispenseMix(
        mix: PaintMix,
        volume: Float,
        settings: SettingsUiState
    ): DispenseResult {
        if (!checkConnection(settings.bypassConnectionCheck)) {
             return DispenseResult.Error("Not Connected")
        }
        
        _isSending.value = true
        return try {
            // Generate G-code
            val gcode: List<String>
            val actualVolume: Float
            val flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f
            
                // Check for Pulse Mode
                if (settings.usePulseMode) {
                    // Calculate pulse compensation profile from geometry settings
                    val profile = calculatePulseProfile(settings)
                    
                    addToHistory(">> DEBUG: Pulse Mode - C:${mix.cyan} M:${mix.magenta} Y:${mix.yellow} K:${mix.black} W:${mix.white}, Vol:${volume}mL")
                    addToHistory(">> DEBUG: Profile - Taper:${(profile.taperFraction * 100).toInt()}%, Boost:${String.format(Locale.US, "%.1f", profile.taperSpeedMultiplier)}x")
                    
                    val result = GCodeGenerator.generateMixingScript(
                        mix = mix,
                        totalVolumeMl = volume,
                        retractionSteps = settings.retractionSteps.toFloatOrNull() ?: 15f,
                        pumps = settings.pumps,
                        flowRateMlPerSec = flowRateMlPerSec,
                        usePulseMode = true,
                        pulseMinimum = settings.pulseMinimum,
                        pulseProfile = profile,
                        pumpPhases = _pumpPhaseSteps,
                        useDynamicAcceleration = settings.useDynamicAcceleration,
                        taperAcceleration = settings.taperAcceleration,
                        nominalAcceleration = settings.nominalAcceleration,
                        maxFeedRate = settings.maxFeedRate
                    )
                    gcode = result.first
                    _pumpPhaseSteps.putAll(result.second)
                    
                    // For pulse mode, we need to calculate actual volume from the profile
                    // This is approximate since we're using compensated segments
                    actualVolume = volume
                    addToHistory(">> PULSE MODE (Compensated): Taper ${(profile.taperFraction * 100).toInt()}%, Speed Boost ${String.format("%.1f", profile.taperSpeedMultiplier)}x")


            } else {
                val result = GCodeGenerator.generateMixingScript(
                    mix = mix,
                    totalVolumeMl = volume,
                    retractionSteps = settings.retractionSteps.toFloatOrNull() ?: 15f,
                    pumps = settings.pumps,
                    flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f,
                    usePulseMode = false
                )
                gcode = result.first
                // No phase update needed for standard mode
                actualVolume = volume
            }
            
            if (gcode.isEmpty()) return DispenseResult.Error("Generated G-Code is empty")
            
            sendGCodeCommands(gcode)
            DispenseResult.Success(actualVolume)
            
        } catch (e: Exception) {
            e.printStackTrace()
            DispenseResult.Error(e.message ?: "Unknown Error")
        } finally {
            _isSending.value = false
        }
    }
    
    suspend fun primePump(axis: String, amount: Float, settings: SettingsUiState) {
        _isSending.value = true
        try {
            val pump = settings.pumps.find { it.axis == axis }
            val stepsPerMl = pump?.calibration?.toFloatOrNull() ?: 100f
            val flowRate = settings.flowRate.toFloatOrNull() ?: 2.0f
            
            val modeStr = if(settings.usePulseMode) "Compensated" else "Standard"
            addToHistory(">> Priming ${pump?.name} ($modeStr)...")

            val primeGcode = if (settings.usePulseMode) {
                val initialPhase = _pumpPhaseSteps[pump!!.name] ?: 0f
                val result = GCodeGenerator.generateCompensatedPrimeScript(
                    pump = pump,
                    volumeMl = amount,
                    flowRateMlPerSec = flowRate,
                    profile = calculatePulseProfile(settings),
                    initialPhaseSteps = initialPhase
                )
                _pumpPhaseSteps[pump.name] = result.second
                result.first
            } else {
                 GCodeGenerator.generatePrimeOnlyScript(
                    axis = axis,
                    volumeMl = amount,
                    stepsPerMl = stepsPerMl,
                    flowRateMlPerSec = flowRate
                )
            }
            sendGCodeCommands(primeGcode)
        } catch (e: Exception) {
            addToHistory(">> Error priming: ${e.message}")
        } finally {
            _isSending.value = false
        }
    }
    
    suspend fun retractAll(settings: SettingsUiState) {
        _isSending.value = true
        try {
            val gcode = GCodeGenerator.generateRetractAllScript(
                pumps = settings.pumps,
                retractionSteps = settings.retractionSteps.toFloatOrNull() ?: 15f,
                flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f
            )
            fluidNCService?.sendMultiple(gcode)
        } catch (e: Exception) {
            addToHistory(">> Error retracting: ${e.message}")
        } finally {
            _isSending.value = false
        }
    }
    
    // --- Pulse Mode Helpers ---
    
    suspend fun dispensePulses(
        pumpIndex: Int, 
        pulseCount: Int, 
        stepsPerPulse: Float, 
        settings: SettingsUiState,
        phaseShiftSteps: Float = 0f
    ) {
        _isSending.value = true
        try {
             val pump = settings.pumps[pumpIndex]
             val flowRate = settings.flowRate.toFloatOrNull() ?: 2.0f
             val testPump = pump.copy(stepsPerPulse = stepsPerPulse)
             
             val gcode = if (settings.usePulseMode) {
                 val rawTruePhase = _pumpPhaseSteps[pump.name] ?: 0f
                 // Apply tuning shift (normalized positive)
                 val initialPhase = ((rawTruePhase + phaseShiftSteps) % stepsPerPulse + stepsPerPulse) % stepsPerPulse
                 
                 val result = GCodeGenerator.generateCompensatedPulseScript(
                     pump = testPump,
                     pulseCount = pulseCount,
                     flowRateMlPerSec = flowRate,
                     profile = calculatePulseProfile(settings),
                     initialPhaseSteps = initialPhase
                 )
                 
                 // Restore true phase logic:
                 // The result.second is the ENDING phase of the shifted operation.
                 // We need to un-shift it to get back to physical phase.
                 // But simply subtracting might be tricky with modulo wraparound.
                 // Alternative robust way: TrueEndPhase = (TrueStartPhase + TotalStepsDispensed) % StepsPerPulse.
                 // Since dispensePulses dispenses exactly `pulseCount * stepsPerPulse` (if full pulses), 
                 // the phase should advance by exactly that amount. 
                 // Actually `generateCompensatedPulseScript` dispenses `pulseCount` pulses.
                 // So TrueEndPhase SHOULD be (TrueStartPhase) if integer pulses?
                 // Wait, Phase Tracking logic in Calculator:
                 // It advances phase by `stepsToDo`.
                 
                 // Robust calculation of true physical end phase:
                 // We know exactly how many steps were dispensed? 
                 // result.first is G-code. result.second is end phase.
                 // For now, let's just reverse the shift.
                 val shiftedEndPhase = result.second
                 val trueEndPhase = ((shiftedEndPhase - phaseShiftSteps) % stepsPerPulse + stepsPerPulse) % stepsPerPulse
                 
                 _pumpPhaseSteps[pump.name] = trueEndPhase
                 result.first
             } else {
                 val cmd = GCodeGenerator.generatePulsePrimeScript(
                     pump = testPump,
                     pulseCount = pulseCount,
                     flowRateMlPerSec = flowRate
                 )
                 // Note: generatePulsePrimeScript now returns List<String> (signature unchanged for that method)
                 cmd
             }
             addToHistory(">> Dispensing $pulseCount pulses")
             fluidNCService?.sendMultiple(gcode)
        } catch (e: Exception) {
            addToHistory(">> Error: ${e.message}")
        } finally {
            _isSending.value = false
        }
    }
    
    suspend fun jogPump(pumpIndex: Int, steps: Int, settings: SettingsUiState) {
        _isSending.value = true
        try {
            val pump = settings.pumps[pumpIndex]
            val flowRate = settings.flowRate.toFloatOrNull() ?: 2.0f
            val mlPerPulse = pump.mlPerPulse
            val stepsPerPulse = pump.stepsPerPulse
            val stepsPerMl = if (mlPerPulse > 0) stepsPerPulse / mlPerPulse else 100f
            val feedRate = (flowRate * stepsPerMl * 60).toInt().coerceAtMost(12000)
            
            val gcode = listOf("G91", "G1 ${pump.axis}$steps F$feedRate", "G90")
            fluidNCService?.sendMultiple(gcode)
        } catch (e: Exception) {
            addToHistory(">> Error Jogging: ${e.message}")
        } finally {
            _isSending.value = false
        }
    }
    
    suspend fun homePump(pumpIndex: Int, settings: SettingsUiState): Float {
        _isSending.value = true
        var dispensed = 0f
        try {
            val pump = settings.pumps[pumpIndex]
            val flowRate = settings.flowRate.toFloatOrNull() ?: 2.0f
            
            val result: Pair<List<String>, Float> = GCodeGenerator.generatePulseHomeScript(pump, flowRate)
            val gcode = result.first
            val vol = result.second
            dispensed = vol
            
            if (gcode.isNotEmpty()) {
                sendGCodeCommands(gcode)
                // Homing resets the physical phase to 0
                _pumpPhaseSteps[pump.name] = 0f
                addToHistory(">> Homed ${pump.name}")
            } else {
                addToHistory(">> ${pump.name} already home")
            }
        } catch (e: Exception) {
            addToHistory(">> Error Homing: ${e.message}")
        } finally {
            _isSending.value = false
        }
        return dispensed
    }
    
    fun sendRaw(gcode: List<String>) {
        sendGCodeCommands(gcode)
    }
    
    /**
     * Send G-code commands to the active connection (BLE or WiFi).
     */
    fun sendGCodeCommands(commands: List<String>) {
        when (currentConnectionType) {
            ConnectionType.BLE -> {
                commands.forEach { command ->
                    bleManager?.sendGCode(command)
                    _gcodeHistory.update { it + ">> $command" }
                }
            }
            ConnectionType.WIFI -> {
                fluidNCService?.sendMultiple(commands)
            }
            null -> {
                addToHistory(">> Error: Not connected")
            }
        }
    }

    private fun checkConnection(bypass: Boolean): Boolean {
        if (bypass) return true
        val status = _connectionStatus.value
        val validStates = listOf("Connected", "Idle", "Run", "Jog", "Hold")
        return status != null && status.state in validStates
    }

    sealed class DispenseResult {
        data class Success(val actualVolume: Float) : DispenseResult()
        data class Error(val message: String) : DispenseResult()
    }
    
    // --- Helper Methods ---
    
    private fun calculatePulseProfile(settings: SettingsUiState): com.example.mejustmix.utils.PulseCompensationCalculator.PulseProfile {
        val flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f
        val avgCalibration = settings.pumps.map { it.calibration.toFloatOrNull() ?: 100f }.average().toFloat()
        val baseFeedRate = (flowRateMlPerSec * avgCalibration * 60).toInt()
        
        // Use tuning width if active, otherwise use persistent setting
        val fullDiameterMm = if (settings.isTuning && settings.tuningPulseWidthDegrees != null) {
            val widthDeg = settings.tuningPulseWidthDegrees
            val fraction = widthDeg / 360f
            (settings.pillowLengthMm - (2f * settings.pillowLengthMm * fraction)).coerceAtLeast(1f)
        } else {
            settings.fullDiameterSectionMm
        }
        
        return com.example.mejustmix.utils.PulseCompensationCalculator.calculateProfile(
            pillowLengthMm = settings.pillowLengthMm,
            fullDiameterSectionMm = fullDiameterMm,
            tubeInnerDiameterMm = settings.tubeInnerDiameterMm,
            baseFeedRate = baseFeedRate,
            strengthFactor = settings.pulseSmoothingStrength
        )
    }
}
