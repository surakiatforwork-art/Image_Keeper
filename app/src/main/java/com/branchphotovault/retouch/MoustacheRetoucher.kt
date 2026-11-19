package com.branchphotovault.retouch

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.nio.ByteBuffer

/** Processes a bounded face crop; original file is never changed. */
enum class RetouchRegion(val label: String) {
    MOUSTACHE("หนวด"), BEARD("เครา"), BOTH("หนวดและเครา")
}

class MoustacheRetoucher(private val context: Context) {
    fun faces(bitmap: Bitmap): List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>> {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("retouch/face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE).setNumFaces(5).setMinFaceDetectionConfidence(0.6f).build()
        return FaceLandmarker.createFromOptions(context, options).use { detector ->
            // MPImage.close() owns/releases its bitmap; never pass the Compose preview.
            val detectionBitmap = bitmap.copy(Bitmap.Config.ARGB_8888,false)
            val image = BitmapImageBuilder(detectionBitmap).build()
            try { detector.detect(image).faceLandmarks() } finally { image.close() }
        }
    }

    fun process(bitmap: Bitmap, faceIndex: Int, region: RetouchRegion = RetouchRegion.BOTH): Bitmap {
        val face = faces(bitmap).getOrNull(faceIndex) ?: error("ไม่พบใบหน้า กรุณาเลือกภาพที่เห็นใบหน้าชัดเจน")
        fun point(i: Int) = PointF(face[i].x() * bitmap.width, face[i].y() * bitmap.height)
        val left = point(61); val right = point(291); val nose = point(2); val lip = point(0)
        val span = kotlin.math.hypot(right.x - left.x, right.y - left.y)
        require(span >= 25 && lip.y > nose.y) { "ใบหน้าเล็กหรือเอียงเกินไป กรุณาใช้ภาพหน้าตรง" }
        val jaw = listOf(234,93,132,58,172,136,150,149,176,148,152,377,400,378,379,365,397,288,361,323,454)
        val bounds = if (region == RetouchRegion.MOUSTACHE) Rect(
            (minOf(left.x, right.x) - span * 0.7f).toInt().coerceAtLeast(0),
            (nose.y - span * 0.6f).toInt().coerceAtLeast(0),
            (maxOf(left.x, right.x) + span * 0.7f).toInt().coerceAtMost(bitmap.width),
            (lip.y + span * 0.6f).toInt().coerceAtMost(bitmap.height))
        else Rect(
            (jaw.minOf { point(it).x } - span * 0.2f).toInt().coerceAtLeast(0),
            (nose.y - span * 0.5f).toInt().coerceAtLeast(0),
            (jaw.maxOf { point(it).x } + span * 0.2f).toInt().coerceAtMost(bitmap.width),
            (point(152).y + span * 0.25f).toInt().coerceAtMost(bitmap.height))
        require(bounds.width() > 0 && bounds.height() > 0) { "ใบหน้าอยู่นอกขอบภาพ" }
        val crop = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
        val input = Bitmap.createScaledBitmap(crop, 512, 512, true).copy(Bitmap.Config.ARGB_8888,true)
        val mask = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val path = Path()
        fun local(i: Int): PointF = point(i).let { PointF((it.x - bounds.left) * 512 / bounds.width(), (it.y - bounds.top) * 512 / bounds.height()) }
        // Follow the upper-lip contour, then close below the nose; exclude the lips.
        val contour = listOf(61, 40, 37, 0, 267, 270, 291)
        contour.forEachIndexed { index, i ->
            val q = local(i)
            q.y -= 3f
            if (index == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
        val n = local(2)
        val l = local(61); val rr = local(291)
        path.lineTo(rr.x, n.y + 5)
        path.lineTo(l.x, n.y + 5)
        path.close()
        if (region == RetouchRegion.BEARD) path.reset()
        if (region != RetouchRegion.MOUSTACHE) {
            val beard = Path()
            jaw.forEachIndexed { index, i ->
                val q = local(i)
                if (index == 0) beard.moveTo(q.x,q.y) else beard.lineTo(q.x,q.y)
            }
            // Upper boundary follows below the mouth, excluding nose and upper cheeks.
            val lowerLip = local(17)
            val mouthR = local(291); val mouthL = local(61)
            beard.lineTo(mouthR.x + 14f, mouthR.y + 8f)
            beard.lineTo(lowerLip.x, lowerLip.y + 7f)
            beard.lineTo(mouthL.x - 14f, mouthL.y + 8f)
            beard.close()
            path.addPath(beard)
        }
        Canvas(mask).apply { drawColor(Color.WHITE); drawPath(path, Paint().apply { color = Color.BLACK }) }
        val count = 512 * 512
        val pixels = IntArray(count); input.getPixels(pixels, 0, 512, 0, 0, 512, 512)
        val maskPixels = IntArray(count); mask.getPixels(maskPixels, 0, 512, 0, 0, 512, 512)
        val rgb = ByteArray(count * 3)
        val m = ByteArray(count)
        for (i in pixels.indices) {
            rgb[i] = Color.red(pixels[i]).toByte(); rgb[count + i] = Color.green(pixels[i]).toByte()
            rgb[count * 2 + i] = Color.blue(pixels[i]).toByte(); m[i] = Color.red(maskPixels[i]).toByte()
        }
        val env = OrtEnvironment.getEnvironment()
        val model = context.assets.open("retouch/migan.onnx").use { it.readBytes() }
        val generated = IntArray(count)
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            env.createSession(model, options).use { session ->
                OnnxTensor.createTensor(env, ByteBuffer.wrap(rgb), longArrayOf(1,3,512,512), OnnxJavaType.UINT8).use { imageTensor ->
                    OnnxTensor.createTensor(env, ByteBuffer.wrap(m), longArrayOf(1,1,512,512), OnnxJavaType.UINT8).use { maskTensor ->
                        session.run(mapOf("image" to imageTensor, "mask" to maskTensor)).use { result ->
                            val bytes = (result[0] as OnnxTensor).byteBuffer
                            for (i in 0 until count) generated[i] = Color.rgb(bytes.get(i).toInt() and 255,
                                bytes.get(count+i).toInt() and 255, bytes.get(count*2+i).toInt() and 255)
                        }
                    }
                }
            }
        }
        val processed = Bitmap.createBitmap(generated,512,512,Bitmap.Config.ARGB_8888)
        // Restrict compositing to the mask, even if the model changes other pixels.
        val alpha = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        Canvas(alpha).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; maskFilter = BlurMaskFilter(3f,BlurMaskFilter.Blur.NORMAL) })
        Canvas(processed).drawBitmap(alpha,0f,0f,Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        val output = bitmap.copy(Bitmap.Config.ARGB_8888,true)
        Canvas(output).drawBitmap(processed,null,bounds,Paint(Paint.FILTER_BITMAP_FLAG))
        listOf(input,mask,alpha,processed).distinct().forEach { if (it !== bitmap && it !== crop) it.recycle() }
        if (crop !== bitmap) crop.recycle()
        return output
    }
}
