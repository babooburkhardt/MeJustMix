package com.example.mejustmix.services

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * Manages BLE connection to the ESP32 Spectral Sensor Bridge.
 */
@SuppressLint("MissingPermission")
class SpectralSensorManager(private val context: Context) {

    // UUIDs as defined in the Firmware
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHAR_DATA_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CHAR_CONTROL_UUID = UUID.fromString("824c965e-269c-4869-9f79-6a3f124c6536")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Standard Client Config

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null

    // State
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _lastReading = MutableStateFlow<List<Float>?>(null)
    val lastReading: StateFlow<List<Float>?> = _lastReading

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    fun connect() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionState.value = "Bluetooth Disabled"
            return
        }

        _connectionState.value = "Scanning..."
        
        // Simple scan callback to find our specific device
        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        val scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
                result?.device?.let { device ->
                    // In a real app, you might match by Name or Service UUID. 
                    // For now, we'll check the name or just connect to the first thing that advertises our Service if possible.
                    // However, standard scan results often don't contain Service UUIDs unless specified.
                    // Let's match by Name for simplicity as set in Firmware: "ESP32_Spectral_Bridge"
                    if (device.name == "ESP32_Spectral_Bridge") {
                        scanner.stopScan(this)
                        _connectionState.value = "Connecting to ${device.name}..."
                        bluetoothGatt = device.connectGatt(context, false, gattCallback)
                    }
                }
            }
        }
        
        scanner.startScan(scanCallback)
        
        // Stop scan after 10s if not found
        Handler(Looper.getMainLooper()).postDelayed({
            if (_connectionState.value == "Scanning...") {
                scanner.stopScan(scanCallback)
                _connectionState.value = "Device Not Found"
            }
        }, 10000)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = "Disconnected"
    }

    fun triggerScan() {
        if (bluetoothGatt != null && controlCharacteristic != null) {
            controlCharacteristic!!.value = byteArrayOf(1) // "1"
            bluetoothGatt!!.writeCharacteristic(controlCharacteristic)
            _connectionState.value = "Requesting Scan..."
        } else {
            _connectionState.value = "Not Connected"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected. Discovering Services..."
                gatt?.discoverServices()
                gatt?.requestMtu(256) // Request higher MTU for the long data string
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

                    // Enable Notifications
                    if (dataCharacteristic != null) {
                        gatt.setCharacteristicNotification(dataCharacteristic, true)
                        val descriptor = dataCharacteristic!!.getDescriptor(CCCD_UUID)
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
            if (characteristic?.uuid == CHAR_DATA_UUID) {
                characteristic?.getStringValue(0)?.let { csvData ->
                    parseData(csvData)
                }
            }
        }
    }

    private fun parseData(csv: String) {
        try {
            val values = csv.split(",").map { it.toFloat() }
            if (values.size == 18) {
                _lastReading.value = values
                _connectionState.value = "Data Received"
                Log.d("Spectral", "Received: $values")
            }
        } catch (e: Exception) {
            Log.e("Spectral", "Parse Error: $csv")
        }
    }
}
