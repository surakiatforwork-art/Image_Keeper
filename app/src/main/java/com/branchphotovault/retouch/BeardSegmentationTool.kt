package com.branchphotovault.retouch

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * On-device binary BEARD model, trained from independently sourced polygon
 * annotations on a 3.2M-parameter ImageNet-pretrained MobileNetV3 LR-ASPP.
 * It is a genuine learned segmentation model, NOT Meitu's proprietary model.
 * Research deployment only pending source-image/dataset license review.
 */
internal class BeardSegmentationTool(private val context: Context) {
    data class BeardMask(
        val left: Float, val top: Float, val side: Float,
        val probability: FloatArray, val detections: Int, val strongestScore: Float
    ) {
        init { require(probability.size == INPUT_SIDE * INPUT_SIDE) }

        fun at(x: Int, y: Int): Float {
            val u = (x-left)/side*INPUT_SIDE-.5f
            val v = (y-top)/side*INPUT_SIDE-.5f
            if(u<0f || v<0f || u>=INPUT_SIDE-1f || v>=INPUT_SIDE-1f) return 0f
            val x0=u.toInt(); val y0=v.toInt()
            val fx=u-x0; val fy=v-y0
            val i=y0*INPUT_SIDE+x0
            return ((probability[i]*(1f-fx)+probability[i+1]*fx)*(1f-fy)+
                (probability[i+INPUT_SIDE]*(1f-fx)+probability[i+INPUT_SIDE+1]*fx)*fy)
                .coerceIn(0f,1f)
        }

        fun coverage(threshold:Float=.22f)=probability.count {it>threshold}
    }

    fun analyse(image:Bitmap,face:List<NormalizedLandmark>):BeardMask {
        require(face.size > 454) { "Face Landmarks เนเธกเนเธเธฃเธ" }
        val iw=image.width.toFloat()
        val ih=image.height.toFloat()
        fun px(i:Int)=face[i].x()*iw
        fun py(i:Int)=face[i].y()*ih
        val faceWidth=max(abs(px(454)-px(234)),abs(px(291)-px(61))*1.95f)
        val topFace=min(py(10),min(py(9),py(151)))
        val chin=py(152)
        val faceHeight=abs(chin-topFace)
        val side=max(faceWidth*1.34f,faceHeight*1.53f).coerceAtLeast(64f)
        val cx=(px(234)+px(454))*.5f
        val cy=(topFace+chin)*.5f+faceHeight*.13f
        val left=cx-side*.5f
        val top=cy-side*.5f

        val crop=Bitmap.createBitmap(INPUT_SIDE,INPUT_SIDE,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(crop)
            canvas.drawColor(Color.rgb(127,127,127))
            val scale=INPUT_SIDE/side
            val matrix=Matrix().apply {
                setScale(scale,scale)
                postTranslate(-left*scale,-top*scale)
            }
            canvas.drawBitmap(image,matrix,Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels=IntArray(INPUT_SIDE*INPUT_SIDE)
            crop.getPixels(pixels,0,INPUT_SIDE,0,0,INPUT_SIDE,INPUT_SIDE)
            val input=FloatArray(pixels.size*3)
            val mean=floatArrayOf(.485f,.456f,.406f)
            val std=floatArrayOf(.229f,.224f,.225f)
            for(i in pixels.indices) {
                input[i]=(Color.red(pixels[i])/255f-mean[0])/std[0]
                input[i+pixels.size]=(Color.green(pixels[i])/255f-mean[1])/std[1]
                input[i+pixels.size*2]=(Color.blue(pixels[i])/255f-mean[2])/std[2]
            }
            val file=prepareModel()
            val env=OrtEnvironment.getEnvironment()
            return OrtSession.SessionOptions().use {options->
                options.setIntraOpNumThreads(3)
                options.setInterOpNumThreads(1)
                env.createSession(file.absolutePath,options).use {session->
                    OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(
                        1,3,INPUT_SIDE.toLong(),INPUT_SIDE.toLong())).use {tensor->
                        session.run(mapOf("input" to tensor)).use {result->
                            val buffer=(result[0] as OnnxTensor).floatBuffer
                            require(buffer.remaining()==INPUT_SIDE*INPUT_SIDE){
                                "เธเธฅเธฅเธฑเธเธเนเนเธกเน€เธ”เธฅเธ•เธฃเธงเธเธเธฑเธเธซเธเธงเธ”เนเธกเนเธชเธกเธเธนเธฃเธ“เน"
                            }
                            val probabilities=FloatArray(INPUT_SIDE*INPUT_SIDE)
                            var topScore=0f
                            for(i in probabilities.indices) {
                                val logit=buffer.get()
                                val p=1f/(1f+exp(-logit.coerceIn(-30f,30f)))
                                probabilities[i]=p.coerceIn(0f,1f)
                                topScore=max(topScore,probabilities[i])
                            }
                            // Small feather for hairs around the segmented border.
                            val expanded=probabilities.copyOf()
                            for(y in 1 until INPUT_SIDE-1)for(x in 1 until INPUT_SIDE-1) {
                                val i=y*INPUT_SIDE+x
                                var n=0f
                                for(dy in -1..1)for(dx in -1..1)
                                    n=max(n,probabilities[i+dy*INPUT_SIDE+dx])
                                expanded[i]=max(probabilities[i],n*.78f)
                            }
                            val count=expanded.count { it>.40f }
                            BeardMask(left,top,side,expanded,
                                if(count>=30)1 else 0,topScore)
                        }
                    }
                }
            }
        } finally {crop.recycle()}
    }

    private fun prepareModel(): File {
        val file=File(context.cacheDir,"beard-mobile-224-v1.onnx")
        if(file.length()!=MODEL_SIZE) {
            val temp=File(context.cacheDir,"beard-mobile-copy.tmp")
            try {
                context.assets.open("retouch/beard-mobile-224.onnx").use {input->
                    temp.outputStream().use {output->input.copyTo(output)}
                }
                require(temp.length()==MODEL_SIZE) { "เนเธเธฅเนเนเธกเน€เธ”เธฅเธ•เธฃเธงเธเธเธฑเธเธซเธเธงเธ”เนเธกเนเธชเธกเธเธนเธฃเธ“เน" }
                if(file.exists())file.delete()
                check(temp.renameTo(file)) { "เน€เธ•เธฃเธตเธขเธกเนเธกเน€เธ”เธฅเธ•เธฃเธงเธเธเธฑเธเธซเธเธงเธ”เนเธกเนเธชเธณเน€เธฃเนเธ" }
            } finally {temp.delete()}
        }
        return file
    }

    companion object {
        private const val INPUT_SIDE=224
        const val MODEL_SIZE=12867356L
    }
}
