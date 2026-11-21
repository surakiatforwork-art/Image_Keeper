package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * V5 Geometry only; never performs an actual beard removal or changes
 * the user's installed photos. Debug output stays in app-private cache.
 */
@RunWith(AndroidJUnit4::class)
class Lip360DevicePrototypeTest {
    @Test fun radialGeometryOnRealPortraits() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val ctx=inst.targetContext
        val retoucher=MoustacheRetoucher(ctx)
        val dir=File(ctx.cacheDir,"v5-geometry").apply { mkdirs() }
        // Large camera frames are evaluated in PC batch research; keep this
        // on-device geometry smoke test bounded to two small fixtures.
        val names=listOf("portrait_fixture.jpg","beard_fixture.jpg")
        val available=inst.context.assets.list("")?.toSet().orEmpty()
        Assume.assumeTrue("Private research portrait fixtures not supplied",
            names.any { it in available })
        var found=0
        for(name in names) {
            val available=inst.context.assets.list("")?.toList().orEmpty()
            if(name !in available) continue
            val bitmap=inst.context.assets.open(name).use {
                requireNotNull(BitmapFactory.decodeStream(it))
            }
            try {
                val face=retoucher.faces(bitmap).firstOrNull() ?: continue
                found++
                val radial=Lip360Landmarks.fromFace(face,bitmap.width,bitmap.height)
                val bins=IntArray(8)
                var scanned=0
                var protected=0
                val t=SystemClock.elapsedRealtime()
                val preview=bitmap.copy(Bitmap.Config.ARGB_8888,true)
                try {
                    // Coarse diagnostic overlay. Dense per-pixel edit masks
                    // will use a dedicated parallel GPU/CPU rasterizer.
                    val step=3
                    for(y in 0 until bitmap.height step step) {
                        for(x in 0 until bitmap.width step step) {
                            val s=radial.sample(x.toFloat(),y.toFloat())
                            if(s.protected) protected++
                            if(!s.searchable) continue
                            bins[(s.angularDegrees/45f).toInt().coerceIn(0,7)]++
                            scanned++
                            val color=bitmap.getPixel(x,y)
                            val r=((Color.red(color)*.54f+255f*.46f).toInt()).coerceIn(0,255)
                            val g=((Color.green(color)*.73f+48f*.27f).toInt()).coerceIn(0,255)
                            val b=((Color.blue(color)*.70f+42f*.30f).toInt()).coerceIn(0,255)
                            for(yy in y..(y+step-1).coerceAtMost(bitmap.height-1))
                                for(xx in x..(x+step-1).coerceAtMost(bitmap.width-1))
                                    preview.setPixel(xx,yy,Color.rgb(r,g,b))
                        }
                    }
                    File(dir,name.substringBeforeLast('.')+"-360.png").outputStream().use {
                        preview.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    assertTrue("No 360 degree face searchable zone for $name",scanned>30)
                    // Diverse photos may show no hair, but *search* should
                    // cover multiple angular sectors for a frontal face.
                    val activeBins=bins.count {it>0}
                    assertTrue("360 ring restricted to too few directions: $activeBins",
                        activeBins>=4)
                    android.util.Log.i("KeeperV5",
                        "file=$name searchable=$scanned protected=$protected "+
                        "angularBins="+bins.joinToString(",")+" "+
                        "elapsedMs="+(SystemClock.elapsedRealtime()-t))
                } finally { preview.recycle() }
            } finally { bitmap.recycle() }
        }
        assertTrue("No usable portrait test data",found>0)
    }
}
