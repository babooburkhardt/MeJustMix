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

/**
 * Manages BLE connection to FluidNC for G-code communication.
 * Similar architecture to SpectralSensorManager.
 */
@SuppressLint("MissingPermission")
class FluidNCBLEManager(private val context: Context) {

    companion object {
        private const val TAG = "FluidNCBLE"
        
        // FluidNC BLE Service UUID (standard Serial Port Profile)
        private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val CHAR_TX_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val CHAR_RX_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse
    
    private val responseBuffer = StringBuilder()

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    /**
     * Scan for FluidNC devices and auto-connect to the first one found.
     */
    fun scanAndAutoConnect(deviceName: String = "FluidNC") {
        if (bluetoothGatt != null) {
            Log.d(TAG, "Already connected")
            return
        }
        connect(deviceName)
    }

    /**
     * Scan for a specific FluidNC device by name.
     */
    fun connect(deviceName: String = "FluidNC") {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = "Bluetooth Disabled"
            return
        }

        _connectionState.value = "Scanning for $deviceName..."
        
        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    if (device.name?.startsWith(deviceName) == true) {
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
        
        scanner.startScan(scanCallback)
        
        // Stop scan after 10s if not found
        Handler(Looper.getMainLooper()).postDelayed({
            if (_connectionState.value.startsWith("Scanning")) {
                scanner.stopScan(scanCallback)
                _connectionState.value = "Device Not Found"
            }
        }, 10000)
    }
    
    /**
     * Connect to a specific device by MAC address.
     */
    fun connectByAddress(address: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = "Bluetooth Disabled"
            return
        }
        
        try {
            val device = bluetoothAdapter!!.getRemoteDevice(address)
            _connectionState.value = "Connecting to ${device.name ?: address}..."
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            _connectionState.value = "Connection Failed: ${e.message}"
            Log.e(TAG, "Failed to connect by address", e)
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = "Disconnected"
    }

    /**
     * Send G-code command to FluidNC.
     */
    fun sendGCode(gcode: String) {
        if (bluetoothGatt != null && txCharacteristic != null) {
            val command = gcode.trim() + "\n"
            txCharacteristic!!.value = command.toByteArray()
            val success = bluetoothGatt!!.writeCharacteristic(txCharacteristic)
            
            if (success) {
                Log.d(TAG, "Sent G-code: $gcode")
            } else {
                Log.e(TAG, "Failed to send G-code: $gcode")
            }
        } else {
            Log.e(TAG, "Cannot send G-code: Not connected")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected. Discovering Services..."
                gatt?.discoverServices()
                gatt?.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(CHAR_TX_UUID)
                    rxCharacteristic = service.getCharacteristic(CHAR_RX_UUID)

                    if (rxCharacteristic != null) {
                        gatt.setCharacteristicNotification(rxCharacteristic, true)
                        val descriptor = rxCharacteristic!!.getDescriptor(CCCD_UUID)
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        _connectionState.value = "Ready"
                    }
                } else {
                    _connectionState.value = "Service Not Found"
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic?.uuid == CHAR_RX_UUID) {
                characteristic?.getStringValue(0)?.let { data ->
                    parseResponse(data)
                }
            }
        }
    }

    private fun parseResponse(data: String) {
        responseBuffer.append(data)
        
        // Check for complete line (ends with newline)
        if (data.contains("\n")) {
            val response = responseBuffer.toString().trim()
            _lastResponse.value = response
            Log.d(TAG, "FluidNC Response: $response")
            responseBuffer.clear()
        }
    }
}
