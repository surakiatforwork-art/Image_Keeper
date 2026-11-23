package com.branchphotovault.retouch

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResidualHairRepairTest {
    private val w = 100
    private val h = 90
    private fun rgb(v: Int) = Color.rgb(v, v, v)

    @Test fun repairsThinResidualStrandsInsidePermittedRegion() {
        val source=IntArray(w*h) { rgb(145) }
        val generated=IntArray(w*h) { rgb(140) }
        // The same dark whisker remains after the first-generation pass,
        // though the model has lightened it somewhat.
        for (x in 24..75) for (y in 43..44) {
            source[y*w+x]=rgb(65)
            generated[y*w+x]=rgb(80)
        }
        val (out,stats)=ResidualHairRepair.refine(source,generated,w,h,85f) { x,y ->
            x in 15..84 && y in 25..65
        }
        assertTrue("Expected targeted correction", stats.corrected > 10)
        assertTrue("Hair center must become lighter", Color.red(out[43*w+50]) > Color.red(generated[43*w+50]))
        assertEquals("Outside mask is byte-identical", generated[5*w+5],out[5*w+5])
        assertArrayEquals("Source image may not be modified", IntArray(w*h) { i ->
            val y=i/w; val x=i%w
            if(x in 24..75 && y in 43..44) rgb(65) else rgb(145)
        },source)
    }

    @Test fun flatShadowAndJawBoundaryRemainUntouched() {
        val source=IntArray(w*h) { i -> if(i/w >= 47) rgb(55) else rgb(140) }
        val first=source.copyOf()
        val (out,stats)=ResidualHairRepair.refine(source,first,w,h,85f) { _,_ -> true }
        assertArrayEquals("Shadow must stay unchanged",first,out)
        assertEquals(0,stats.corrected)
    }

    @Test fun learnedEvidenceGatesCandidatesButDoesNotExcludeCleanDonorSkin() {
        val source=IntArray(w*h) { rgb(145) }; val prepared=IntArray(w*h) { rgb(140) }
        for(x in 24..75) { source[43*w+x]=rgb(65); prepared[43*w+x]=rgb(80) }
        val (result,stats)=ResidualHairRepair.refine(source,prepared,w,h,85f,strandOnly=true,
            candidateEvidence={x,y -> y==43 && x in 24..50},
            permitted={x,y -> x in 15..84 && y in 25..65})
        assertTrue(stats.corrected>5)
        assertTrue(Color.red(result[43*w+40])>80)
        assertEquals(prepared[43*w+65],result[43*w+65])
        assertEquals(prepared[40*w+40],result[40*w+40])
    }

    @Test fun focusedCleanupDoesNotEraseAmbiguousRoundMoles() {
        val source=IntArray(w*h) { rgb(145) }; val prepared=IntArray(w*h) { rgb(140) }
        source[43*w+50]=rgb(65);prepared[43*w+50]=rgb(80)
        val (result,stats)=ResidualHairRepair.refine(source,prepared,w,h,85f,strandOnly=true,
            candidateEvidence={_,_ -> true},permitted={_,_ -> true})
        assertArrayEquals(prepared,result)
        assertEquals(0,stats.corrected)
    }

    @Test fun protectedPixelsRemainUnchanged() {
        val source=IntArray(w*h) { rgb(140) }
        val first=source.copyOf()
        for(x in 20..78) { source[40*w+x]=rgb(40); first[40*w+x]=rgb(80) }
        val (out,stats)=ResidualHairRepair.refine(source,first,w,h,85f) { x,y ->
            x in 22..78 && y in 33..38
        }
        assertArrayEquals("Rejected lip/neck must remain untouched",first,out)
        assertEquals(0,stats.corrected)
    }
}
