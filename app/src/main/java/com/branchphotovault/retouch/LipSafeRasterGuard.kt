package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Smart Shave V6: source-exact protected lips and adjacent vermilion border.
 *
 * Rasterizes a polygon + padded edge around the full upper AND lower lip
 * contour. It NEVER authorizes a facial edit, only blocks it. Protects
 * everything it marks even after optional AI refinement, preventing warped
 * lip edges. The margin scales with actual mouth size, not image resolution.
 */
internal class LipSafeRasterGuard private constructor(
    private val protected: BooleanArray,
    private val lipContour: List<PointF>,
    private val mouthSpan: Float,
    val width: Int,
    val height: Int
) {
    fun at(x: Int,y: Int): Boolean =
        if (x in 0 until width && y in 0 until height)
            protected[y*width+x] else true

    fun atIndex(index: Int): Boolean =
        index in protected.indices && protected[index]

    /**
     * The INNER HAIR RING is skin immediately OUTSIDE the true vermilion
     * contour. Do not confuse it with protected lip pixels: those remain
     * exact original pixels. This check alone never grants edit permission.
     */
    fun inInnerHairRing(x: Int,y: Int): Boolean {
        if (at(x,y)) return false
        val maxDist=max(3f,mouthSpan*.20f)
        val radius2=maxDist*maxDist
        var best=Float.POSITIVE_INFINITY
        for(i in lipContour.indices) {
            val p=lipContour[i]
            val q=lipContour[(i+1)%lipContour.size]
            val dx=q.x-p.x
            val dy=q.y-p.y
            val len=dx*dx+dy*dy
            val t=if(len<.0001f)0f else
                (((x-p.x)*dx+(y-p.y)*dy)/len).coerceIn(0f,1f)
            val ex=x-p.x-t*dx
            val ey=y-p.y-t*dy
            val squared=ex*ex+ey*ey
            if(squared<best)best=squared
        }
        return best<=radius2
    }

    fun protectOriginal(original: IntArray, altered: IntArray): Int {
        require(original.size == width*height && altered.size == original.size)
        var fixed=0
        for(i in original.indices) {
            if(protected[i] && altered[i] != original[i]) {
                altered[i]=original[i]
                fixed++
            }
        }
        return fixed
    }

    companion object {
        private val OUTER=intArrayOf(61,40,37,0,267,270,291,321,314,17,84,91)
        private val NOSE=intArrayOf(98,2,327,4)
        fun fromFace(face: List<NormalizedLandmark>,w:Int,h:Int):LipSafeRasterGuard {
            require(face.size>454 && w>0 && h>0)
            fun p(i:Int)=PointF(face[i].x()*w,face[i].y()*h)
            val mouth=OUTER.map(::p)
            val nose=NOSE.map(::p)
            val left=p(61); val right=p(291)
            val dx=right.x-left.x;val dy=right.y-left.y
            val mouthSpan=kotlin.math.hypot(dx,dy).coerceAtLeast(1f)
            return fromPolygons(mouth,nose,mouthSpan,w,h)
        }
        /** Used in deterministic Android instrumentation tests. */
        fun fromPolygons(
            mouth:List<PointF>,nose:List<PointF>,
            mouthSpan:Float,w:Int,h:Int
        ):LipSafeRasterGuard {
            require(mouth.size>=3 && nose.size>=3 && w>0 && h>0 && mouthSpan>0f)
            fun path(points:List<PointF>):Path = Path().apply {
                points.forEachIndexed { index,point ->
                    if(index==0) moveTo(point.x,point.y)
                    else lineTo(point.x,point.y)
                }
                close()
            }
            val mouthPath=path(mouth)
            val nosePath=path(nose)
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val px=IntArray(w*h)
            try {
                val canvas=Canvas(bitmap)
                canvas.drawColor(Color.TRANSPARENT)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.WHITE
                    style=Paint.Style.FILL
                    strokeJoin=Paint.Join.ROUND
                    strokeCap=Paint.Cap.ROUND
                }
                canvas.drawPath(mouthPath,paint)
                canvas.drawPath(nosePath,paint)
                paint.style=Paint.Style.STROKE
                // V6.2: V6 protected a ring of .09 mouth widths on EACH
                // side of the lip contour, leaving visible inner moustache.
                // Keep the vermilion fill fully protected but restrict the
                // outer safety margin to ~.025 mouth widths per side.
                paint.strokeWidth=max(1.5f,mouthSpan*.05f)
                canvas.drawPath(mouthPath,paint)
                paint.strokeWidth=max(1.5f,mouthSpan*.035f)
                canvas.drawPath(nosePath,paint)
                bitmap.getPixels(px,0,w,0,0,w,h)
                return LipSafeRasterGuard(
                    BooleanArray(px.size){ Color.alpha(px[it])>0 },
                    mouth.toList(),mouthSpan,w,h
                )
            } finally { bitmap.recycle() }
        }
    }
}
