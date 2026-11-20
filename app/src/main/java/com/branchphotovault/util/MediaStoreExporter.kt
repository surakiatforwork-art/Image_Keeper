package com.branchphotovault.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class MediaStoreExporter(private val context: Context) {

    fun exportImage(
        sourceFile: File,
        displayName: String
    ): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, GalleryNaming.RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Image Keeper")
                if (!folder.isDirectory && !folder.mkdirs()) return false
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(folder, displayName).absolutePath)
            }
        }

        val targetUri = try { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) }
            catch (_: Exception) { null }
            ?: return false

        return try {
            resolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: error("Cannot open gallery output")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(targetUri, contentValues, null, null)
            }
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(targetUri, null, null) }
            false
        }
    }
}
