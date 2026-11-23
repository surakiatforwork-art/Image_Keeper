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

@RunWith(AndroidJUnit4::class)
class V67PrivateCasesTest {
    @Test fun compareAuthorizedLocalCasesWithoutAccessingGalleryOrDatabase() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.filesDir,"v67-private-lab")
        assumeTrue(File(directory,"manifest.json").isFile)
        val manifest=JSONArray(File(directory,"manifest.json").readText())
        val reports=JSONArray()
        for(case in 0 until manifest.length()) {
            val name=manifest.getJSONObject(case).getString("name")
            require(name.matches(Regex("case-[0-9]+")))
            val source=checkNotNull(BitmapFactory.decodeFile(File(directory,"$name.png").absolutePath))
            try {
                val landmarks=JSONArray(File(directory,"$name.json").readText())
                val face=(0 until landmarks.length()).map { i ->val p=landmarks.getJSONArray(i)
                    NormalizedLandmark.create(p.getDouble(0).toFloat(),p.getDouble(1).toFloat(),p.getDouble(2).toFloat()) }
                val w=source.width;val h=source.height
                fun pixels(bitmap:Bitmap)=IntArray(w*h).also { bitmap.getPixels(it,0,w,0,0,w,h) }
                val before=pixels(source)
                val guard=LipSafeRasterGuard.fromFace(face,w,h)
                val resultDirectory=File(directory,name).apply { mkdirs() }
                for(flag in listOf(false,true)) {
                    val start=android.os.SystemClock.elapsedRealtime()
                    val base=MoustacheRetoucher(context).processV6Passes(source,0,RetouchRegion.BOTH,.84f,
                        passes=1,v67Refinement=flag,originalFace=face)
                    val (result,stats)=try { BeardReconstructionV66(context).reconstruct(source,base.image,face,.94f,refineV67=flag) }
                        finally { base.image.recycle() }
                    try {
                        val after=pixels(result)
                        for(i in after.indices) if(guard.atIndex(i)) assertEquals("Original lip/nose $name",before[i],after[i])
                        assertArrayEquals("Original source bitmap $name",before,pixels(source))
                        assertEquals(before[0],after[0]);assertEquals(before.last(),after.last())
                        val label=if(flag)"v67" else "v66"
                        File(resultDirectory,"$label.png").outputStream().use { result.compress(Bitmap.CompressFormat.PNG,100,it) }
                        val elapsed=android.os.SystemClock.elapsedRealtime()-start
                        reports.put(org.json.JSONObject().put("case",name).put("variant",label).put("elapsed_ms",elapsed)
                            .put("changed_pixels",after.indices.count { after[it]!=before[it] }).put("beard_model_ms",stats.modelMs)
                            .put("source_unchanged",true).put("protected_pixels_unchanged",true))
                        Log.i("KeeperV67Case","$name $label elapsed_ms=$elapsed")
                    } finally { result.recycle() }
                }
            } finally { source.recycle() }
        }
        File(directory,"results.json").writeText(reports.toString(2))
    }
}
