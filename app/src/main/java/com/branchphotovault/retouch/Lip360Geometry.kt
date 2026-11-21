package com.branchphotovault.retouch

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Smart Shave V5 research primitive: a *continuous 360 degree search zone*
 * based on the entire outer lip contour, not rectangular moustache boxes.
 *
 * IMPORTANT: The output says WHERE hair may be searched for, NOT that there
 * is hair present, and must never be used alone as an edit/repaint mask.
 * Learned hair evidence + protected anatomy must authorize reconstruction.
 *
 * Pure Kotlin so geometry can be tested without any Android runtime.
 * Does not change the existing Beta 19 AttGAN engine.
 */
internal class Lip360Geometry(
    private val lipOuter: List<Pt>,
    private val safeFace: List<Pt>,
    private val protectedFeatures: List<List<Pt>>,
    val mouthWidthPx: Float,
    val faceWidthPx: Float
) {
    data class Pt(val x: Float, val y: Float)
    data class Sample(
        val searchable: Boolean,
        val protected: Boolean,
        val angularDegrees: Float,
        val distanceToLipsPx: Float,
        val normalizedRadius: Float
    )

    init {
        require(lipOuter.size >= 3)
        require(safeFace.size >= 3)
        require(mouthWidthPx > 0f && faceWidthPx > 0f)
    }

    val center: Pt by lazy {
        Pt(lipOuter.sumOf { it.x.toDouble() }.toFloat()/lipOuter.size,
            lipOuter.sumOf { it.y.toDouble() }.toFloat()/lipOuter.size)
    }

    /**
     * All angular directions can be searched (if inside the face and not
     * inside the lips/nose/edge protection). No above/below/left/right rules.
     */
    fun sample(x: Float,y: Float): Sample {
        val theta=(Math.toDegrees(atan2((y-center.y).toDouble(),
            (x-center.x).toDouble()))+360.0)%360.0
        val distance=distanceToPolyline(x,y,lipOuter)
        val protected = insidePolygon(x,y,lipOuter) ||
            protectedFeatures.any { insidePolygon(x,y,it) } ||
            distance < max(2f,mouthWidthPx*.06f) ||
            distanceToPolyline(x,y,safeFace) < max(2f,faceWidthPx*.025f)
        val maxRadius = max(mouthWidthPx*2.8f,faceWidthPx*.78f)
        val searchable = insidePolygon(x,y,safeFace) &&
            !protected && distance<=maxRadius
        return Sample(searchable, protected, theta.toFloat(), distance,
            distance/maxRadius)
    }

    companion object {
        private fun insidePolygon(x:Float,y:Float,poly:List<Pt>):Boolean {
            var odd=false
            var j=poly.size-1
            for(i in poly.indices) {
                val a=poly[i];val b=poly[j]
                if((a.y>y)!=(b.y>y) &&
                    x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x) odd=!odd
                j=i
            }
            return odd
        }
        private fun distanceToPolyline(x:Float,y:Float,line:List<Pt>):Float {
            var best=Float.POSITIVE_INFINITY
            for(i in line.indices) {
                val a=line[i]
                val b=line[(i+1)%line.size]
                val dx=b.x-a.x;val dy=b.y-a.y
                val length2=dx*dx+dy*dy
                val t=if(length2<1e-4f) 0f else
                    (((x-a.x)*dx+(y-a.y)*dy)/length2).coerceIn(0f,1f)
                val nx=a.x+t*dx
                val ny=a.y+t*dy
                best=min(best,hypot(x-nx,y-ny))
            }
            return best
        }
    }
}
