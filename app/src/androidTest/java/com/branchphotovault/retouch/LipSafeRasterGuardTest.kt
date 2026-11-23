package com.branchphotovault.retouch

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android without needing an actual face or media library. */
@RunWith(AndroidJUnit4::class)
class LipSafeRasterGuardTest {
    private val lips=listOf(
        PointF(36f,40f),PointF(44f,36f),PointF(52f,40f),
        PointF(52f,44f),PointF(44f,48f),PointF(36f,44f)
    )
    private val nose=listOf(
        PointF(40f,27f),PointF(44f,24f),PointF(48f,27f),PointF(44f,32f)
    )
    @Test fun protectsBothLipEdgesAndNose() {
        val g=LipSafeRasterGuard.fromPolygons(lips,nose,16f,96,96)
        assertTrue(g.at(44,40))
        assertTrue(g.at(44,47))
        assertTrue(g.at(44,27))
        assertFalse(g.at(0,0))
        assertFalse(g.at(10,60))
    }
    @Test fun innerRingIsEditableButActualLipRemainsProtected() {
        val g=LipSafeRasterGuard.fromPolygons(lips,nose,16f,96,96)
        // The upper lip vertex is at (44,36). A beard hair at y=34
        // must not be excluded merely because it is adjacent to the lip.
        assertTrue(g.at(44,36))
        assertFalse(g.at(44,34))
        assertTrue(g.inInnerHairRing(44,34))
        assertFalse(g.inInnerHairRing(44,36))
        assertFalse(g.inInnerHairRing(10,70))
        assertTrue(g.at(44,40))
    }

    @Test fun restoresOriginalPixelExactWithoutChangingUnprotectedArea() {
        val g=LipSafeRasterGuard.fromPolygons(lips,nose,16f,96,96)
        val old=IntArray(96*96){ it }
        val proposed=IntArray(96*96){ it+123 }
        val fixed=g.protectOriginal(old,proposed)
        assertTrue(fixed>50)
        for(i in old.indices) {
            if(g.atIndex(i)) assertEquals(old[i],proposed[i])
            else assertEquals(old[i]+123,proposed[i])
        }
    }
    @Test fun guardScalesWithFaceAndKeepsOutsideSafe() {
        val scale=2f
        val g=LipSafeRasterGuard.fromPolygons(
            lips.map { PointF(it.x*scale,it.y*scale) },
            nose.map { PointF(it.x*scale,it.y*scale) },32f,192,192)
        assertTrue(g.at(88,80))
        assertFalse(g.at(0,0))
        assertTrue(g.at(-1,0))
    }
}
