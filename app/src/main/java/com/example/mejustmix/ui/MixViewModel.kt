package com.example.mejustmix.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mejustmix.data.ColorFolder
import com.example.mejustmix.data.PhotoFolder
import com.example.mejustmix.data.SavedColor
import com.example.mejustmix.data.SavedPhoto
import com.example.mejustmix.data.SortOption
import com.example.mejustmix.data.LibraryRepository
import com.example.mejustmix.data.PhotoLibraryRepository
import com.example.mejustmix.data.HistoryItem
import com.example.mejustmix.data.PaintMix
import com.example.mejustmix.services.BackupService
import com.example.mejustmix.services.ColorMixingService
import com.example.mejustmix.services.FluidNCService
import com.example.mejustmix.services.FluidNCStatus
import com.example.mejustmix.services.GCodeGenerator
import com.example.mejustmix.services.KubelkaMunkColorMixing
import com.example.mejustmix.ui.SettingsUiState 
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sqrt

class MixViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsViewModel: SettingsViewModel
) : AndroidViewModel(application) {

    companion object {
        private const val COLOR_CHANGE_THRESHOLD = 0.05f
        private const val MAX_HISTORY_SIZE = 25
        private const val RECONNECT_DELAY_MS = 200L
        private const val IP_DEBOUNCE_MS = 1000L
    }

    // --- State ---
    private val _color = MutableStateFlow(Color.White)
    val color = _color.asStateFlow()

    private val _paintMix = MutableStateFlow(PaintMix(0f, 0f, 0f, 0f, 0f))
    val paintMix = _paintMix.asStateFlow()

    // Predicted color from K-M mixing (The "Real Paint" Preview)
    private val _predictedColor = MutableStateFlow(Color.White)
    val predictedColor: StateFlow<Color> = _predictedColor.asStateFlow()

    private val _totalVolume = MutableStateFlow(5f)
    val totalVolume = _totalVolume.asStateFlow()

    // Raw libraries
    private val _libraryRaw = MutableStateFlow<List<ColorFolder>>(emptyList())
    private val _photoLibraryRaw = MutableStateFlow<List<PhotoFolder>>(emptyList())

    // --- SORTED LIBRARIES ---
    val library: StateFlow<List<ColorFolder>> = _libraryRaw.map { folders ->
        folders.map { folder ->
            val sortedColors = when (folder.sortOption) {
                SortOption.DATE_DESC -> folder.colors.sortedByDescending { it.createdAt }
                SortOption.NAME_ASC -> folder.colors.sortedBy { it.name.lowercase() }
                SortOption.HUE -> folder.colors.sortedBy { getHue(it.color) }
                else -> folder.colors
            }
            folder.copy(colors = sortedColors)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val photoLibrary: StateFlow<List<PhotoFolder>> = _photoLibraryRaw.map { folders ->
        folders.map { folder ->
            val sortedPhotos = when (folder.sortOption) {
                SortOption.DATE_DESC, SortOption.HUE -> folder.photos.sortedByDescending { it.createdAt }
                SortOption.NAME_ASC -> folder.photos.sortedBy { it.name.lowercase() }
                else -> folder.photos
            }
            folder.copy(photos = sortedPhotos)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    private val _lastUsedPhotoFolderName = MutableStateFlow<String?>(null)
    val lastUsedPhotoFolderName = _lastUsedPhotoFolderName.asStateFlow()

    private val _currentImageUri = MutableStateFlow<Uri?>(null)
    val currentImageUri = _currentImageUri.asStateFlow()

    private val _fluidNCStatus = MutableStateFlow<FluidNCStatus?>(null)
    val fluidNCStatus = _fluidNCStatus.asStateFlow()

    private val _retraction = MutableStateFlow(15f)
    val retraction = _retraction.asStateFlow()

    private val _isOutOfGamut = MutableStateFlow(false)
    val isOutOfGamut: StateFlow<Boolean> = _isOutOfGamut.asStateFlow()

    private val _mixHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val mixHistory = _mixHistory.asStateFlow()

    private val _lastUsedFolderName = MutableStateFlow<String?>(null)
    val lastUsedFolderName = _lastUsedFolderName.asStateFlow()

    private val libraryRepository = LibraryRepository(getApplication())
    private val photoLibraryRepository = PhotoLibraryRepository(getApplication())

    private var fluidNCService: FluidNCService? = null

    private val _gcodeHistory = MutableStateFlow<List<String>>(emptyList())
    val gcodeHistory = _gcodeHistory.asStateFlow()

    private val _pumpDepletionWarning = MutableStateFlow<String?>(null)
    val pumpDepletionWarning = _pumpDepletionWarning.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val colorHistory = mutableListOf<Color>()
    private var historyIndex = -1

    val manualBaseName = mutableStateOf<String?>(null)
    val manualTransparency = mutableStateOf(0f)
    val includeWhitePump = mutableStateOf(true)

    init {
        // Sync retraction from settings
        settingsViewModel.uiState
            .map { it.retractionSteps.toFloatOrNull() ?: 15f }
            .distinctUntilChanged()
            .onEach { steps -> _retraction.value = steps }
            .launchIn(viewModelScope)
        
        // Sync IP Address
        settingsViewModel.uiState
            .map { it.ipAddress }
            .distinctUntilChanged()
            .debounce(IP_DEBOUNCE_MS)
            .onEach { ip -> reconnectFluidNCService(ip) }
            .launchIn(viewModelScope)

        // CRITICAL UPDATE: Watch for Pigment Database or Strength changes
        // This ensures the preview updates instantly if you change settings/code
        settingsViewModel.uiState
            .map { Triple(it.pigmentStrengths, it.useKubelkaMunk, it.kmDatabase) }
            .distinctUntilChanged { old, new -> old == new } // Only trigger if these specific fields change
            .onEach { calculateMix() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _libraryRaw.value = libraryRepository.loadLibrary()
            _photoLibraryRaw.value = photoLibraryRepository.loadPhotoLibrary()
        }
        
        // Initial Calculation
        calculateMix()
    }

    // --- CORE CALCULATION (OPTION A IMPLEMENTATION) ---

    private fun calculateMix() {
        val settings = settingsViewModel.uiState.value
        
        // 1. Calculate the mix ratios
        val newMix = ColorMixingService.calculateMixRatios(
            colorInt = _color.value.toArgb(), 
            strengths = settings.pigmentStrengths,
            useKubelkaMunk = settings.useKubelkaMunk
        )
        
        if (newMix != _paintMix.value) {
            _paintMix.value = newMix
        }
        
        // 2. PREDICT THE REALITY (Physics Engine)
        // This calculates exactly what the paint will look like based on K/S values.
        // It bypasses the "Fake" Simulator entirely.
        
        val database = settings.kmDatabase ?: KubelkaMunkColorMixing.createDefaultPigmentDatabase()
        val predictedRgbInt = KubelkaMunkColorMixing.previewMixedColor(newMix, database)
        
        _predictedColor.value = Color(predictedRgbInt)
    }

    // --- HELPER FUNCTIONS ---

    fun updateConnectionStatus(status: FluidNCStatus) {
        _fluidNCStatus.value = status
    }

    fun addToTerminalHistory(message: String) {
        _gcodeHistory.update { it + message }
    }

    // --- Sorting Actions ---
    
    fun setFolderSort(folderId: String, sortOption: SortOption) {
        _libraryRaw.update { lib ->
            lib.map { if (it.id == folderId) it.copy(sortOption = sortOption) else it }
        }
        saveLibrary()
    }

    fun setPhotoFolderSort(folderId: String, sortOption: SortOption) {
        _photoLibraryRaw.update { lib ->
            lib.map { if (it.id == folderId) it.copy(sortOption = sortOption) else it }
        }
        savePhotoLibrary()
    }

    private fun getHue(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[0]
    }

    private fun reconnectFluidNCService(ipAddress: String) {
        viewModelScope.launch {
            fluidNCService?.disconnect()
            delay(RECONNECT_DELAY_MS)
            
            fluidNCService = FluidNCService(
                context = getApplication(),
                onStatusChange = { status -> updateConnectionStatus(status) },
                onGCodeSent = { gcode -> addToTerminalHistory(">> $gcode") }
            )
            
            fluidNCService?.connect(ipAddress, 23)
        }
    }

    override fun onCleared() {
        fluidNCService?.disconnect()
        super.onCleared()
    }

    private fun saveLibrary() {
        viewModelScope.launch {
            libraryRepository.saveLibrary(_libraryRaw.value)
        }
    }

    fun setRetraction(value: Float) { _retraction.value = value }

    fun setColor(color: Color) { updateColor(color) }

    fun setBrightness(brightness: Float) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(_color.value.toArgb(), hsv)
        hsv[2] = brightness
        updateColor(Color(android.graphics.Color.HSVToColor(hsv)))
    }

    private fun checkGamut(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        
        val luminance = ColorUtils.calculateLuminance(color.toArgb()).toFloat()
        val peakLuminance = getPeakLuminanceForHue(hue)
        val isSafeZone = luminance <= peakLuminance

        val maxPermissibleSat = if (isSafeZone) {
            1.0f 
        } else {
            val range = 1.0f - peakLuminance
            if (range > 0f) {
                val progress = (luminance - peakLuminance) / range
                (1.0f - progress) * 1.15f
            } else {
                0f
            }
        }

        _isOutOfGamut.value = (saturation > 0.15f) && (saturation > maxPermissibleSat)
    }

    private fun getPeakLuminanceForHue(hue: Float): Float {
        return when {
            hue < 60 -> lerp(0.25f, 0.92f, hue / 60f)          
            hue < 120 -> lerp(0.92f, 0.35f, (hue - 60) / 60f)  
            hue < 180 -> lerp(0.35f, 0.30f, (hue - 120) / 60f) 
            hue < 240 -> lerp(0.30f, 0.15f, (hue - 180) / 60f) 
            hue < 300 -> lerp(0.15f, 0.30f, (hue - 240) / 60f) 
            else -> lerp(0.30f, 0.25f, (hue - 300) / 60f)      
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }

    fun updateColor(newColor: Color) {
        val currentStateColor = _color.value
        val dist = sqrt(
            (newColor.red - currentStateColor.red).let { it * it } +
            (newColor.green - currentStateColor.green).let { it * it } +
            (newColor.blue - currentStateColor.blue).let { it * it }
        )
        
        if (dist < COLOR_CHANGE_THRESHOLD) return
        
        if (historyIndex < colorHistory.size - 1) {
            colorHistory.subList(historyIndex + 1, colorHistory.size).clear()
        }
        colorHistory.add(newColor)
        historyIndex = colorHistory.size - 1
        
        if (colorHistory.size > 50) {
            colorHistory.removeAt(0)
            historyIndex--
        }
        
        _color.value = newColor
        checkGamut(newColor)
        calculateMix()
    }

    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            _color.value = colorHistory[historyIndex]
            checkGamut(colorHistory[historyIndex])
            calculateMix()
        }
    }

    fun redo() {
        if (historyIndex < colorHistory.size - 1) {
            historyIndex++
            _color.value = colorHistory[historyIndex]
            checkGamut(colorHistory[historyIndex])
            calculateMix()
        }
    }

    fun canUndo(): Boolean = historyIndex > 0
    fun canRedo(): Boolean = historyIndex < colorHistory.size - 1

    fun setTotalVolume(volume: Float) { _totalVolume.value = volume }

    fun isMixPossible(volume: Float): Boolean {
        val settings = settingsViewModel.uiState.value
        return settings.pumps.size >= 5 &&
            _paintMix.value.cyan * volume <= settings.pumps[0].currentVolumeMl &&
            _paintMix.value.magenta * volume <= settings.pumps[1].currentVolumeMl &&
            _paintMix.value.yellow * volume <= settings.pumps[2].currentVolumeMl &&
            _paintMix.value.black * volume <= settings.pumps[3].currentVolumeMl &&
            _paintMix.value.white * volume <= settings.pumps[4].currentVolumeMl
    }

    fun sendMix() {
        viewModelScope.launch(Dispatchers.Default) {
            _isSending.value = true
            
            try {
                addToTerminalHistory(">> DEBUG: Dispense Button Clicked")
    
                val settings = settingsViewModel.uiState.value
                val currentStatus = _fluidNCStatus.value
                
                if (!settings.bypassConnectionCheck) {
                    val validStates = listOf("Connected", "Idle", "Run", "Jog", "Hold")
                    if (currentStatus == null || currentStatus.state !in validStates) {
                        val msg = "Cannot dispense: Not Connected (${currentStatus?.state})"
                        addToTerminalHistory(">> DEBUG: $msg")
                        _pumpDepletionWarning.value = msg
                        return@launch
                    }
                }
    
                val isManualMode = manualBaseName.value != null || settings.useManualBase
                val paintRatio = 1f - manualTransparency.value
                val dispenseVolume = _totalVolume.value * paintRatio
                
                val mixToSend = if (isManualMode && !includeWhitePump.value) {
                    val original = _paintMix.value
                    val totalRemaining = original.cyan + original.magenta + original.yellow + original.black
                    if (totalRemaining > 0) {
                        PaintMix(
                            original.cyan / totalRemaining,
                            original.magenta / totalRemaining,
                            original.yellow / totalRemaining,
                            original.black / totalRemaining,
                            0f
                        )
                    } else {
                        PaintMix(0f, 0f, 0f, 0f, 0f)
                    }
                } else {
                    _paintMix.value
                }
    
                val gcode = GCodeGenerator.generateMixingScript(
                    mix = mixToSend,
                    totalVolumeMl = dispenseVolume, 
                    retractionSteps = _retraction.value,
                    pumps = settings.pumps,
                    flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f
                )
                
                if (gcode.isEmpty()) {
                     _pumpDepletionWarning.value = "Error: Generated G-Code is empty."
                     return@launch
                }
                
                fluidNCService?.sendMultiple(gcode)
                
                addToHistory(_paintMix.value, _color.value)
                settingsViewModel.consumePaint(
                    mix = mixToSend, 
                    totalVolume = dispenseVolume,
                    onPumpDepleted = { pumpName ->
                        _pumpDepletionWarning.value = "$pumpName is empty!"
                    }
                )

            } catch (e: Exception) {
                val err = "Error generating G-code: ${e.message}"
                addToTerminalHistory(">> DEBUG: $err")
                e.printStackTrace()
                _pumpDepletionWarning.value = err
            } finally {
                _isSending.value = false
            }
        }
    }

    fun sendRawGCode(gcode: List<String>) {
        viewModelScope.launch(Dispatchers.Default) {
            _isSending.value = true
            try {
                fluidNCService?.sendMultiple(gcode)
            } catch (e: Exception) {
                addToTerminalHistory(">> Error sending G-code: ${e.message}")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearManualMode() {
        manualBaseName.value = null
        manualTransparency.value = 0f
        includeWhitePump.value = true
    }

    fun primePump(axis: String, amount: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            _isSending.value = true
            try {
                val settings = settingsViewModel.uiState.value
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
                addToTerminalHistory(">> Error priming pump: ${e.message}")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun retractAll() {
        viewModelScope.launch(Dispatchers.Default) {
            _isSending.value = true
            try {
                val settings = settingsViewModel.uiState.value
                val gcode = GCodeGenerator.generateRetractAllScript(
                    pumps = settings.pumps,
                    retractionSteps = _retraction.value,
                    flowRateMlPerSec = settings.flowRate.toFloatOrNull() ?: 2.0f
                )
                fluidNCService?.sendMultiple(gcode)
            } catch (e: Exception) {
                addToTerminalHistory(">> Error retracting: ${e.message}")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun addToHistory(currentMix: PaintMix, currentColor: Color) {
        val newItem = HistoryItem(color = currentColor, paintMix = currentMix)
        val currentList = _mixHistory.value.toMutableList()
        currentList.add(0, newItem) 
        if (currentList.size > MAX_HISTORY_SIZE) {
            currentList.removeLast()
        }
        _mixHistory.value = currentList
    }

    fun restoreFromHistory(item: HistoryItem) {
        _paintMix.value = item.paintMix
        _color.value = item.color
        checkGamut(item.color) 
    }

    fun saveColorToLibrary(folderName: String) {
        val newColor = SavedColor(id = UUID.randomUUID().toString(), color = _color.value)
        _libraryRaw.update { currentLibrary ->
            val existingFolder = currentLibrary.find { it.name == folderName }
            if (existingFolder != null) {
                currentLibrary.map { if (it.name == folderName) it.copy(colors = it.colors + newColor) else it }
            } else {
                currentLibrary + ColorFolder(id = UUID.randomUUID().toString(), name = folderName, colors = listOf(newColor))
            }
        }
        _lastUsedFolderName.value = folderName
        saveLibrary()
    }

    fun deleteColor(colorId: String) {
        _libraryRaw.update { lib -> 
            lib.map { folder -> 
                folder.copy(colors = folder.colors.filterNot { c -> c.id == colorId }) 
            }.filterNot { it.colors.isEmpty() } 
        }
        saveLibrary()
    }
    
    fun renameColor(colorId: String, newName: String) {
        _libraryRaw.update { lib -> lib.map { it.copy(colors = it.colors.map { c -> if(c.id == colorId) c.copy(name = newName) else c }) } }
        saveLibrary()
    }
    
    fun deleteFolder(folderId: String) {
        _libraryRaw.update { it.filterNot { f -> f.id == folderId } }
        saveLibrary()
    }
    
    fun renameFolder(folderId: String, newName: String) {
        _libraryRaw.update { lib -> lib.map { if (it.id == folderId) it.copy(name = newName) else it } }
        saveLibrary()
    }

    fun clearPumpWarning() { _pumpDepletionWarning.value = null }
    
    fun setCurrentImage(uri: Uri?) { _currentImageUri.value = uri }

    fun savePhotoToLibrary(folderName: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val savedFile = copyImageToInternalStorage(context, uri)
            
            if (savedFile != null) {
                val savedUri = Uri.fromFile(savedFile)
                val newPhoto = SavedPhoto(id = UUID.randomUUID().toString(), uriString = savedUri.toString())

                _photoLibraryRaw.update { currentLibrary ->
                    val existingFolder = currentLibrary.find { it.name == folderName }
                    if (existingFolder != null) {
                        currentLibrary.map { if (it.name == folderName) it.copy(photos = it.photos + newPhoto) else it }
                    } else {
                        currentLibrary + PhotoFolder(id = UUID.randomUUID().toString(), name = folderName, photos = listOf(newPhoto))
                    }
                }
                _lastUsedPhotoFolderName.value = folderName
                savePhotoLibrary()
            }
        }
    }

    private fun copyImageToInternalStorage(context: android.content.Context, sourceUri: Uri): java.io.File? {
        return try {
            val photosDir = java.io.File(context.filesDir, "saved_photos")
            if (!photosDir.exists()) photosDir.mkdirs()

            val fileName = "photo_${UUID.randomUUID()}.jpg"
            val destFile = java.io.File(photosDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deletePhoto(photoId: String) {
        _photoLibraryRaw.update { lib -> 
            lib.map { folder -> 
                folder.copy(photos = folder.photos.filterNot { p -> p.id == photoId }) 
            }.filterNot { it.photos.isEmpty() }
        }
        savePhotoLibrary()
    }
    
    fun renamePhoto(photoId: String, newName: String) {
        _photoLibraryRaw.update { lib -> lib.map { it.copy(photos = it.photos.map { p -> if(p.id == photoId) p.copy(name = newName) else p }) } }
        savePhotoLibrary()
    }
    
    fun deletePhotoFolder(folderId: String) {
        _photoLibraryRaw.update { it.filterNot { f -> f.id == folderId } }
        savePhotoLibrary()
    }
    
    fun renamePhotoFolder(folderId: String, newName: String) {
        _photoLibraryRaw.update { lib -> lib.map { if (it.id == folderId) it.copy(name = newName) else it } }
        savePhotoLibrary()
    }

    private fun savePhotoLibrary() {
        viewModelScope.launch {
            photoLibraryRepository.savePhotoLibrary(_photoLibraryRaw.value)
        }
    }

    fun exportMixRecipe(): String {
        val mix = _paintMix.value
        val color = _color.value
        val volume = _totalVolume.value
        val colorHex = String.format("#%08X", color.toArgb())
        val cPercent = String.format("%.1f", mix.cyan * 100)
        val mPercent = String.format("%.1f", mix.magenta * 100)
        val yPercent = String.format("%.1f", mix.yellow * 100)
        val kPercent = String.format("%.1f", mix.black * 100)
        val wPercent = String.format("%.1f", mix.white * 100)
        val cVol = (mix.cyan * volume).toMlString(2)
        val mVol = (mix.magenta * volume).toMlString(2)
        val yVol = (mix.yellow * volume).toMlString(2)
        val kVol = (mix.black * volume).toMlString(2)
        val wVol = (mix.white * volume).toMlString(2)
        
        return "MeJustMix Recipe\n================\nColor: $colorHex\n\nMix Ratios:\n- Cyan: $cPercent%\n- Magenta: $mPercent%\n- Yellow: $yPercent%\n- Black: $kPercent%\n- White: $wPercent%\n\nVolumes (for ${volume.toMlString()}):\n- Cyan: $cVol\n- Magenta: $mVol\n- Yellow: $yVol\n- Black: $kVol\n- White: $wVol\n"
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSending.value = true
                addToTerminalHistory(">> Starting Backup Export...")
                BackupService.createBackup(getApplication(), uri, settingsViewModel.uiState.value, _libraryRaw.value, _photoLibraryRaw.value)
                addToTerminalHistory(">> Backup Export Successful!")
            } catch (e: Exception) {
                addToTerminalHistory(">> Backup Failed: ${e.message}")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSending.value = true
                addToTerminalHistory(">> Starting Backup Import...")
                val data = BackupService.restoreBackup(getApplication(), uri)
                _libraryRaw.value = data.colorLibrary
                _photoLibraryRaw.value = data.photoLibrary
                saveLibrary()
                savePhotoLibrary()
                kotlinx.coroutines.withContext(Dispatchers.Main) { settingsViewModel.restoreSettings(data.settings) }
                addToTerminalHistory(">> Backup Import Successful!")
            } catch (e: Exception) {
                addToTerminalHistory(">> Import Failed: ${e.message}")
            } finally {
                _isSending.value = false
            }
        }
    }
}

fun Float.toMlString(decimals: Int = 1): String {
    val fmt = "%." + decimals + "f"
    return String.format(fmt, this) + " mL"
}