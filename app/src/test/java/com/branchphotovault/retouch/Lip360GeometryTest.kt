package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class Lip360GeometryTest {
    private fun pt(x:Number,y:Number)=Lip360Geometry.Pt(x.toFloat(),y.toFloat())
    private val lips=listOf(pt(80,98),pt(85,90),pt(100,87),pt(115,90),
        pt(120,98),pt(115,108),pt(100,113),pt(85,108))
    private val face=listOf(pt(12,50),pt(185,50),pt(200,140),
        pt(185,207),pt(100,222),pt(15,207),pt(0,140))
    private val model=Lip360Geometry(lips,face,emptyList(),40f,190f)

    @Test fun allEightDirectionsAreSearchedWithoutQuadrantRestrictions() {
        val cx=100f;val cy=100f
        for(deg in 0 until 360 step 45) {
            val a=Math.toRadians(deg.toDouble())
            val x=cx+(cos(a)*38).toFloat()
            val y=cy+(sin(a)*38).toFloat()
            val v=model.sample(x,y)
            assertTrue("Direction $deg should be searchable",v.searchable)
            assertFalse("Direction $deg is not protected",v.protected)
            assertEquals(deg.toFloat(),v.angularDegrees,2f)
        }
    }

    @Test fun lipInteriorAndOuterRimAreNeverPaintable() {
        assertTrue(model.sample(100f,100f).protected)
        assertFalse(model.sample(100f,100f).searchable)
        assertTrue(model.sample(100f,85f).protected)
        assertFalse(model.sample(100f,85f).searchable)
    }

    @Test fun faceContourIsKeptAndCannotBeWidenedByLipSearch() {
        assertFalse(model.sample(205f,95f).searchable)
        assertFalse(model.sample(-2f,130f).searchable)
        val atJaw=model.sample(100f,221f)
        assertTrue(atJaw.protected)
        assertFalse(atJaw.searchable)
    }

    @Test fun noseIsExcludedEvenInside360Ring() {
        val nose=listOf(pt(92,57),pt(110,57),pt(111,75),pt(91,75))
        val withNose=Lip360Geometry(lips,face,listOf(nose),40f,190f)
        assertTrue(withNose.sample(100f,65f).protected)
        assertFalse(withNose.sample(100f,65f).searchable)
        assertTrue(withNose.sample(142f,100f).searchable)
    }

    @Test fun sameRelativeFaceGeometryWorksAtDifferentSizesAndRotations() {
        val scaled=2.7f
        fun scale(p:Lip360Geometry.Pt)=pt(p.x*scaled,p.y*scaled)
        val scaledModel=Lip360Geometry(lips.map(::scale),face.map(::scale),
            emptyList(),40f*scaled,190f*scaled)
        for(deg in 0 until 360 step 30) {
            val angle=deg*PI/180
            val x=100f+(cos(angle)*38).toFloat()
            val y=100f+(sin(angle)*38).toFloat()
            assertEquals(model.sample(x,y).searchable,
                scaledModel.sample(x*scaled,y*scaled).searchable)
        }
    }
}
