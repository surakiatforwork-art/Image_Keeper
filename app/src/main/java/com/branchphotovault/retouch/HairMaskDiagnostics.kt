package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * V3 diagnostic candidate map, NOT a trained facial-hair segmentation model.
 * Red: local hair-like dark texture inside editable mask.
 * Orange: hair-like texture in the broad lower-face/neck study area but outside
 * the editable mask. Green: other editable pixels.
 *
 * The map is preview-only. It must never be used to drive edits or be saved
 * as an output photograph. Smooth cast shadows are intentionally excluded.
 */
object HairMaskDiagnostics {
    const val RED = 0xFFFF415D.toInt()
    const val ORANGE = 0xFFFFB020.toInt()
    const val GREEN = 0xFF32D99C.toInt()
    data class Stats(val accepted: Int, val excluded: Int, val editable: Int)

    fun render(
        original: IntArray, width: Int, height: Int,
        editable: (Int, Int) -> Boolean,
        normalized: (Int, Int) -> Pair<Float, Float>,
        upperLip: Float, chin: Float,
        left: Float, right: Float
    ): Pair<Bitmap, Stats> {
        require(width > 0 && height > 0 && original.size == width*height)
        val out = original.copyOf()
        var accepted=0
        var excluded=0
        var editableCount=0
        val top=min(upperLip, chin)-12f
        val bottom=max(upperLip, chin)+54f
        val l=min(left,right)-30f
        val r=max(left,right)+30f
        fun light(px:Int):Float = Color.red(px)*.299f + Color.green(px)*.587f + Color.blue(px)*.114f
        fun mark(originalPixel:Int,overlay:Int,opacity:Float):Int {
            val t=opacity.coerceIn(0f,1f); val s=1f-t
            return Color.rgb(
                (Color.red(originalPixel)*s+Color.red(overlay)*t).toInt().coerceIn(0,255),
                (Color.green(originalPixel)*s+Color.green(overlay)*t).toInt().coerceIn(0,255),
                (Color.blue(originalPixel)*s+Color.blue(overlay)*t).toInt().coerceIn(0,255))
        }
        for(y in 4 until height-4) for(x in 4 until width-4) {
            val (u,v)=normalized(x,y)
            if(u !in l..r || v !in top..bottom) continue
            val i=y*width+x
            val isEditable=editable(x,y)
            if(isEditable) editableCount++
            val c=light(original[i])
            // Require strand-like dark center with balanced side samples.
            // A smooth gradual jaw/neck shadow should not pass this test.
            var confidence=0f
            for(d in 0..3) {
                val dx=when(d) {0->3;1->0;2->3;else->3}
                val dy=when(d) {0->0;1->3;2->3;else->-3}
                val a=light(original[(y-dy)*width+x-dx])
                val b=light(original[(y+dy)*width+x+dx])
                val contrast=(a+b)*.5f-c
                val asymmetry=abs(a-b)
                val score=(contrast-7f-asymmetry*.65f)/32f
                confidence=max(confidence,score)
            }
            if(confidence >= .18f && c in 15f..213f) {
                if(isEditable) {
                    accepted++
                    out[i]=mark(original[i],RED,(confidence*.77f+.24f).coerceAtMost(.85f))
                } else {
                    excluded++
                    out[i]=mark(original[i],ORANGE,(confidence*.68f+.19f).coerceAtMost(.79f))
                }
            } else if(isEditable) {
                // Faint green indicates the broad mask; green does NOT mean hair.
                out[i]=mark(original[i],GREEN,.12f)
            }
        }
        val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        image.setPixels(out,0,width,0,0,width,height)
        return image to Stats(accepted,excluded,editableCount)
    }
}
