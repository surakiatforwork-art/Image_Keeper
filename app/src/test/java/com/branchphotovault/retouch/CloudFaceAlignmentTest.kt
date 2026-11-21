package com.branchphotovault.retouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CloudFaceAlignmentTest {
    private fun point(x:Double,y:Double)=CloudFaceAlignment.Point(x,y)

    private val original= listOf(
        point(230.0,295.0), point(540.0,305.0),
        point(385.0,415.0), point(380.0,460.0),
        point(310.0,565.0), point(480.0,568.0),
        point(385.0,378.0)
    )

    @Test fun squareCloudImageIsAlignedWithoutStretchingPortrait() {
        // AILabTools may output 1024×1024 after receiving e.g. 800×1200.
        // Simulate moving the original face uniformly into a square canvas
        // rather than stretching the original portrait to a square.
        val remote = original.map { p -> point(p.x*.84+155.0,p.y*.84+125.0) }
        val fit=CloudFaceAlignment.fit(remote,original)
        assertEquals(1.0/.84,fit.scale,1e-8)
        assertTrue(fit.meanResidual<1e-8)
        assertTrue(fit.worstResidual<1e-8)
        for (i in remote.indices) {
            val output=fit.map(remote[i])
            assertEquals(original[i].x,output.x,1e-7)
            assertEquals(original[i].y,output.y,1e-7)
        }
    }

    @Test fun rotationAndTranslationAreRecovered() {
        val a=.9
        val b=.25
        val remote=original.map { p ->
            point(a*p.x-b*p.y+120, b*p.x+a*p.y+20)
        }
        val fit=CloudFaceAlignment.fit(remote,original)
        assertTrue(fit.meanResidual<.00001)
        val p=fit.map(point(235.0,325.0))
        assertTrue(p.x.isFinite() && p.y.isFinite())
        val xDistance= hypot(
            fit.map(remote[0]).x-fit.map(remote[1]).x,
            fit.map(remote[0]).y-fit.map(remote[1]).y
        )
        val targetDistance=hypot(
            original[0].x-original[1].x,
            original[0].y-original[1].y
        )
        assertEquals(targetDistance,xDistance,1e-7)
    }

    @Test fun mismatchedFaceGeometryProducesVisibleResidual() {
        val remote=original.mapIndexed { index,p ->
            when (index) {
                4 -> point(p.x+115,p.y-50)
                5 -> point(p.x+110,p.y+70)
                else -> p
            }
        }
        val fit=CloudFaceAlignment.fit(remote,original)
        assertTrue("Changed lips should trigger caller safety gate",fit.worstResidual>60)
        assertTrue("Changed lips should trigger caller safety gate",fit.meanResidual>20)
    }

    @Test fun degenerateAndInsufficientLandmarksAreRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CloudFaceAlignment.fit(listOf(point(1.0,1.0)),listOf(point(1.0,1.0)))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CloudFaceAlignment.fit(List(4){point(1.0,1.0)},List(4){point(2.0,2.0)})
        }
    }
}
