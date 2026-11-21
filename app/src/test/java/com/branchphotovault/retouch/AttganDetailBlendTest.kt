package com.branchphotovault.retouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttganDetailBlendTest {
    @Test fun zeroWeightIsBitIdenticalToVerifiedBaseline() {
        for (value in 0..255 step 7) {
            assertEquals(value,AttganDetailBlend.channel(value,0f,255f,0f))
        }
    }
    @Test fun correctionNeverExceedsTwentyChannelLevels() {
        for (value in 0..255 step 3) {
            val result=AttganDetailBlend.channel(value,-150f,490f,.8f)
            assertTrue(result in 0..255)
            assertTrue(kotlin.math.abs(result-value) <= 20)
        }
    }
    @Test fun reversedShiftCanDarkenWithoutDesaturatingSourceOutsideMask() {
        assertEquals(108,AttganDetailBlend.channel(120,180f,160f,.6f))
        assertEquals(132,AttganDetailBlend.channel(120,160f,180f,.6f))
        assertEquals(120,AttganDetailBlend.channel(120,180f,160f,-1f))
    }
    @Test fun balancedPixelDoesNotShiftSkinHueOrExceedSafeLimit() {
        val baseline=0xFFAB8471.toInt()
        val after=AttganDetailBlend.balancedPixel(
            baseline,
            -.6f,-.4f,-.2f,
            -.25f,-.65f,.50f,
            .45f
        )
        val b=intArrayOf((baseline ushr 16) and 255,(baseline ushr 8) and 255,baseline and 255)
        val a=intArrayOf((after ushr 16) and 255,(after ushr 8) and 255,after and 255)
        val deltas=a.indices.map { a[it]-b[it] }
        assertTrue(deltas.all { kotlin.math.abs(it) <= 9 })
        assertTrue(deltas.max()-deltas.min() <= 3)
        assertEquals((baseline ushr 24) and 255,(after ushr 24) and 255)
    }

    @Test fun balancedPixelZeroWeightAndBadOutputAreUnchanged() {
        val v=0xFF947A62.toInt()
        assertEquals(v,AttganDetailBlend.balancedPixel(v,.8f,.9f,.9f,-1f,-1f,-1f,0f))
        assertEquals(v,AttganDetailBlend.balancedPixel(v,.1f,.2f,.3f,Float.NaN,.5f,.4f,.8f))
    }

    @Test fun unusableSecondPassCannotCorruptExistingPhoto() {
        assertEquals(94,AttganDetailBlend.channel(94,Float.NaN,100f,.6f))
        assertEquals(94,AttganDetailBlend.channel(94,100f,Float.POSITIVE_INFINITY,.6f))
    }
}
