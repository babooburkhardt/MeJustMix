package com.example.mejustmix.services

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.mejustmix.data.ColorFolder
import com.example.mejustmix.data.PhotoFolder
import com.example.mejustmix.data.SavedColor
import com.example.mejustmix.ui.SettingsUiState
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.*
import java.lang.reflect.Type
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupData(
    val version: Int = 2,  // Updated version to include K-M data
    val timestamp: Long = System.currentTimeMillis(),
    val settings: SettingsUiState,
    val colorLibrary: List<ColorFolder>,
    val photoLibrary: List<PhotoFolder>
)

object BackupService {

    // Custom Gson to handle Compose Color class
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, object : JsonSerializer<Color>, JsonDeserializer<Color> {
            override fun serialize(src: Color, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(src.toArgb())
            }
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Color {
                return Color(json.asInt)
            }
        })
        .setPrettyPrinting()
        .create()

    fun createBackup(
        context: Context,
        destUri: Uri,
        settings: SettingsUiState,
        colors: List<ColorFolder>,
        photos: List<PhotoFolder>
    ) {
        val contentResolver = context.contentResolver
        
        // 1. Prepare Data Object
        // Note: settings includes kmDatabase with all Kubelka-Munk spectral calibration data
        val backupData = BackupData(
            settings = settings,  // Includes: IP, pumps, pigmentStrengths, kmDatabase (K/S values), display settings
            colorLibrary = colors,
            photoLibrary = photos
        )
        val jsonString = gson.toJson(backupData)

        // 2. Start Zip Stream
        contentResolver.openOutputStream(destUri)?.use { os ->
            ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                
                // A. Add JSON Data
                zos.putNextEntry(ZipEntry("data.json"))
                zos.write(jsonString.toByteArray())
                zos.closeEntry()

                // B. Add Photos
                val photosDir = File(context.filesDir, "saved_photos")
                val photosToSave = photos.flatMap { it.photos }.map { it.uriString }
                
                if (photosDir.exists()) {
                    photosToSave.forEach { uriString ->
                        try {
                            // Extract filename from the URI stored in DB
                            val uri = Uri.parse(uriString)
                            // We assume standard storage: file:///.../photo_UUID.jpg
                            val file = File(uri.path ?: return@forEach)
                            
                            if (file.exists() && file.parentFile?.name == "saved_photos") {
                                val entryName = "images/${file.name}"
                                zos.putNextEntry(ZipEntry(entryName))
                                FileInputStream(file).use { fis -> fis.copyTo(zos) }
                                zos.closeEntry()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun restoreBackup(
        context: Context,
        sourceUri: Uri
    ): BackupData {
        var backupData: BackupData? = null
        val imagesDir = File(context.filesDir, "saved_photos")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        context.contentResolver.openInputStream(sourceUri)?.use { `is` ->
            ZipInputStream(BufferedInputStream(`is`)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    
                    when {
                        name == "data.json" -> {
                            val jsonString = zis.bufferedReader().readText()
                            backupData = gson.fromJson(jsonString, BackupData::class.java)
                        }
                        name.startsWith("images/") -> {
                            // Restore Image File
                            val fileName = File(name).name
                            val destFile = File(imagesDir, fileName)
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
        }

        if (backupData == null) throw Exception("Invalid backup file: No data.json found")

        // Note: backupData.settings includes all Kubelka-Munk calibration data (kmDatabase)
        // which will be automatically restored when settings are applied
        
        // FIX PATHS: The imported URIs point to the OLD phone's file path.
        // We must update them to point to the NEW phone's path.
        val fixedPhotoLibrary = backupData!!.photoLibrary.map { folder ->
            val fixedPhotos = folder.photos.map { photo ->
                try {
                    val oldUri = Uri.parse(photo.uriString)
                    val fileName = File(oldUri.path!!).name
                    val newFile = File(imagesDir, fileName)
                    photo.copy(uriString = Uri.fromFile(newFile).toString())
                } catch (e: Exception) {
                    photo // Keep as is if parsing fails
                }
            }
            folder.copy(photos = fixedPhotos)
        }

        return backupData!!.copy(photoLibrary = fixedPhotoLibrary)
    }
}