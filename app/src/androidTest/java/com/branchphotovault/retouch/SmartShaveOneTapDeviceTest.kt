package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Full one-action pipeline. Does not read or write the user's Gallery/DB. */
@RunWith(AndroidJUnit4::class)
class SmartShaveOneTapDeviceTest {
    @Test fun oneTapRunsBothRegionsPreservesOriginalAndSourceLips() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val source=inst.context.assets.open("v66_beard_fixture.jpg")
            .use{checkNotNull(BitmapFactory.decodeStream(it))}
        try {
            val arr=JSONArray(inst.context.assets.open("v66_beard_fixture_landmarks.json")
                .bufferedReader().use{it.readText()})
            val landmarks=(0 until arr.length()).map {i->
                val pt=arr.getJSONArray(i)
                NormalizedLandmark.create(
                    pt.getDouble(0).toFloat(),pt.getDouble(1).toFloat(),
                    pt.getDouble(2).toFloat()
                )
            }
            val w=source.width;val h=source.height
            val before=IntArray(w*h).apply{source.getPixels(this,0,w,0,0,w,h)}
            val t=SystemClock.elapsedRealtime()
            val stages=mutableListOf<String>()
            val outcome=SmartShaveAuto.run(
                context,source,faceIndex=0,face=landmarks,
                onStatus={stages.add(it)}
            )
            val ms=SystemClock.elapsedRealtime()-t
            try {
                assertNotSame("Must produce an edited copy",source,outcome.image)
                assertEquals(w,outcome.image.width)
                assertEquals(h,outcome.image.height)
                assertTrue("Moustache stage should succeed on physical ARM64",
                    outcome.moustacheApplied)
                assertTrue("Specialist chin should be selected when bundled",
                    outcome.beardStage==SmartShaveAuto.BeardStage.CHIN_STUDENT)
                val after=IntArray(w*h).apply{
                    outcome.image.getPixels(this,0,w,0,0,w,h)
                }
                val guard=LipSafeRasterGuard.fromFace(landmarks,w,h)
                var lipCount=0;var changed=0
                for(i in before.indices) {
                    if(guard.atIndex(i)) {
                        assertEquals("Original lips must be exact",before[i],after[i])
                        lipCount++
                    }
                    if(before[i]!=after[i])changed++
                }
                assertTrue(lipCount>100)
                assertTrue("Should edit at least some facial hair",changed>400)
                val afterOriginal=IntArray(w*h).apply {
                    source.getPixels(this,0,w,0,0,w,h)
                }
                assertArrayEquals("Source photo must remain byte-exact",
                    before,afterOriginal)
                assertEquals("Background untouched",before[0],after[0])
                Log.i("KeeperOneTapTest",
                    "engine="+outcome.beardStage+" total_ms="+ms+
                    " modified="+changed+" protected_lips="+lipCount+
                    " status_events="+stages.size)
            }finally{if(outcome.image!==source)outcome.image.recycle()}
        }finally{source.recycle()}
    }
}
