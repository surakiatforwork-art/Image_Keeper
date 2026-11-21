package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import java.io.File
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Device-only regression test for the optional 2nd AttGAN view.
 * It must never redraw background or displace the chin silhouette.
 */
@RunWith(AndroidJUnit4::class)
class OfflineAttganDetailIntegrationTest {
    @Test fun optionalDetailStaysWithinAnatomyAndRespects20LevelCorrection() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        // Supplied locally by an authorized tester; never ship private
        // portraits in the repository or the publicly released APK.
        val testPhoto=File(context.cacheDir,"retouch-fixtures/portrait.jpg")
        Assume.assumeTrue("No private face test photo; skip",testPhoto.exists())
        val source=requireNotNull(BitmapFactory.decodeFile(testPhoto.absolutePath))
        val retoucher=MoustacheRetoucher(context)
        val face=retoucher.faces(source).first()
        val upper=face[2].y()*source.height
        val chin=face[152].y()*source.height
        var baseline:Bitmap?=null
        var detailed:Bitmap?=null
        try {
            baseline=retoucher.process(source,0,RetouchRegion.BOTH,RetouchEngine.BASELINE)
            detailed=retoucher.process(
                source,0,RetouchRegion.BOTH,RetouchEngine.BASELINE,extraDetail=true
            )
            assertEquals(source.width,baseline.width)
            assertEquals(source.width,detailed.width)
            assertEquals(source.height,baseline.height)
            assertEquals(source.height,detailed.height)
            val count=source.width*source.height
            val basePixels=IntArray(count)
            val newPixels=IntArray(count)
            baseline.getPixels(basePixels,0,source.width,0,0,source.width,source.height)
            detailed.getPixels(newPixels,0,source.width,0,0,source.width,source.height)
            var changed=0
            for (i in 0 until count) {
                val base=basePixels[i]
                val newer=newPixels[i]
                val y=i/source.width
                // Second pass is strictly in the lower-face crop.
                if(y < upper-40 || y > chin+40) {
                    assertEquals("Background must stay bit-for-bit equal to baseline",base,newer)
                }
                if(base != newer) changed++
                assertTrue(abs(Color.red(base)-Color.red(newer)) <= 20)
                assertTrue(abs(Color.green(base)-Color.green(newer)) <= 20)
                assertTrue(abs(Color.blue(base)-Color.blue(newer)) <= 20)
                assertEquals(Color.alpha(base),Color.alpha(newer))
            }
            assertTrue("Second pass should have an observable lower-face correction",changed>0)
        } finally {
            baseline?.recycle()
            detailed?.recycle()
            source.recycle()
        }
    }
}
