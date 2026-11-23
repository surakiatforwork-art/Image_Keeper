package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test

class V6CoverageBlendTest {
    @Test fun noHairZoneNeverIncreasesCoverage() {
        assertEquals(.3f,V6CoverageBlend.confidence(.3f,.9f,1,25f,false,1f),.00001f)
    }
    @Test fun protectedFaceLabelsCannotGainCoverage() {
        for(label in listOf(0,2,3,4,6,7,10,11,12,13,15,16,18))
            assertEquals(.3f,V6CoverageBlend.confidence(.3f,.9f,label,30f,true,1f),.00001f)
    }
    @Test fun hairEvidenceBoostsWithinSkinZone() {
        val improved=V6CoverageBlend.confidence(.4f,.8f,1,20f,true,.84f)
        assertTrue(improved>.65f)
        assertTrue(improved<=1f)
    }
    @Test fun evidenceFreeShadowRemainsUnchanged() {
        assertEquals(.22f,V6CoverageBlend.confidence(.22f,0f,1,1f,true,1f),.00001f)
    }
    @Test fun zeroStrengthAndStrengthClampingBehave() {
        assertEquals(.33f,V6CoverageBlend.confidence(.33f,.8f,1,22f,true,0f),.00001f)
        assertEquals(
            V6CoverageBlend.confidence(.33f,.8f,1,22f,true,1f),
            V6CoverageBlend.confidence(.33f,.8f,1,22f,true,9f),.00001f
        )
    }
    @Test fun neckRequiresStrongLearnedHairEvidence() {
        assertEquals(.2f,V6CoverageBlend.confidence(.2f,.1f,14,24f,true,1f),.00001f)
        assertTrue(V6CoverageBlend.confidence(.2f,.7f,14,24f,true,1f)>.2f)
    }
}
