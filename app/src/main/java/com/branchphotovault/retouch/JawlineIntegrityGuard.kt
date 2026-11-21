package com.branchphotovault.retouch

import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * Preservation of the actual chin silhouette and neck/cast-shadow.
 *
 * Landmark-aligned lower-face boundary is treated as a NO-EDIT boundary,
 * not a request to erase a beard until an arbitrarily extended neck line.
 * The band is guarded on both sides: exact original pixels at the border
 * and below it, gentle transition toward facial skin above the boundary.
 *
 * An intentionally conservative rule: missing a few beard strands at the
 * jaw is preferable to deforming the visible jaw or its natural cast shadow.
 */
internal class JawlineIntegrityGuard private constructor(
    private val jaw: List<PointF>,
    private val mouthWidth: Float,
    private val a: Float,
    private val b: Float,
    private val tx: Float,
    private val ty: Float
) {
    init {
        require(jaw.size >= 3)
        require(mouthWidth > 0f)
        require(jaw.zipWithNext().all { (p,q) -> p.x <= q.x })
    }

    fun weightAt(x: Int, y: Int): Float =
        weightAligned(a*x + b*y + tx, -b*x + a*y + ty)

    /** 0 = preserve source exactly, 1 = allow existing retouch processing. */
    fun weightAligned(u:Float, v:Float): Float {
        if(!u.isFinite() || !v.isFinite()) return 0f
        if(u < jaw.first().x || u > jaw.last().x) {
            // Do not extrapolate a face mask into neck patches beyond cheeks.
            val endpoint=if(u < jaw.first().x) jaw.first() else jaw.last()
            return if(v >= endpoint.y-mouthWidth*.25f) 0f else 1f
        }
        var low=0
        var high=jaw.lastIndex
        while(high-low>1) {
            val mid=(low+high)/2
            if(jaw[mid].x <= u) low=mid else high=mid
        }
        val p=jaw[low]; val q=jaw[high]
        val fraction=((u-p.x)/max(.001f,q.x-p.x)).coerceIn(0f,1f)
        val boundary=p.y+(q.y-p.y)*fraction
        val insideDepth=boundary-v // positive means toward nose / inside face
        // Protect a strip of original flesh + shadow around the contour.
        val noEdit=max(3.0f,mouthWidth*.17f)
        val fade=max(5.0f,mouthWidth*.25f)
        if(insideDepth <= noEdit) return 0f
        val t=((insideDepth-noEdit)/fade).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }

    fun jawVAt(u: Float):Float {
        if(u<=jaw.first().x) return jaw.first().y
        if(u>=jaw.last().x)return jaw.last().y
        for(i in 0 until jaw.lastIndex) {
            val p=jaw[i];val q=jaw[i+1]
            if(u<=q.x) {
                val t=((u-p.x)/max(.001f,q.x-p.x)).coerceIn(0f,1f)
                return p.y+(q.y-p.y)*t
            }
        }
        return jaw.last().y
    }

    companion object {
        // MediaPipe FaceMesh external lower cheek and jaw contour, ordered
        // left -> chin 152 -> right. Avoid interior mouth or neck landmarks.
        private val JAW_INDICES=intArrayOf(
            234,93,132,58,172,136,150,149,176,148,152,
            377,400,378,379,365,397,288,361,323,454
        )

        fun fromLandmarks(
            landmarks: List<NormalizedLandmark>,
            width:Int,height:Int,
            a:Float,b:Float,tx:Float,ty:Float
        ):JawlineIntegrityGuard {
            require(landmarks.size>454)
            fun point(i:Int):PointF = PointF(landmarks[i].x()*width,landmarks[i].y()*height)
            fun aligned(p:PointF):PointF=PointF(
                a*p.x+b*p.y+tx,-b*p.x+a*p.y+ty
            )
            val left=aligned(point(61))
            val right=aligned(point(291))
            val mouthWidth=abs(right.x-left.x).coerceAtLeast(8f)
            return JawlineIntegrityGuard(
                JAW_INDICES.map { aligned(point(it)) }.sortedBy { it.x },
                mouthWidth,a,b,tx,ty
            )
        }

        /** Synthetic curves allow deterministic tests without mocking ML landmarks. */
        fun fromAlignedPointsForTest(
            points:List<PointF>,mouthWidth:Float
        )=JawlineIntegrityGuard(points.sortedBy { it.x },
            mouthWidth,1f,0f,0f,0f)
    }
}
