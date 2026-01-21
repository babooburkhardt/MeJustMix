package com.example.mejustmix.data

import android.content.Context
import com.example.mejustmix.services.FluidNCService
import com.example.mejustmix.services.FluidNCStatus
import com.example.mejustmix.services.GCodeGenerator
import com.example.mejustmix.ui.PumpConfig
import com.example.mejustmix.ui.SettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository for handling interactions with the FluidNC Printer.
 * Manages connection, GCode generation, and sending.
 */
class PrinterRepository(private val context: Context) {

    private var fluidNCService: FluidNCService? = null
    
    private val _connectionStatus = MutableStateFlow<FluidNCStatus?>(null)
    val connectionStatus = _connectionStatus.asStateFlow()
    
    private val _gcodeHistory = MutableStateFlow<List<String>>(emptyList())
    val gcodeHistory = _gcodeHistory.asStateFlow()
    
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    fun connect(ipAddress: String) {
        disconnect()
        fluidNCService = FluidNCService(
            context = context,
            onStatusChange = { status -> _connectionStatus.value = status },
            onGCodeSent = { gcode -> _gcodeHistory.update { it + ">> $gcode" } }
        )
        fluidNCService?.connect(ipAddress, 23)
    }

    fun disconnect() {
        fluidNCService?.disconnect()
        fluidNCService = null
        _connectionStatus.value = null
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
            val gcode: List<String>
            val actualVolume: Float
            
            if (settings.usePulseMode) {
                val pulseResult = GCodeGenerator.generatePulseMixingScript(
                    mix = mix,
                    totalVolumeMl = volume,
                    retractionSteps = settings.retractionSteps.toFloatOrNull() ?: 15f,
                    pumps = settings.pumps,
                    flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f,
                    pulseMinimum = settings.pulseMinimum
                )
                gcode = pulseResult.commands
                actualVolume = pulseResult.actualVolumeMl
                addToHistory(">> PULSE MODE: Scaled ${pulseResult.scaleFactor}x, Actual: ${String.format("%.2f", actualVolume)}ml")
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
            
            fluidNCService?.sendMultiple(gcode)
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
            fluidNCService?.sendMultiple(primeGcode)
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
                fluidNCService?.sendMultiple(gcode)
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
         fluidNCService?.sendMultiple(gcode)
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
