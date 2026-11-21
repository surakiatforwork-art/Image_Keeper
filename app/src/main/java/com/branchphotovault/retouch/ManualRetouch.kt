package com.branchphotovault.retouch

import android.graphics.*

data class RetouchPoint(val x: Float, val y: Float)
data class RetouchStroke(
    val points: List<RetouchPoint>,
    val radius: Float,
    val restore: Boolean,
    // With healing enabled, sampled skin is regenerated independently of
    // AttGAN; it works even if the AI never edited these pixels.
    val heal: Boolean = false,
    val donor: RetouchPoint? = null
)
data class RetouchBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Edits only the generated layer; the source and protected pixels remain unchanged. */
object ManualRetouch {
    /**
     * Compute the transparent edit layer once per brush/frame edit, not once
     * per slider movement. UI can vary opacity on the GPU immediately.
     */
    fun prepareLayer(generated: Bitmap, strokes: List<RetouchStroke>,
        bounds: RetouchBounds? = null): Bitmap {
        val w=generated.width; val h=generated.height
        val mask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val layer=generated.copy(Bitmap.Config.ARGB_8888,true)
        try {
            mask.eraseColor(Color.WHITE)
            val canvas=Canvas(mask)
            for (stroke in strokes) {
                if(stroke.points.isEmpty()) continue
                // True manual healing, NOT repainting an unchanged AttGAN
                // result: use a clean-skin sample and source texture to
                // replace hair wherever the user paints.
                if(stroke.heal && !stroke.restore) ManualSkinHeal.repair(layer,stroke)
                val radius=(stroke.radius*minOf(w,h)).coerceAtLeast(1f)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.WHITE; style=Paint.Style.STROKE; strokeWidth=radius*2
                    strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
                    maskFilter=BlurMaskFilter(radius*.25f,BlurMaskFilter.Blur.NORMAL)
                    if(stroke.restore) xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                }
                val path=Path()
                stroke.points.forEachIndexed { index,p ->
                    if(index==0) path.moveTo(p.x*w,p.y*h) else path.lineTo(p.x*w,p.y*h)
                }
                if(stroke.points.size==1) canvas.drawCircle(stroke.points[0].x*w,stroke.points[0].y*h,radius,paint.apply { style=Paint.Style.FILL })
                else canvas.drawPath(path,paint)
            }
            Canvas(layer).drawBitmap(mask,0f,0f,Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            // Pre-clip the overlay so the frame never needs to be redrawn as
            // the strength changes.
            bounds?.let {
                val clip=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                try {
                    Canvas(clip).apply {
                        clipRect(it.left*w,it.top*h,it.right*w,it.bottom*h)
                        drawBitmap(layer,0f,0f,null)
                    }
                    layer.recycle()
                    return clip.copy(Bitmap.Config.ARGB_8888,false)
                } finally { clip.recycle() }
            }
            return layer
        } catch(error: Throwable) { if(!layer.isRecycled) layer.recycle(); throw error }
        finally { mask.recycle() }
    }

    /** Full-resolution, exact final composite, performed off the UI thread. */
    fun composeLayer(source: Bitmap, layer: Bitmap, strength: Float): Bitmap {
        require(source.width == layer.width && source.height == layer.height)
        val output=source.copy(Bitmap.Config.ARGB_8888,true)
        try {
            Canvas(output).drawBitmap(layer,0f,0f,
                Paint().apply { alpha=(strength.coerceIn(0f,1f)*255).toInt() })
            return output
        } catch(error: Throwable) { output.recycle(); throw error }
    }

    fun compose(source: Bitmap, generated: Bitmap, strokes: List<RetouchStroke>,
        strength: Float, bounds: RetouchBounds? = null): Bitmap {
        val layer=prepareLayer(generated,strokes,bounds)
        return try { composeLayer(source,layer,strength) }
            finally { layer.recycle() }
    }
}
