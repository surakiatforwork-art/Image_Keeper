package com.branchphotovault.retouch

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OriginalFaceContourGuardTest {
    private val shape=listOf(
        OriginalFaceContourGuard.Point(10f,10f),OriginalFaceContourGuard.Point(90f,10f),
        OriginalFaceContourGuard.Point(90f,70f),OriginalFaceContourGuard.Point(65f,95f),
        OriginalFaceContourGuard.Point(50f,100f),OriginalFaceContourGuard.Point(35f,95f),
        OriginalFaceContourGuard.Point(10f,70f))
    @Test fun protectsContourNeckAndBackgroundExactly() {
        val guard=OriginalFaceContourGuard(shape,30f)
        assertEquals(0f,guard.weightAt(50,100),0f)
        assertEquals(0f,guard.weightAt(50,101),0f)
        assertEquals(0f,guard.weightAt(0,60),0f)
        assertTrue(guard.signedDistance(50f,105f)<0f)
        assertEquals(1f,guard.weightAt(50,90),0f)
    }
    @Test fun transitionFeathersInwardWithoutReservingABroadBeardStrip() {
        val guard=OriginalFaceContourGuard(shape,100f)
        assertTrue(guard.weightAt(50,99)>.01f && guard.weightAt(50,99)<.5f)
        assertTrue(guard.weightAt(50,98)>guard.weightAt(50,99))
        assertEquals(1f,guard.weightAt(50,95),0f)
        assertEquals(0f,guard.weightAt(50,102),0f)
    }
    @Test fun signedDistanceRespectsTiltAndScale() {
        val original=OriginalFaceContourGuard(shape,30f)
        for(degrees in listOf(-45,20,60)) {
            val angle=Math.toRadians(degrees.toDouble()); val a=cos(angle).toFloat(); val b=sin(angle).toFloat()
            fun transform(p:OriginalFaceContourGuard.Point)=OriginalFaceContourGuard.Point(
                (p.x*a-p.y*b)*2.3f+80,(p.x*b+p.y*a)*2.3f+10)
            val changed=OriginalFaceContourGuard(shape.map(::transform),69f)
            for(point in listOf(OriginalFaceContourGuard.Point(50f,85f),OriginalFaceContourGuard.Point(50f,110f))) {
                val mapped=transform(point)
                assertEquals(original.signedDistance(point.x,point.y)*2.3f,changed.signedDistance(mapped.x,mapped.y),.001f)
            }
        }
    }
    @Test fun nonFiniteSamplesFailClosed() {
        val guard=OriginalFaceContourGuard(shape,30f)
        assertTrue(guard.signedDistance(Float.NaN,20f)<0f)
    }
    @Test(expected=IllegalArgumentException::class) fun degenerateOutlineIsRejected() {
        OriginalFaceContourGuard(List(3) { OriginalFaceContourGuard.Point(1f,1f) },30f)
    }
}
