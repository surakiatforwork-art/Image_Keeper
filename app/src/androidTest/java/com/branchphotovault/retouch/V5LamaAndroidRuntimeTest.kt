package com.branchphotovault.retouch

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import org.junit.runner.RunWith
import java.io.File
import java.nio.FloatBuffer

/**
 * Smart Shave V5 research — offline generative reconstruction *runtime only*.
 * Uses a private test APK asset; no LaMa model in public Image Keeper.
 * Random gradients verify model can run, NOT actual beard removal quality.
 */
@RunWith(AndroidJUnit4::class)
class V5LamaAndroidRuntimeTest {
    @Test fun lamaFaceReconstructionCanRunLocally() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val testAssets=inst.context.assets.list("")?.toSet().orEmpty()
        Assume.assumeTrue("Local research ONNX asset not supplied",
            "lama_lab_only.onnx" in testAssets)
        val cache=inst.targetContext.cacheDir
        val model=File(cache,"lama-v5-testonly.onnx")
        inst.context.assets.open("lama_lab_only.onnx").use { stream ->
            model.outputStream().use { stream.copyTo(it) }
        }
        assertEquals(102527873L,model.length())
        val env=OrtEnvironment.getEnvironment()
        try {
            OrtSession.SessionOptions().use { opts ->
                opts.setIntraOpNumThreads(2)
                opts.setInterOpNumThreads(1)
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                val begin=SystemClock.elapsedRealtime()
                env.createSession(model.absolutePath,opts).use { session ->
                    Log.i("KeeperV5Runtime",
                        "LaMa loadedMs="+(SystemClock.elapsedRealtime()-begin)+
                        " PSSMB="+Debug.getPss()/1024)
                    for(side in listOf(256,512)) {
                        val plane=side*side
                        val image=FloatArray(plane*3) { i ->
                            val x=i%side
                            val y=(i%plane)/side
                            (.30f+.35f*x/side+.2f*y/side).coerceIn(0f,1f)
                        }
                        val mask=FloatArray(plane) { i ->
                            val x=i%side
                            val y=i/side
                            if(x in side/3 until side*2/3 &&
                               y in side/3 until side*2/3) 1f else 0f
                        }
                        val t=SystemClock.elapsedRealtime()
                        OnnxTensor.createTensor(env,FloatBuffer.wrap(image),
                            longArrayOf(1,3,side.toLong(),side.toLong())).use { tImg ->
                            OnnxTensor.createTensor(env,FloatBuffer.wrap(mask),
                                longArrayOf(1,1,side.toLong(),side.toLong())).use { tMask ->
                                session.run(mapOf("image" to tImg,"mask" to tMask)).use { result ->
                                    val values=(result[0] as OnnxTensor).floatBuffer
                                    assertEquals(plane*3,values.remaining())
                                    val elapsed=SystemClock.elapsedRealtime()-t
                                    Log.i("KeeperV5Runtime",
                                        "LaMa side=$side ms=$elapsed PSSMB="+Debug.getPss()/1024+
                                        " output="+values.remaining())
                                    assertTrue("Mobile offline runtime too slow",
                                        elapsed<120000L)
                                }
                            }
                        }
                    }
                }
            }
        } finally {model.delete()}
    }
}
