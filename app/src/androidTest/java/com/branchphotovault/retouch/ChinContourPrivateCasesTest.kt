package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
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

/**
 * Only pre-authorized cases already in the PRIVATE app sandbox on the emulator.
 * Never reads PhotoRepository, shared storage, Gallery or private phone data.
 */
@RunWith(AndroidJUnit4::class)
class ChinContourPrivateCasesTest {
    @Test fun preservesOriginalLipsAcrossThreePrivateCases() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(ctx.filesDir,"v67-private-lab")
        assumeTrue(File(directory,"manifest.json").isFile)
        assumeTrue(ctx.assets.list("retouch")?.contains(ChinStudent.asset)==true)
        val manifest=JSONArray(File(directory,"manifest.json").readText())
        assertTrue("Need three private, diverse fixtures",manifest.length()>=3)
        val outputs=JSONArray()
        for(case in 0 until manifest.length()) {
            val name=manifest.getJSONObject(case).getString("name")
            require(name.matches(Regex("case-[0-9]+")))
            val file=File(directory,"$name.png")
            val source=checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                val points=JSONArray(File(directory,"$name.json").readText())
                val face=(0 until points.length()).map {i->
                    val p=points.getJSONArray(i)
                    NormalizedLandmark.create(p.getDouble(0).toFloat(),
                        p.getDouble(1).toFloat(),p.getDouble(2).toFloat())
                }
                val w=source.width;val h=source.height
                fun pixels(b:Bitmap)=IntArray(w*h).also{
                    b.getPixels(it,0,w,0,0,w,h)
                }
                val original=pixels(source)
                val start=SystemClock.elapsedRealtime()
                val legacy=ChinStudent.apply(ctx,source,source,face,naturalContour=false)
                val oldMs=SystemClock.elapsedRealtime()-start
                val stage=SystemClock.elapsedRealtime()
                val natural=ChinStudent.apply(ctx,source,source,face,naturalContour=true)
                val v68Ms=SystemClock.elapsedRealtime()-stage
                try {
                    val a=pixels(legacy);val b=pixels(natural)
                    val lip=LipSafeRasterGuard.fromFace(face,w,h)
                    val silhouette=OriginalFaceContourGuard.fromFace(face,w,h)
                    var beforeChanged=0
                    var naturalChanged=0
                    var oldOutside=0
                    var newOutside=0
                    for(i in original.indices) {
                        val x=i%w;val y=i/w
                        if(lip.atIndex(i)) {
                            assertEquals("Legacy protected $name",original[i],a[i])
                            assertEquals("Natural protected $name",original[i],b[i])
                        }
                        val o=a[i]!=original[i]
                        val n=b[i]!=original[i]
                        if(o || n) {
                            if(o)beforeChanged++
                            if(n)naturalChanged++
                            if(silhouette.signedDistance(x.toFloat(),y.toFloat())<0f) {
                                if(o)oldOutside++
                                if(n)newOutside++
                            }
                        }
                    }
                    assertArrayEquals("Original source not modified $name",
                        original,pixels(source))
                    assertTrue("Chin composite not inert $name",beforeChanged>0)
                    assertTrue("Natural should not expand edited area $name",
                        naturalChanged<=beforeChanged)
                    val outdir=File(directory,name).apply {mkdirs()}
                    File(outdir,"chinstudent03_beta28.png").outputStream().use {
                        legacy.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    File(outdir,"chincontour_v68_beta29.png").outputStream().use {
                        natural.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    outputs.put(JSONObject().put("case",name)
                        .put("beta28_changed",beforeChanged)
                        .put("beta29_changed",naturalChanged)
                        .put("beta28_outside",oldOutside)
                        .put("beta29_outside",newOutside)
                        .put("beta28_ms",oldMs).put("beta29_ms",v68Ms))
                    Log.i("KeeperChinV68Test",
                        "$name beta28=$beforeChanged beta29=$naturalChanged "+
                        "outside28=$oldOutside outside29=$newOutside "+
                        "ms28=$oldMs ms29=$v68Ms")
                } finally {legacy.recycle();natural.recycle()}
            } finally {source.recycle()}
        }
        File(directory,"chincontour_v68_results.json").writeText(outputs.toString(2))
    }
}
