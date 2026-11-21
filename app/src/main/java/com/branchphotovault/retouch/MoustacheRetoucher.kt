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

/** Both engines retain the licensed local AttGAN model. The V2 path is an
 * independent hair-aware mask/compositing experiment, not a Meitu model. */
enum class RetouchEngine(val label: String) {
    SMART_V4("Smart Shave V4.1 · AI ตรวจหนวด"),
    MASK_INSPECT("V3 · ตรวจแผนที่ Mask"),
    HYBRID_BASELINE("V2.3 · หนวด AttGAN + ผิว V2.1"),
    MOUSTACHE_FOCUS("V2.2 · เน้นเก็บหนวด (ทดลอง)"),
    RESIDUAL_REPAIR("V2.1 · เก็บหนวดตกหล่น (ทดลอง)"),
    HAIR_AWARE("V2 · เงาปลอดภัย"),
    BASELINE("AttGAN รอบเดียว")
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
        engine: RetouchEngine = RetouchEngine.BASELINE,
        onDiagnostics: ((Bitmap, HairMaskDiagnostics.Stats) -> Unit)? = null,
        extraDetail: Boolean = false,
        extraDetailStrength: Float = .28f,
        onProgress: (RetouchStage) -> Unit = {}
    ): Bitmap = synchronized(lock) {
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
        // The previously trained 19-class face parser cannot segment facial
        // hair. V4 adds an independently trained, two-class beard/hair model
        // and only EXPANDS the editable mask for high-confidence BEARD pixels
        // inside anatomically limited, protected face-and-upper-neck zones.
        val anatomy = if(engine == RetouchEngine.SMART_V4)
            FacialHairAnatomy(face,width,height,a,b,tx,ty,region) else null
        // Independently preserve the face silhouette, even when a beard model
        // detects dark facial hair along the chin and neck boundary.
        val jawGuard = if(engine == RetouchEngine.SMART_V4)
            JawlineIntegrityGuard.fromLandmarks(face,width,height,a,b,tx,ty) else null
        val protectedRegion = Region().apply {
            setPath(protected,Region(0,0,width,height))
        }
        val learnedBeard = if(engine == RetouchEngine.SMART_V4) {
            try {
                val model=BeardSegmentationTool(context).analyse(bitmap,face)
                android.util.Log.i("KeeperRetouchV4",
                    "learned model detections=${model.detections}, max=${model.strongestScore}, area=${model.coverage()}")
                model
            } catch(error:Exception) {
                android.util.Log.e("KeeperRetouchV4",
                    "Neural beard segmentation unavailable; falling back to V2.3",error)
                null
            }
        } else null
        val oldEditableMask=if(engine == RetouchEngine.SMART_V4 && onDiagnostics != null)
            BooleanArray(maskPixels.size) { i ->
                maskPixels[i] != 0 && FaceParsingResult.allowsRetouch(labelAt(i%width,i/width))
            } else null
        var editablePixels = 0
        var expandedPixels = 0
        for(i in maskPixels.indices) {
            val x=i%width; val y=i/width
            val label=labelAt(x,y)
            val jawAllowed=jawGuard?.weightAt(x,y)?.let {it>.001f} ?: true
            val oldAllowed=jawAllowed && Color.alpha(maskPixels[i])!=0 &&
                FaceParsingResult.allowsRetouch(label)
            val expanded = if (jawAllowed && learnedBeard != null &&
                anatomy?.maskZone(x,y)==true &&
                !protectedRegion.contains(x,y)) {
                val confidence=learnedBeard.at(x,y)
                confidence>.20f && (FaceParsingResult.allowsRetouch(label) ||
                    (label==0 && confidence>.68f))
            } else false
            if(oldAllowed || expanded) {
                if(expanded && !oldAllowed) expandedPixels++
                maskPixels[i]=Color.WHITE
                editablePixels++
            } else maskPixels[i]=Color.TRANSPARENT
        }
        if(engine == RetouchEngine.SMART_V4) {
            android.util.Log.i("KeeperRetouchV4","expanded mask pixels=$expandedPixels")
            if(onDiagnostics != null && learnedBeard != null) {
                val (preview,stats)=NeuralBeardPreview.render(
                    bitmap,learnedBeard,
                    permitted={x,y -> maskPixels[y*width+x]!=0 &&
                        anatomy?.maskZone(x,y)==true &&
                        !protectedRegion.contains(x,y)},
                    oldEditable={x,y -> oldEditableMask?.get(y*width+x)==true}
                )
                onDiagnostics(preview,stats)
            }
        }
        require(editablePixels >= max(25,(span*span*.05f).toInt())) {
            "AI แยกพื้นที่ผิวไม่ชัดเจน กรุณาลองภาพที่เห็นใบหน้าชัดกว่าเดิม"
        }
        if(engine == RetouchEngine.MASK_INSPECT && onDiagnostics != null) {
            val upper=p(13); val chin=p(152)
            fun mappedX(q:PointF) = a*q.x+b*q.y+tx
            fun mappedY(q:PointF) = -b*q.x+a*q.y+ty
            // Diagnostic zone deliberately extends beyond existing beard mask:
            // orange pixels make clipped sideburns/neck candidates visible.
            val (map,stats)=HairMaskDiagnostics.render(
                original,width,height,
                editable={x,y -> maskPixels[y*width+x] != 0},
                normalized={x,y -> Pair(a*x+b*y+tx,-b*x+a*y+ty)},
                upperLip=mappedY(upper),chin=mappedY(chin),
                left=min(mappedX(p(234)),mappedX(p(61))),
                right=max(mappedX(p(454)),mappedX(p(291)))
            )
            android.util.Log.i("KeeperMaskV3",
                "candidate_inside=${stats.accepted}, outside=${stats.excluded}, editable=${stats.editable}")
            onDiagnostics(map,stats)
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
        val leftCleanSkin = Array(3) { mutableListOf<Float>() }
        val rightCleanSkin = Array(3) { mutableListOf<Float>() }
        val globalCleanSkin = Array(3) { mutableListOf<Float>() }
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
            val onLeft=a*x+b*y+tx < 128
            val side = if (onLeft) leftOffsets else rightOffsets
            val reliablySkin = engine == RetouchEngine.SMART_V4 &&
                (learnedBeard?.at(x,y) ?: 1f)<.12f &&
                (jawGuard?.weightAt(x,y) ?: 0f)>.98f
            val skinSide = if(onLeft) leftCleanSkin else rightCleanSkin
            for (c in 0..2) {
                val difference = first[c]-second[c]
                offsets[c].add(difference)
                side[c].add(difference)
                if(reliablySkin) {
                    globalCleanSkin[c].add(first[c])
                    skinSide[c].add(first[c])
                }
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
        fun medianSkin(values: Array<MutableList<Float>>): FloatArray? =
            if(values[0].size >= 24) FloatArray(3) { c ->
                values[c].sorted().let { (it[(it.size-1)/2]+it[it.size/2])/2f }
            } else null
        val allSkinReference=medianSkin(globalCleanSkin)
        val leftSkinReference=medianSkin(leftCleanSkin) ?: allSkinReference
        val rightSkinReference=medianSkin(rightCleanSkin) ?: allSkinReference
        if(engine == RetouchEngine.SMART_V4) {
            android.util.Log.i("KeeperSkinGuard",
                "clean skin donors left=${leftCleanSkin[0].size}, right=${rightCleanSkin[0].size}")
        }

        onProgress(RetouchStage.TEXTURE)
        // Meitu's APK identifies a dedicated beard category and supplies a face
        // ROI to its AIGC service. We keep the image completely offline and use
        // our own hair-versus-shadow confidence mask inside the existing,
        // anatomy-protected ROI. The single-pass baseline is untouched.
        val hairGuard = if (engine != RetouchEngine.BASELINE)
            FacialHairRegionGuard(original, width, height, span) else null
        // Face-aligned upper-lip ROI, independent of camera rotation and face
        // location. Face-parsing and the protected lip/nose polygons remain
        // mandatory; this never enlarges the editable anatomical mask.
        val mouthLeft = p(61); val mouthRight = p(291)
        val nose = p(2); val upperLip = p(13)
        fun alignedX(q: PointF) = a*q.x+b*q.y+tx
        fun alignedY(q: PointF) = -b*q.x+a*q.y+ty
        val mouthU0 = min(alignedX(mouthLeft),alignedX(mouthRight))
        val mouthU1 = max(alignedX(mouthLeft),alignedX(mouthRight))
        val noseV = alignedY(nose)
        val lipV = alignedY(upperLip)
        fun inMoustache(x:Int,y:Int): Boolean {
            val u=a*x+b*y+tx
            val v=-b*x+a*y+ty
            val pad=(mouthU1-mouthU0)*.12f
            val top=min(noseV,lipV); val bottom=max(noseV,lipV)
            return u in (mouthU0-pad)..(mouthU1+pad) &&
                v in (top+2f)..(bottom-2f)
        }
        // V2.3: verified pure AttGAN removes more moustache than V2.1.
        // Use exactly its compositing coefficient in the middle of the
        // upper-lip ROI, fading smoothly into V2.1 around the boundary.
        // This is deliberately not another heuristic hair detector.
        fun baselineMoustacheWeight(x:Int,y:Int): Float {
            if (region == RetouchRegion.BEARD) return 0f
            val u=a*x+b*y+tx
            val v=-b*x+a*y+ty
            val mouthWidth=mouthU1-mouthU0
            val top=min(noseV,lipV)
            val bottom=max(noseV,lipV)
            if (mouthWidth <= 2f || bottom-top <= 3f) return 0f
            val pad=mouthWidth*.10f
            val l=mouthU0-pad
            val r=mouthU1+pad
            val verticalMargin=(bottom-top)*.16f
            val h=(bottom-top)
            // No changes in nose and actual lip; the native anatomical mask
            // continues to protect these regions independently.
            val wx=min((u-l)/(mouthWidth*.15f),(r-u)/(mouthWidth*.15f))
                .coerceIn(0f,1f)
            val wy=min((v-top)/max(1f,verticalMargin),
                (bottom-v)/max(1f,verticalMargin)).coerceIn(0f,1f)
            val t=(wx*wy).coerceIn(0f,1f)
            return t*t*(3f-2f*t)
        }
        val output = original.copyOf()
        for (i in output.indices) {
            if (distance[i] <= 0f) continue
            val x = i%width; val y = i/width
            val lab = rgbToLab(generatedAt(x,y,0).toInt().toFloat(),generatedAt(x,y,1).toInt().toFloat(),generatedAt(x,y,2).toInt().toFloat())
            val sideMix = ((a*x+b*y+tx-96)/64).coerceIn(0f,1f)
            val localOffset = FloatArray(3) { c -> if (unevenLight) leftOffset[c]*(1-sideMix)+rightOffset[c]*sideMix else offset[c] }
            val source = original[i]
            val sourceLab = rgbToLab(Color.red(source).toFloat(),Color.green(source).toFloat(),Color.blue(source).toFloat())
            val modelHairP=if(learnedBeard!=null && anatomy?.maskZone(x,y)==true)
                learnedBeard.at(x,y) else 0f
            val proposedLab=FloatArray(3) { c -> lab[c]+localOffset[c] }
            val reference = if(leftSkinReference != null && rightSkinReference != null)
                FloatArray(3) { c ->
                    leftSkinReference[c]*(1-sideMix)+rightSkinReference[c]*sideMix
                } else leftSkinReference ?: rightSkinReference
            val guarded=if(engine == RetouchEngine.SMART_V4)
                SkinColorContinuityGuard.harmonize(
                    sourceLab,proposedLab,reference,modelHairP,
                    jawGuard?.weightAt(x,y) ?: 1f)
            else SkinToneGuard.apply(sourceLab,proposedLab)
            val matched = labToRgb(guarded[0],guarded[1],guarded[2])
            val feather = (distance[i]/max(2f,span*.055f)).coerceIn(0f,1f)
            val label = if (hairGuard == null) 1 else labelAt(x,y)
            val confidence = when {
                hairGuard == null -> 1f
                engine == RetouchEngine.SMART_V4 -> {
                    val safe=hairGuard.confidence(x,y,label,source,matched)
                    val beardBoost=((modelHairP-.16f)/.36f).coerceIn(0f,1f)
                    val focus=baselineMoustacheWeight(x,y)
                    safe+(1f-safe)*max(beardBoost,focus)
                }
                (engine == RetouchEngine.HYBRID_BASELINE ||
                    engine == RetouchEngine.MASK_INSPECT) -> {
                    val safe=hairGuard.confidence(x,y,label,source,matched)
                    val focus=baselineMoustacheWeight(x,y)
                    safe+(1f-safe)*focus
                }
                engine == RetouchEngine.MOUSTACHE_FOCUS && inMoustache(x,y) ->
                    hairGuard.moustacheConfidence(x,y,label,source,matched)
                else -> hairGuard.confidence(x,y,label,source,matched)
            }
            // Learned-beard confidence alone must not modify the jaw contour.
            val alpha = feather*confidence*(jawGuard?.weightAt(x,y) ?: 1f)
            if (alpha <= 0f) continue
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
        // V2.1 is deliberately distinct from V2. Keep the smoothing/shadow
        // result intact, then repair only clear residual dark strands.
        val finalPixels = if (engine == RetouchEngine.RESIDUAL_REPAIR ||
            engine == RetouchEngine.MOUSTACHE_FOCUS ||
            engine == RetouchEngine.HYBRID_BASELINE ||
            engine == RetouchEngine.MASK_INSPECT ||
            engine == RetouchEngine.SMART_V4) {
            onProgress(RetouchStage.REFINE)
            val minInset = max(2f, span*.025f)
            val (refined, stats) = ResidualHairRepair.refine(
                original, output, width, height, span
            ) { x, y ->
                val i = y*width+x
                distance[i] >= minInset && maskPixels[i] != 0 &&
                    labelAt(x,y) == 1
            }
            android.util.Log.i("KeeperRetouch", "V2.1 residual candidates=${stats.candidates}, corrected=${stats.corrected}")
            if (engine == RetouchEngine.MOUSTACHE_FOCUS) {
                // Extra second-scale repair ONLY between nose and upper lip.
                // The beard/chin result stays exactly as V2.1 produced it.
                val (focused, detail) = ResidualHairRepair.refine(
                    original, refined, width, height, span, radiusScale=1.7f,
                    strengthScale=1.15f
                ) { x, y ->
                    val i=y*width+x
                    distance[i] >= minInset && maskPixels[i] != 0 &&
                        inMoustache(x,y) &&
                        (labelAt(x,y) == 1 || labelAt(x,y) == 17)
                }
                // Keep the accurate anatomical/face-aligned ROI for focus.
                android.util.Log.i("KeeperRetouch",
                    "V2.2 focus candidates=${detail.candidates}, corrected=${detail.corrected}")
                focused
            } else if(engine == RetouchEngine.SMART_V4 &&
                learnedBeard != null && learnedBeard.detections > 0) {
                val (reconstructed, repair)=NeuralHairReconstruction.refine(
                    original,refined,width,height,learnedBeard,
                    permitted={x,y ->
                        val i=y*width+x
                        maskPixels[i]!=0 && distance[i]>max(2f,span*.017f) &&
                            anatomy?.maskZone(x,y)==true &&
                            (jawGuard?.weightAt(x,y) ?: 1f) >= .95f &&
                            !protectedRegion.contains(x,y)
                    },
                    isNeck={x,y -> anatomy?.isNeck(x,y)==true}
                )
                android.util.Log.i("KeeperRetouchV4",
                    "reconstructed=${repair.pixels} donor_cells=${repair.filledCells} skipped_cells=${repair.skippedCells}")
                reconstructed
            } else refined
        } else output
        // Final, separate gate covers every postprocess, including residual
        // repair. It restores the ORIGINAL below the jaw and fades above it.
        if(jawGuard != null) {
            var fullyProtected=0
            var softened=0
            for(i in finalPixels.indices) {
                val after=finalPixels[i]
                if(after==original[i]) continue
                val f=jawGuard.weightAt(i%width,i/width)
                if(f<=.001f) {
                    finalPixels[i]=original[i]
                    fullyProtected++
                } else if(f<.999f) {
                    val before=original[i]
                    fun merge(a:Int,b:Int):Int =
                        (a*(1f-f)+b*f).roundToInt().coerceIn(0,255)
                    finalPixels[i]=Color.rgb(
                        merge(Color.red(before),Color.red(after)),
                        merge(Color.green(before),Color.green(after)),
                        merge(Color.blue(before),Color.blue(after)))
                    softened++
                }
            }
            android.util.Log.i("KeeperJawGuard",
                "restored original pixels=$fullyProtected, feathered=$softened")
        }
        // Optional offline quality experiment: run the *same licensed* AttGAN
        // a second time with the normalized face moved 25 model pixels upward.
        // This changes which facial-hair details receive model attention.
        // Blend ONLY the bounded delta between the two model predictions into
        // the original, already-approved baseline result. This cannot enlarge
        // the edit mask, alter the jaw silhouette or distort the output canvas.
        // Default is OFF: the trusted single-pass AttGAN remains unchanged.
        if (extraDetail && engine == RetouchEngine.BASELINE) {
            onProgress(RetouchStage.REFINE)
            val movedTy = ty - 25f
            val shiftedInput = FloatArray(PLANE*3)
            for (yy in 0 until SIZE) for (xx in 0 until SIZE) {
                val sx = (a*(xx-tx)-b*(yy-movedTy))/det
                val sy = (b*(xx-tx)+a*(yy-movedTy))/det
                for (c in 0..2) shiftedInput[c*PLANE+yy*SIZE+xx] =
                    sampleOriginal(original,width,height,sx,sy,c)
                        .roundToInt().coerceIn(0,255)/127.5f-1f
            }
            val shiftedGenerated = runModel(shiftedInput)
            val refinementJawGuard = JawlineIntegrityGuard.fromLandmarks(
                face,width,height,a,b,tx,ty
            )
            val noseY = p(2).y
            val chinY = p(152).y
            val scanTop = max(0, (noseY-span*.1f).toInt())
            val scanBottom = min(height-1, (chinY+span*.08f).toInt())
            val scanLeft = max(0,(min(p(234).x,p(454).x)-span*.1f).toInt())
            val scanRight = min(width-1,(max(p(234).x,p(454).x)+span*.1f).toInt())
            var corrected=0
            val baselineForDetail=finalPixels.copyOf()
            val textureRadius=(span*.04f).toInt().coerceIn(3,12)
            for (yy in scanTop..scanBottom) for (xx in scanLeft..scanRight) {
                val i=yy*width+xx
                if (maskPixels[i]==Color.TRANSPARENT || distance[i]<=0f) continue
                val jawWeight=refinementJawGuard.weightAt(xx,yy)
                if (jawWeight<.995f) continue
                val feather=(distance[i]/max(2f,span*.055f)).coerceIn(0f,1f)
                // The second model may shift RGB hues independently,
                // creating unnatural skin seams. Convert its normalized
                // ONNX output into a SMALL, mostly luminance correction.
                // Keep original AttGAN chroma, texture and face geometry.
                val hairWeight=ResidualHairDetailGate.weight(
                    baselineForDetail,width,height,xx,yy,textureRadius)
                val weight=feather*jawWeight*extraDetailStrength.coerceIn(0f,.65f)*hairWeight
                if (weight<=0f) continue
                val u=a*xx+b*yy+tx
                val v=-b*xx+a*yy+ty
                val current=finalPixels[i]
                val updated=AttganDetailBlend.balancedPixel(
                    current,
                    samplePlane(generated,u,v,0),
                    samplePlane(generated,u,v,1),
                    samplePlane(generated,u,v,2),
                    samplePlane(shiftedGenerated,u,v-25f,0),
                    samplePlane(shiftedGenerated,u,v-25f,1),
                    samplePlane(shiftedGenerated,u,v-25f,2),weight
                )
                if (updated!=current) { finalPixels[i]=updated;corrected++ }
            }
            android.util.Log.i("KeeperOfflineDetail","two-view correction pixels=$corrected")
        }
        onProgress(RetouchStage.VERIFY)
        check(finalPixels.indices.all { Color.alpha(maskPixels[it]) != 0 || finalPixels[it] == original[it] })
        Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply { setPixels(finalPixels,0,width,0,0,width,height) }
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
