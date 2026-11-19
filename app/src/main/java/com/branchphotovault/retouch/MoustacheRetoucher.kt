package com.branchphotovault.retouch

import android.content.Context
import android.graphics.*
import ai.onnxruntime.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.*

enum class RetouchRegion(val label: String) {
    MOUSTACHE("หนวด"), BEARD("เครา"), BOTH("หนวดและเครา")
}

/** Offline AttGAN compositing. The caller's bitmap and original file are never modified. */
class MoustacheRetoucher(private val context: Context) {
    fun faces(bitmap: Bitmap): List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>> {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("retouch/face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE).setNumFaces(5).setMinFaceDetectionConfidence(0.6f).build()
        return FaceLandmarker.createFromOptions(context, options).use { detector ->
            val image = BitmapImageBuilder(bitmap.copy(Bitmap.Config.ARGB_8888, false)).build()
            try { detector.detect(image).faceLandmarks() } finally { image.close() }
        }
    }

    fun process(bitmap: Bitmap, faceIndex: Int, region: RetouchRegion = RetouchRegion.BOTH,
        onProgress: (RetouchStage) -> Unit = {}): Bitmap = synchronized(lock) {
        onProgress(RetouchStage.LOCATE)
        val face = faces(bitmap).getOrNull(faceIndex) ?: error("ไม่พบใบหน้า กรุณาเลือกภาพที่เห็นใบหน้าชัดเจน")
        val width = bitmap.width
        val height = bitmap.height
        fun p(i: Int) = PointF(face[i].x() * width, face[i].y() * height)
        val left = p(61); val right = p(291)
        val span = hypot(right.x - left.x, right.y - left.y)
        require(span >= 25) { "ใบหน้าเล็กเกินไป กรุณาเลือกภาพที่เห็นใบหน้าชัดเจน" }
        val eyeL = PointF((p(33).x + p(133).x) / 2, (p(33).y + p(133).y) / 2)
        val eyeR = PointF((p(362).x + p(263).x) / 2, (p(362).y + p(263).y) / 2)
        val dx = eyeR.x - eyeL.x; val dy = eyeR.y - eyeL.y
        val normSquared = dx * dx + dy * dy
        require(normSquared > 1) { "ตำแหน่งใบหน้าไม่ชัดเจน" }
        val a = dx * 64f / normSquared; val b = dy * 64f / normSquared
        val centerX = (eyeL.x + eyeR.x) / 2; val centerY = (eyeL.y + eyeR.y) / 2
        val tx = 128f - a * centerX - b * centerY
        val ty = 115.2f + b * centerX - a * centerY
        val det = a * a + b * b
        val original = IntArray(width * height)
        bitmap.getPixels(original, 0, width, 0, 0, width, height)
        val input = FloatArray(PLANE * 3)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val sx = (a * (x - tx) - b * (y - ty)) / det
            val sy = (b * (x - tx) + a * (y - ty)) / det
            for (c in 0..2) input[c * PLANE + y * SIZE + x] =
                sampleOriginal(original, width, height, sx, sy, c).roundToInt().coerceIn(0,255) / 127.5f - 1f
        }
        onProgress(RetouchStage.ANALYSE)
        val parsing = FaceParsingTool(context).analyse(input)
        fun labelAt(x: Int,y: Int) = parsing.labelAt(a*x+b*y+tx,-b*x+a*y+ty)
        onProgress(RetouchStage.PROTECT)
        val path = Path()
        fun polygon(points: List<PointF>, target: Path = path) {
            points.forEachIndexed { i, q -> if (i == 0) target.moveTo(q.x,q.y) else target.lineTo(q.x,q.y) }
            target.close()
        }
        if (region != RetouchRegion.BEARD) {
            val mouth = listOf(61,40,37,0,267,270,291).map { p(it).apply { y -= max(2f,span*.035f) } }
            val top = p(2).y + span*.04f
            polygon(mouth + listOf(PointF(mouth.last().x,top),PointF(mouth.first().x,top)))
        }
        if (region != RetouchRegion.MOUSTACHE) {
            val jaw = listOf(58,172,136,150,149,176,148,152,377,400,378,379,365,397,288).map(::p)
            polygon(jaw + listOf(PointF(right.x+span*.12f,right.y+span*.02f),
                p(17).apply { y += span*.04f },PointF(left.x-span*.12f,left.y+span*.02f)))
        }
        val protected = Path()
        polygon(listOf(61,40,37,0,267,270,291,321,314,17,84,91).map(::p),protected)
        polygon(listOf(98,2,327,4).map(::p),protected)
        val mask = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(original.size)
        try {
            val canvas = Canvas(mask)
            val paint = Paint().apply { color = Color.WHITE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
            canvas.drawPath(path,paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2,(span*.16f).toInt()) * 2f
            canvas.drawPath(path,paint)
            paint.style = Paint.Style.FILL
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawPath(protected,paint)
            mask.getPixels(maskPixels,0,width,0,0,width,height)
        } finally { mask.recycle() }
        var editablePixels = 0
        for (i in maskPixels.indices) {
            if (Color.alpha(maskPixels[i]) == 0) continue
            if (!FaceParsingResult.allowsRetouch(labelAt(i%width,i/width))) maskPixels[i] = 0
            else editablePixels++
        }
        require(editablePixels >= max(25,(span*span*.05f).toInt())) {
            "AI แยกพื้นที่ผิวไม่ชัดเจน กรุณาลองภาพที่เห็นใบหน้าชัดกว่าเดิม"
        }
        onProgress(RetouchStage.GENERATE)
        // Close each native session before starting the next to keep peak memory bounded.
        val generated = runModel(input)
        fun generatedAt(x: Int,y: Int,c: Int) = samplePlane(generated,a*x+b*y+tx,-b*x+a*y+ty,c)
        // Feather inward only: pixels outside the mask stay byte-identical to the input.
        val distance = FloatArray(original.size) { if (Color.alpha(maskPixels[it]) == 0) 0f else 1e6f }
        for (y in 0 until height) for (x in 0 until width) {
            val i = y*width+x
            if (x == 0 || y == 0 || x == width-1 || y == height-1) distance[i] = 0f
            if (x > 0) distance[i] = min(distance[i],distance[i-1]+1)
            if (y > 0) distance[i] = min(distance[i],distance[i-width]+1)
            if (x > 0 && y > 0) distance[i] = min(distance[i],distance[i-width-1]+1.414214f)
            if (x+1 < width && y > 0) distance[i] = min(distance[i],distance[i-width+1]+1.414214f)
        }
        for (y in height-1 downTo 0) for (x in width-1 downTo 0) {
            val i = y*width+x
            if (x+1 < width) distance[i] = min(distance[i],distance[i+1]+1)
            if (y+1 < height) distance[i] = min(distance[i],distance[i+width]+1)
            if (x+1 < width && y+1 < height) distance[i] = min(distance[i],distance[i+width+1]+1.414214f)
            if (x > 0 && y+1 < height) distance[i] = min(distance[i],distance[i+width-1]+1.414214f)
        }
        onProgress(RetouchStage.COLOUR)
        val offsets = Array(3) { mutableListOf<Float>() }
        val leftOffsets = Array(3) { mutableListOf<Float>() }
        val rightOffsets = Array(3) { mutableListOf<Float>() }
        val radius = max(3,(span*.12f).toInt())
        val samples = HashSet<Int>()
        for (center in listOf(50,280,205,425).map(::p)) {
            val cx = center.x.toInt(); val cy = center.y.toInt()
            for (y in max(0,cy-radius)..min(height-1,cy+radius)) for (x in max(0,cx-radius)..min(width-1,cx+radius)) {
                if ((x-cx)*(x-cx)+(y-cy)*(y-cy) <= radius*radius && labelAt(x,y) == 1) samples.add(y*width+x)
            }
        }
        require(samples.size >= 16) { "AI หาพื้นที่ผิวอ้างอิงไม่ได้ กรุณาลองภาพที่เห็นใบหน้าชัดกว่าเดิม" }
        for (i in samples) {
            val x = i%width; val y = i/width; val source = original[i]
            val first = rgbToLab(Color.red(source).toFloat(),Color.green(source).toFloat(),Color.blue(source).toFloat())
            val second = rgbToLab(generatedAt(x,y,0).toInt().toFloat(),generatedAt(x,y,1).toInt().toFloat(),generatedAt(x,y,2).toInt().toFloat())
            val side = if (a*x+b*y+tx < 128) leftOffsets else rightOffsets
            for (c in 0..2) {
                val difference = first[c]-second[c]
                offsets[c].add(difference)
                side[c].add(difference)
            }
        }
        val offset = FloatArray(3) { c -> offsets[c].sorted().let { (it[(it.size-1)/2]+it[it.size/2])/2 } }
        fun sideMedian(values: Array<MutableList<Float>>) = FloatArray(3) { c ->
            if (values[c].size < 16) offset[c]
            else values[c].sorted().let { (it[(it.size-1)/2]+it[it.size/2])/2 }
        }
        val leftOffset = sideMedian(leftOffsets)
        val rightOffset = sideMedian(rightOffsets)
        val unevenLight = abs(leftOffset[0]-rightOffset[0]) > 4f
        onProgress(RetouchStage.TEXTURE)
        val output = original.copyOf()
        for (i in output.indices) {
            if (distance[i] <= 0f) continue
            val x = i%width; val y = i/width
            val lab = rgbToLab(generatedAt(x,y,0).toInt().toFloat(),generatedAt(x,y,1).toInt().toFloat(),generatedAt(x,y,2).toInt().toFloat())
            val sideMix = ((a*x+b*y+tx-96)/64).coerceIn(0f,1f)
            val localOffset = FloatArray(3) { c -> if (unevenLight) leftOffset[c]*(1-sideMix)+rightOffset[c]*sideMix else offset[c] }
            val source = original[i]
            val sourceLab = rgbToLab(Color.red(source).toFloat(),Color.green(source).toFloat(),Color.blue(source).toFloat())
            val guarded = SkinToneGuard.apply(sourceLab,FloatArray(3) { c -> lab[c]+localOffset[c] })
            val matched = labToRgb(guarded[0],guarded[1],guarded[2])
            val alpha = (distance[i]/max(2f,span*.055f)).coerceIn(0f,1f)
            val channels = IntArray(3) { c ->
                val value = channel(source,c).toFloat()
                var average = 0f
                for (yy in -1..1) for (xx in -1..1) average += channel(original[
                    (y+yy).coerceIn(0,height-1)*width+(x+xx).coerceIn(0,width-1)],c)
                val fine = (value-average/9f).coerceIn(-2f,4f)*.7f
                (value*(1-alpha)+(matched[c]+fine).coerceIn(0f,255f)*alpha).toInt().coerceIn(0,255)
            }
            output[i] = Color.rgb(channels[0],channels[1],channels[2])
        }
        onProgress(RetouchStage.VERIFY)
        check(output.indices.all { Color.alpha(maskPixels[it]) != 0 || output[it] == original[it] })
        Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply { setPixels(output,0,width,0,0,width,height) }
    }

    private fun runModel(input: FloatArray): FloatArray {
        val file = File(context.cacheDir,"attgan-d0011f493b12.onnx")
        if (file.length() != 86936315L) {
            val temporary = File(context.cacheDir,"attgan-copy.tmp")
            try {
                context.assets.open("retouch/attgan.onnx").use { source -> temporary.outputStream().use { source.copyTo(it) } }
                check(temporary.length() == 86936315L && temporary.renameTo(file)) { "เตรียมโมเดลไม่สำเร็จ" }
            } finally { temporary.delete() }
        }
        val env = OrtEnvironment.getEnvironment()
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            env.createSession(file.absolutePath,options).use { session ->
                OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,256,256)).use { tensor ->
                    session.run(mapOf("image" to tensor)).use { result ->
                        val buffer = (result[0] as OnnxTensor).floatBuffer
                        FloatArray(PLANE*3).also { buffer.get(it); check(it.all { v -> v.isFinite() }) }
                    }
                }
            }
        }
    }
    private fun channel(pixel: Int,c: Int) = when(c) { 0 -> Color.red(pixel); 1 -> Color.green(pixel); else -> Color.blue(pixel) }
    private fun reflected(value: Int,size: Int): Int {
        if (size <= 1) return 0
        val period = (size-1)*2
        val normalized = ((value%period)+period)%period
        return if (normalized >= size) period-normalized else normalized
    }
    private fun sampleOriginal(pixels: IntArray,width: Int,height: Int,x: Float,y: Float,c: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt(); val fx = x-x0; val fy = y-y0
        fun v(xx: Int,yy: Int) = channel(pixels[reflected(yy,height)*width+reflected(xx,width)],c).toFloat()
        return (v(x0,y0)*(1-fx)+v(x0+1,y0)*fx)*(1-fy)+(v(x0,y0+1)*(1-fx)+v(x0+1,y0+1)*fx)*fy
    }
    private fun samplePlane(pixels: FloatArray,x: Float,y: Float,c: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt(); val fx = x-x0; val fy = y-y0
        fun v(xx: Int,yy: Int) = pixels[c*PLANE+reflected(yy,SIZE)*SIZE+reflected(xx,SIZE)]
        return (v(x0,y0)*(1-fx)+v(x0+1,y0)*fx)*(1-fy)+(v(x0,y0+1)*(1-fx)+v(x0+1,y0+1)*fx)*fy
    }
    private fun rgbToLab(r: Float,g: Float,b: Float): FloatArray {
        fun linear(v: Float) = (v/255.0).let { if (it <= .04045) it/12.92 else ((it+.055)/1.055).pow(2.4) }
        val rr = linear(r); val gg = linear(g); val bb = linear(b)
        fun f(t: Double) = if (t > .0088564517) Math.cbrt(t) else 7.787037*t+16.0/116
        val x = f((rr*.4124564+gg*.3575761+bb*.1804375)/.95047)
        val y = f(rr*.2126729+gg*.7151522+bb*.0721750)
        val z = f((rr*.0193339+gg*.1191920+bb*.9503041)/1.08883)
        return floatArrayOf(((116*y-16)*2.55).toFloat(),(500*(x-y)+128).toFloat(),(200*(y-z)+128).toFloat())
    }
    private fun labToRgb(l: Float,a: Float,b: Float): FloatArray {
        val fy = (l.coerceIn(0f,255f)/2.55+16)/116
        val fx = fy+(a.coerceIn(0f,255f)-128)/500.0; val fz = fy-(b.coerceIn(0f,255f)-128)/200.0
        fun inv(t: Double) = if (t > 6.0/29) t*t*t else (t-16.0/116)/7.787037
        val x = inv(fx)*.95047; val y = inv(fy); val z = inv(fz)*1.08883
        fun gamma(t: Double) = (255*(if (t <= .0031308) 12.92*t else 1.055*t.pow(1/2.4)-.055)).toFloat().coerceIn(0f,255f)
        return floatArrayOf(gamma(x*3.2404542-y*1.5371385-z*.4985314),gamma(-x*.9692660+y*1.8760108+z*.0415560),gamma(x*.0556434-y*.2040259+z*1.0572252))
    }
    companion object {
        private const val SIZE = 256
        private const val PLANE = SIZE*SIZE
        private val lock = Any()
    }
}
