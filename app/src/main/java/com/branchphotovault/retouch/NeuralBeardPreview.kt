package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

/** Face-centered 512px diagnostic preview using learned beard masks. */
internal object NeuralBeardPreview {
    fun render(
        source: Bitmap,
        beard: BeardSegmentationTool.BeardMask,
        permitted: (Int,Int)->Boolean,
        oldEditable: (Int,Int)->Boolean
    ): Pair<Bitmap,HairMaskDiagnostics.Stats> {
        val size=512
        val image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image)
        canvas.drawColor(Color.rgb(112,112,112))
        val scale=size/beard.side
        val matrix=Matrix().apply {
            setScale(scale,scale)
            postTranslate(-beard.left*scale,-beard.top*scale)
        }
        canvas.drawBitmap(source,matrix,Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels=IntArray(size*size)
        image.getPixels(pixels,0,size,0,0,size,size)
        var inside=0
        var outside=0
        var editable=0
        fun overlay(old:Int,r:Int,g:Int,b:Int,opacity:Float):Int {
            val o=opacity.coerceIn(0f,1f);val s=1f-o
            return Color.rgb(
                (Color.red(old)*s+r*o).toInt().coerceIn(0,255),
                (Color.green(old)*s+g*o).toInt().coerceIn(0,255),
                (Color.blue(old)*s+b*o).toInt().coerceIn(0,255))
        }
        for(y in 0 until size)for(x in 0 until size) {
            val sx=(beard.left+(x+.5f)*beard.side/size).toInt()
            val sy=(beard.top+(y+.5f)*beard.side/size).toInt()
            if(sx !in 0 until source.width || sy !in 0 until source.height) continue
            val index=y*size+x
            val isEditable=oldEditable(sx,sy)
            if(isEditable) editable++
            val confidence=beard.at(sx,sy)
            if(confidence>.20f) {
                if(permitted(sx,sy)) {
                    inside++
                    pixels[index]=overlay(pixels[index],250,55,88,(confidence*.80f+.12f).coerceAtMost(.90f))
                } else {
                    outside++
                    pixels[index]=overlay(pixels[index],255,160,30,(confidence*.77f+.12f).coerceAtMost(.88f))
                }
            } else if(isEditable && x%2==0 && y%2==0) {
                pixels[index]=overlay(pixels[index],34,211,166,.13f)
            }
        }
        image.setPixels(pixels,0,size,0,0,size,size)
        return image to HairMaskDiagnostics.Stats(inside,outside,editable)
    }
}
