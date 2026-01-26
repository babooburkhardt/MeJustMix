package com.example.mejustmix.di

import com.example.mejustmix.services.ColorMixingService
import com.example.mejustmix.services.GCodeGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides service-level dependencies.
 * 
 * These are business logic services that don't require Android context
 * or have their own lifecycle management.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    // Note: ColorMixingService and GCodeGenerator are currently object singletons.
    // If you want to convert them to injectable classes, add providers here:
    
    // Example of how to convert an object to injectable:
    // 
    // @Provides
    // @Singleton
    // fun provideColorMixingService(): ColorMixingService {
    //     return ColorMixingService()
    // }
    
    // For now, these remain as Kotlin objects and can be used directly.
    // When ready to refactor, uncomment the providers above and convert
    // the object declarations to classes.
}
