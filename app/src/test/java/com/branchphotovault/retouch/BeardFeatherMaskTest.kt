package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test

class BeardFeatherMaskTest {
    @Test fun conservativeMatteDoesNotDilateSkinOutsideSourceBeard() {
        val probabilities=FloatArray(224*224) { i -> if(i%224 in 50..173 && i/224 in 50..173) .9f else 0f }
        val unchanged=probabilities.copyOf()
        val mask=BeardSegmentationTool.BeardMask(0f,0f,224f,probabilities,1,.9f)
        val old=BeardFeatherMask(mask)
        val revised=BeardFeatherMask(mask,preserveSourceBoundary=true)
        assertTrue(old.at(42,100)>0f)
        assertEquals(0f,revised.at(42,100),0f)
        assertEquals(1f,revised.at(100,100),.001f)
        assertTrue(revised.at(50,100)<revised.at(54,100))
        assertArrayEquals(unchanged,probabilities,0f)
    }
    @Test fun closingKeepsSmallInteriorConfidenceHolesFromBecomingUnretouchedIslands() {
        val probabilities=FloatArray(224*224) { i -> if(i%224 in 50..173 && i/224 in 50..173) .9f else 0f }
        for(y in 99..101) for(x in 99..101) probabilities[y*224+x]=0f
        val mask=BeardSegmentationTool.BeardMask(0f,0f,224f,probabilities,1,.9f)
        assertEquals(1f,BeardFeatherMask(mask,preserveSourceBoundary=true).at(100,100),.001f)
    }
}
