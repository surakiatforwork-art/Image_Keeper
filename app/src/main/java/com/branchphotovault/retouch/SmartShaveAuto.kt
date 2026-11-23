package com.branchphotovault.retouch

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.CancellationException

/**
 * One action, one predictable offline recipe. No experimental switches in UI.
 *
 * Primary: 1x V6.7 safe moustache removal -> Chin Student 03 with the optional
 * V6.8 source-contour compositor ON. This preserves the proven model weights.
 * On-device fallback: V6.6 beard model -> prior V6 result if an optional
 * checkpoint fails. Never saves/overwrites the source photo.
 */
internal object SmartShaveAuto {
    internal enum class BeardStage {
        CHIN_STUDENT, FACEBENCH_FALLBACK, V6_ONLY, UNAVAILABLE
    }
    internal data class Outcome(
        val image:Bitmap,
        val moustacheApplied:Boolean,
        val beardStage:BeardStage
    ) {
        val summary:String get()=when(beardStage) {
            BeardStage.CHIN_STUDENT ->
                if(moustacheApplied)
                    "AI ลบหนวดและเก็บเคราแล้ว · ตรวจขอบปากและรูปคางก่อนบันทึก"
                else "AI เก็บเคราแล้ว แต่ลบหนวดไม่ครบ · ตรวจภาพก่อนบันทึก"
            BeardStage.FACEBENCH_FALLBACK ->
                if(moustacheApplied)
                    "AI ลบหนวดและเก็บเคราด้วยระบบสำรองแล้ว · ตรวจคางก่อนบันทึก"
                else "AI เก็บเคราด้วยระบบสำรองแล้ว แต่ลบหนวดไม่ครบ"
            BeardStage.V6_ONLY ->
                "AI เก็บหนวดและเคราด้วย V6 แล้ว · ตรวจคางก่อนบันทึก"
            BeardStage.UNAVAILABLE ->
                if(moustacheApplied) "AI ลบหนวดแล้ว แต่ยังเก็บเคราไม่ได้ · ตรวจภาพก่อนบันทึก"
                else "โมเดล AI ไม่พร้อมใช้งาน · ภาพต้นฉบับยังอยู่ครบ"
        }
    }

    fun run(
        context:Context,
        source:Bitmap,
        faceIndex:Int,
        face:List<NormalizedLandmark>,
        onStatus:(String)->Unit={}
    ):Outcome {
        require(face.size>454 && !source.isRecycled)
        fun asset(name:String)=
            context.assets.list("retouch")?.contains(name)==true
        val hasV6=asset("beard-mobile-224.onnx")
        val hasStudent=asset(ChinStudent.asset)
        val hasFacebench=asset("facebench_no_beard_fp32.onnx")
        val tool=MoustacheRetoucher(context)
        var moustache:Bitmap=source
        var moustacheApplied=false
        try {
            onStatus("กำลังลบหนวดรอบริมฝีปากด้วย AI…")
            try {
                moustache=if(hasV6) {
                    tool.processV6Passes(
                        original=source,faceIndex=faceIndex,
                        region=RetouchRegion.MOUSTACHE,
                        v6CoverageStrength=.84f,passes=1,
                        v67Refinement=true,originalFace=face,
                        onProgress={stage->onStatus("AI ลบหนวด · "+stage.message)}
                    ).image
                } else {
                    tool.process(
                        source,faceIndex,RetouchRegion.MOUSTACHE,
                        RetouchEngine.BASELINE,fixedFace=face,
                        onProgress={stage->onStatus("AI ลบหนวด · "+stage.message)}
                    )
                }
                moustacheApplied=true
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) {
                // The chin checkpoint can still run when baseline moustache
                // generation fails. Never expose a partially corrupted image.
                Log.w("KeeperOneTap","Moustache stage unavailable",error)
                onStatus("กำลังใช้ AI เก็บเคราโดยตรง…")
            }
            if(hasStudent) {
                try {
                    onStatus("กำลังสร้างผิวคางและเกลี่ยแนวกรามด้วย AI…")
                    val edited=ChinStudent.apply(
                        context,source,moustache,face,naturalContour=true
                    )
                    if(moustache !== source && edited !== moustache)
                        moustache.recycle()
                    Log.i("KeeperOneTap",
                        "result=chin-student03 + contour-v68 moustache="+moustacheApplied)
                    return Outcome(edited,moustacheApplied,BeardStage.CHIN_STUDENT)
                } catch(cancelled:CancellationException) { throw cancelled }
                catch(error:Exception) {
                    Log.w("KeeperOneTap","Chin Student failed; trying safe fallback",error)
                }
            }
            if(hasV6 && hasFacebench) {
                try {
                    onStatus("กำลังเก็บเคราด้วย AI ระบบสำรอง…")
                    val edited=BeardReconstructionV66(context).reconstruct(
                        source,moustache,face,strength=.90f,refineV67=true
                    ).first
                    if(moustache !== source && edited !== moustache)
                        moustache.recycle()
                    Log.i("KeeperOneTap","result=facebench-fallback")
                    return Outcome(edited,moustacheApplied,BeardStage.FACEBENCH_FALLBACK)
                } catch(cancelled:CancellationException) { throw cancelled }
                catch(error:Exception) {
                    Log.w("KeeperOneTap","Beard fallback failed",error)
                }
            }
            // If the specialist model is missing, a bounded whole-face V6
            // pipeline is better than silently claiming a beard-free result.
            if(hasV6 && moustacheApplied) {
                try {
                    onStatus("กำลังเก็บเคราด้วยระบบ V6…")
                    val edited=tool.processV6Passes(
                        source,faceIndex,RetouchRegion.BOTH,.84f,
                        passes=1,v67Refinement=true,originalFace=face
                    ).image
                    if(moustache !== source && edited !== moustache)
                        moustache.recycle()
                    return Outcome(edited,true,BeardStage.V6_ONLY)
                } catch(cancelled:CancellationException) { throw cancelled }
                catch(error:Exception) { Log.w("KeeperOneTap","V6 fallback failed",error) }
            }
            if(moustacheApplied) {
                val retained=moustache
                moustache=source // ownership passes to caller; do not recycle.
                return Outcome(retained,true,BeardStage.UNAVAILABLE)
            }
            error("AI ไม่สามารถประมวลผลหนวดหรือเคราได้ โปรดลองอีกครั้ง")
        } catch(error:Throwable) {
            if(moustache !== source && !moustache.isRecycled)
                moustache.recycle()
            throw error
        }
    }
}
