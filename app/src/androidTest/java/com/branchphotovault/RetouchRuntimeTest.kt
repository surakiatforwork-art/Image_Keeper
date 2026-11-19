package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.branchphotovault.retouch.MoustacheRetoucher
import ai.onnxruntime.*
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetouchRuntimeTest {
    @Test fun decodeAndFaceModelLoadOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.LTGRAY)
        val bytes = java.io.ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,
            BitmapFactory.Options().apply { inSampleSize = 1; inPreferredConfig = Bitmap.Config.ARGB_8888 }))
        try {
            assertTrue(MoustacheRetoucher(context).faces(decoded).isEmpty())
            assertFalse("Detection must not recycle the UI bitmap",decoded.isRecycled)
        }
        finally { decoded.recycle(); source.recycle() }
    }

    @Test fun inpaintingModelRunsOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val size = 512 * 512
        val rgb = ByteArray(size * 3) { 170.toByte() }
        val mask = ByteArray(size) { 255.toByte() }
        for (y in 230..250) for (x in 190..310) mask[y*512+x] = 0
        val env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            env.createSession(context.assets.open("retouch/migan.onnx").use { it.readBytes() },options).use { session ->
                OnnxTensor.createTensor(env,ByteBuffer.wrap(rgb),longArrayOf(1,3,512,512),OnnxJavaType.UINT8).use { image ->
                    OnnxTensor.createTensor(env,ByteBuffer.wrap(mask),longArrayOf(1,1,512,512),OnnxJavaType.UINT8).use { m ->
                        session.run(mapOf("image" to image,"mask" to m)).use { result ->
                            val bytes = (result[0] as OnnxTensor).byteBuffer
                            assertEquals(size*3,bytes.remaining())
                            val pixels = IntArray(size) { i -> Color.rgb(
                                bytes.get(i).toInt() and 255,
                                bytes.get(size+i).toInt() and 255,
                                bytes.get(size*2+i).toInt() and 255) }
                            val processed = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
                            try {
                                processed.setPixels(pixels,0,512,0,0,512,512)
                                assertTrue(processed.isMutable)
                                android.graphics.Canvas(processed).drawColor(Color.TRANSPARENT,
                                    android.graphics.PorterDuff.Mode.DST_IN)
                            } finally { processed.recycle() }
                        }
                    }
                }
            }
        }
    }
}
