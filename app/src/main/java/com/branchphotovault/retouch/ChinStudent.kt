package com.branchphotovault.retouch

import ai.onnxruntime.*
import android.content.Context
import android.graphics.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.nio.FloatBuffer
import kotlin.math.*

internal object ChinStudent {
    const val asset = "chin-student-03.onnx"
    fun apply(ctx: Context, src: Bitmap, base: Bitmap, face: List<NormalizedLandmark>): Bitmap {
        val s=384
        val n=s*s
        val pts=FloatArray(face.size*2) { i -> if(i%2==0) face[i/2].x()*src.width else face[i/2].y()*src.height }
        fun eye(a:Int,b:Int,k:Int)=(pts[2*a+k]+pts[2*b+k])*.5f
        val lx=eye(33,133,0); val ly=eye(33,133,1)
        val rx=eye(263,362,0); val ry=eye(263,362,1)
        require(hypot(rx-lx,ry-ly)>2f)
        val transform=Matrix()
        check(transform.setPolyToPoly(floatArrayOf(lx,ly,rx,ry),0,floatArrayOf(s*.375f,s*.4f,s*.625f,s*.4f),0,2))
        val crop=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888)
        val input=IntArray(n)
        try {
            Canvas(crop).drawBitmap(src,transform,Paint(Paint.FILTER_BITMAP_FLAG))
            crop.getPixels(input,0,s,0,0,s,s)
        } finally { crop.recycle() }
        transform.mapPoints(pts)
        val ids=intArrayOf(61,17,291,454,365,397,377,152,148,176,172,234)
        val poly=ids.mapIndexed { j,i -> PointF(pts[i*2],pts[i*2+1]+if(j in 4..10) 8f else 0f) }
        val cutoff=ceil(pts[35])+2
        val contour=OriginalFaceContourGuard(poly.map { OriginalFaceContourGuard.Point(it.x,it.y) },24f)
        val mask=FloatArray(n) { i -> val y=(i/s).toFloat()
            (min(y-cutoff,contour.signedDistance((i%s).toFloat(),y))/5f).coerceIn(0f,1f) }
        val rgb=FloatArray(3*n) { i -> ((input[i%n] ushr (16-8*(i/n))) and 255)/255f }
        val env=OrtEnvironment.getEnvironment()
        val diff=IntArray(n)
        OrtSession.SessionOptions().use { opt ->
            opt.setIntraOpNumThreads(3)
            env.createSession(ctx.assets.open("retouch/$asset").use { it.readBytes() },opt).use { session ->
                OnnxTensor.createTensor(env,FloatBuffer.wrap(rgb),longArrayOf(1,3,384,384)).use { x ->
                    OnnxTensor.createTensor(env,FloatBuffer.wrap(mask),longArrayOf(1,1,384,384)).use { m ->
                        session.run(mapOf("rgb" to x,"mask" to m)).use { r ->
                            val values=(r[0] as OnnxTensor).floatBuffer
                            fun delta(i:Int)=128+((values[i]-rgb[i])*255).roundToInt().coerceIn(-127,127)
                            for(i in diff.indices) diff[i]=Color.rgb(delta(i),delta(i+n),delta(i+2*n))
                        }
                    }
                }
            }
        }
        val inv=Matrix(); check(transform.invert(inv))
        val bounds=RectF(0f,0f,s.toFloat(),s.toFloat()); inv.mapRect(bounds)
        require(bounds.intersect(0f,0f,src.width.toFloat(),src.height.toFloat()))
        val x=floor(bounds.left).toInt(); val y=floor(bounds.top).toInt()
        val w=ceil(bounds.right).toInt()-x; val h=ceil(bounds.bottom).toInt()-y
        val mapped=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val patch=Bitmap.createBitmap(diff,s,s,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(w*h)
        try {
            mapped.eraseColor(Color.rgb(128,128,128))
            val canvas=Canvas(mapped); canvas.translate(-x.toFloat(),-y.toFloat())
            canvas.drawBitmap(patch,inv,Paint(Paint.FILTER_BITMAP_FLAG))
            mapped.getPixels(pixels,0,w,0,0,w,h)
        } finally { mapped.recycle(); patch.recycle() }
        val guard=LipSafeRasterGuard.fromFace(face,src.width,src.height)
        val colors=IntArray(w*h); base.getPixels(colors,0,w,x,y,w,h)
        for(i in colors.indices) if(!guard.at(x+i%w,y+i/w)) {
            fun channel(k:Int)=(((colors[i] ushr k) and 255)+((pixels[i] ushr k) and 255)-128).coerceIn(0,255)
            colors[i]=Color.rgb(channel(16),channel(8),channel(0))
        }
        return base.copy(Bitmap.Config.ARGB_8888,true).apply { setPixels(colors,0,w,x,y,w,h) }
    }
}
