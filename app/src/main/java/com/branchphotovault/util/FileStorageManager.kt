package com.branchphotovault.util

import android.content.Context
import java.io.File

class FileStorageManager(private val context: Context) {

    fun createMainFile(account: String, branchCode: String, monthKey: String, photoId: String): File {
        return createPhotoFile(
            rootFolder = AppConfig.FOLDER_PHOTOS,
            account = account,
            branchCode = branchCode,
            monthKey = monthKey,
            fileName = "${photoId}_main.jpg"
        )
    }

    fun createThumbFile(account: String, branchCode: String, monthKey: String, photoId: String): File {
        return createPhotoFile(
            rootFolder = AppConfig.FOLDER_THUMBS,
            account = account,
            branchCode = branchCode,
            monthKey = monthKey,
            fileName = "${photoId}_thumb.jpg"
        )
    }

    fun createCaptureTempFile(account: String, branchCode: String): File {
        val directory = File(
            context.cacheDir,
            "${AppConfig.FOLDER_CAPTURE_TEMP}/${sanitize(account)}/${sanitize(branchCode)}"
        )
        directory.mkdirs()
        return File(directory, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun deleteFile(path: String) {
        runCatching { File(path).takeIf(File::exists)?.delete() }
    }

    private fun createPhotoFile(
        rootFolder: String,
        account: String,
        branchCode: String,
        monthKey: String,
        fileName: String
    ): File {
        val directory = File(
            context.filesDir,
            "$rootFolder/${sanitize(account)}/${sanitize(branchCode)}/${sanitize(monthKey)}"
        )
        directory.mkdirs()
        return File(directory, fileName)
    }

    private fun sanitize(value: String): String {
        return value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }
    }
}

