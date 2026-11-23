package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test

class ChinContourCompositeTest {
    @Test fun denseBeardInsideKeepsMostOfTheEdit() {
        val inside=ChinContourComposite.weight(.92f,38f,100f,.98f)
        assertTrue("Strong source beard should not vanish from editing",inside>.92f)
    }

    @Test fun weakHairOutsidePhotographedContourIsSuppressed() {
        val outsideLow=ChinContourComposite.weight(.02f,-12f,100f)
        val outsideHair=ChinContourComposite.weight(.95f,-12f,100f)
        assertTrue(outsideLow<.08f)
        assertTrue("Real protruding beard may still be edited",outsideHair>.35f)
        assertTrue(outsideHair>outsideLow*6f)
    }

    @Test fun exactlyAtTheChinContourFadesGradually() {
        val outside=ChinContourComposite.weight(.8f,-2f,100f)
        val edge=ChinContourComposite.weight(.8f,0f,100f)
        val inside=ChinContourComposite.weight(.8f,18f,100f)
        assertTrue(outside<edge)
        assertTrue(edge<inside)
        assertTrue(inside<=1f)
    }

    @Test fun weightsAndResidualChannelAreAlwaysBounded() {
        assertEquals(0f,ChinContourComposite.weight(.5f,5f,30f,0f))
        for (value in listOf(-5f,0f, .3f, .9f, 2f)) {
            val weight=ChinContourComposite.weight(value,-8f,50f,5f)
            assertTrue(weight in 0f..1f)
        }
        assertEquals(255,ChinContourComposite.compositeChannel(245,127,1f))
        assertEquals(0,ChinContourComposite.compositeChannel(3,-128,1f))
        assertEquals(75,ChinContourComposite.compositeChannel(100,-50,.5f))
        assertEquals(100,ChinContourComposite.compositeChannel(100,75,0f))
    }
}
