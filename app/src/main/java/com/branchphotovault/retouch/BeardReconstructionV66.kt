package com.branchphotovault.retouch

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PRIVATE V6.6: specialized No_Beard- model + source-anchored beard compositor.
 *
 * Model license: RESEARCH ONLY (FaceBench students); do NOT publicly release.
 * All computations happen entirely offline on the Android CPU.
 */
internal class BeardReconstructionV66(private val context:Context) {
    data class Stats(
        val modelMs:Long, val candidatePixels:Int, val editedPixels:Int,
        val jawProtected:Int, val lipProtected:Int, val pssMb:Long
    )
    companion object {
        private const val CW=208
        private const val CH=256
        private const val PLANE=CW*CH
        const val MODEL_BYTES=117887838L
        fun smooth01(x:Float):Float {
            val v=x.coerceIn(0f,1f)
            return v*v*(3f-2f*v)
        }
    }
    private fun modelFile():File {
        val destination=File(context.cacheDir,"v66-facebench-no-beard-117887838.onnx")
        if(destination.length()==MODEL_BYTES) return destination
        val temp=File(context.cacheDir,"v66-facebench-copy.tmp")
        try {
            context.assets.open("retouch/facebench_no_beard_fp32.onnx").use {src->
                temp.outputStream().use {dst->src.copyTo(dst)}
            }
            check(temp.length()==MODEL_BYTES) {"โมเดล AI ลบเคราไม่สมบูรณ์"}
            if(destination.exists())destination.delete()
            check(temp.renameTo(destination)) {"เตรียมโมเดล AI ลบเคราไม่สำเร็จ"}
        }finally{temp.delete()}
        return destination
    }

    private data class Align(val a:Float,val b:Float,val tx:Float,val ty:Float) {
        fun u(x:Float,y:Float)=a*x+b*y+tx
        fun v(x:Float,y:Float)=-b*x+a*y+ty
        fun ix(u:Float,v:Float)=(a*(u-tx)-b*(v-ty))/(a*a+b*b)
        fun iy(u:Float,v:Float)=(b*(u-tx)+a*(v-ty))/(a*a+b*b)
    }
    private fun align(face:List<NormalizedLandmark>,w:Int,h:Int):Align {
        fun p(i:Int)=PointF(face[i].x()*w,face[i].y()*h)
        fun mid(p:PointF,q:PointF)=PointF((p.x+q.x)*.5f,(p.y+q.y)*.5f)
        val eL=mid(p(33),p(133));val eR=mid(p(362),p(263))
        val ctr=mid(eL,eR)
        val dx=eR.x-eL.x;val dy=eR.y-eL.y
        val span=hypot(dx,dy)
        require(span>12f){"ใบหน้าเล็กเกินไป"}
        val chin=p(152)
        val chinDepth=(-dy*(chin.x-ctr.x)+dx*(chin.y-ctr.y))/span
        require(chinDepth>0f)
        // Center the EYES at (104,92) while keeping CHIN 152 in viewport; validated on dense-beard fixtures.
        // We do not mistake LOWER LIP 17 for the bottom of the real chin.
        val scale=min(48f/span,138f/chinDepth)
        val a=dx/span*scale;val b=dy/span*scale
        val output=Align(a,b,104f-a*ctr.x-b*ctr.y,92f+b*ctr.x-a*ctr.y)
        check(output.v(chin.x,chin.y)<=232f)
        return output
    }

    private fun color(p:Int,c:Int):Int=when(c) {
        0->Color.red(p)
        1->Color.green(p)
        else->Color.blue(p)
    }
    private fun samplePixels(px:IntArray,w:Int,h:Int,x:Float,y:Float,c:Int):Float {
        val ix=x.toInt().coerceIn(0,w-1);val iy=y.toInt().coerceIn(0,h-1)
        val nx=(ix+1).coerceAtMost(w-1);val ny=(iy+1).coerceAtMost(h-1)
        val fx=(x-ix).coerceIn(0f,1f);val fy=(y-iy).coerceIn(0f,1f)
        val top=color(px[iy*w+ix],c)*(1f-fx)+color(px[iy*w+nx],c)*fx
        val bot=color(px[ny*w+ix],c)*(1f-fx)+color(px[ny*w+nx],c)*fx
        return top*(1f-fy)+bot*fy
    }
    private fun sampleOutput(px:FloatArray,u:Float,v:Float,c:Int):Float {
        val x=u.coerceIn(0f,(CW-1).toFloat())
        val y=v.coerceIn(0f,(CH-1).toFloat())
        val x0=x.toInt();val y0=y.toInt()
        val x1=(x0+1).coerceAtMost(CW-1)
        val y1=(y0+1).coerceAtMost(CH-1)
        val fx=x-x0;val fy=y-y0;val base=c*PLANE
        val top=px[base+y0*CW+x0]*(1f-fx)+px[base+y0*CW+x1]*fx
        val bot=px[base+y1*CW+x0]*(1f-fx)+px[base+y1*CW+x1]*fx
        return (top*(1f-fy)+bot*fy+1f)*127.5f
    }
    fun reconstruct(
        original:Bitmap, prepared:Bitmap, face:List<NormalizedLandmark>,
        strength:Float=.96f,
        refineV67:Boolean=false,
        strictContourDiagnostic:Boolean=false
    ):Pair<Bitmap,Stats> {
        require(original.width==prepared.width && original.height==prepared.height)
        require(face.size>454)
        val w=original.width;val h=original.height
        val src=IntArray(w*h);val dst=IntArray(w*h)
        original.getPixels(src,0,w,0,0,w,h)
        prepared.getPixels(dst,0,w,0,0,w,h)
        val t=align(face,w,h)
        val modelInput=FloatArray(PLANE*3)
        for(y in 0 until CH)for(x in 0 until CW) {
            val sx=t.ix(x.toFloat(),y.toFloat())
            val sy=t.iy(x.toFloat(),y.toFloat())
            for(c in 0..2) modelInput[c*PLANE+y*CW+x]=
                samplePixels(dst,w,h,sx,sy,c)/127.5f-1f
        }
        val beard=BeardSegmentationTool(context).analyse(original,face)
        if(beard.detections==0) return prepared to
            Stats(0,0,0,0,0,Debug.getPss()/1024)
        val lips=LipSafeRasterGuard.fromFace(face,w,h)
        val sourceContour=if(refineV67) OriginalFaceContourGuard.fromFace(face,w,h) else null
        val contour=if(strictContourDiagnostic) sourceContour else null
        val residualBeard=if(refineV67 && prepared!==original) BeardSegmentationTool(context).analyse(prepared,face) else beard
        val softBeard=BeardFeatherMask(beard,preserveSourceBoundary=refineV67)
        fun p(i:Int)=PointF(face[i].x()*w,face[i].y()*h)
        val l=p(234);val r=p(454)
        val mouthW=hypot(p(61).x-p(291).x,p(61).y-p(291).y)
        // Thick beard can hang BELOW MediaPipe's 152 chin marker.
        // Do not use the lower lip or jaw contour as a hard stop for beard.
        // Facial silhouette must be inferred by the specialist clean-shave
        // model within a tight original beard mask. Outside it is unchanged.
        val minX=max(0,(min(l.x,r.x)-mouthW*.42f).toInt())
        val maxX=min(w-1,(max(l.x,r.x)+mouthW*.42f).toInt())
        val minY=max(0,(min(p(13).y,p(17).y)-mouthW*.34f).toInt())
        val eyeY=(p(33).y+p(133).y+p(362).y+p(263).y)*.25f
        val maxY=min(h-1,(p(152).y+
            max(mouthW*.85f,abs(p(152).y-eyeY)*.40f)).toInt())
        val lipTopV=t.v(p(13).x,p(13).y)
        val chinV=t.v(p(152).x,p(152).y)
        val scaledMouth=mouthW*hypot(t.a,t.b)
        val lowerLipV=t.v(p(17).x,p(17).y)
        val scanTopV=if(strictContourDiagnostic) lowerLipV else lipTopV-scaledMouth*.34f
        val scanBottomV=chinV+max(scaledMouth*.9f,
            abs(chinV-t.v(p(33).x,p(33).y))*.40f)

        val output=FloatArray(PLANE*3)
        val started=SystemClock.elapsedRealtime()
        val env=OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use {options->
            options.setIntraOpNumThreads(3)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            env.createSession(modelFile().absolutePath,options).use {session->
                OnnxTensor.createTensor(
                    env,FloatBuffer.wrap(modelInput),longArrayOf(1,3,CH.toLong(),CW.toLong())
                ).use {tensor->
                    session.run(mapOf("image" to tensor)).use {result->
                        val buffer=(result[0] as OnnxTensor).floatBuffer
                        check(buffer.remaining()==output.size) {
                            "โมเดลผิวเคราคืนข้อมูลไม่ครบ"
                        }
                        buffer.get(output)
                        check(output.all { it.isFinite() }) { "โมเดลผิวเคราส่งค่าที่ไม่ถูกต้อง" }
                    }
                }
            }
        }
        val ms=SystemClock.elapsedRealtime()-started

        // Match RGB in clean reference cheek patches; never sample hair
        // as the donor for an inferred clean skin colour.
        val references=Array(3){mutableListOf<Float>()}
        for(anchor in listOf(50,280,205,425)){
            val q=p(anchor)
            for(yy in -3..3)for(xx in -3..3) {
                val x=q.x.roundToInt()+xx;val y=q.y.roundToInt()+yy
                if(x !in 0 until w || y !in 0 until h ||
                    beard.at(x,y)>.18f)continue
                val u=t.u(x.toFloat(),y.toFloat());val v=t.v(x.toFloat(),y.toFloat())
                if(u !in 2f..205f || v !in 2f..253f)continue
                val index=y*w+x
                for(c in 0..2) references[c].add(
                    (color(dst[index],c)-sampleOutput(output,u,v,c)).coerceIn(-40f,40f)
                )
            }
        }
        val rgbOffset=FloatArray(3){c->
            val sorted=references[c].sorted()
            if(sorted.size>=5) sorted[sorted.size/2].coerceIn(-27f,27f)
            else 0f
        }

        // Use visible, low-hair-confidence original neck skin to match the neck's lighting.
        // This changes a bounded color field, never copies original beard pixels back in.
        val neckReferences=Array(3) { mutableListOf<Float>() }
        if(refineV67) {
            val cheekColors=Array(3) { mutableListOf<Int>() }
            for(anchor in listOf(50,280,205,425)) {
                val q=p(anchor);val x=q.x.toInt().coerceIn(0,w-1);val y=q.y.toInt().coerceIn(0,h-1)
                if(beard.at(x,y)<.18f) for(c in 0..2) cheekColors[c].add(color(src[y*w+x],c))
            }
            val cheek=FloatArray(3) { c -> cheekColors[c].takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f }
            val total=cheek.sum()
            if(total>30f) {
                val left=max(0,(p(152).x-mouthW*.5f).toInt())
                val right=min(w-1,(p(152).x+mouthW*.5f).toInt())
                val top=max(0,(p(152).y+mouthW*.12f).toInt())
                val bottom=min(h-1,(p(152).y+mouthW*.8f).toInt())
                for(y in top..bottom step 2) for(x in left..right step 2) {
                    if(beard.at(x,y)>.08f) continue
                    val before=src[y*w+x]
                    val sum=(color(before,0)+color(before,1)+color(before,2)).toFloat()
                    if(sum<105f || sum>690f) continue
                    var chroma=0f
                    for(c in 0..2) { val d=color(before,c)/sum-cheek[c]/total;chroma+=d*d }
                    if(chroma>.004f) continue
                    val u=t.u(x.toFloat(),y.toFloat());val v=t.v(x.toFloat(),y.toFloat())
                    if(u !in 2f..205f || v !in 2f..253f) continue
                    for(c in 0..2) neckReferences[c].add((color(before,c)-sampleOutput(output,u,v,c)).coerceIn(-35f,35f))
                }
            }
        }
        val neckOffset=FloatArray(3) { c -> if(neckReferences[c].size>=8)
            neckReferences[c].sorted()[neckReferences[c].size/2].coerceIn(rgbOffset[c]-20f,rgbOffset[c]+20f) else rgbOffset[c] }

        var candidate=0;var changed=0;var outsideBeard=0
        for(y in minY..maxY)for(x in minX..maxX) {
            if(lips.at(x,y))continue
            val contourWeight=contour?.weightAt(x,y) ?: 1f
            if(contourWeight<=0f) continue
            val u=t.u(x.toFloat(),y.toFloat())
            val v=t.v(x.toFloat(),y.toFloat())
            if(v<scanTopV || v>scanBottomV ||
                u<2f || u>(CW-3).toFloat() ||
                v<2f || v>(CH-3).toFloat())continue
            val hair=softBeard.at(x,y)
            if(hair<=.005f){outsideBeard++;continue}
            // Smooth in ONLY near the very top, not a horizontal cut through
            // the middle of the moustache as in the original V6.6 patch.
            // Above the lower lip, refine only where the prepared image still contains beard.
            var upperBlend=smooth01((v-scanTopV)/max(1.5f,scaledMouth*.27f))
            if(refineV67 && !strictContourDiagnostic && v<lowerLipV) {
                val remaining=smooth01((residualBeard.at(x,y)-.25f)/.40f)
                upperBlend*=remaining
            }
            val opacity=hair*upperBlend*strength.coerceIn(0f,1f)*contourWeight
            if(opacity<.01f)continue
            val index=y*w+x;val before=dst[index]
            val neckMix=sourceContour?.signedDistance(x.toFloat(),y.toFloat())?.let {
                smooth01(-it/max(2f,mouthW*.25f)) } ?: 0f
            val generated=FloatArray(3){c->
                val offset=rgbOffset[c]*(1-neckMix)+neckOffset[c]*neckMix
                (sampleOutput(output,u,v,c)+offset).coerceIn(0f,255f)
            }
            val luma=generated[0]*.299f+generated[1]*.587f+
                generated[2]*.114f
            if(luma !in 35f..235f)continue
            candidate++
            fun merged(c:Int):Int {
                val current=color(before,c)
                return (current+(generated[c]-current)
                    .coerceIn(-110f,150f)*opacity).roundToInt().coerceIn(0,255)
            }
            val after=Color.rgb(merged(0),merged(1),merged(2))
            if(after!=before){dst[index]=after;changed++}
        }
        val restored=lips.protectOriginal(src,dst)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(dst,0,w,0,0,w,h)
        val stats=Stats(ms,candidate,changed,outsideBeard,restored,Debug.getPss()/1024)
        Log.i("KeeperV66Beard",
            "model_ms="+ms+" candidates="+candidate+" changed="+changed+
            " source_outside_beard="+outsideBeard+
            " matte_area="+softBeard.area()+" lip_guard="+restored+
            " pss_mb="+stats.pssMb+
            " rgb_offset="+rgbOffset.joinToString(","))
        return bitmap to stats
    }
}
