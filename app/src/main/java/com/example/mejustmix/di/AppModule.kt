package com.example.mejustmix.di

import android.content.Context
import android.content.SharedPreferences
import com.example.mejustmix.data.LibraryRepository
import com.example.mejustmix.data.MachineManager
import com.example.mejustmix.data.PhotoLibraryRepository
import com.example.mejustmix.data.PrinterRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides application-level dependencies.
 * 
 * Dependencies provided here are:
 * - Singleton-scoped (one instance for entire app lifetime)
 * - Available for injection throughout the app
 * - Automatically cleaned up when app is destroyed
 * 
 * ## Adding new dependencies
 * 
 * To add a new singleton dependency:
 * ```kotlin
 * @Provides
 * @Singleton
 * fun provideMyService(@ApplicationContext context: Context): MyService {
 *     return MyService(context)
 * }
 * ```
 * 
 * Then inject it anywhere:
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val myService: MyService
 * ) : ViewModel()
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provides the main SharedPreferences instance for app settings.
     */
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences("mejustmix_settings", Context.MODE_PRIVATE)
    }
    
    /**
     * Provides the PrinterRepository singleton.
     * Handles all communication with FluidNC controller (WiFi and BLE).
     */
    @Provides
    @Singleton
    fun providePrinterRepository(
        @ApplicationContext context: Context
    ): PrinterRepository {
        return PrinterRepository.getInstance(context)
    }
    
    /**
     * Provides the LibraryRepository for color library persistence.
     */
    @Provides
    @Singleton
    fun provideLibraryRepository(
        @ApplicationContext context: Context
    ): LibraryRepository {
        return LibraryRepository(context)
    }
    
    /**
     * Provides the PhotoLibraryRepository for photo library persistence.
     */
    @Provides
    @Singleton
    fun providePhotoLibraryRepository(
        @ApplicationContext context: Context
    ): PhotoLibraryRepository {
        return PhotoLibraryRepository(context)
    }
    
    /**
     * Provides the MachineManager for multi-machine profile support.
     */
    @Provides
    @Singleton
    fun provideMachineManager(
        @ApplicationContext context: Context
    ): MachineManager {
        return MachineManager(context)
    }
}
