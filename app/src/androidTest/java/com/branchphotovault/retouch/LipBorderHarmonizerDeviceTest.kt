package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Beta31 narrow lip border continuity smoke test.
 * A research fixture is bundled in androidTest; does not read Gallery/DB.
 */
@RunWith(AndroidJUnit4::class)
class LipBorderHarmonizerDeviceTest {
    @Test fun oneTapPolishesLipSeamWithoutRepaintingOriginalLipPixels() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val src=inst.context.assets.open("v66_beard_fixture.jpg").use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
        try {
            val json=JSONArray(inst.context.assets.open("v66_beard_fixture_landmarks.json")
                .bufferedReader().use { it.readText() })
            val face=(0 until json.length()).map {i->
                val a=json.getJSONArray(i)
                NormalizedLandmark.create(a.getDouble(0).toFloat(),
                    a.getDouble(1).toFloat(),a.getDouble(2).toFloat())
            }
            val w=src.width
            val h=src.height
            fun pixels(bitmap:Bitmap)=IntArray(w*h).also{
                bitmap.getPixels(it,0,w,0,0,w,h)
            }
            val original=pixels(src)
            val raw=SmartShaveAuto.run(context,src,0,face,lipBlend=false)
            val (improved,stats)=LipBorderHarmonizer.refine(src,raw.image,face)
            try {
                val baseline=pixels(raw.image)
                val after=pixels(improved)
                val lips=LipSafeRasterGuard.fromFace(face,w,h)
                val contourIds=intArrayOf(61,40,37,0,267,270,291,321,314,17,84,91)
                val xs=contourIds.map { face[it].x()*w }
                val ys=contourIds.map { face[it].y()*h }
                val pad=kotlin.math.hypot(
                    (face[61].x()-face[291].x())*w,
                    (face[61].y()-face[291].y())*h
                )*.19f+5f
                val l=xs.minOrNull()!!-pad
                val r=xs.maxOrNull()!!+pad
                val t=ys.minOrNull()!!-pad
                val b=ys.maxOrNull()!!+pad
                var modified=0
                var exactLips=0
                for(i in original.indices) {
                    if(lips.atIndex(i)) {
                        assertEquals("Original lips must be unchanged",original[i],after[i])
                        exactLips++
                    }
                    if(after[i]!=baseline[i]) {
                        modified++
                        val px=i%w
                        val py=i/w
                        assertTrue("No outside-mouth lip editing",
                            px.toFloat() in l..r && py.toFloat() in t..b)
                    }
                }
                assertTrue("Lip polygons must have a useful pixel region",exactLips>100)
                assertArrayEquals("Original photo must not be mutated",original,pixels(src))
                assertEquals("Background top-left exact",baseline[0],after[0])
                assertEquals("Background bottom-right exact",baseline.last(),after.last())
                assertTrue("No edit may happen outside the lip annulus",
                    modified<=stats.examined)
                assertTrue("Lip harmonizer should detect candidate skin",stats.eligible>=0)
                File(context.filesDir,"lip_beta30_reference.png")
                    .outputStream().use {raw.image.compress(Bitmap.CompressFormat.PNG,100,it)}
                File(context.filesDir,"lip_beta31_harmonized.png")
                    .outputStream().use {improved.compress(Bitmap.CompressFormat.PNG,100,it)}
                Log.i("KeeperLipBeta31Test",
                    "changed="+modified+" ring="+stats.examined+
                    " eligible="+stats.eligible+" no_donor="+stats.noEditedDonor+
                    " lip_exact="+exactLips+" ms="+stats.elapsedMs)
            }finally{
                if(improved!==raw.image && improved!==src)improved.recycle()
                if(raw.image!==src)raw.image.recycle()
            }
        }finally{src.recycle()}
    }
}
