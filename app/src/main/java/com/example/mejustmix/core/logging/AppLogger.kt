package com.example.mejustmix.core.logging

import timber.log.Timber

/**
 * Logging utility that wraps Timber for consistent logging throughout the app.
 * 
 * ## Setup
 * 
 * Initialize in your Application class:
 * ```kotlin
 * class MeJustMixApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         AppLogger.initialize(BuildConfig.DEBUG)
 *     }
 * }
 * ```
 * 
 * ## Usage
 * 
 * ```kotlin
 * // Simple logging
 * AppLogger.d("Connection established")
 * AppLogger.e("Failed to connect", exception)
 * 
 * // Tagged logging (recommended for services)
 * val logger = AppLogger.withTag("FluidNC")
 * logger.d("Sending G-code: G1 X10")
 * logger.e("Command timeout", timeoutException)
 * 
 * // Specialized loggers
 * AppLogger.network.d("WebSocket opened")
 * AppLogger.hardware.w("Pump near empty")
 * AppLogger.color.i("Mix calculated: ${mix}")
 * ```
 * 
 * ## Categories
 * 
 * Pre-configured loggers for common categories:
 * - `network`: Network/WebSocket communication
 * - `hardware`: FluidNC/pump hardware interactions
 * - `color`: Color mixing calculations
 * - `calibration`: Calibration processes
 * - `ui`: UI-related events
 * - `data`: Data persistence operations
 */
object AppLogger {
    
    private var isInitialized = false
    
    /**
     * Initialize the logging system.
     * Call this once in Application.onCreate().
     * 
     * @param debug If true, enables debug logging. Set to BuildConfig.DEBUG.
     */
    fun initialize(debug: Boolean) {
        if (isInitialized) return
        
        if (debug) {
            Timber.plant(Timber.DebugTree())
        }
        // In production, you could plant a crash reporting tree:
        // else {
        //     Timber.plant(CrashReportingTree())
        // }
        
        isInitialized = true
    }
    
    // ========================================================================
    // DIRECT LOGGING METHODS
    // ========================================================================
    
    /** Log a verbose message */
    fun v(message: String, vararg args: Any?) {
        Timber.v(message, *args)
    }
    
    /** Log a debug message */
    fun d(message: String, vararg args: Any?) {
        Timber.d(message, *args)
    }
    
    /** Log an info message */
    fun i(message: String, vararg args: Any?) {
        Timber.i(message, *args)
    }
    
    /** Log a warning message */
    fun w(message: String, vararg args: Any?) {
        Timber.w(message, *args)
    }
    
    /** Log a warning with exception */
    fun w(t: Throwable?, message: String, vararg args: Any?) {
        Timber.w(t, message, *args)
    }
    
    /** Log an error message */
    fun e(message: String, vararg args: Any?) {
        Timber.e(message, *args)
    }
    
    /** Log an error with exception */
    fun e(t: Throwable?, message: String, vararg args: Any?) {
        Timber.e(t, message, *args)
    }
    
    /** Log a WTF (What a Terrible Failure) message */
    fun wtf(message: String, vararg args: Any?) {
        Timber.wtf(message, *args)
    }
    
    // ========================================================================
    // TAGGED LOGGERS
    // ========================================================================
    
    /**
     * Create a logger with a specific tag.
     * Useful for tagging logs from specific components.
     */
    fun withTag(tag: String): TaggedLogger = TaggedLogger(tag)
    
    // Pre-configured category loggers
    val network = withTag("NETWORK")
    val hardware = withTag("HARDWARE")
    val color = withTag("COLOR")
    val calibration = withTag("CALIBRATION")
    val ui = withTag("UI")
    val data = withTag("DATA")
    
    /**
     * A logger with a fixed tag prefix.
     */
    class TaggedLogger(private val tag: String) {
        fun v(message: String, vararg args: Any?) {
            Timber.tag(tag).v(message, *args)
        }
        
        fun d(message: String, vararg args: Any?) {
            Timber.tag(tag).d(message, *args)
        }
        
        fun i(message: String, vararg args: Any?) {
            Timber.tag(tag).i(message, *args)
        }
        
        fun w(message: String, vararg args: Any?) {
            Timber.tag(tag).w(message, *args)
        }
        
        fun w(t: Throwable?, message: String, vararg args: Any?) {
            Timber.tag(tag).w(t, message, *args)
        }
        
        fun e(message: String, vararg args: Any?) {
            Timber.tag(tag).e(message, *args)
        }
        
        fun e(t: Throwable?, message: String, vararg args: Any?) {
            Timber.tag(tag).e(t, message, *args)
        }
        
        fun wtf(message: String, vararg args: Any?) {
            Timber.tag(tag).wtf(message, *args)
        }
    }
}

/**
 * Extension function for logging AppErrors with appropriate severity.
 */
fun com.example.mejustmix.core.error.AppError.log() {
    val logger = when (this) {
        is com.example.mejustmix.core.error.AppError.Network -> AppLogger.network
        is com.example.mejustmix.core.error.AppError.Bluetooth -> AppLogger.network
        is com.example.mejustmix.core.error.AppError.Hardware -> AppLogger.hardware
        is com.example.mejustmix.core.error.AppError.Calibration -> AppLogger.calibration
        is com.example.mejustmix.core.error.AppError.ColorMixing -> AppLogger.color
        is com.example.mejustmix.core.error.AppError.Data -> AppLogger.data
        is com.example.mejustmix.core.error.AppError.Unknown -> AppLogger.withTag("UNKNOWN")
    }
    
    if (cause != null) {
        logger.e(cause, "[${code}] $message")
    } else {
        logger.e("[${code}] $message")
    }
}
