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
    @Test fun unusableSecondPassCannotCorruptExistingPhoto() {
        assertEquals(94,AttganDetailBlend.channel(94,Float.NaN,100f,.6f))
        assertEquals(94,AttganDetailBlend.channel(94,100f,Float.POSITIVE_INFINITY,.6f))
    }
}
