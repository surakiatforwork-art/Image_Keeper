package com.branchphotovault.retouch

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot
import kotlin.math.max

/**
 * Android / MediaPipe adapter for the pure 360° search geometry.
 * Research-only: caller still needs a hair detector, a face parser and
 * a reconstruction model before compositing pixels.
 */
internal object Lip360Landmarks {
    private val OUTER_LIPS = intArrayOf(61,40,37,0,267,270,291,321,314,17,84,91)
    private val NOSE = intArrayOf(98,2,327,4)
    private val JAW = intArrayOf(
        234,93,132,58,172,136,150,149,176,148,152,
        377,400,378,379,365,397,288,361,323,454
    )

    fun fromFace(
        landmarks: List<NormalizedLandmark>,
        width: Int,
        height: Int
    ): Lip360Geometry {
        require(landmarks.size > 454 && width>0 && height>0)
        fun p(i:Int)=Lip360Geometry.Pt(landmarks[i].x()*width,landmarks[i].y()*height)
        fun points(ids:IntArray)=ids.map(::p)
        val lips=points(OUTER_LIPS)
        val nose=points(NOSE)
        val jaw=points(JAW)
        val mouthWidth=hypot(p(61).x-p(291).x,p(61).y-p(291).y)
        val faceWidth=max(1f,hypot(p(234).x-p(454).x,p(234).y-p(454).y))
        val nosePoint=p(2)
        val topY=nosePoint.y-mouthWidth*.18f
        val upperRight=Lip360Geometry.Pt(p(454).x+faceWidth*.08f,topY)
        val upperLeft=Lip360Geometry.Pt(p(234).x-faceWidth*.08f,topY)
        val envelope=jaw+listOf(upperRight,upperLeft)
        return Lip360Geometry(lips,envelope,listOf(nose),
            mouthWidthPx=mouthWidth.coerceAtLeast(1f),
            faceWidthPx=faceWidth)
    }
}
