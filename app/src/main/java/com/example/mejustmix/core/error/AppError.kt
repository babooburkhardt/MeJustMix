package com.example.mejustmix.core.error

/**
 * Sealed class hierarchy representing all possible errors in the MeJustMix application.
 * 
 * This centralized error system provides:
 * - Type-safe error handling across the app
 * - Consistent error messages and categorization
 * - Easy logging and debugging
 * - User-friendly error presentation
 * 
 * Usage:
 * ```kotlin
 * when (result) {
 *     is AppResult.Error -> when (result.error) {
 *         is AppError.Network -> handleNetworkError(result.error)
 *         is AppError.Hardware -> handleHardwareError(result.error)
 *         is AppError.Calibration -> handleCalibrationError(result.error)
 *         // ...
 *     }
 * }
 * ```
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
    open val code: String? = null
) {
    
    // ========================================================================
    // NETWORK ERRORS
    // ========================================================================
    
    /**
     * Errors related to network communication (WiFi/WebSocket connections).
     */
    sealed class Network(
        override val message: String,
        override val cause: Throwable? = null,
        override val code: String? = null
    ) : AppError(message, cause, code) {
        
        /** Failed to establish connection to FluidNC controller */
        data class ConnectionFailed(
            val ipAddress: String,
            val port: Int,
            override val cause: Throwable? = null
        ) : Network(
            message = "Failed to connect to $ipAddress:$port",
            cause = cause,
            code = "NET_001"
        )
        
        /** Connection was lost during operation */
        data class ConnectionLost(
            val lastState: String,
            override val cause: Throwable? = null
        ) : Network(
            message = "Connection lost (last state: $lastState)",
            cause = cause,
            code = "NET_002"
        )
        
        /** Connection timeout */
        data class Timeout(
            val operation: String,
            val timeoutMs: Long
        ) : Network(
            message = "Timeout during $operation after ${timeoutMs}ms",
            code = "NET_003"
        )
        
        /** WebSocket handshake failed */
        data class HandshakeFailed(
            override val cause: Throwable? = null
        ) : Network(
            message = "WebSocket handshake failed",
            cause = cause,
            code = "NET_004"
        )
    }
    
    // ========================================================================
    // BLUETOOTH ERRORS
    // ========================================================================
    
    /**
     * Errors related to Bluetooth Low Energy communication.
     */
    sealed class Bluetooth(
        override val message: String,
        override val cause: Throwable? = null,
        override val code: String? = null
    ) : AppError(message, cause, code) {
        
        /** Bluetooth is disabled on the device */
        data object Disabled : Bluetooth(
            message = "Bluetooth is disabled",
            code = "BLE_001"
        )
        
        /** Device not found during scan */
        data class DeviceNotFound(
            val deviceName: String
        ) : Bluetooth(
            message = "BLE device '$deviceName' not found",
            code = "BLE_002"
        )
        
        /** Failed to connect to BLE device */
        data class ConnectionFailed(
            val deviceAddress: String,
            override val cause: Throwable? = null
        ) : Bluetooth(
            message = "Failed to connect to BLE device: $deviceAddress",
            cause = cause,
            code = "BLE_003"
        )
        
        /** BLE service discovery failed */
        data class ServiceDiscoveryFailed(
            override val cause: Throwable? = null
        ) : Bluetooth(
            message = "BLE service discovery failed",
            cause = cause,
            code = "BLE_004"
        )
        
        /** Required permissions not granted */
        data object PermissionDenied : Bluetooth(
            message = "Bluetooth permissions not granted",
            code = "BLE_005"
        )
    }
    
    // ========================================================================
    // HARDWARE ERRORS
    // ========================================================================
    
    /**
     * Errors related to hardware communication and control.
     */
    sealed class Hardware(
        override val message: String,
        override val cause: Throwable? = null,
        override val code: String? = null
    ) : AppError(message, cause, code) {
        
        /** G-code command was rejected by FluidNC */
        data class CommandRejected(
            val command: String,
            val response: String
        ) : Hardware(
            message = "Command rejected: '$command' -> $response",
            code = "HW_001"
        )
        
        /** FluidNC reported an alarm condition */
        data class Alarm(
            val alarmCode: Int,
            val description: String
        ) : Hardware(
            message = "ALARM:$alarmCode - $description",
            code = "HW_002"
        )
        
        /** FluidNC reported an error */
        data class GrblError(
            val errorCode: Int,
            val description: String
        ) : Hardware(
            message = "error:$errorCode - $description",
            code = "HW_003"
        )
        
        /** Command timeout - no response from controller */
        data class CommandTimeout(
            val command: String,
            val timeoutMs: Long
        ) : Hardware(
            message = "No response for '$command' after ${timeoutMs}ms",
            code = "HW_004"
        )
        
        /** Pump is empty or has insufficient volume */
        data class PumpDepleted(
            val pumpName: String,
            val requiredMl: Float,
            val availableMl: Float
        ) : Hardware(
            message = "$pumpName needs ${requiredMl}mL but only has ${availableMl}mL",
            code = "HW_005"
        )
        
        /** Not connected to any controller */
        data object NotConnected : Hardware(
            message = "Not connected to controller",
            code = "HW_006"
        )
    }
    
    // ========================================================================
    // CALIBRATION ERRORS
    // ========================================================================
    
    /**
     * Errors related to calibration processes.
     */
    sealed class Calibration(
        override val message: String,
        override val cause: Throwable? = null,
        override val code: String? = null
    ) : AppError(message, cause, code) {
        
        /** Pigment spectra data is missing or invalid */
        data class MissingPigmentData(
            val pigmentName: String
        ) : Calibration(
            message = "Missing calibration data for $pigmentName",
            code = "CAL_001"
        )
        
        /** Calibration measurement is out of expected range */
        data class OutOfRange(
            val parameter: String,
            val value: Float,
            val expectedRange: ClosedFloatingPointRange<Float>
        ) : Calibration(
            message = "$parameter value $value is outside expected range [${expectedRange.start}, ${expectedRange.endInclusive}]",
            code = "CAL_002"
        )
        
        /** Reference measurement is missing */
        data class MissingReference(
            val referenceType: String
        ) : Calibration(
            message = "Missing $referenceType reference measurement",
            code = "CAL_003"
        )
        
        /** Spectral sensor not available */
        data object SpectralSensorUnavailable : Calibration(
            message = "Spectral sensor is not connected",
            code = "CAL_004"
        )
        
        /** Calibration step failed */
        data class StepFailed(
            val step: String,
            val reason: String
        ) : Calibration(
            message = "Calibration step '$step' failed: $reason",
            code = "CAL_005"
        )
    }
    
    // ========================================================================
    // COLOR MIXING ERRORS
    // ========================================================================
    
    /**
     * Errors related to color calculation and mixing.
     */
    sealed class ColorMixing(
        override val message: String,
        override val cause: Throwable? = null,
        override val code: String? = null
    ) : AppError(message, cause, code) {
        
        /** Target color is outside the achievable gamut */
        data class OutOfGamut(
            val targetColor: Int,
            val nearestAchievable: Int
        ) : ColorMixing(
            message = "Target color is outside achievable gamut",
            code = "MIX_001"
        )
        
        /** K-M database is not initialized */
        data object DatabaseNotInitialized : ColorMixing(
            message = "Kubelka-Munk pigment database not initialized",
            code = "MIX_002"
        )
        
        /** Invalid mix ratios (don't sum to 1.0) */
        data class InvalidRatios(
            val sum: Float
        ) : ColorMixing(
            message = "Mix ratios sum to $sum instead of 1.0",
            code = "MIX_003"
        )
        
        /** Optimization failed to converge */
        data class OptimizationFailed(
            val iterations: Int,
            val finalError: Float
        ) : ColorMixing(
            message = "Color optimization failed after $iterations iterations (error: $finalError)",
            code = "MIX_004"
        )
    }
    
    // ========================================================================
    // FILE/DATA ERRORS
    // ========================================================================
    
    /**
     * Errors related to file operations and data persistence.
     */
    sealed class Data(
        override val message: String,
        override val cause: Throwable? = null,
        override val code: String? = null
    ) : AppError(message, cause, code) {
        
        /** Failed to save data */
        data class SaveFailed(
            val dataType: String,
            override val cause: Throwable? = null
        ) : Data(
            message = "Failed to save $dataType",
            cause = cause,
            code = "DATA_001"
        )
        
        /** Failed to load data */
        data class LoadFailed(
            val dataType: String,
            override val cause: Throwable? = null
        ) : Data(
            message = "Failed to load $dataType",
            cause = cause,
            code = "DATA_002"
        )
        
        /** Data format is invalid or corrupted */
        data class InvalidFormat(
            val dataType: String,
            val reason: String
        ) : Data(
            message = "Invalid $dataType format: $reason",
            code = "DATA_003"
        )
        
        /** Export operation failed */
        data class ExportFailed(
            val destination: String,
            override val cause: Throwable? = null
        ) : Data(
            message = "Failed to export to $destination",
            cause = cause,
            code = "DATA_004"
        )
        
        /** Import operation failed */
        data class ImportFailed(
            val source: String,
            override val cause: Throwable? = null
        ) : Data(
            message = "Failed to import from $source",
            cause = cause,
            code = "DATA_005"
        )
    }
    
    // ========================================================================
    // UNKNOWN/GENERIC ERRORS
    // ========================================================================
    
    /**
     * Catch-all for unexpected errors.
     */
    data class Unknown(
        override val message: String = "An unexpected error occurred",
        override val cause: Throwable? = null
    ) : AppError(message, cause, "ERR_999")
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    /**
     * Get a user-friendly message suitable for displaying in UI.
     */
    fun getUserMessage(): String = when (this) {
        is Network -> when (this) {
            is Network.ConnectionFailed -> "Couldn't connect to the mixer. Check the IP address and make sure it's powered on."
            is Network.ConnectionLost -> "Connection was lost. Attempting to reconnect..."
            is Network.Timeout -> "The operation timed out. Please try again."
            is Network.HandshakeFailed -> "Communication error. Please reconnect."
        }
        is Bluetooth -> when (this) {
            is Bluetooth.Disabled -> "Please enable Bluetooth to connect."
            is Bluetooth.DeviceNotFound -> "Couldn't find the mixer. Make sure it's powered on and nearby."
            is Bluetooth.ConnectionFailed -> "Couldn't connect via Bluetooth. Please try again."
            is Bluetooth.ServiceDiscoveryFailed -> "Bluetooth connection incomplete. Please reconnect."
            is Bluetooth.PermissionDenied -> "Bluetooth permission is required. Please grant it in Settings."
        }
        is Hardware -> when (this) {
            is Hardware.CommandRejected -> "The mixer rejected a command: $response"
            is Hardware.Alarm -> "Mixer alarm! $description"
            is Hardware.GrblError -> "Mixer error: $description"
            is Hardware.CommandTimeout -> "The mixer didn't respond. Check the connection."
            is Hardware.PumpDepleted -> "$pumpName needs a refill."
            is Hardware.NotConnected -> "Please connect to the mixer first."
        }
        is Calibration -> when (this) {
            is Calibration.MissingPigmentData -> "Calibration needed for $pigmentName."
            is Calibration.OutOfRange -> "Measurement out of range. Please try again."
            is Calibration.MissingReference -> "Please capture a $referenceType reference first."
            is Calibration.SpectralSensorUnavailable -> "Connect the spectral sensor to continue."
            is Calibration.StepFailed -> "Calibration failed: $reason"
        }
        is ColorMixing -> when (this) {
            is ColorMixing.OutOfGamut -> "This exact color can't be mixed. Using closest match."
            is ColorMixing.DatabaseNotInitialized -> "Color database needs setup."
            is ColorMixing.InvalidRatios -> "Internal error in color calculation."
            is ColorMixing.OptimizationFailed -> "Couldn't calculate the color mix. Try a different color."
        }
        is Data -> when (this) {
            is Data.SaveFailed -> "Couldn't save your data. Please try again."
            is Data.LoadFailed -> "Couldn't load $dataType."
            is Data.InvalidFormat -> "The file format isn't recognized."
            is Data.ExportFailed -> "Export failed. Check storage permissions."
            is Data.ImportFailed -> "Couldn't import the file."
        }
        is Unknown -> "Something went wrong. Please try again."
    }
    
    /**
     * Get the error category for logging/analytics.
     */
    fun getCategory(): String = when (this) {
        is Network -> "NETWORK"
        is Bluetooth -> "BLUETOOTH"
        is Hardware -> "HARDWARE"
        is Calibration -> "CALIBRATION"
        is ColorMixing -> "COLOR_MIXING"
        is Data -> "DATA"
        is Unknown -> "UNKNOWN"
    }
    
    companion object {
        /**
         * Create an AppError from a generic exception.
         */
        fun fromException(e: Throwable): AppError = when (e) {
            is java.net.SocketTimeoutException -> Network.Timeout("socket", 5000)
            is java.net.ConnectException -> Network.ConnectionFailed("unknown", 0, e)
            is java.io.IOException -> Data.LoadFailed("file", e)
            else -> Unknown(e.message ?: "Unknown error", e)
        }
    }
}
