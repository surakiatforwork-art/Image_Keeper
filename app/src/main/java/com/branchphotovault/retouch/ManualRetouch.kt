package com.branchphotovault.retouch

import android.graphics.*

data class RetouchPoint(val x: Float, val y: Float)
data class RetouchStroke(val points: List<RetouchPoint>, val radius: Float, val restore: Boolean)
data class RetouchBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Edits only the generated layer; the source and protected pixels remain unchanged. */
object ManualRetouch {
    fun compose(source: Bitmap, generated: Bitmap, strokes: List<RetouchStroke>,
        strength: Float, bounds: RetouchBounds? = null): Bitmap {
        require(source.width == generated.width && source.height == generated.height)
        val w=source.width; val h=source.height
        val mask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val layer=generated.copy(Bitmap.Config.ARGB_8888,true)
        try {
            mask.eraseColor(Color.WHITE)
            val canvas=Canvas(mask)
            for (stroke in strokes) {
                if(stroke.points.isEmpty()) continue
                val radius=(stroke.radius*minOf(w,h)).coerceAtLeast(1f)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.WHITE; style=Paint.Style.STROKE; strokeWidth=radius*2
                    strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
                    maskFilter=BlurMaskFilter(radius*.25f,BlurMaskFilter.Blur.NORMAL)
                    if(stroke.restore) xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
                val path=Path()
                stroke.points.forEachIndexed { index,p -> if(index==0) path.moveTo(p.x*w,p.y*h) else path.lineTo(p.x*w,p.y*h) }
                if(stroke.points.size==1) canvas.drawCircle(stroke.points[0].x*w,stroke.points[0].y*h,radius,paint.apply { style=Paint.Style.FILL })
                else canvas.drawPath(path,paint)
            }
            Canvas(layer).drawBitmap(mask,0f,0f,Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            val output=source.copy(Bitmap.Config.ARGB_8888,true)
            try {
                Canvas(output).apply {
                    bounds?.let { clipRect(it.left*w,it.top*h,it.right*w,it.bottom*h) }
                    drawBitmap(layer,0f,0f,Paint().apply { alpha=(strength.coerceIn(0f,1f)*255).toInt() })
                }
                return output
            } catch(error: Throwable) { output.recycle(); throw error }
        } finally { mask.recycle(); layer.recycle() }
    }
}
