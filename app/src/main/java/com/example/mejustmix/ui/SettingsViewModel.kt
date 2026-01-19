package com.example.mejustmix.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import com.example.mejustmix.services.PigmentStrengths
import com.example.mejustmix.services.KSColor
import com.example.mejustmix.services.KSPigmentDatabase
import com.example.mejustmix.services.KubelkaMunkColorMixing
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Fully defined PumpConfig
data class PumpConfig(
    val name: String,
    val axis: String,
    val calibration: String = "100.0", // steps per mL
    val currentVolumeMl: Float = 100f,
    val maxVolumeMl: Float = 100f,
    val colorArgb: Int = Color.Cyan.toArgb() 
)

data class SettingsUiState(
    val ipAddress: String = "192.168.1.100",
    val webPortalPort: String = "81",
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
        PumpConfig("Cyan", "X", "100.0", 100f, 100f, Color(0xFF0074A2).toArgb()),
        
        // Quinacridone Magenta (#9F005D) - Deep, berry-like Magenta
        PumpConfig("Magenta", "Y", "100.0", 100f, 100f, Color(0xFF9F005D).toArgb()),
        
        // Cadmium Yellow Medium Hue (#FFD800) - Warm, golden Yellow (not neon)
        PumpConfig("Yellow", "Z", "100.0", 100f, 100f, Color(0xFFFFD800).toArgb()),
        
        PumpConfig("Black", "A", "100.0", 100f, 100f, Color.Black.toArgb()),
        PumpConfig("White", "B", "100.0", 100f, 100f, Color.White.toArgb())
    ),
    
    // Kubelka-Munk settings (now using 3-channel K/S) - DEFAULT ON
    val useKubelkaMunk: Boolean = true,
    val kmDatabase: KSPigmentDatabase? = null,
    
    // Display settings
    val showRealPaintPreview: Boolean = false,
    val realPaintPreviewIntensity: Float = 0.7f
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("mejustmix_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadSettings()
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
            prefs.edit().putString("settings_json", json).apply()
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
        _uiState.update { currentState ->
            val updatedPumps = currentState.pumps.map { pump ->
                if (pump.axis == axis) pump.copy(calibration = value) else pump
            }
            currentState.copy(pumps = updatedPumps)
        }
        saveSettings()
    }
    
    fun onPumpCalibrationChanged(pumpIndex: Int, newValue: String) {
        _uiState.update { state ->
            val newPumps = state.pumps.toMutableList()
            newPumps[pumpIndex] = newPumps[pumpIndex].copy(calibration = newValue)
            state.copy(pumps = newPumps)
        }
        saveSettings()
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
}
