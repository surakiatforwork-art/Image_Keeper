package com.branchphotovault.retouch

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A source-anchored silhouette, not an estimate of the skin hidden underneath a beard. */
internal class OriginalFaceContourGuard(outline: List<Point>, mouthSpan: Float) {
    data class Point(val x: Float, val y: Float)
    private val polygon=outline.toList()
    private val feather=max(1.5f,mouthSpan*.04f)

    init {
        require(polygon.size>=3 && polygon.all { it.x.isFinite() && it.y.isFinite() })
        require(mouthSpan.isFinite() && mouthSpan>0)
        val twiceArea=polygon.indices.sumOf { i ->
            val p=polygon[i]; val q=polygon[(i+1)%polygon.size]
            p.x.toDouble()*q.y-q.x.toDouble()*p.y
        }
        require(kotlin.math.abs(twiceArea)>1e-4)
    }

    /** Positive inside, zero on the silhouette, negative outside, in source pixels. */
    fun signedDistance(x: Float,y: Float): Float {
        if(!x.isFinite() || !y.isFinite()) return Float.NEGATIVE_INFINITY
        var inside=false
        var best=Float.POSITIVE_INFINITY
        for(i in polygon.indices) {
            val p=polygon[i]; val q=polygon[(i+1)%polygon.size]
            if((p.y>y)!=(q.y>y) && x<(q.x-p.x)*(y-p.y)/(q.y-p.y)+p.x) inside=!inside
            val dx=q.x-p.x; val dy=q.y-p.y
            val squared=dx*dx+dy*dy
            val t=if(squared<=1e-8f) 0f else (((x-p.x)*dx+(y-p.y)*dy)/squared).coerceIn(0f,1f)
            val ex=x-p.x-t*dx; val ey=y-p.y-t*dy
            best=min(best,ex*ex+ey*ey)
        }
        val distance=sqrt(best)
        return if(distance<=1e-4f) 0f else if(inside) distance else -distance
    }

    fun weightAt(x: Int,y: Int): Float {
        val depth=signedDistance(x.toFloat(),y.toFloat())
        if(depth<=0f) return 0f
        val t=(depth/feather).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }

    companion object {
        private val OVAL=intArrayOf(10,338,297,332,284,251,389,356,454,323,361,288,
            397,365,379,378,400,377,152,148,176,149,150,136,172,58,132,93,234,
            127,162,21,54,103,67,109)

        fun fromFace(face: List<NormalizedLandmark>,width: Int,height: Int): OriginalFaceContourGuard {
            require(face.size>454 && width>0 && height>0)
            val span=hypot((face[61].x()-face[291].x())*width,(face[61].y()-face[291].y())*height)
            return OriginalFaceContourGuard(OVAL.map { Point(face[it].x()*width,face[it].y()*height) },span)
        }
    }
}
