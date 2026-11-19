package com.branchphotovault.retouch

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SkinToneGuardTest {
    @Test fun preservesSmallCorrections() {
        val proposed = floatArrayOf(135f,130f,138f)
        assertArrayEquals(proposed,SkinToneGuard.apply(floatArrayOf(130f,128f,136f),proposed),.001f)
    }
    @Test fun limitsArtificialBrighteningAndHueShift() {
        assertArrayEquals(floatArrayOf(136f,133f,125f),SkinToneGuard.apply(
            floatArrayOf(100f,128f,130f),floatArrayOf(200f,160f,90f)),.001f)
    }
    @Test fun neverOverDarkensSkin() {
        assertArrayEquals(floatArrayOf(94f,128f,130f),SkinToneGuard.apply(
            floatArrayOf(100f,128f,130f),floatArrayOf(20f,128f,130f)),.001f)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonFiniteValues() {
        SkinToneGuard.apply(floatArrayOf(100f,128f,130f),floatArrayOf(Float.NaN,128f,130f))
    }
}
