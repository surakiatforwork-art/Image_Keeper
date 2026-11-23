package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log

/** The first-pass Bitmap is retained if the second pass cannot complete. */
internal data class V6PassOutcome(val image:Bitmap,val completedPasses:Int)

/**
 * V6 on-device two-pass: ORIGINAL -> V6 -> V6.
 * Every pass uses the same original face landmarks (NOT an altered landmark
 * detection on already generated skin), but uses the preceding pass pixels
 * as the NEW image for beard segmentation, AttGAN and compositing.
 *
 * The final lip/nose and jaw hard guards anchor to original photo pixels.
 * Does not read/write network, gallery files, or app database.
 */
internal fun MoustacheRetoucher.processV6Passes(
    original:Bitmap,
    faceIndex:Int,
    region:RetouchRegion,
    v6CoverageStrength:Float,
    passes:Int=2,
    v67Refinement:Boolean=false,
    originalFace: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>? = null,
    onRound: (Int)->Unit = {},
    onProgress: (RetouchStage)->Unit = {}
):V6PassOutcome {
    require(passes in 1..2)
    // The dialog has already detected landmarks; reuse them for every pass.
    // Avoid repeated MediaPipe initialization and any identity/order drift.
    val selected=originalFace ?: faces(original).getOrNull(faceIndex)
        ?: error("ไม่พบใบหน้าที่เลือกในต้นฉบับ")
    var sharedParsing: FaceParsingResult? = null
    onRound(1)
    val first=process(
        original,faceIndex,region,RetouchEngine.V6_OFFLINE,
        v6CoverageStrength=v6CoverageStrength,
        v67Refinement=v67Refinement,
        fixedFace=selected,
        onParsingReady={sharedParsing=it},
        onProgress=onProgress
    )
    if(passes==1) return V6PassOutcome(first,1)
    return try {
        onRound(2)
        val second=process(
            first,faceIndex,region,RetouchEngine.V6_OFFLINE,
            v6CoverageStrength=v6CoverageStrength,
            v67Refinement=v67Refinement,
            fixedFace=selected,
            fixedParsing=sharedParsing,
            onProgress=onProgress
        )
        try {
            val w=original.width
            val h=original.height
            val source=IntArray(w*h)
            val final=IntArray(w*h)
            original.getPixels(source,0,w,0,0,w,h)
            second.getPixels(final,0,w,0,0,w,h)
            val lips=LipSafeRasterGuard.fromFace(selected,w,h)
            val lipRestored=lips.protectOriginal(source,final)
            // Enforce original face silhouette (not the inferred second).
            fun p(i:Int)=PointF(selected[i].x()*w,selected[i].y()*h)
            val left=PointF((p(33).x+p(133).x)*.5f,(p(33).y+p(133).y)*.5f)
            val right=PointF((p(362).x+p(263).x)*.5f,(p(362).y+p(263).y)*.5f)
            val dx=right.x-left.x;val dy=right.y-left.y
            val norm=dx*dx+dy*dy
            require(norm>1f)
            val a=dx*64f/norm;val b=dy*64f/norm
            val cx=(left.x+right.x)*.5f
            val cy=(left.y+right.y)*.5f
            val tx=128f-a*cx-b*cy
            val ty=115.2f+b*cx-a*cy
            val jaw=JawlineIntegrityGuard.fromLandmarks(selected,w,h,a,b,tx,ty)
            var jawRestored=0
            for(i in final.indices) {
                if(final[i]!=source[i] && jaw.weightAt(i%w,i/w)<=.001f) {
                    final[i]=source[i]
                    jawRestored++
                }
            }
            second.setPixels(final,0,w,0,0,w,h)
            Log.i("KeeperV63TwoPass",
                "passes=2 original_lip_restored=$lipRestored original_jaw_restored=$jawRestored")
        } catch(err:Exception) {
            second.recycle()
            throw err
        }
        first.recycle()
        V6PassOutcome(second,2)
    } catch(err:Exception) {
        Log.w("KeeperV63TwoPass","Second pass failed; retain first-pass output",err)
        V6PassOutcome(first,1)
    }
}
