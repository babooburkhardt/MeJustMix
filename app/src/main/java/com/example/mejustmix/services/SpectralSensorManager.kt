package com.example.mejustmix.services

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.collections.ArrayList

/**
 * Manages BLE connection to the ESP32 Spectral Sensor Bridge.
 * Updated with Rolling Average Noise Reduction and Auto-Connect.
 */
@SuppressLint("MissingPermission")
class SpectralSensorManager(private val context: Context) {

    // UUIDs as defined in the Firmware
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHAR_DATA_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CHAR_CONTROL_UUID = UUID.fromString("824c965e-269c-4869-9f79-6a3f124c6536")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _lastReading = MutableStateFlow<List<Float>?>(null)
    val lastReading: StateFlow<List<Float>?> = _lastReading
    
    // Rolling Average Buffer
    private val scanBuffer = ArrayList<List<Float>>()
    private var pendingScans = 0
    private val SCAN_COUNT = 7  // Increased from 5 to 7 for better noise reduction

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    fun scanAndAutoConnect() {
         if (bluetoothGatt != null) return // Already connected
         connect() // Re-uses the existing specific scanning logic
    }

    fun connect() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = "Bluetooth Disabled"
            return
        }

        _connectionState.value = "Scanning..."
        
        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    if (device.name == "ESP32_Spectral_Bridge") {
                        scanner.stopScan(this)
                        _connectionState.value = "Found ${device.name}, Connecting..."
                        bluetoothGatt = device.connectGatt(context, false, gattCallback)
                    }
                }
            }
            override fun onScanFailed(errorCode: Int) {
                 _connectionState.value = "Scan Failed ($errorCode)"
            }
        }
        
        try {
        scanner.startScan(scanCallback)
    } catch (e: SecurityException) {
        _connectionState.value = "Permission Denied"
        return
    }
        
        // Stop scan after 5s if not found
        Handler(Looper.getMainLooper()).postDelayed({
            if (_connectionState.value == "Scanning...") {
                scanner.stopScan(scanCallback)
                _connectionState.value = "Device Not Found"
            }
        }, 5000)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = "Disconnected"
    }

    /**
     * Triggers a Rolling Average Scan sequence.
     * Takes [SCAN_COUNT] readings, averages them, and updates [lastReading].
     */
    fun triggerScan() {
        if (bluetoothGatt != null && controlCharacteristic != null) {
            _connectionState.value = "Acquiring samples..."
            scanBuffer.clear()
            pendingScans = SCAN_COUNT 
            sendScanCommand()
        } else {
            _connectionState.value = "Not Connected"
        }
    }
    
    private fun sendScanCommand() {
        if (bluetoothGatt != null && controlCharacteristic != null) {
            val commandByte: Byte = if (expectedSensorType == "AS7265x") 2 else 1
            controlCharacteristic!!.value = byteArrayOf(commandByte)
            bluetoothGatt!!.writeCharacteristic(controlCharacteristic)
        }
    }

    fun triggerWarmup() {
        if (bluetoothGatt != null && controlCharacteristic != null) {
            _connectionState.value = "Warming up sensor..."
            val commandByte: Byte = 3
            controlCharacteristic!!.value = byteArrayOf(commandByte)
            bluetoothGatt!!.writeCharacteristic(controlCharacteristic)
        } else {
            _connectionState.value = "Not Connected"
        }
    }
    
    private fun processBuffer() {
        if (scanBuffer.isEmpty()) return
        
        // If we didn't get enough samples for some reason, just average what we have
        val samples = scanBuffer.size
        val channelCount = scanBuffer[0].size
        
        // Compute Average
        val sum = FloatArray(channelCount) { 0f }
        scanBuffer.forEach { reading ->
            if (reading.size == channelCount) {
                reading.forEachIndexed { i, value ->
                     sum[i] += value
                }
            }
        }
        
        val average = sum.map { it / samples }
        
        _lastReading.value = average
        _connectionState.value = "Data Received (Avg of $samples)"
        Log.d("Spectral", "Averaged Reading: $average")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected. Discovering Services..."
                gatt?.discoverServices()
                gatt?.requestMtu(256)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    dataCharacteristic = service.getCharacteristic(CHAR_DATA_UUID)
                    controlCharacteristic = service.getCharacteristic(CHAR_CONTROL_UUID)

                    if (dataCharacteristic != null) {
                        gatt.setCharacteristicNotification(dataCharacteristic, true)
                        val descriptor = dataCharacteristic!!.getDescriptor(CCCD_UUID)
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        _connectionState.value = "Ready"
                    }
                } else {
                    _connectionState.value = "Ready (Service Missing?)" 
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == CHAR_DATA_UUID) {
                characteristic?.getStringValue(0)?.let { csvData ->
                    parseData(csvData)
                }
            }
        }
    }

    private var expectedSensorType: String = "AS7341"

    fun setSensorType(type: String) {
        expectedSensorType = type
    }

    private var isOptimizingGain = false
    private var currentGainIndex = 6 // Default mid-range
    
    fun setGain(index: Int) {
        if (bluetoothGatt != null && controlCharacteristic != null) {
             val maxIndex = if (expectedSensorType == "AS7265x") 3 else 10
             currentGainIndex = index.coerceIn(0, maxIndex)
             
            val commandByte: Byte = 4
            val gainByte = currentGainIndex.toByte()
            controlCharacteristic!!.value = byteArrayOf(commandByte, gainByte)
            bluetoothGatt!!.writeCharacteristic(controlCharacteristic)
            Log.d("Spectral", "Set Gain to Index $currentGainIndex")
        }
    }

    fun runAutoGain() {
        if (bluetoothGatt != null && controlCharacteristic != null) {
            _connectionState.value = "Optimizing Gain..."
            isOptimizingGain = true
            // Start at a mid-low gain to be safe? Or current.
            // Let's start at current.
            sendScanCommand()
        }
    }

    private fun parseData(csv: String) {
        try {
            val values = csv.split(",").map { it.toFloat() }
            
            // Validate against expected sensor type
            val isValid = when (expectedSensorType) {
                "AS7341" -> values.size == 10
                "AS7265x" -> values.size == 18
                else -> false
            }

            if (isValid) {
                if (isOptimizingGain) {
                    checkGainAndOptimize(values)
                } else if (pendingScans > 0) {
                    scanBuffer.add(values)
                    pendingScans--
                    
                    if (pendingScans > 0) {
                        // Request next sample after minimal delay
                        Handler(Looper.getMainLooper()).postDelayed({
                             sendScanCommand()
                        }, 10) 
                        _connectionState.value = "Sampling... ($pendingScans left)"
                    } else {
                        processBuffer()
                    }
                } else {
                    _lastReading.value = values
                    _connectionState.value = "Reading Received (${values.size} ch)"
                }
            } else {
                Log.w("Spectral", "Sensor Mismatch: Expected $expectedSensorType but got ${values.size} channels")
                _connectionState.value = "Error: Wrong Sensor Type (${values.size} ch)"
            }
        } catch (e: Exception) {
            Log.e("Spectral", "Parse Error: $csv")
        }
    }
    
    private fun checkGainAndOptimize(values: List<Float>) {
        val maxVal = values.maxOrNull() ?: 0f
        val SATURATION_LIMIT = 65000f
        val LOW_SIGNAL_LIMIT = 3000f // Heuristic
        
        val maxGainIndex = if (expectedSensorType == "AS7265x") 3 else 10
        
        if (maxVal > SATURATION_LIMIT) {
            // Saturated, lower gain
            if (currentGainIndex > 0) {
                Log.d("Spectral", "Saturated ($maxVal). Lowering gain.")
                setGain(currentGainIndex - 1)
                // Wait for sensor to apply gain then scan again. 
                // Sensor integration time is ~50ms?
                Handler(Looper.getMainLooper()).postDelayed({ sendScanCommand() }, 200)
            } else {
                Log.d("Spectral", "Saturated but at Min Gain.")
                finishOptimization()
            }
        } else if (maxVal < LOW_SIGNAL_LIMIT) {
             // Too low, raise gain
             if (currentGainIndex < maxGainIndex) {
                 Log.d("Spectral", "Signal Low ($maxVal). Increasing gain.")
                 setGain(currentGainIndex + 1)
                 Handler(Looper.getMainLooper()).postDelayed({ sendScanCommand() }, 200)
             } else {
                 Log.d("Spectral", "Low Signal but at Max Gain.")
                 finishOptimization()
             }
        } else {
            // Good range
            Log.d("Spectral", "Gain Optimized at $currentGainIndex (Max: $maxVal)")
            finishOptimization()
        }
    }
    
    private fun finishOptimization() {
        isOptimizingGain = false
        triggerScan() // Now do the real scan
    }
}

