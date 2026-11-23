package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Private emulator regression (no Gallery, Drive, network or on-device albums). */
@RunWith(AndroidJUnit4::class)
class LipBorderPrivateCasesTest {
    @Test fun respectsOriginalLipContourAcrossThreePrivateResearchFixtures() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(app.filesDir,"v67-private-lab")
        assumeTrue(File(dir,"manifest.json").isFile)
        val summary=JSONArray()
        for(n in 1..3) {
            val name="case-$n"
            val src=checkNotNull(BitmapFactory.decodeFile(
                File(dir,"$name.png").absolutePath
            ))
            val prepared=checkNotNull(BitmapFactory.decodeFile(
                File(dir,"$name/v67.png").absolutePath
            ))
            try {
                val json=JSONArray(File(dir,"$name.json").readText())
                val face=(0 until json.length()).map {i->
                    val p=json.getJSONArray(i)
                    NormalizedLandmark.create(p.getDouble(0).toFloat(),
                        p.getDouble(1).toFloat(),p.getDouble(2).toFloat())
                }
                val w=src.width;val h=src.height
                fun pixels(b:Bitmap)=IntArray(w*h).also {
                    b.getPixels(it,0,w,0,0,w,h)
                }
                val original=pixels(src)
                val existing=pixels(prepared)
                val (out,stats)=LipBorderHarmonizer.refine(src,prepared,face)
                try {
                    val changed=pixels(out)
                    val lips=LipSafeRasterGuard.fromFace(face,w,h)
                    var exact=0
                    var updated=0
                    for(i in original.indices){
                        if(lips.atIndex(i)) {
                            assertEquals("Lip must be EXACT original in $name",original[i],changed[i])
                            exact++
                        }
                        if(changed[i]!=existing[i])updated++
                    }
                    assertTrue(exact>100)
                    assertArrayEquals("Source bitmap immutable $name",original,pixels(src))
                    assertTrue("No changes outside narrow ring $name",updated<=stats.examined)
                    val outpath=File(dir,"$name/lip_beta31.png")
                    outpath.outputStream().use {
                        out.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    summary.put(JSONObject().put("case",name)
                        .put("changed",updated).put("eligible",stats.eligible)
                        .put("ring",stats.examined).put("noDonor",stats.noEditedDonor)
                        .put("ms",stats.elapsedMs))
                    Log.i("KeeperLipBeta31Case","$name changed=$updated ring="+
                        stats.examined+" ms="+stats.elapsedMs)
                }finally{
                    if(out!==prepared)out.recycle()
                }
            }finally{prepared.recycle();src.recycle()}
        }
        File(dir,"lip_beta31_result.json").writeText(summary.toString(2))
    }
}
