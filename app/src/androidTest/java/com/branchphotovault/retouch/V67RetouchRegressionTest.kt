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

/** Private lab fixtures are deliberately not included in public releases. */
@RunWith(AndroidJUnit4::class)
class V67RetouchRegressionTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private fun fixture():Pair<Bitmap,List<NormalizedLandmark>> {
        assumeTrue(context.assets.list("retouch")?.contains("facebench_no_beard_fp32.onnx")==true)
        assumeTrue(inst.context.assets.list("")?.contains("v66_beard_fixture.jpg")==true)
        val bitmap=inst.context.assets.open("v66_beard_fixture.jpg").use { checkNotNull(BitmapFactory.decodeStream(it)) }
        val json=JSONArray(inst.context.assets.open("v66_beard_fixture_landmarks.json").bufferedReader().use { it.readText() })
        return bitmap to (0 until json.length()).map { i -> val p=json.getJSONArray(i)
            NormalizedLandmark.create(p.getDouble(0).toFloat(),p.getDouble(1).toFloat(),p.getDouble(2).toFloat()) }
    }
    private fun pixels(bitmap:Bitmap)=IntArray(bitmap.width*bitmap.height).also { bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height) }
    private fun save(bitmap:Bitmap,name:String) { File(context.filesDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }

    @Test fun beardSpecialistKeepsUpperLipAndSourceSilhouette() {
        val (original,face)=fixture()
        try {
            val source=pixels(original); val w=original.width; val h=original.height
            val guard=OriginalFaceContourGuard.fromFace(face,w,h)
            val lips=LipSafeRasterGuard.fromFace(face,w,h)
            val (baseline,_)=BeardReconstructionV66(context).reconstruct(original,original,face,1f)
            val (revised,stats)=BeardReconstructionV66(context).reconstruct(original,original,face,1f,refineV67=true,strictContourDiagnostic=true)
            try {
                val old=pixels(baseline); val result=pixels(revised)
                var oldOutside=0; var insideEdits=0; var protectedCount=0
                val dx=((face[263].x()+face[362].x()-face[33].x()-face[133].x())*.5f*w)
                val dy=((face[263].y()+face[362].y()-face[33].y()-face[133].y())*.5f*h)
                val lipX=face[17].x()*w;val lipY=face[17].y()*h
                for(i in result.indices) {
                    val x=i%w;val y=i/w
                    if(guard.weightAt(x,y)==0f) {
                        assertEquals("No generated skin beyond source silhouette",source[i],result[i])
                        if(old[i]!=source[i]) oldOutside++
                    } else if(result[i]!=source[i]) insideEdits++
                    if(-dy*(x-lipX)+dx*(y-lipY)<=0f)
                        assertEquals("Beard specialist must preserve moustache stage",source[i],result[i])
                    if(lips.atIndex(i)) { assertEquals(source[i],result[i]);protectedCount++ }
                }
                assertTrue("Still remove beard inside original contour",insideEdits>500)
                assertTrue("Baseline fixture exercises contour leak",oldOutside>0)
                assertArrayEquals(source,pixels(original))
                save(baseline,"v67_baseline_beard.png");save(revised,"v67_contour_beard.png")
                Log.i("KeeperV67Test","baseline_outside=$oldOutside inside_edits=$insideEdits protected_lips=$protectedCount model_ms=${stats.modelMs}")
            } finally { baseline.recycle();revised.recycle() }
        } finally { original.recycle() }
    }

    @Test fun fullPipelineKeepsSourceAndLipsWithNoExtraGenerativeRound() {
        val (original,face)=fixture()
        try {
            val source=pixels(original); val w=original.width;val h=original.height
            val start=android.os.SystemClock.elapsedRealtime()
            val base=MoustacheRetoucher(context).processV6Passes(original,0,RetouchRegion.BOTH,.84f,
                passes=1,v67Refinement=true,originalFace=face)
            val (final,stats)=try { BeardReconstructionV66(context).reconstruct(original,base.image,face,.94f,refineV67=true) }
                finally { base.image.recycle() }
            try {
                val result=pixels(final)
                val lips=LipSafeRasterGuard.fromFace(face,w,h)
                val guard=OriginalFaceContourGuard.fromFace(face,w,h)
                val sourceMatte=BeardFeatherMask(BeardSegmentationTool(context).analyse(original,face),preserveSourceBoundary=true)
                for(i in result.indices) if(lips.atIndex(i) || (guard.weightAt(i%w,i/w)==0f && sourceMatte.at(i%w,i/w)<=.005f))
                    assertEquals("Protected original pixels stay exact after all stages",source[i],result[i])
                assertArrayEquals(source,pixels(original))
                assertTrue(result.indices.count { result[it]!=source[it] }>500)
                assertTrue("Do not restore the whole original beard outside anatomical landmarks",
                    result.indices.count { guard.weightAt(it%w,it/w)==0f && result[it]!=source[it] }>500)
                assertEquals(1,base.completedPasses)
                save(final,"v67_full_pipeline.png")
                Log.i("KeeperV67Test","full_pipeline_ms=${android.os.SystemClock.elapsedRealtime()-start} beard_model_ms=${stats.modelMs}")
            } finally { final.recycle() }
        } finally { original.recycle() }
    }
}
