package com.branchphotovault.retouch

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.floor

/** Secondary offline model: anatomical labels, not a facial-hair detector. */
internal class FaceParsingTool(private val context: Context) {
    fun analyse(alignedRgb: FloatArray): FaceParsingResult {
        require(alignedRgb.size == 3*256*256)
        val mean = floatArrayOf(.485f,.456f,.406f)
        val std = floatArrayOf(.229f,.224f,.225f)
        val input = FloatArray(3*512*512)
        for (y in 0 until 512) for (x in 0 until 512) {
            val sx = (x+.5f)/2-.5f; val sy = (y+.5f)/2-.5f
            val x0 = floor(sx).toInt(); val y0 = floor(sy).toInt()
            val fx = sx-x0; val fy = sy-y0
            for (c in 0..2) {
                fun v(xx: Int, yy: Int) = alignedRgb[c*256*256+yy.coerceIn(0,255)*256+xx.coerceIn(0,255)]
                val value = (v(x0,y0)*(1-fx)+v(x0+1,y0)*fx)*(1-fy)+(v(x0,y0+1)*(1-fx)+v(x0+1,y0+1)*fx)*fy
                input[c*512*512+y*512+x] = ((value+1)*.5f-mean[c])/std[c]
            }
        }
        val file = File(context.cacheDir,"face-parser-f68ce0be6439.onnx")
        if (file.length() != 26623985L) {
            val temporary = File(context.cacheDir,"face-parser-copy.tmp")
            try {
                context.assets.open("retouch/face_parser.onnx").use { source -> temporary.outputStream().use { source.copyTo(it) } }
                check(temporary.length() == 26623985L && temporary.renameTo(file)) { "เตรียมโมเดลวิเคราะห์ใบหน้าไม่สำเร็จ" }
            } finally { temporary.delete() }
        }
        val env = OrtEnvironment.getEnvironment()
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            env.createSession(file.absolutePath,options).use { session ->
                OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,512,512)).use { tensor ->
                    session.run(mapOf("input" to tensor)).use { result ->
                        val buffer = (result[0] as OnnxTensor).byteBuffer
                        check(buffer.remaining() == 512*512) { "ผลวิเคราะห์ใบหน้าไม่สมบูรณ์" }
                        val labels = ByteArray(512*512)
                        buffer.get(labels)
                        check(labels.all { (it.toInt() and 255) in 0..18 })
                        FaceParsingResult(labels)
                    }
                }
            }
        }
    }
}

internal class FaceParsingResult(private val labels: ByteArray) {
    init { require(labels.size == 512*512) }
    fun labelAt(u: Float, v: Float): Int {
        val x = floor(u*2+1).toInt(); val y = floor(v*2+1).toInt()
        return if (x in 0..511 && y in 0..511) labels[y*512+x].toInt() and 255 else 0
    }
    companion object {
        // Neck/hair are allowed only within the separate anatomical moustache/jaw mask.
        fun allowsRetouch(label: Int) = label == 1 || label == 14 || label == 17
    }
}

enum class RetouchStage(val message: String) {
    LOCATE("กำลังตรวจตำแหน่งใบหน้า…"),
    ANALYSE("AI กำลังแยกผิว ปาก จมูก และแว่น…"),
    PROTECT("กำลังกั้นพื้นที่ที่ไม่ควรแก้…"),
    GENERATE("AttGAN กำลังลดหนวดและเครา…"),
    COLOUR("กำลังเลือกวิธีปรับสีและแสงผิว…"),
    TEXTURE("กำลังเกลี่ยขอบและรักษารายละเอียดผิว…"),
    VERIFY("กำลังตรวจพื้นที่ที่ต้องคงเดิม…")
}
