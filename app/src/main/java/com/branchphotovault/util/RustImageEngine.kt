package com.branchphotovault.util

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.util.Log
import com.branchphotovault.data.model.ImageAspectRatioOption
import kotlin.math.abs
import kotlin.math.roundToInt

/** Experimental Rust core: combines lossless pixel orientation, mirror and crop. */
internal object RustImageEngine {
    private const val TAG = "KeeperRust"
    private val available: Boolean by lazy {
        try {
            System.loadLibrary("keeper_core")
            true
        } catch (e: LinkageError) {
            Log.w(TAG, "Native core unavailable; using Android Bitmap", e)
            false
        }
    }

    @JvmStatic private external fun transformNative(
        source: Bitmap, dest: Bitmap, orientation: Int, mirror: Boolean, x: Int, y: Int
    ): Boolean

    fun transform(
        bitmap: Bitmap, exif: Int, aspect: ImageAspectRatioOption,
        forcePortrait: Boolean, mirror: Boolean
    ): Bitmap? {
        if (!available || bitmap.config != Bitmap.Config.ARGB_8888) return null
        val orientation = if (forcePortrait && (exif == 0 || exif == 1) &&
            bitmap.width > bitmap.height) 8 else if (exif in 1..8) exif else 1
        val w = if (orientation in 5..8) bitmap.height else bitmap.width
        val h = if (orientation in 5..8) bitmap.width else bitmap.height
        val ratio = if (!forcePortrait) aspect else if (aspect != ImageAspectRatioOption.ORIGINAL)
            aspect.portraitVariant() else if (h >= w) aspect else ImageAspectRatioOption.RATIO_3_4
        var cw = w
        var ch = h
        val tw = ratio.width
        val th = ratio.height
        if (tw != null && th != null) {
            val target = tw.toFloat() / th.toFloat()
            val current = w.toFloat() / h.toFloat()
            if (abs(current - target) >= 0.01f) {
                if (current > target) cw = (h * target).roundToInt().coerceIn(1, w)
                else ch = (w / target).roundToInt().coerceIn(1, h)
            }
        }
        if (orientation == 1 && !mirror && cw == w && ch == h) return bitmap
        // Leave simple crop to the existing optimized Android path.
        if (orientation == 1 && !mirror) return null
        val start = System.nanoTime()
        var output: Bitmap? = null
        try {
            val dest = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888,
                bitmap.hasAlpha(), bitmap.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
            output = dest
            dest.density = bitmap.density
            dest.setPremultiplied(bitmap.isPremultiplied)
            if (!transformNative(bitmap, dest, orientation, mirror, (w-cw)/2, (h-ch)/2)) {
                dest.recycle()
                return null
            }
            Log.d(TAG, "Rust transform: " + (System.nanoTime()-start)/1_000_000 + " ms")
            return dest
        } catch (e: LinkageError) {
            output?.recycle()
            Log.w(TAG, "Native transform unavailable", e)
            return null
        } catch (e: RuntimeException) {
            output?.recycle()
            Log.w(TAG, "Native transform failed; using Android Bitmap", e)
            return null
        }
    }
}
