package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test

class ResidualHairDetailGateTest {
    private fun grey(i:Int)=(0xFF shl 24) or (i shl 16) or (i shl 8) or i

    @Test fun uniformSkinDoesNotQualifyAsResidualHair() {
        val image=IntArray(25*25){grey(168)}
        assertEquals(0f,ResidualHairDetailGate.weight(image,25,25,12,12,5),0.00001f)
    }
    @Test fun darkHairAgainstSkinGetsHighWeight() {
        val image=IntArray(25*25){grey(168)}
        image[12*25+12]=grey(100)
        assertTrue(ResidualHairDetailGate.weight(image,25,25,12,12,5)>.9f)
    }
    @Test fun brightPatchesDoNotTriggerHairRepair() {
        val image=IntArray(25*25){grey(168)}
        image[12*25+12]=grey(200)
        assertEquals(0f,ResidualHairDetailGate.weight(image,25,25,12,12,5),0.00001f)
    }
    @Test fun nearEdgeWorksWithoutCrash() {
        val image=IntArray(8*8){grey(140)}
        assertEquals(0f,ResidualHairDetailGate.weight(image,8,8,0,0,6),.00001f)
    }
}
