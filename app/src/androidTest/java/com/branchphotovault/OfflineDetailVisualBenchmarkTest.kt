package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Local-only QA with the user's screen-video reference, not a camera original.
 * This fixture is intentionally excluded from Git/public APK distribution.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDetailVisualBenchmarkTest {
    @Test fun realPortraitSecondPassKeepsSkinHueAndMaskBounds() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        // Not published to Git or the Android test artifact in CI.
        // Users must explicitly provide their own local test fixture.
        val assets=inst.context.assets.list("")?.toSet().orEmpty()
        Assume.assumeTrue("Private local fixture unavailable",assets.contains("meitu_video_before_proxy.png"))
        val src=inst.context.assets.open("meitu_video_before_proxy.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
        val retoucher=MoustacheRetoucher(context)
        var baseline:Bitmap?=null
        var refined:Bitmap?=null
        try {
            val face=retoucher.faces(src)
            assertTrue("Face found in user-provided test portrait",face.isNotEmpty())
            val t0=SystemClock.elapsedRealtime()
            baseline=retoucher.process(src,0,RetouchRegion.BOTH,RetouchEngine.BASELINE)
            val t1=SystemClock.elapsedRealtime()
            refined=retoucher.process(src,0,RetouchRegion.BOTH,RetouchEngine.BASELINE,
                extraDetail=true,extraDetailStrength=.28f)
            val t2=SystemClock.elapsedRealtime()
            val w=src.width
            val h=src.height
            val pa=IntArray(w*h)
            val pb=IntArray(w*h)
            baseline.getPixels(pa,0,w,0,0,w,h)
            refined.getPixels(pb,0,w,0,0,w,h)
            var diffCount=0
            var maxDiff=0
            var maxHueDrift=0
            var hueOutliers=0
            for(i in pa.indices) {
                val a=pa[i];val b=pb[i]
                val dR=Color.red(b)-Color.red(a)
                val dG=Color.green(b)-Color.green(a)
                val dB=Color.blue(b)-Color.blue(a)
                val max=maxOf(abs(dR),abs(dG),abs(dB))
                maxDiff=maxOf(maxDiff,max)
                val spread=maxOf(dR,dG,dB)-minOf(dR,dG,dB)
                maxHueDrift=maxOf(maxHueDrift,spread)
                if(spread>3) hueOutliers++
                if(a!=b) diffCount++
                assertEquals(Color.alpha(a),Color.alpha(b))
                assertTrue("Second pass correction exceeded 9 levels",max<=9)

            }
            assertTrue("Refinement should change some pixels",diffCount>100)
            assertTrue("Chroma shift is too widespread: $hueOutliers",
                hueOutliers < w*h/5000)
            val out=File(context.cacheDir,"offline-detail-research")
            out.mkdirs()
            File(out,"attgan_original.png").outputStream().use { src.compress(Bitmap.CompressFormat.PNG,100,it) }
            File(out,"attgan_baseline.png").outputStream().use { baseline.compress(Bitmap.CompressFormat.PNG,100,it) }
            File(out,"attgan_smooth_refined.png").outputStream().use { refined.compress(Bitmap.CompressFormat.PNG,100,it) }
            android.util.Log.i("KeeperDetailQA",
                "editedPixels=$diffCount maxRGBChange=$maxDiff maxHueDrift=$maxHueDrift timeSingle=${t1-t0}ms timeTwoView=${t2-t1}ms")
        } finally {
            baseline?.recycle();refined?.recycle();src.recycle()
        }
    }
}
