package com.example.mejustmix.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified BLE scanner for discovering both FluidNC devices and spectral sensors.
 * Provides a single scanning interface for all BLE devices used by the app.
 */
@SuppressLint("MissingPermission")
class UnifiedBLEScanner(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedBLEScanner"
        private const val SCAN_PERIOD = 10000L // 10 seconds
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    
    // Discovered devices
    private val _fluidNCDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val fluidNCDevices: StateFlow<List<DiscoveredDevice>> = _fluidNCDevices
    
    private val _spectralSensors = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val spectralSensors: StateFlow<List<DiscoveredDevice>> = _spectralSensors
    
    // Scanning state
    private val _isScanningState = MutableStateFlow(false)
    val isScanningState: StateFlow<Boolean> = _isScanningState
    
    // Callbacks for immediate device discovery
    var onFluidNCFound: ((DiscoveredDevice) -> Unit)? = null
    var onSpectralSensorFound: ((DiscoveredDevice) -> Unit)? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    /**
     * Start scanning for all BLE devices (FluidNC + Spectral Sensor).
     */
    fun startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth not available or disabled")
            return
        }
        
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        // Clear previous results
        _fluidNCDevices.value = emptyList()
        _spectralSensors.value = emptyList()
        
        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        isScanning = true
        _isScanningState.value = true
        
        scanner.startScan(scanCallback)
        
        // Stop scan after SCAN_PERIOD
        Handler(Looper.getMainLooper()).postDelayed({
            stopScanning()
        }, SCAN_PERIOD)
        
        Log.d(TAG, "Started BLE scan")
    }

    /**
     * Stop scanning.
     */
    fun stopScanning() {
        if (!isScanning) return
        
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        _isScanningState.value = false
        
        Log.d(TAG, "Stopped BLE scan")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val deviceName = device.name ?: return
                
                when {
                    deviceName.startsWith("FluidNC") -> {
                        val discovered = DiscoveredDevice(
                            name = deviceName,
                            address = device.address,
                            type = DeviceType.FLUIDNC
                        )
                        
                        // Add to list if not already present
                        if (_fluidNCDevices.value.none { it.address == device.address }) {
                            _fluidNCDevices.value = _fluidNCDevices.value + discovered
                            onFluidNCFound?.invoke(discovered)
                            Log.d(TAG, "Found FluidNC: $deviceName (${device.address})")
                        }
                    }
                    
                    deviceName == "ESP32_Spectral_Bridge" -> {
                        val discovered = DiscoveredDevice(
                            name = deviceName,
                            address = device.address,
                            type = DeviceType.SPECTRAL_SENSOR
                        )
                        
                        // Add to list if not already present
                        if (_spectralSensors.value.none { it.address == device.address }) {
                            _spectralSensors.value = _spectralSensors.value + discovered
                            onSpectralSensorFound?.invoke(discovered)
                            Log.d(TAG, "Found Spectral Sensor: $deviceName (${device.address})")
                        }
                    }
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            isScanning = false
            _isScanningState.value = false
        }
    }
    
    /**
     * Clear all discovered devices.
     */
    fun clearResults() {
        _fluidNCDevices.value = emptyList()
        _spectralSensors.value = emptyList()
    }
}

/**
 * Represents a discovered BLE device.
 */
data class DiscoveredDevice(
    val name: String,
    val address: String,
    val type: DeviceType
)

enum class DeviceType {
    FLUIDNC,
    SPECTRAL_SENSOR
}
