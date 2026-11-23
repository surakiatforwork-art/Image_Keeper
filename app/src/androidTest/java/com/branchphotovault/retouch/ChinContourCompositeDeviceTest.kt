package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * No gallery/photos or account data: uses optional private-bundled public research fixture.
 * Compares the exact Beta 28 Chin Student compositor with experimental V6.8.
 */
@RunWith(AndroidJUnit4::class)
class ChinContourCompositeDeviceTest {
    @Test fun naturalChinProtectsSourcePixelsAndSoftensOuterEdit() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val ctx=inst.targetContext
        assumeTrue(ctx.assets.list("retouch")?.contains(ChinStudent.asset)==true)
        assumeTrue(inst.context.assets.list("")?.contains("v66_beard_fixture.jpg")==true)
        assumeTrue(inst.context.assets.list("")?.contains("v66_beard_fixture_landmarks.json")==true)
        val src=inst.context.assets.open("v66_beard_fixture.jpg")
            .use { checkNotNull(BitmapFactory.decodeStream(it)) }
        try {
            val items=JSONArray(inst.context.assets.open("v66_beard_fixture_landmarks.json")
                .bufferedReader().use { it.readText() })
            val face=(0 until items.length()).map {i->
                val item=items.getJSONArray(i)
                NormalizedLandmark.create(item.getDouble(0).toFloat(),
                    item.getDouble(1).toFloat(),item.getDouble(2).toFloat())
            }
            val w=src.width;val h=src.height
            fun pixels(bitmap:Bitmap)=IntArray(w*h).also {
                bitmap.getPixels(it,0,w,0,0,w,h)
            }
            val before=pixels(src)
            val old=ChinStudent.apply(ctx,src,src,face,naturalContour=false)
            val newer=try {
                ChinStudent.apply(ctx,src,src,face,naturalContour=true)
            }catch(e:Exception){
                old.recycle()
                throw e
            }
            try {
                val legacy=pixels(old)
                val natural=pixels(newer)
                val guard=LipSafeRasterGuard.fromFace(face,w,h)
                var changedLegacy=0;var changedNatural=0
                var outsideLegacy=0;var outsideNatural=0
                val outline=OriginalFaceContourGuard.fromFace(face,w,h)
                for(i in before.indices) {
                    val x=i%w;val y=i/w
                    if(guard.atIndex(i)) {
                        assertEquals("Legacy lip exact",before[i],legacy[i])
                        assertEquals("V6.8 lip exact",before[i],natural[i])
                    }
                    if(legacy[i]!=before[i]) {
                        changedLegacy++
                        if(outline.signedDistance(x.toFloat(),y.toFloat())<0f)
                            outsideLegacy++
                    }
                    if(natural[i]!=before[i]) {
                        changedNatural++
                        if(outline.signedDistance(x.toFloat(),y.toFloat())<0f)
                            outsideNatural++
                    }
                }
                assertArrayEquals("Source must not be edited",before,pixels(src))
                assertEquals(before[0],natural[0])
                assertEquals(before.last(),natural.last())
                assertTrue("Legacy must have a measurable beard edit",changedLegacy>500)
                assertTrue("Natural must still affect real beard",changedNatural>300)
                assertTrue("Natural should not paint more outside the oval",
                    outsideNatural<=outsideLegacy)
                assertTrue("Natural should not change more than baseline",
                    changedNatural<=changedLegacy)
                File(ctx.filesDir,"chin_v68_legacy_fixture.png")
                    .outputStream().use{old.compress(Bitmap.CompressFormat.PNG,100,it)}
                File(ctx.filesDir,"chin_v68_natural_fixture.png")
                    .outputStream().use{newer.compress(Bitmap.CompressFormat.PNG,100,it)}
                Log.i("KeeperChinV68Test",
                    "legacy=$changedLegacy natural=$changedNatural"+
                    " outside_legacy=$outsideLegacy outside_natural=$outsideNatural")
            }finally{old.recycle();newer.recycle()}
        }finally{src.recycle()}
    }
}
