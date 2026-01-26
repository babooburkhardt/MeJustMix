package com.example.mejustmix

import android.app.Application
import com.example.mejustmix.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for MeJustMix.
 * 
 * This class is annotated with @HiltAndroidApp to enable Hilt dependency injection
 * throughout the application. Hilt will generate a base Application class that
 * handles all DI component generation.
 * 
 * ## What this enables:
 * - Automatic dependency injection in Activities, Fragments, ViewModels
 * - Singleton scoping for shared dependencies
 * - Easy testing through dependency replacement
 * 
 * ## To use in your code:
 * 
 * ### In Activity
 * ```kotlin
 * @AndroidEntryPoint
 * class MainActivity : ComponentActivity() {
 *     @Inject
 *     lateinit var printerRepository: PrinterRepository
 * }
 * ```
 * 
 * ### In ViewModel
 * ```kotlin
 * @HiltViewModel
 * class MixViewModel @Inject constructor(
 *     private val printerRepository: PrinterRepository,
 *     private val colorMixingService: ColorMixingService
 * ) : ViewModel()
 * ```
 */
@HiltAndroidApp
class MeJustMixApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber logging (debug mode detection via applicationInfo flags)
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppLogger.initialize(isDebug)
        AppLogger.i("MeJustMix Application started")
        
        // Any other app-wide initialization can go here
    }
}
