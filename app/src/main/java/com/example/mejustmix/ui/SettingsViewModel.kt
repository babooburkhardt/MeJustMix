package com.example.mejustmix.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import com.example.mejustmix.data.ConnectionType
import com.example.mejustmix.data.MachineManager
import com.example.mejustmix.data.MotorConfig
import com.example.mejustmix.services.PigmentStrengths
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.services.KSPigmentDatabase
import com.example.mejustmix.services.KSCalibrationBundle
import com.example.mejustmix.services.KubelkaMunkColorMixing
import com.example.mejustmix.services.StepBasedGCodeGenerator
import com.example.mejustmix.services.UnifiedBLEScanner
import com.example.mejustmix.data.PrinterRepository
import com.example.mejustmix.utils.PulseModeCalculator
import com.example.mejustmix.services.SpectralSensorManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

// Fully defined PumpConfig
data class PumpConfig(
    val name: String,
    val axis: String,
    val axisIndex: Int = 0,                  // FluidNC axis index for $12X commands (X=0, Y=1, Z=2, A=3, B=4)
    val calibration: String = "100.0",       // steps per mL
    val currentVolumeMl: Float = 100f,
    val maxVolumeMl: Float = 100f,
    val colorArgb: Int = Color.Cyan.toArgb(),
    
    // Pulse mode calibration
    val stepsPerPulse: Float = 50f,          // Steps for one complete roller rotation
    val mlPerPulse: Float = 0.5f,            // mL dispensed per pulse (calibrated)
    val pulseHomeOffset: Float = 0f,         // Steps from current position to pulse boundary (0 = at home)
    
    // Geometry-based homing
    val lastKnownAngle: Float = 225f,        // Last observed roller angle (225° = pulse boundary)
    
    // Drift tracking (learns pump behavior over time)
    val driftHistory: List<Float> = emptyList(),  // History of drift measurements in degrees
    val driftCompensation: Float = 0f             // Applied drift compensation in degrees
)

data class SettingsUiState(
    val ipAddress: String = "192.168.1.100",
    val webPortalPort: String = "81",
    val connectionMode: ConnectionType? = null, // null = not set yet, defaults to WiFi
    val flowRate: String = "2.0",
    val retractionSteps: String = "15.0",
    val useManualBase: Boolean = false,
    val showTerminal: Boolean = false,
    val showRealityCheck: Boolean = true,
    val bypassConnectionCheck: Boolean = false,
    val cameraColorPickerEnabled: Boolean = false,
    val pigmentStrengths: PigmentStrengths = PigmentStrengths(),
    val pumps: List<PumpConfig> = listOf(
        // Phthalocyanine Blue GS (#0074A2) - Deep, realistic Cyan
        PumpConfig("Cyan", "X", axisIndex = 0, calibration = "100.0", currentVolumeMl = 100f, maxVolumeMl = 100f, colorArgb = Color(0xFF0074A2).toArgb()),
        
        // Quinacridone Magenta (#9F005D) - Deep, berry-like Magenta
        PumpConfig("Magenta", "Y", axisIndex = 1, calibration = "100.0", currentVolumeMl = 100f, maxVolumeMl = 100f, colorArgb = Color(0xFF9F005D).toArgb()),
        
        // Cadmium Yellow Medium Hue (#FFD800) - Warm, golden Yellow (not neon)
        PumpConfig("Yellow", "Z", axisIndex = 2, calibration = "100.0", currentVolumeMl = 100f, maxVolumeMl = 100f, colorArgb = Color(0xFFFFD800).toArgb()),
        
        PumpConfig("Black", "A", axisIndex = 3, calibration = "100.0", currentVolumeMl = 100f, maxVolumeMl = 100f, colorArgb = Color.Black.toArgb()),
        PumpConfig("White", "B", axisIndex = 4, calibration = "100.0", currentVolumeMl = 100f, maxVolumeMl = 100f, colorArgb = Color.White.toArgb())
    ),
    
    // Kubelka-Munk settings (now using 3-channel K/S) - DEFAULT ON
    val useKubelkaMunk: Boolean = true,
    val kmDatabase: KSPigmentDatabase? = null,
    
    // Display settings
    val showRealPaintPreview: Boolean = false,
    val realPaintPreviewIntensity: Float = 0.7f,
    
    // Pulse mode settings
    val usePulseMode: Boolean = true,
    val pulseMinimum: Int = 0,              // Minimum pulses for any non-zero component
    
    // Pulse compensation geometry (used when pulse mode is enabled)
    val pillowLengthMm: Float = 40f,        // Total pillow length
    val tubeInnerDiameterMm: Float = 3f,    // Tube bore (for volume calculations)
    val fullDiameterSectionMm: Float = 32f, // Length at full tube expansion
    val pulseSmoothingStrength: Float = 1.0f, // Tuning factor for pulse compensation (1.0 = standard, >1.0 = stronger, <1.0 = weaker)
    
    // Dynamic acceleration control
    val useDynamicAcceleration: Boolean = false,  // Enable FluidNC acceleration adjustment
    val taperAcceleration: Float = 500f,          // Acceleration for taper zones (mm/s²)
    val nominalAcceleration: Float = 1000f,       // Nominal acceleration for full-flow zones (mm/s²)
    
    // Simul-Mix (Parallel Dispensing)
    val useSimulMix: Boolean = false,             // Dispense all colors simultaneously
    
    // FluidNC speed limits (for "you can go faster" configuration)
    val maxFeedRate: Float = 5000f,               // Max feed rate per axis (mm/min) - $110-$115
    
    // Spectral Sensor State
    val spectralSensorEnabled: Boolean = false,
    val spectralSensorType: String = "AS7341", // Options: "AS7265x", "AS7341"
    val spectralConnectionStatus: String = "Disconnected",
    val spectralData: List<Float>? = null,
    val whiteReference: List<Float>? = null,
    val darkReference: List<Float>? = null,
    
    // First-time setup
    val hasSeenCalibrationWarning: Boolean = false,
    
    // Live Tuning Tool State
    val isTuning: Boolean = false,
    val tuningPumpIndex: Int = -1,
    val tuningPhaseOffset: Float = 0f, // Phase shift in degrees (-45 to +45)
    val tuningPulseWidthDegrees: Float? = null, // Override for taper width (5-45)
    
    // ==================== MOTOR & STEP-BASED CONTROL ====================
    
    /**
     * Use step-based G-code instead of distance-based.
     * When enabled, FluidNC is configured with $10X=1.0 (1 step = 1 unit)
     * and all movements are specified in native motor steps.
     */
    val useStepBasedGCode: Boolean = true,
    
    /**
     * Motor configuration for step calculations.
     * Includes microstepping, gear ratio, speed limits, etc.
     */
    val motorConfig: MotorConfig = MotorConfig.DEFAULT,
    
    /**
     * Whether FluidNC has been synchronized with current motor config.
     * Reset to false when motor config changes, set to true after sync.
     */
    val fluidNCConfigSynced: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {


    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()
    
    // Spectral Manager
    private val spectralManager = SpectralSensorManager(application)
    
    // Machine Manager for multi-machine support
    val machineManager = MachineManager(application)
    
    // Unified BLE Scanner
    private val bleScanner = UnifiedBLEScanner(application)
    
    // Repository for printer communication
    private val printerRepository = PrinterRepository.getInstance(application)

    private val prefs = application.getSharedPreferences("mejustmix_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadSettings()
        
        // Auto-connect to Spectral Sensor if available
        if (uiState.value.spectralSensorEnabled) {
            spectralManager.scanAndAutoConnect()
        }
        
        // Setup BLE device discovery callbacks
        bleScanner.onFluidNCFound = { _ ->
            // Notify user of discovered FluidNC device
            // This will be handled in the UI layer
        }
        
        bleScanner.onSpectralSensorFound = { _ ->
            // Auto-enable spectral sensor if found
            if (!uiState.value.spectralSensorEnabled) {
                showToast("Spectral Bridge discovered! Enabling and connecting...")
                toggleSpectralSensor(true)
                connectSpectralSensor()
            }
        }
        
        // Collect Spectral State
        viewModelScope.launch {
            spectralManager.connectionState.collect { status ->
                _uiState.update { it.copy(spectralConnectionStatus = status) }
            }
        }
        
        viewModelScope.launch {
            spectralManager.lastReading.collect { data ->
                _uiState.update { it.copy(spectralData = data) }
            }
        }
    }

    // --- Persistence Logic ---

    private fun loadSettings() {
        val json = prefs.getString("settings_json", null)
        if (json != null) {
            try {
                val savedState = gson.fromJson(json, SettingsUiState::class.java)
                _uiState.value = savedState.copy(
                    // Always reload default database if K-M is enabled but no database saved
                    kmDatabase = if (savedState.useKubelkaMunk && savedState.kmDatabase == null) {
                        KubelkaMunkColorMixing.createDefaultPigmentDatabase()
                    } else {
                        savedState.kmDatabase
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to load settings, using defaults")
            }
        }
    }

    private fun saveSettings() {
        try {
            val json = gson.toJson(_uiState.value)
            prefs.edit(commit = false) { 
                putString("settings_json", json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to save settings: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    // --- Update Methods (Now with auto-save) ---

    fun updateIpAddress(newIp: String) {
        _uiState.update { it.copy(ipAddress = newIp) }
        saveSettings()
    }
    
    fun updateConnectionMode(mode: ConnectionType) {
        _uiState.update { it.copy(connectionMode = mode) }
        saveSettings()
    }

    fun updateWebPortalPort(newPort: String) {
        _uiState.update { it.copy(webPortalPort = newPort) }
        saveSettings()
    }

    fun updateFlowRate(newRate: String) {
        _uiState.update { it.copy(flowRate = newRate) }
        saveSettings()
    }
    
    fun updateRetractionSteps(newSteps: String) {
        _uiState.update { it.copy(retractionSteps = newSteps) }
        saveSettings()
    }
    
    fun toggleManualBase(enabled: Boolean) {
        _uiState.update { it.copy(useManualBase = enabled) }
        saveSettings()
    }

    fun toggleShowTerminal(enabled: Boolean) {
        _uiState.update { it.copy(showTerminal = enabled) }
        saveSettings()
    }

    fun toggleRealityCheck(enabled: Boolean) {
        _uiState.update { it.copy(showRealityCheck = enabled) }
        saveSettings()
    }

    fun toggleBypassConnectionCheck(enabled: Boolean) {
        _uiState.update { it.copy(bypassConnectionCheck = enabled) }
        saveSettings()
    }

    fun toggleCameraColorPicker(enabled: Boolean) {
        _uiState.update { it.copy(cameraColorPickerEnabled = enabled) }
        saveSettings()
    }
    
    fun toggleRealPaintPreview(enabled: Boolean) {
        _uiState.update { it.copy(showRealPaintPreview = enabled) }
        saveSettings()
    }



    fun toggleSpectralSensor(enabled: Boolean) {
        _uiState.update { it.copy(spectralSensorEnabled = enabled) }
        saveSettings()
    }
    
    fun setSpectralSensorType(type: String) {
        _uiState.update { it.copy(spectralSensorType = type) }
        spectralManager.setSensorType(type)
        saveSettings()
    }
    
    fun setWhiteReference(ref: List<Float>) {
        _uiState.update { it.copy(whiteReference = ref) }
        saveSettings()
    }
    
    fun setDarkReference(ref: List<Float>) {
        _uiState.update { it.copy(darkReference = ref) }
        saveSettings()
    }
    
    fun triggerSpectralScan() {
        spectralManager.triggerScan()
    }

    fun triggerSpectralAutoGain() {
        spectralManager.runAutoGain()
    }

    fun triggerSpectralWarmup() {
        spectralManager.triggerWarmup()
    }
    
    fun disconnectSpectralSensor() {
        spectralManager.disconnect()
    }
    
    fun markCalibrationWarningSeen() {
        _uiState.update { it.copy(hasSeenCalibrationWarning = true) }
        saveSettings()
    }

    // --- Kubelka-Munk Methods (Updated for 3-channel K/S) ---
    
    fun toggleKubelkaMunk(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                useKubelkaMunk = enabled,
                kmDatabase = if (enabled && state.kmDatabase == null) {
                    KubelkaMunkColorMixing.createDefaultPigmentDatabase()
                } else {
                    state.kmDatabase
                }
            )
        }
        saveSettings()
    }
    
    fun resetKMDatabaseToDefaults() {
        _uiState.update { 
            it.copy(kmDatabase = KubelkaMunkColorMixing.createDefaultPigmentDatabase())
        }
        saveSettings()
        showToast("K-M database reset to defaults")
    }
    
    /**
     * Update a pigment's K/S values (3-channel).
     */
    fun updatePigmentKS(pigment: String, ksColor: KSColor) {
        _uiState.update { state ->
            val db = state.kmDatabase ?: KubelkaMunkColorMixing.createDefaultPigmentDatabase()
            
            val updatedDb = when (pigment.lowercase()) {
                "cyan" -> db.copy(cyan = ksColor)
                "magenta" -> db.copy(magenta = ksColor)
                "yellow" -> db.copy(yellow = ksColor)
                "black" -> db.copy(black = ksColor)
                "white" -> db.copy(white = ksColor)
                else -> db
            }
            
            state.copy(kmDatabase = updatedDb)
        }
        saveSettings()
    }
    
    /**
     * Get current K/S values for a pigment.
     */
    fun getPigmentKS(pigment: String): KSColor {
        val db = _uiState.value.kmDatabase ?: KubelkaMunkColorMixing.createDefaultPigmentDatabase()
        return when (pigment.lowercase()) {
            "cyan" -> db.cyan
            "magenta" -> db.magenta
            "yellow" -> db.yellow
            "black" -> db.black
            "white" -> db.white
            else -> KSColor(1f, 1f, 1f)
        }
    }

    // --- Pump Configuration Methods ---

    fun onPumpAxisChanged(pumpIndex: Int, newAxis: String) {
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            val currentPump = newPumps[pumpIndex]
            val oldAxis = currentPump.axis
            val conflictingPumpIndex = newPumps.indexOfFirst { 
                it.axis == newAxis && newPumps.indexOf(it) != pumpIndex 
            }
            if (conflictingPumpIndex != -1) {
                newPumps[conflictingPumpIndex] = newPumps[conflictingPumpIndex].copy(axis = oldAxis)
            }
            newPumps[pumpIndex] = newPumps[pumpIndex].copy(axis = newAxis)
            state.copy(pumps = newPumps)
        }
        saveSettings()
    }

    fun updatePumpCalibration(axis: String, value: String) {
        // Find pump before update
        val targetPump = _uiState.value.pumps.find { it.axis == axis }
        val oldCal = targetPump?.calibration?.toFloatOrNull() ?: 100f
        val newCal = value.toFloatOrNull() ?: 100f
        
        _uiState.update { currentState ->
            val updatedPumps = currentState.pumps.map { pump ->
                if (pump.axis == axis) pump.copy(calibration = value) else pump
            }
            currentState.copy(pumps = updatedPumps)
        }
        
        // Auto-adjust pigment strength if calibration changed significantly
        if (targetPump != null && oldCal > 0 && newCal > 0 && kotlin.math.abs(oldCal - newCal) > 0.1f) {
            val ratio = newCal / oldCal
            adjustPigmentStrengthForPump(targetPump.name, ratio)
        }
        
        saveSettings()
    }
    
    fun onPumpCalibrationChanged(pumpIndex: Int, newValue: String) {
        val targetPump = _uiState.value.pumps.getOrNull(pumpIndex)
        val oldCal = targetPump?.calibration?.toFloatOrNull() ?: 100f
        val newCal = newValue.toFloatOrNull() ?: 100f
        
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            if (pumpIndex in newPumps.indices) {
                newPumps[pumpIndex] = newPumps[pumpIndex].copy(calibration = newValue)
            }
            state.copy(pumps = newPumps)
        }
        
        // Auto-adjust pigment strength
        if (targetPump != null && oldCal > 0 && newCal > 0 && kotlin.math.abs(oldCal - newCal) > 0.1f) {
            val ratio = newCal / oldCal
            adjustPigmentStrengthForPump(targetPump.name, ratio)
        }
        
        saveSettings()
    }

    private fun adjustPigmentStrengthForPump(pumpName: String, ratio: Float) {
        _uiState.update { state ->
            val currentStrengths = state.pigmentStrengths
            val newStrengths = when(pumpName) {
                "Cyan" -> currentStrengths.copy(cyan = currentStrengths.cyan * ratio)
                "Magenta" -> currentStrengths.copy(magenta = currentStrengths.magenta * ratio)
                "Yellow" -> currentStrengths.copy(yellow = currentStrengths.yellow * ratio)
                "Black" -> currentStrengths.copy(black = currentStrengths.black * ratio)
                "White" -> currentStrengths.copy(white = currentStrengths.white * ratio)
                else -> currentStrengths
            }
            state.copy(pigmentStrengths = newStrengths)
        }
        
        val percentChange = ((ratio - 1f) * 100f).toInt()
        val changeText = if (percentChange > 0) "+$percentChange%" else "$percentChange%"
        showToast("Adjusted ${pumpName} strength by $changeText (to match flow change)")
    }

    fun updatePumpVolume(pumpIndex: Int, newVolume: Float) {
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            newPumps[pumpIndex] = newPumps[pumpIndex].copy(currentVolumeMl = newVolume)
            state.copy(pumps = newPumps)
        }
        saveSettings()
    }

    // --- Pigment Tuning Methods ---

    fun updatePigmentStrength(colorName: String, newStrength: Float) {
        _uiState.update { state ->
            val currentStrengths = state.pigmentStrengths
            val newStrengths = when(colorName) {
                "Cyan" -> currentStrengths.copy(cyan = newStrength)
                "Magenta" -> currentStrengths.copy(magenta = newStrength)
                "Yellow" -> currentStrengths.copy(yellow = newStrength)
                "Black" -> currentStrengths.copy(black = newStrength)
                "White" -> currentStrengths.copy(white = newStrength)
                else -> currentStrengths
            }
            state.copy(pigmentStrengths = newStrengths)
        }
        saveSettings()
    }

    fun consumePaint(
        mix: com.example.mejustmix.data.PaintMix, 
        totalVolume: Float,
        onPumpDepleted: ((String) -> Unit)? = null
    ) {
        _uiState.update { state ->
            val newPumps = state.pumps.mapIndexed { index, pump ->
                val consumed = when(index) {
                    0 -> mix.cyan * totalVolume
                    1 -> mix.magenta * totalVolume
                    2 -> mix.yellow * totalVolume
                    3 -> mix.black * totalVolume
                    4 -> mix.white * totalVolume
                    else -> 0f
                }
                val newVolume = (pump.currentVolumeMl - consumed).coerceAtLeast(0f)
                
                // Notify if pump just ran out
                if (newVolume == 0f && pump.currentVolumeMl > 0f) {
                    onPumpDepleted?.invoke(pump.name)
                }
                
                pump.copy(currentVolumeMl = newVolume)
            }
            state.copy(pumps = newPumps)
        }
        saveSettings()
    }

    fun restoreSettings(newState: SettingsUiState) {
        _uiState.value = newState
        saveSettings()
    }
    
    // --- Pulse Mode Methods ---
    
    fun togglePulseMode(enabled: Boolean) {
        _uiState.update { it.copy(usePulseMode = enabled) }
        saveSettings()
    }
    
    /**
     * Snap all pumps to their nearest pulse boundary (home position).
     * Used to physically align rollers for integer-pulse dispensing.
     */
    fun snapAllPumpsToHome() {
        val cmds = mutableListOf<String>()
        val newPumps = _uiState.value.pumps.toMutableList()
        var hasMovement = false
        
        cmds.add("G91") // Relative positioning
        
        newPumps.forEachIndexed { index, pump ->
            val currentAngle = pump.lastKnownAngle ?: 0f
            val stepsPerPulse = pump.stepsPerPulse
            
            // Calculate steps to nearest home
            // Convert angle to steps from home: (angle/360) * stepsPerPulse
            val stepsFromHome = (currentAngle / 360f) * stepsPerPulse
            val nearestHomeSteps = kotlin.math.round(stepsFromHome / stepsPerPulse) * stepsPerPulse
            val deltaSteps = nearestHomeSteps - stepsFromHome
            
            // If significant movement needed (ignore tiny rounding errors)
            if (kotlin.math.abs(deltaSteps) > 0.5f) {
                cmds.add("G1 ${pump.axis}${String.format(Locale.US, "%.2f", deltaSteps)} F${_uiState.value.maxFeedRate.toInt()}")
                hasMovement = true
            }
            
            // Update pump state to be at home (0 degrees)
            newPumps[index] = pump.copy(lastKnownAngle = 0f)
        }
        
        cmds.add("G90") // Back to absolute
        
        if (hasMovement) {
            viewModelScope.launch {
                val repository = PrinterRepository.getInstance(getApplication())
                repository.sendGCodeCommands(cmds)
                // Also reset phase tracking in repo
                repository.resetAllPhases()
            }
            // Update UI state with new 0 angles
            _uiState.update { it.copy(pumps = newPumps) }
            saveSettings()
        }
    }
    
    private var tuningJob: kotlinx.coroutines.Job? = null

    fun updatePulseMinimum(minimum: Int) {
        _uiState.update { it.copy(pulseMinimum = minimum.coerceAtLeast(0)) }
        saveSettings()
    }
    

    
    // --- Live Tuning Tools ---
    
    fun toggleTuning(pumpIndex: Int, enable: Boolean) {
        if (enable) startTuning(pumpIndex) else stopTuning()
    }
    
    fun startTuning(pumpIndex: Int) {
        stopTuning()
        
        // Calculate current width degrees from geometry
        val currentProfile = com.example.mejustmix.utils.PulseCompensationCalculator.calculateProfile(
            pillowLengthMm = _uiState.value.pillowLengthMm,
            fullDiameterSectionMm = _uiState.value.fullDiameterSectionMm
        )
        val currentWidthDeg = (currentProfile.taperFraction * 360f)
        
        _uiState.update { it.copy(
            isTuning = true, 
            tuningPumpIndex = pumpIndex, 
            tuningPhaseOffset = 0f,
            tuningPulseWidthDegrees = currentWidthDeg
        ) }
        
        tuningJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Configure FluidNC for aggressive tuning
            printerRepository.ensureTuningSettings(pumpIndex, _uiState.value.pumps)
            
            // Wait for UI to settle
            kotlinx.coroutines.delay(200)
            
            while (isActive && _uiState.value.isTuning) {
                val state = _uiState.value
                val pump = state.pumps.getOrNull(pumpIndex) ?: break
                
                // Dispense small chunks (3 pulses per batch)
                val pulses = 3
                
                val stepsPerPulse = pump.stepsPerPulse.takeIf { it > 1.0f } ?: 200f
                val shiftSteps = (state.tuningPhaseOffset / 360f) * stepsPerPulse
                
                printerRepository.dispensePulses(
                    pumpIndex = pumpIndex,
                    pulseCount = pulses,
                    stepsPerPulse = stepsPerPulse,
                    settings = state,
                    phaseShiftSteps = shiftSteps
                )
                
                kotlinx.coroutines.delay(3000) // 3 second delay to prevent queue flooding
            }
            
            with(kotlinx.coroutines.Dispatchers.Main) {
                _uiState.update { it.copy(isTuning = false) }
            }
        }
    }
    
    fun stopTuning() {
        tuningJob?.cancel()
        _uiState.update { it.copy(isTuning = false) }
    }
    
    fun updateTuningOffset(offsetDegrees: Float) {
        _uiState.update { it.copy(tuningPhaseOffset = offsetDegrees) }
    }
    
    fun updateTuningWidth(degrees: Float) {
        _uiState.update { it.copy(tuningPulseWidthDegrees = degrees) }
    }
    
    fun saveTuningResult() {
        val state = _uiState.value
        val pumpIndex = state.tuningPumpIndex
        if (pumpIndex < 0) return
        
        // Save Timing (Per Pump)
        val pump = state.pumps[pumpIndex]
        val stepsPerPulse = pump.stepsPerPulse.takeIf { it > 1.0f } ?: 200f
        val shiftSteps = (state.tuningPhaseOffset / 360f) * stepsPerPulse
        val newHomeOffset = pump.pulseHomeOffset - shiftSteps
        var updatedPump = pump.copy(pulseHomeOffset = newHomeOffset)
        
        // Save Width (Global)
        if (state.tuningPulseWidthDegrees != null) {
            val widthDeg = state.tuningPulseWidthDegrees
            // fullDiameter = pillow - 2 * (pillow * fraction)
            val fraction = widthDeg / 360f
            val newFullDiameter = state.pillowLengthMm - (2f * state.pillowLengthMm * fraction)
            _uiState.update { it.copy(fullDiameterSectionMm = newFullDiameter.coerceAtLeast(1f)) }
            saveSettings() 
        }
        
        // Update pump config inline
        _uiState.update { s ->
            val newPumps = s.pumps.toMutableList()
            newPumps[pumpIndex] = updatedPump
            s.copy(pumps = newPumps)
        }
        saveSettings()
        
        stopTuning()
    }

    fun saveTuningOffset(pumpIndex: Int) {
        if (_uiState.value.tuningPumpIndex == pumpIndex) {
            saveTuningResult()
        }
    }

    fun updatePulseSmoothingStrength(strength: Float) {
        _uiState.update { it.copy(pulseSmoothingStrength = strength.coerceIn(0f, 3f)) }
        saveSettings()
    }
    
    fun updatePumpPulseConfig(
        pumpIndex: Int,
        stepsPerPulse: Float? = null,
        mlPerPulse: Float? = null,
        pulseHomeOffset: Float? = null
    ) {
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            val pump = newPumps[pumpIndex]
            newPumps[pumpIndex] = pump.copy(
                stepsPerPulse = stepsPerPulse ?: pump.stepsPerPulse,
                mlPerPulse = mlPerPulse ?: pump.mlPerPulse,
                pulseHomeOffset = pulseHomeOffset ?: pump.pulseHomeOffset
            )
            state.copy(pumps = newPumps)
        }
        saveSettings()
    }
    
    /**
     * Reset pulse home offset to 0 (call after homing).
     */
    fun setPumpHomed(pumpIndex: Int) {
        updatePumpPulseConfig(pumpIndex, pulseHomeOffset = 0f)
    }
    
    /**
     * Update pulse home offset after partial movement.
     */
    fun updatePumpHomeOffset(pumpIndex: Int, stepsDispensed: Float) {
        _uiState.update { state ->
            val pump = state.pumps[pumpIndex]
            val stepsPerPulse = pump.stepsPerPulse
            // New offset = (old offset + steps dispensed) mod stepsPerPulse
            val newOffset = (pump.pulseHomeOffset + stepsDispensed) % stepsPerPulse
            val newPumps = state.pumps.toMutableList()
            newPumps[pumpIndex] = pump.copy(pulseHomeOffset = newOffset)
            state.copy(pumps = newPumps)
        }
        saveSettings()
    }
    
    // --- Pulse Mode Scroll Wheel Functions ---
    
    /**
     * Track the visual offset from home without moving the pump.
     * This is called by the scroll wheel to remember where home should be.
     * 
     * @param pumpIndex Index of the pump
     * @param offsetSteps The tracked offset in steps from the current position
     */
    fun updatePumpTrackedOffset(pumpIndex: Int, offsetSteps: Float) {
        _uiState.update { state ->
            val updatedPumps = state.pumps.toMutableList()
            val pump = updatedPumps[pumpIndex]
            updatedPumps[pumpIndex] = pump.copy(
                pulseHomeOffset = offsetSteps
            )
            state.copy(pumps = updatedPumps)
        }
        // Don't save on every scroll - only on successful prime
    }
    
    /**
     * Prime the pump to move it to the tracked home position.
     * This is the button that actually sends G-code to move the pump.
     * 
     * @param pumpIndex Index of the pump to prime
     */
    fun primePumpToHome(pumpIndex: Int) {
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
        // Use effective steps per pulse (MotorConfig if step-mode, else pump's value)
        val stepsPerPulse = getEffectiveStepsPerPulse()
        
        // Calculate steps to move to reach home boundary
        val currentOffset = pump.pulseHomeOffset
        val stepsToMove = (stepsPerPulse - (currentOffset % stepsPerPulse)) % stepsPerPulse
        
        // Don't move if already at home (or very close)
        if (stepsToMove < 1f) {
            showToast("${pump.name} already at home")
            return
        }

        viewModelScope.launch {
            try {
                // Send G-code command to prime
                val flowRate = _uiState.value.flowRate.toFloatOrNull() ?: 2.0f
                val stepsPerMl = pump.calibration.toFloatOrNull() ?: 100f
                val volumeMl = stepsToMove / stepsPerMl
                
                val primeGcode = com.example.mejustmix.services.GCodeGenerator.generatePrimeOnlyScript(
                     axis = pump.axis,
                     volumeMl = volumeMl,
                     stepsPerMl = stepsPerMl,
                     flowRateMlPerSec = flowRate
                )
                
                printerRepository.sendRaw(primeGcode)
                
                // After successful prime, the pump is now at home (offset = 0)
                _uiState.update { state ->
                    val updatedPumps = state.pumps.toMutableList()
                    updatedPumps[pumpIndex] = pump.copy(
                        pulseHomeOffset = 0f  // Reset to 0 after successful prime to home
                    )
                    state.copy(pumps = updatedPumps)
                }
                saveSettings()
                showToast("${pump.name} primed to home position")
            } catch (e: Exception) {
                showToast("Error priming: ${e.message}")
            }
        }
    }
    
    /**
     * Save pump position based on observed roller angle.
     * This eliminates the need for priming - just tell the app where the roller is.
     * 
     * @param pumpIndex Index of the pump
     * @param observedAngle Current roller angle in degrees (0-360)
     * @param driftDegrees Measured drift from expected position (null if first calibration)
     */
    fun savePumpAngle(pumpIndex: Int, observedAngle: Float, driftDegrees: Float?) {
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
        
        // Safer access to potentially null fields (from old JSON files)
        val safeDriftHistory = pump.driftHistory ?: emptyList()
        val safeDriftCompensation = pump.driftCompensation ?: 0f
        
        // Calculate offset from angle using geometry utils
        val stepsToHome = com.example.mejustmix.utils.PulseGeometryUtils.stepsToNextBoundary(observedAngle)
        
        // Update drift history if we have drift data
        val updatedDriftHistory = if (driftDegrees != null) {
            // Keep last 10 drift measurements
            (safeDriftHistory + driftDegrees).takeLast(10)
        } else {
            safeDriftHistory
        }
        
        // Analyze drift pattern for auto-compensation
        val driftAnalysis = com.example.mejustmix.utils.PulseGeometryUtils.analyzeDriftPattern(updatedDriftHistory)
        val newCompensation = driftAnalysis.recommendedCompensation ?: safeDriftCompensation
        
        _uiState.update { state ->
            val updatedPumps = state.pumps.toMutableList()
            updatedPumps[pumpIndex] = pump.copy(
                pulseHomeOffset = stepsToHome,
                lastKnownAngle = observedAngle,
                driftHistory = updatedDriftHistory,
                driftCompensation = newCompensation
            )
            state.copy(pumps = updatedPumps)
        }
        saveSettings()
        
        // Show appropriate message
        val message = when {
            driftAnalysis.recommendedCompensation != null && driftDegrees != null ->
                "${pump.name} position saved. Drift pattern detected: ${String.format(Locale.US, "%.1f", driftAnalysis.averageDrift)}°/session auto-compensated!"
            driftDegrees != null ->
                "${pump.name} position saved. Drift: ${String.format(Locale.US, "%.1f", driftDegrees)}° (${updatedDriftHistory.size}/5 samples for pattern detection)"
            else ->
                "${pump.name} position saved at ${observedAngle.toInt()}°"
        }
        showToast(message)
    }
    
    /**
     * Clear drift history for a pump (e.g., after tube change).
     */
    fun clearPumpDriftHistory(pumpIndex: Int) {
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
        
        _uiState.update { state ->
            val updatedPumps = state.pumps.toMutableList()
            updatedPumps[pumpIndex] = pump.copy(
                driftHistory = emptyList(),
                driftCompensation = 0f
            )
            state.copy(pumps = updatedPumps)
        }
        saveSettings()
        showToast("${pump.name} drift history cleared")
    }

    
    /**
     * Update a pump's mL per pulse calibration value.
     * Steps per pulse is known from motor specs, only mL/pulse needs calibration.
     * 
     * @param pumpIndex Index of the pump
     * @param mlPerPulse Measured mL dispensed per pulse
     */
    fun updatePumpMlPerPulse(pumpIndex: Int, mlPerPulse: Float) {
        _uiState.update { state ->
            val updatedPumps = state.pumps.toMutableList()
            val pump = updatedPumps[pumpIndex]
            
            // Also update stepsPerPulse from motor specs if not set
            val stepsPerPulse = if (pump.stepsPerPulse <= 0f) {
                PulseModeCalculator.MotorSpecs.STEPS_PER_PULSE
            } else {
                pump.stepsPerPulse
            }
            
            updatedPumps[pumpIndex] = pump.copy(
                stepsPerPulse = stepsPerPulse,
                mlPerPulse = mlPerPulse
            )
            
            state.copy(pumps = updatedPumps)
        }
        saveSettings()
        showToast("${_uiState.value.pumps[pumpIndex].name} calibration saved: ${String.format(Locale.US, "%.3f", mlPerPulse)} mL/pulse")
    }
    
    /**
     * Dispense a specific number of pulses for calibration testing.
     * The pump should already be at home position before calling this.
     * 
     * @param pumpIndex Index of the pump
     * @param pulseCount Number of pulses to dispense
     */
    fun dispensePulsesForCalibration(pumpIndex: Int, pulseCount: Int) {
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
        val stepsPerPulse = getEffectiveStepsPerPulse()
        val totalSteps = (pulseCount * stepsPerPulse).toInt()
        
        // Send G-code command to dispense
        sendGCodeToController("G0 ${pump.axis}$totalSteps")
        
        // After dispensing, update the offset (pump has moved)
        _uiState.update { state ->
            val updatedPumps = state.pumps.toMutableList()
            updatedPumps[pumpIndex] = pump.copy(
                pulseHomeOffset = (totalSteps % stepsPerPulse)  // Track where we are now
            )
            state.copy(pumps = updatedPumps)
        }
        
        showToast("Dispensing $pulseCount pulses from ${pump.name}")
    }
    
    /**
     * Helper to send G-code commands to the active machine.
     */
    private fun sendGCodeToController(gcode: String) {
        viewModelScope.launch {
            printerRepository.sendRaw(listOf(gcode))
        }
    }
    // --- Spectral Sensor Methods ---
    
    fun connectSpectralSensor() {
        spectralManager.connect()
    }
    
    fun exportSpectralData() {
        val data = _uiState.value.spectralData
        if (data == null || data.size != 18) {
            showToast("No spectral data to export")
            return
        }
        
        // AS7265x wavelengths in nm
        val wavelengths = listOf(410, 435, 460, 485, 510, 535, 560, 585, 610, 645, 680, 705, 730, 760, 810, 860, 900, 940)
        
        viewModelScope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "spectral_data_$timestamp.csv"
                
                // Create CSV content
                val csvContent = buildString {
                    appendLine("Wavelength (nm),Intensity")
                    wavelengths.forEachIndexed { index, wavelength ->
                        appendLine("$wavelength,${data[index]}")
                    }
                }
                
                // Save to Downloads folder
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, filename)
                file.writeText(csvContent)
                
                showToast("Exported to Downloads/$filename")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Export failed: ${e.message}")
            }
        }
    }
    
    fun importSpectralData(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val csvText = inputStream?.bufferedReader()?.use { it.readText() }
                
                if (csvText == null) {
                    showToast("Failed to read file")
                    return@launch
                }
                
                // Parse CSV
                val lines = csvText.lines().filter { it.isNotBlank() }
                if (lines.size < 19) { // Header + 18 data lines
                    showToast("Invalid CSV format")
                    return@launch
                }
                
                val intensities = mutableListOf<Float>()
                
                // Skip header, parse data lines
                for (i in 1 until lines.size) {
                    val parts = lines[i].split(",")
                    if (parts.size >= 2) {
                        val intensity = parts[1].trim().toFloatOrNull()
                        if (intensity != null) {
                            intensities.add(intensity)
                        }
                    }
                }
                
                if (intensities.size == 18) {
                    _uiState.update { it.copy(spectralData = intensities) }
                    showToast("Spectral data imported successfully!")
                } else {
                    showToast("Invalid data: Expected 18 wavelengths, got ${intensities.size}")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Import failed: ${e.message}")
            }
        }
    }
    
    /**
     * Export K/S values and pigment weights to a JSON file in the Downloads folder.
     */
    fun exportKSDatabase() {
        val database = _uiState.value.kmDatabase ?: return
        val strengths = _uiState.value.pigmentStrengths
        val bundle = KSCalibrationBundle(database = database, strengths = strengths)

        viewModelScope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "ks_calibration_$timestamp.json"
                val json = gson.toJson(bundle)
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, filename)
                file.writeText(json)
                
                showToast("KS calibration exported to Downloads/$filename")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Import K/S values and pigment weights from a JSON file.
     */
    fun importKSDatabase(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader()?.use { it.readText() }
                
                if (jsonText == null) {
                    showToast("Failed to read file")
                    return@launch
                }
                
                // Try to parse as bundle first, then fallback to just database
                var importedDatabase: KSPigmentDatabase? = null
                var importedStrengths: PigmentStrengths? = null
                
                try {
                    val bundle = gson.fromJson(jsonText, KSCalibrationBundle::class.java)
                    if (bundle != null && bundle.database != null) {
                        importedDatabase = bundle.database
                        importedStrengths = bundle.strengths
                    }
                } catch (e: Exception) {
                    // Fallback to old format
                    importedDatabase = gson.fromJson(jsonText, KSPigmentDatabase::class.java)
                }

                if (importedDatabase != null) {
                    _uiState.update { state -> 
                        state.copy(
                            kmDatabase = importedDatabase,
                            pigmentStrengths = importedStrengths ?: state.pigmentStrengths
                        )
                    }
                    saveSettings()
                    showToast("KS calibration imported successfully!")
                } else {
                    showToast("Invalid KS database format")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Import failed: ${e.message}")
            }
        }
    }

    // --- BLE Device Discovery ---
    
    /**
     * Start scanning for BLE devices (FluidNC + Spectral Sensor).
     */
    fun startBLEScan() {
        bleScanner.startScanning()
    }
    
    /**
     * Stop BLE scanning.
     */
    fun stopBLEScan() {
        bleScanner.stopScanning()
    }
    
    /**
     * Get discovered FluidNC devices.
     */
    fun getDiscoveredFluidNCDevices() = bleScanner.fluidNCDevices
    
    /**
     * Get discovered spectral sensors.
     */
    fun getDiscoveredSpectralSensors() = bleScanner.spectralSensors
    
    /**
     * Check if currently scanning.
     */
    fun isScanning() = bleScanner.isScanningState
    
    // --- Connection Mode ---
    
    /**
     * Set the connection mode (BLE or WiFi).
     */
    fun setConnectionMode(mode: ConnectionType) {
        _uiState.update { it.copy(connectionMode = mode) }
        saveSettings()
    }
    
    // --- Pulse Compensation Geometry Settings ---
    
    /**
     * Update the pillow length geometry (mm).
     */
    fun setPillowLengthMm(length: Float) {
        // Allow user to enter what they want (must be non-negative)
        _uiState.update { it.copy(pillowLengthMm = length.coerceAtLeast(0f)) }
        saveSettings()
    }
    
    /**
     * Update the tube inner diameter (mm).
     */
    fun setTubeInnerDiameterMm(diameter: Float) {
        _uiState.update { it.copy(tubeInnerDiameterMm = diameter.coerceAtLeast(0f)) }
        saveSettings()
    }
    
    /**
     * Update the full diameter section length (mm).
     */
    fun setFullDiameterSectionMm(length: Float) {
        // Allow 0 fully (triangular pillow profile)
        _uiState.update { it.copy(fullDiameterSectionMm = length.coerceAtLeast(0f)) }
        saveSettings()
    }
    
    /**
     * Enable or disable dynamic acceleration control.
     */
    fun setUseDynamicAcceleration(enabled: Boolean) {
        _uiState.update { it.copy(useDynamicAcceleration = enabled) }
        saveSettings()
    }
    
    /**
     * Update the taper zone acceleration (mm/s²).
     */
    fun setTaperAcceleration(value: Float) {
        _uiState.update { it.copy(taperAcceleration = value.coerceIn(50f, 2000f)) }
        saveSettings()
    }
    
    /**
     * Update the nominal acceleration for full-flow zones (mm/s²).
     */
    fun setNominalAcceleration(value: Float) {
        _uiState.update { it.copy(nominalAcceleration = value.coerceIn(100f, 3000f)) }
        saveSettings()
    }
    
    /**
     * Enable or disable Simul-Mix (Parallel Dispensing).
     */
    fun setUseSimulMix(enabled: Boolean) {
        _uiState.update { it.copy(useSimulMix = enabled) }
        saveSettings()
    }
    
    
    /**
     * Update the max feed rate limit (mm/min).
     */
    fun setMaxFeedRate(value: Float) {
        _uiState.update { it.copy(maxFeedRate = value.coerceIn(1000f, 20000f)) }
        saveSettings()
    }
    
    // --- Pulse Geometry Wizard Support ---
    
    /**
     * Jog pump with backlash compensation for geometry wizard.
     * If moving backwards, overshoots and returns to eliminate gear play.
     */
    fun jogPumpWithBacklash(pumpIndex: Int, steps: Float) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (steps < 0) {
                // Reverse: overshoot by 5 steps, then return
                val overshoot = 5f
                printerRepository.jogPump(pumpIndex, steps - overshoot, _uiState.value.pumps)
                kotlinx.coroutines.delay(200)
                printerRepository.jogPump(pumpIndex, overshoot, _uiState.value.pumps)
            } else {
                printerRepository.jogPump(pumpIndex, steps, _uiState.value.pumps)
            }
        }
    }
    
    /**
     * Save geometry measurements from wizard.
     * Saves both global geometry (tube properties) and per-pump phase offset (timing).
     */
    // Calibration Session State (Key = Pump Index, Value = Measurement)
    private val fullDiameterMeasurements = mutableMapOf<Int, Float>()

    /**
     * Save geometry measurements from wizard.
     * Saves both global geometry (tube properties) and per-pump phase offset (timing).
     * NOW AVERAGES global geometry across multiple pump measurements (Updating existing).
     */
    fun saveGeometryFromWizard(
        pumpIndex: Int,
        taperStartSteps: Float,
        taperLengthMm: Float,
        fullDiameterMm: Float
    ) {
        // 1. Accumulate/Update Measurement for this Pump
        fullDiameterMeasurements[pumpIndex] = fullDiameterMm
        val avgFullDiameter = fullDiameterMeasurements.values.average().toFloat()

        // 2. Save GLOBAL geometry (Averaged)
        _uiState.update { 
            it.copy(fullDiameterSectionMm = avgFullDiameter.coerceAtLeast(0f)) 
        }
        
        // 3. Save PER-PUMP phase offset (timing is always local)
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
        val updatedPump = pump.copy(pulseHomeOffset = -taperStartSteps)
        
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            newPumps[pumpIndex] = updatedPump
            state.copy(pumps = newPumps)
        }
        
        saveSettings()
        showToast("${pump.name} Saved. Global Geometry Avg (N=${fullDiameterMeasurements.size}): ${String.format("%.1f", avgFullDiameter)}mm")
    }
    
    /**
     * Update steps per pulse (rotation calibration) for a specific pump.
     */
    fun updateStepsPerPulse(pumpIndex: Int, stepsPerPulse: Float) {
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return
        val updatedPump = pump.copy(stepsPerPulse = stepsPerPulse)
        
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            newPumps[pumpIndex] = updatedPump
            state.copy(pumps = newPumps)
        }
        
        saveSettings()
        showToast("${pump.name}: Rotation calibrated (${String.format(Locale.US, "%.0f", stepsPerPulse)} steps/pulse)")
    }
    
    // ==================== MOTOR & STEP-BASED G-CODE METHODS ====================
    
    /**
     * Toggle step-based G-code mode.
     * When enabled, FluidNC is configured with $10X=1.0 (1 step = 1 unit).
     */
    fun toggleStepBasedGCode(enabled: Boolean) {
        _uiState.update { state ->
            val newState = state.copy(
                useStepBasedGCode = enabled,
                fluidNCConfigSynced = false  // Mark as needing sync
            )
            
            // Push Propagation: Update all pumps with new step configuration
            if (enabled) {
                val newStepCount = newState.motorConfig.stepsPerPulse
                val updatedPumps = newState.pumps.map { pump ->
                    pump.copy(stepsPerPulse = newStepCount)
                }
                newState.copy(pumps = updatedPumps)
            } else {
                newState
            }
        }
        saveSettings()
        
        if (enabled) {
            showToast("Step-based G-code enabled & synced to all pumps.")
        }
    }
    
    /**
     * Update motor configuration.
     * This affects step calculations for all pumps.
     */
    fun updateMotorConfig(config: MotorConfig) {
        _uiState.update { state -> 
            val newState = state.copy(
                motorConfig = config,
                fluidNCConfigSynced = false  // Mark as needing sync
            )
            
            // Push Propagation: If step-based is active, auto-update all pumps
            if (state.useStepBasedGCode) {
                val newStepCount = config.stepsPerPulse
                val updatedPumps = newState.pumps.map { pump ->
                    pump.copy(stepsPerPulse = newStepCount)
                }
                newState.copy(pumps = updatedPumps)
            } else {
                newState
            }
        }
        saveSettings()
    }
    
    /**
     * Sync motor speed and acceleration to FluidNC.
     * Sends $11X (max feed rate) and $12X (acceleration) commands for each axis via G-code.
     * Note: $10X (steps/unit) must be set in the FluidNC config file manually.
     */
    fun syncMotorConfigToFluidNC() {
        val state = _uiState.value
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val motorConfig = state.motorConfig
                val commands = mutableListOf<String>()
                
                // Generate $11X (max feed rate) and $12X (acceleration) for each axis
                // We only send speed/accel - $10X must be configured in config file
                state.pumps.forEach { pump ->
                    val axisIndex = pump.axisIndex
                    // $11X = max feed rate in steps/min
                    commands.add("\$11$axisIndex=${motorConfig.maxFeedRateStepsPerMin}")
                    // $12X = acceleration in steps/sec²
                    commands.add("\$12$axisIndex=${motorConfig.accelerationStepsPerSec2}")
                }
                
                // Send commands to FluidNC
                printerRepository.sendGCodeCommands(commands)
                
                // Mark as synced
                _uiState.update { it.copy(fluidNCConfigSynced = true) }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Applied speed & acceleration to ${state.pumps.size} axes")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Sync failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Read current FluidNC configuration.
     * Parses $ response to update local motor config.
     * 
     * Note: This is a placeholder - actual implementation requires
     * parsing FluidNC's $ response which is complex.
     */
    fun readMotorConfigFromFluidNC() {
        readFluidNCConfig()
    }
    
    /**
     * Alias for readMotorConfigFromFluidNC for UI compatibility.
     */
    fun readFluidNCConfig() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Send $ command to query settings
                // Note: This requires FluidNCService to capture and return the response
                // For now, just show a toast indicating the feature
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Reading FluidNC config... (check terminal for values)")
                }
                
                // Send the query command
                printerRepository.sendRaw(listOf("$"))
                
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showToast("Read failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Get the current motor configuration's steps per pulse.
     * This is the authoritative source for step calculations when step-based mode is enabled.
     */
    fun getEffectiveStepsPerPulse(): Float {
        return if (_uiState.value.useStepBasedGCode) {
            _uiState.value.motorConfig.stepsPerPulse
        } else {
            // Fall back to pump's individual stepsPerPulse (legacy mode)
            _uiState.value.pumps.firstOrNull()?.stepsPerPulse ?: 50f
        }
    }
    
    /**
     * Calculate steps needed to dispense a given volume using motor config.
     */
    fun calculateStepsForVolume(volumeMl: Float, pumpIndex: Int): Float {
        val pump = _uiState.value.pumps.getOrNull(pumpIndex) ?: return 0f
        val motorConfig = _uiState.value.motorConfig
        
        if (pump.mlPerPulse <= 0f) return 0f
        
        val pulses = volumeMl / pump.mlPerPulse
        return if (_uiState.value.useStepBasedGCode) {
            pulses * motorConfig.stepsPerPulse
        } else {
            pulses * pump.stepsPerPulse
        }
    }
}

