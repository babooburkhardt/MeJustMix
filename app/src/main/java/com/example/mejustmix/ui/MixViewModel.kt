package com.example.mejustmix.ui

import androidx.annotation.OptIn
import kotlinx.coroutines.FlowPreview

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
import com.example.mejustmix.services.FluidNCStatus
import com.example.mejustmix.services.KubelkaMunkColorMixing
import com.example.mejustmix.ui.SettingsUiState 
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.sqrt

@OptIn(FlowPreview::class)
class MixViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsViewModel: SettingsViewModel = SettingsViewModel(application)
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
    
    // --- Restored State ---
    private val _gcodeHistory = MutableStateFlow<List<String>>(emptyList())
    // Expose as StateFlow if needed by UI, or just keep private if only debugging
    val gcodeHistory = _gcodeHistory.asStateFlow()

    private val _pumpDepletionWarning = MutableStateFlow<String?>(null)
    val pumpDepletionWarning = _pumpDepletionWarning.asStateFlow()
    
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    // Manual Mode State
    val manualBaseName = mutableStateOf<String?>(null)
    val manualTransparency = mutableStateOf(0f)
    val includeWhitePump = mutableStateOf(true)

    private val libraryRepository = LibraryRepository(getApplication())
    private val photoLibraryRepository = PhotoLibraryRepository(getApplication())

    // --- REPOSITORY ---
    private val printerRepository = com.example.mejustmix.data.PrinterRepository(application)
    
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
            .onEach { ip -> printerRepository.connect(ip) }
            .launchIn(viewModelScope)

        // CRITICAL UPDATE: Watch for Pigment Database or Strength changes
        settingsViewModel.uiState
            .map { Triple(it.pigmentStrengths, it.useKubelkaMunk, it.kmDatabase) }
            .distinctUntilChanged { old, new -> old == new }
            .onEach { calculateMix() }
            .launchIn(viewModelScope)

        // Observe Printer Status
        printerRepository.connectionStatus
            .onEach { status -> updateConnectionStatus(status) }
            .launchIn(viewModelScope)
            
        printerRepository.gcodeHistory
            .onEach { history -> _gcodeHistory.value = history }
            .launchIn(viewModelScope)
            
        printerRepository.isSending
            .onEach { sending -> _isSending.value = sending }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _libraryRaw.value = libraryRepository.loadLibrary()
            _photoLibraryRaw.value = photoLibraryRepository.loadPhotoLibrary()
        }
        
        // Initial Calculation
        calculateMix()
    }
    
    fun setColor(newColor: Color) {
        if (_color.value != newColor) {
            // Save current state to undo stack before changing
            val currentState = HistoryItem(_color.value, _paintMix.value)
            undoStack.push(currentState)
            if (undoStack.size > MAX_HISTORY_SIZE) {
                undoStack.removeAt(0) // Remove oldest
            }
            redoStack.clear() // Clear redo stack on new action
            
            _color.value = newColor
            calculateMix()
            checkGamut(newColor)
        }
    }

    fun setBrightness(level: Float) {
        val current = _color.value
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(current.toArgb(), hsl)
        hsl[2] = level.coerceIn(0f, 1f)
        setColor(Color(ColorUtils.HSLToColor(hsl)))
    }
    
    fun setTotalVolume(volume: Float) {
        _totalVolume.value = volume.coerceIn(0.1f, 100f)
    }

    fun calculateMix() {
         viewModelScope.launch(Dispatchers.Default) {
             val currentColor = _color.value
             val uiState = settingsViewModel.uiState.value
             val strengths = uiState.pigmentStrengths
             val useKM = uiState.useKubelkaMunk
             
             // Calculate Mix
             val mix = ColorMixingService.calculateMixRatios(
                 currentColor.toArgb(),
                 strengths,
                 useKM
             )
             _paintMix.value = mix
             
             // Update Preview
             val predictedInt = ColorMixingService.previewMixedColor(mix, useKM)
             _predictedColor.value = Color(predictedInt)
         }
    }
    
    fun setTargetColorFromSpectral(spectralData: List<Float>) {
        viewModelScope.launch(Dispatchers.Default) {
            val whiteRef = settingsViewModel.uiState.value.whiteReference
            if (whiteRef != null) {
                val rgbInt = KubelkaMunkColorMixing.calculateRGBFromSpectral(spectralData, whiteRef)
                setColor(Color(rgbInt))
            }
        }
    }

    fun checkGamut(color: Color) {
        // Simple check: if saturation is very high, it might be out of gamut for CMYK
        // Real implementation would compare against pigment max chroma
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        _isOutOfGamut.value = hsl[1] > 0.95f && hsl[2] in 0.2f..0.8f
    }
    
    private fun getHue(color: Color): Float {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        return hsl[0]
    }
    
    private fun saveLibrary() {
         viewModelScope.launch {
             libraryRepository.saveLibrary(_libraryRaw.value)
         }
    }

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

    private val undoStack = java.util.Stack<HistoryItem>()
    private val redoStack = java.util.Stack<HistoryItem>()

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = HistoryItem(_color.value, _paintMix.value)
            redoStack.push(current)
            restoreFromHistory(undoStack.pop())
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = HistoryItem(_color.value, _paintMix.value)
            undoStack.push(current)
            restoreFromHistory(redoStack.pop())
        }
    }

    fun isMixPossible(volumeToCheck: Float = _totalVolume.value): Boolean {
        // Basic Check: Is fluidNC connected OR "Bypass" enabled?
        // AND total volume > 0
        // AND not currently sending
        val status = _fluidNCStatus.value?.state
        // Valid FluidNC states: "Connected", "Idle", "Run", "Jog", "Hold"
        val connected = status == "Idle" || 
                       status == "Connected" ||
                       status == "Run" ||
                       settingsViewModel.uiState.value.bypassConnectionCheck
                       
        return connected && volumeToCheck > 0f && !_isSending.value
    }

    fun updateConnectionStatus(status: FluidNCStatus?) {
        _fluidNCStatus.value = status
    }

    fun addToTerminalHistory(message: String) {
        printerRepository.addToHistory(message)
    }
    
    fun setRetraction(steps: Float) {
        _retraction.value = steps
    }

    private fun reconnectFluidNCService(ipAddress: String) {
        printerRepository.connect(ipAddress)
    }

    override fun onCleared() {
        printerRepository.disconnect()
        super.onCleared()
    }

    fun sendMix() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                addToTerminalHistory(">> DEBUG: Dispense Button Clicked")
    
                val settings = settingsViewModel.uiState.value
                
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
    
                val result = printerRepository.dispenseMix(mixToSend, dispenseVolume, settings)
                
                when (result) {
                    is com.example.mejustmix.data.PrinterRepository.DispenseResult.Success -> {
                        addToHistory(_paintMix.value, _color.value)
                        settingsViewModel.consumePaint(
                            mix = mixToSend, 
                            totalVolume = result.actualVolume,
                            onPumpDepleted = { pumpName ->
                                _pumpDepletionWarning.value = "$pumpName is empty!"
                            }
                        )
                    }
                    is com.example.mejustmix.data.PrinterRepository.DispenseResult.Error -> {
                         _pumpDepletionWarning.value = result.message
                         addToTerminalHistory(">> Error: ${result.message}")
                    }
                }

            } catch (e: Exception) {
                val err = "Error in sendMix: ${e.message}"
                addToTerminalHistory(">> DEBUG: $err")
                e.printStackTrace()
            }
        }
    }
    
    fun sendRawGCode(gcode: List<String>) {
        printerRepository.sendRaw(gcode)
    }

    fun clearManualMode() {
        manualBaseName.value = null
        manualTransparency.value = 0f
        includeWhitePump.value = true
    }
    
    fun primePump(axis: String, amount: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            val settings = settingsViewModel.uiState.value
            printerRepository.primePump(axis, amount, settings)
        }
    }
    
    fun retractAll() {
        viewModelScope.launch(Dispatchers.Default) {
            val settings = settingsViewModel.uiState.value
            printerRepository.retractAll(settings)
        }
    }
    
    // --- Pulse Mode Functions ---
    
    fun dispensePulses(pumpIndex: Int, pulseCount: Int, stepsPerPulse: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            val settings = settingsViewModel.uiState.value
            printerRepository.dispensePulses(pumpIndex, pulseCount, stepsPerPulse, settings)
        }
    }
    
    fun jogPump(pumpIndex: Int, steps: Int, stepsPerPulse: Float) {
         viewModelScope.launch(Dispatchers.Default) {
             val settings = settingsViewModel.uiState.value
             printerRepository.jogPump(pumpIndex, steps, settings)
         }
    }
    
    fun homePump(pumpIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val settings = settingsViewModel.uiState.value
            val volume = printerRepository.homePump(pumpIndex, settings)
            if (volume > 0) {
                 settingsViewModel.setPumpHomed(pumpIndex)
            }
        }
    }
    
    fun homeAllPumps() {
        viewModelScope.launch(Dispatchers.Default) {
             val settings = settingsViewModel.uiState.value
             settings.pumps.indices.forEach { i ->
                 homePump(i) // Sequential homing via reuse
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