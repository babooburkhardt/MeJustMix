package com.example.mejustmix.data

import android.content.Context
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
            
            if (settings.usePulseMode) {
                // Calculate pulse compensation profile from geometry settings
                val profile = com.example.mejustmix.utils.PulseCompensationCalculator.calculateProfile(
                    pillowLengthMm = settings.pillowLengthMm,
                    fullDiameterSectionMm = settings.fullDiameterSectionMm,
                    tubeInnerDiameterMm = settings.tubeInnerDiameterMm
                )
                
                gcode = GCodeGenerator.generateMixingScript(
                    mix = mix,
                    totalVolumeMl = volume,
                    retractionSteps = settings.retractionSteps.toFloatOrNull() ?: 15f,
                    pumps = settings.pumps,
                    flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f,
                    usePulseMode = true,
                    pulseMinimum = settings.pulseMinimum,
                    pulseProfile = profile,
                    useDynamicAcceleration = settings.useDynamicAcceleration,
                    taperAcceleration = settings.taperAcceleration
                )
                
                // For pulse mode, we need to calculate actual volume from the profile
                // This is approximate since we're using compensated segments
                actualVolume = volume
                addToHistory(">> PULSE MODE (Compensated): Taper ${(profile.taperFraction * 100).toInt()}%, Speed Boost ${String.format("%.1f", profile.taperSpeedMultiplier)}x")
            } else {
                gcode = GCodeGenerator.generateMixingScript(
                    mix = mix,
                    totalVolumeMl = volume,
                    retractionSteps = settings.retractionSteps.toFloatOrNull() ?: 15f,
                    pumps = settings.pumps,
                    flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f,
                    usePulseMode = false
                )
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
            
            val primeGcode = GCodeGenerator.generatePrimeOnlyScript(
                axis = axis,
                volumeMl = amount,
                stepsPerMl = stepsPerMl,
                flowRateMlPerSec = flowRate
            )
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
    
    suspend fun dispensePulses(pumpIndex: Int, pulseCount: Int, stepsPerPulse: Float, settings: SettingsUiState) {
        _isSending.value = true
        try {
             val pump = settings.pumps[pumpIndex]
             val flowRate = settings.flowRate.toFloatOrNull() ?: 2.0f
             val testPump = pump.copy(stepsPerPulse = stepsPerPulse)
             
             val gcode = GCodeGenerator.generatePulsePrimeScript(
                 pump = testPump,
                 pulseCount = pulseCount,
                 flowRateMlPerSec = flowRate
             )
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
            
            val (gcode, vol) = GCodeGenerator.generatePulseHomeScript(pump, flowRate)
            dispensed = vol
            
            if (gcode.isNotEmpty()) {
                sendGCodeCommands(gcode)
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
    private fun sendGCodeCommands(commands: List<String>) {
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
}
