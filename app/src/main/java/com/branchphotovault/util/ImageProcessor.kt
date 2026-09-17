package com.branchphotovault.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.branchphotovault.data.model.ImageAspectRatioOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class ImageProcessor(private val context: Context) {

    suspend fun processUri(
        uri: Uri,
        mainFile: File,
        thumbFile: File,
        mainLongEdge: Int,
        aspectRatioOption: ImageAspectRatioOption,
        forcePortrait: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val exifOrientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).exifOrientation()
        } ?: ExifInterface.ORIENTATION_NORMAL

        val mainBitmap = decodeBitmapFromUri(
            contentResolver = context.contentResolver,
            uri = uri,
            longEdge = mainLongEdge
        )
        val thumbBitmap = decodeBitmapFromUri(
            contentResolver = context.contentResolver,
            uri = uri,
            longEdge = AppConfig.THUMB_LONG_EDGE
        )

        writeBitmap(
            prepareBitmap(mainBitmap, exifOrientation, aspectRatioOption, mainLongEdge, forcePortrait),
            mainFile,
            AppConfig.MAIN_QUALITY
        )
        writeBitmap(
            prepareBitmap(
                thumbBitmap,
                exifOrientation,
                aspectRatioOption,
                AppConfig.THUMB_LONG_EDGE,
                forcePortrait
            ),
            thumbFile,
            AppConfig.THUMB_QUALITY
        )
    }

    suspend fun processFile(
        sourceFile: File,
        mainFile: File,
        thumbFile: File,
        mainLongEdge: Int,
        aspectRatioOption: ImageAspectRatioOption,
        forcePortrait: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val exifOrientation = ExifInterface(sourceFile.absolutePath).exifOrientation()
        val mainBitmap = decodeBitmapFromFile(sourceFile, mainLongEdge)
        val thumbBitmap = decodeBitmapFromFile(sourceFile, AppConfig.THUMB_LONG_EDGE)

        writeBitmap(
            prepareBitmap(mainBitmap, exifOrientation, aspectRatioOption, mainLongEdge, forcePortrait),
            mainFile,
            AppConfig.MAIN_QUALITY
        )
        writeBitmap(
            prepareBitmap(
                thumbBitmap,
                exifOrientation,
                aspectRatioOption,
                AppConfig.THUMB_LONG_EDGE,
                forcePortrait
            ),
            thumbFile,
            AppConfig.THUMB_QUALITY
        )
    }

    private fun decodeBitmapFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        longEdge: Int
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, longEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("Unable to decode image.")

        return scaleBitmap(decoded, longEdge)
    }

    private fun decodeBitmapFromFile(file: File, longEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, longEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: error("Unable to decode image.")
        return scaleBitmap(decoded, longEdge)
    }

    private fun calculateInSampleSize(width: Int, height: Int, longEdge: Int): Int {
        val originalLongEdge = max(width, height)
        if (originalLongEdge <= longEdge || width <= 0 || height <= 0) return 1

        var sample = 1
        var halfLongEdge = originalLongEdge / 2
        while ((halfLongEdge / sample) >= longEdge) {
            sample *= 2
        }
        return sample
    }

    private fun scaleBitmap(bitmap: Bitmap, longEdge: Int): Bitmap {
        val originalLongEdge = max(bitmap.width, bitmap.height)
        if (originalLongEdge <= longEdge) {
            return bitmap
        }

        val scale = longEdge.toFloat() / originalLongEdge.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun prepareBitmap(
        bitmap: Bitmap,
        exifOrientation: Int,
        aspectRatioOption: ImageAspectRatioOption,
        longEdge: Int,
        forcePortrait: Boolean
    ): Bitmap {
        val oriented = applyExifOrientation(bitmap, exifOrientation)
        val normalized = if (forcePortrait) {
            normalizeCapturedPortrait(oriented, exifOrientation)
        } else {
            oriented
        }
        val resolvedAspectRatio = if (forcePortrait) {
            resolvePortraitAspectRatio(normalized, aspectRatioOption)
        } else {
            aspectRatioOption
        }
        val cropped = cropToAspectRatio(normalized, resolvedAspectRatio)
        return scaleBitmap(cropped, longEdge)
    }

    private fun applyExifOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED -> return bitmap

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.setScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.setRotate(180f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.setRotate(90f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.setRotate(-90f)
            }

            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropToAspectRatio(
        bitmap: Bitmap,
        aspectRatioOption: ImageAspectRatioOption
    ): Bitmap {
        val targetWidth = aspectRatioOption.width ?: return bitmap
        val targetHeight = aspectRatioOption.height ?: return bitmap
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val currentRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (kotlin.math.abs(currentRatio - targetRatio) < 0.01f) {
            return bitmap
        }

        return if (currentRatio > targetRatio) {
            val newWidth = (bitmap.height * targetRatio).roundToInt().coerceAtMost(bitmap.width)
            val offsetX = ((bitmap.width - newWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, offsetX, 0, newWidth, bitmap.height)
        } else {
            val newHeight = (bitmap.width / targetRatio).roundToInt().coerceAtMost(bitmap.height)
            val offsetY = ((bitmap.height - newHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, 0, offsetY, bitmap.width, newHeight)
        }
    }

    private fun resolvePortraitAspectRatio(
        bitmap: Bitmap,
        aspectRatioOption: ImageAspectRatioOption
    ): ImageAspectRatioOption {
        if (aspectRatioOption != ImageAspectRatioOption.ORIGINAL) {
            return aspectRatioOption.portraitVariant()
        }

        return if (bitmap.height >= bitmap.width) {
            ImageAspectRatioOption.ORIGINAL
        } else {
            ImageAspectRatioOption.RATIO_3_4
        }
    }

    private fun normalizeCapturedPortrait(
        bitmap: Bitmap,
        exifOrientation: Int
    ): Bitmap {
        val hasExplicitExifRotation = exifOrientation != ExifInterface.ORIENTATION_NORMAL &&
            exifOrientation != ExifInterface.ORIENTATION_UNDEFINED

        if (hasExplicitExifRotation || bitmap.height >= bitmap.width) {
            return bitmap
        }

        val matrix = Matrix().apply { setRotate(-90f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun writeBitmap(bitmap: Bitmap, destination: File, quality: Int) {
        FileOutputStream(destination).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                error("Unable to compress image.")
            }
        }
    }

    private fun ExifInterface.exifOrientation(): Int {
        return getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
}
