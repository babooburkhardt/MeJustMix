package com.example.mejustmix.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.lang.ref.WeakReference

// OPTIMIZATION: Bitmap cache with weak references to prevent memory leaks
object BitmapCache {
    private val cache = mutableMapOf<String, WeakReference<Bitmap>>()
    private const val MAX_CACHE_SIZE = 25
    private val cacheOrder = mutableListOf<String>()
    
    fun get(key: String): Bitmap? {
        return cache[key]?.get()
    }
    
    fun put(key: String, bitmap: Bitmap) {
        // Remove oldest if cache is full
        if (cacheOrder.size >= MAX_CACHE_SIZE) {
            val oldestKey = cacheOrder.removeAt(0)
            cache.remove(oldestKey)
        }
        
        cache[key] = WeakReference(bitmap)
        cacheOrder.remove(key) // Remove if exists
        cacheOrder.add(key) // Add to end
    }
    
    fun clear() {
        cache.clear()
        cacheOrder.clear()
    }
}

// OPTIMIZATION: Load bitmap with smart downsampling to balance quality and memory
fun loadOptimizedBitmap(context: Context, uri: Uri, targetWidth: Int = 2560): Bitmap? {
    try {
        // Check cache first
        val cacheKey = uri.toString()
        BitmapCache.get(cacheKey)?.let { return it }
        
        // First decode bounds only (no memory allocation)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        
        // Calculate optimal sample size
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetWidth)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888 // Better quality, worth the memory
        
        // Decode with sampling
        val sampledBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        
        // Handle EXIF rotation
        val rotatedBitmap = context.contentResolver.openInputStream(uri)?.use { exifStream ->
            val exif = ExifInterface(exifStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return@use sampledBitmap
            }
            
            Bitmap.createBitmap(
                sampledBitmap, 0, 0,
                sampledBitmap.width, sampledBitmap.height,
                matrix, true
            ).also {
                if (it != sampledBitmap) {
                    sampledBitmap.recycle() // Free original bitmap
                }
            }
        } ?: sampledBitmap
        
        // Cache the result
        BitmapCache.put(cacheKey, rotatedBitmap)
        
        return rotatedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        // Calculate the largest inSampleSize that is a power of 2 and keeps both
        // height and width larger than the requested height and width
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    
    return inSampleSize
}
