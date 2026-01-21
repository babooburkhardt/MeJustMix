package com.example.mejustmix.data

import com.example.mejustmix.services.KSPigmentDatabase
import com.example.mejustmix.services.PigmentStrengths
import com.example.mejustmix.ui.PumpConfig

/**
 * Connection type for paint mixer machines.
 */
enum class ConnectionType {
    BLE,    // Bluetooth Low Energy
    WIFI    // HTTP over Wi-Fi
}

/**
 * Profile for a paint mixer machine.
 * Supports multiple machines with different configurations.
 */
data class MachineProfile(
    val id: String,                          // Unique identifier (UUID)
    val name: String,                        // User-friendly name (e.g., "Acrylics", "Airbrush")
    val connectionType: ConnectionType,      // BLE or WiFi
    
    // BLE connection info
    val bleDeviceName: String? = null,       // BLE advertised name (e.g., "FluidNC_Acrylics")
    val bleAddress: String? = null,          // MAC address for direct connection
    
    // WiFi connection info
    val ipAddress: String? = null,           // IP address for HTTP connection
    val webPortalPort: String = "81",        // Port for FluidNC web interface
    
    // Machine-specific settings
    val pumps: List<PumpConfig>,
    val flowRate: String = "2.0",
    val retractionSteps: String = "15.0",
    val kmDatabase: KSPigmentDatabase? = null,
    val pigmentStrengths: PigmentStrengths = PigmentStrengths(),
    val usePulseMode: Boolean = false,
    val pulseMinimum: Int = 1,
    
    // State tracking
    val isActive: Boolean = false,           // Currently selected machine
    val lastConnected: Long? = null,         // Timestamp of last successful connection
    val isConnected: Boolean = false         // Current connection status
) {
    /**
     * Returns a display string for the connection method.
     */
    fun getConnectionDisplay(): String {
        return when (connectionType) {
            ConnectionType.BLE -> bleDeviceName ?: "BLE Device"
            ConnectionType.WIFI -> ipAddress ?: "WiFi"
        }
    }
    
    /**
     * Returns true if this machine has valid connection info.
     */
    fun hasValidConnectionInfo(): Boolean {
        return when (connectionType) {
            ConnectionType.BLE -> !bleDeviceName.isNullOrBlank() || !bleAddress.isNullOrBlank()
            ConnectionType.WIFI -> !ipAddress.isNullOrBlank()
        }
    }
}
