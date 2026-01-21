package com.example.mejustmix.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repository for persisting photo library data.
 * 
 * Features:
 * - Automatic backup before each save
 * - Corruption recovery with backup restoration
 * - Thread-safe file operations
 */
class PhotoLibraryRepository(private val context: Context) {

    private val file = File(context.filesDir, "photo_library.json")
    private val backupFile = File(context.filesDir, "photo_library_backup.json")

    /**
     * Saves the photo library to disk with automatic backup.
     * Creates a backup of the existing file before overwriting.
     */
    suspend fun savePhotoLibrary(library: List<PhotoFolder>) = withContext(Dispatchers.IO) {
        try {
            // Create backup before overwriting
            if (file.exists()) {
                file.copyTo(backupFile, overwrite = true)
            }
            
            val jsonString = Json.encodeToString(library)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to save photo library: ${e.message}")
        }
    }

    /**
     * Loads the photo library from disk.
     * If the main file is corrupt, attempts to restore from backup.
     */
    suspend fun loadPhotoLibrary(): List<PhotoFolder> = withContext(Dispatchers.IO) {
        // Try loading from main file
        if (file.exists()) {
            try {
                val jsonString = file.readText()
                return@withContext Json.decodeFromString<List<PhotoFolder>>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                
                // Main file corrupt - try backup
                if (backupFile.exists()) {
                    try {
                        val jsonString = backupFile.readText()
                        val library = Json.decodeFromString<List<PhotoFolder>>(jsonString)
                        
                        // Restore backup to main file
                        backupFile.copyTo(file, overwrite = true)
                        
                        return@withContext library
                    } catch (backupError: Exception) {
                        backupError.printStackTrace()
                    }
                }
                
                // Both files corrupt - archive and start fresh
                val corruptBackup = File(context.filesDir, "photo_library_corrupt_${System.currentTimeMillis()}.json")
                file.renameTo(corruptBackup)
            }
        }
        
        // No valid file found - return empty
        emptyList()
    }
}
