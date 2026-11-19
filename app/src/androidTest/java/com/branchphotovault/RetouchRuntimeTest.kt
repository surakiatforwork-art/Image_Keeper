package com.branchphotovault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.branchphotovault.retouch.MoustacheRetoucher
import ai.onnxruntime.*
import java.nio.FloatBuffer
import java.io.File
import com.branchphotovault.retouch.RetouchRegion
import com.branchphotovault.retouch.FaceParsingTool
import com.branchphotovault.retouch.FaceParsingResult
import com.branchphotovault.retouch.RetouchStage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetouchRuntimeTest {
    @Test fun faceParsingToolRunsAndProtectsAnatomicalClasses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parsing = FaceParsingTool(context).analyse(FloatArray(3*256*256))
        assertTrue(parsing.labelAt(128f,128f) in 0..18)
        assertEquals(0,parsing.labelAt(-1f,128f))
        for (label in listOf(0,2,3,4,5,6,7,8,9,10,11,12,13,15,16,18)) assertFalse(FaceParsingResult.allowsRetouch(label))
        for (label in listOf(1,14,17)) assertTrue(FaceParsingResult.allowsRetouch(label))
    }
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

    @Test fun attganModelRunsOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val size = 256 * 256
        val file = File(context.cacheDir,"attgan-runtime-test.onnx")
        context.assets.open("retouch/attgan.onnx").use { source -> file.outputStream().use { source.copyTo(it) } }
        val env = OrtEnvironment.getEnvironment()
        try { OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            env.createSession(file.absolutePath,options).use { session ->
                OnnxTensor.createTensor(env,FloatBuffer.wrap(FloatArray(size*3)),longArrayOf(1,3,256,256)).use { image ->
                    session.run(mapOf("image" to image)).use { result ->
                        val buffer = (result[0] as OnnxTensor).floatBuffer
                        assertEquals(size*3,buffer.remaining())
                        val pixels = FloatArray(size*3)
                        buffer.get(pixels)
                        assertTrue(pixels.all { it.isFinite() && it in 0f..255f })
                    }
                }
            }
        } } finally { file.delete() }
    }

    @Test fun realPhotosRetouchWithoutChangingOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(context.cacheDir,"retouch-fixtures").listFiles { file -> file.extension == "jpg" }.orEmpty().sortedBy { it.name }
        org.junit.Assume.assumeTrue("Private test fixtures are supplied separately, never bundled",files.isNotEmpty())
        val retoucher = MoustacheRetoucher(context)
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
        for ((index,file) in files.withIndex()) {
            val source = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            val before = IntArray(source.width*source.height)
            source.getPixels(before,0,source.width,0,0,source.width,source.height)
            val regions = if (index == 0) RetouchRegion.entries else listOf(RetouchRegion.BOTH)
            try { for (region in regions) {
                val started = android.os.SystemClock.elapsedRealtime()
                val stages = mutableListOf<RetouchStage>()
                val result = retoucher.process(source,0,region) { stages.add(it) }
                try {
                    assertEquals(RetouchStage.entries,stages)
                    assertFalse(source.isRecycled)
                    assertEquals(source.width,result.width)
                    assertEquals(source.height,result.height)
                    val after = IntArray(before.size)
                    source.getPixels(after,0,source.width,0,0,source.width,source.height)
                    assertArrayEquals("Retouch must not change the preview bitmap",before,after)
                    result.getPixels(after,0,result.width,0,0,result.width,result.height)
                    assertTrue("Retouch must change some pixels",before.indices.any { before[it] != after[it] })
                    assertEquals("Background must be unchanged",before[0],after[0])
                    assertEquals(before.last(),after.last())
                    val face = retoucher.faces(source).first()
                    for (point in listOf(4,13,14)) {
                        val x = (face[point].x()*source.width).toInt().coerceIn(0,source.width-1)
                        val y = (face[point].y()*source.height).toInt().coerceIn(0,source.height-1)
                        assertEquals("Nose/lip pixels must be protected",source.getPixel(x,y),result.getPixel(x,y))
                    }
                    if (region == RetouchRegion.BOTH) File(context.cacheDir,"${file.nameWithoutExtension}-android.png").outputStream().use {
                        assertTrue(result.compress(Bitmap.CompressFormat.PNG,100,it))
                    }
                    android.util.Log.i("KeeperRetouchTest","${file.name} $region ${android.os.SystemClock.elapsedRealtime()-started}ms")
                } finally { result.recycle() }
            } } finally { source.recycle() }
        }
        } finally { scenario.close() }
    }
}
