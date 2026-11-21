package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A genuine, completely OFFLINE texture-preserving healing brush.
 *
 * Unlike the old 'add AI' brush, this never runs AttGAN again or reuses only
 * pixels that AI already changed. It copies clean local skin texture from a
 * user-selected donor (or an automatically selected nearby smooth patch),
 * color-matches it to the surrounding skin, and feathers only the brush mask.
 *
 * The photo/AI input is never modified: only the working edit layer changes.
 */
internal object ManualSkinHeal {
    fun repair(layer: Bitmap, stroke: RetouchStroke) {
        if (stroke.points.isEmpty()) return
        require(layer.isMutable) { "Healing layer must be mutable" }
        val width=layer.width
        val height=layer.height
        val r=(stroke.radius*min(width,height)).coerceIn(2f,120f)
        val points=stroke.points.map {
            Pair(it.x.coerceIn(0f,1f)*width,it.y.coerceIn(0f,1f)*height)
        }
        val pad=ceil(r*1.4f).toInt()+2
        val left=(points.minOf { it.first }.toInt()-pad).coerceAtLeast(0)
        val top=(points.minOf { it.second }.toInt()-pad).coerceAtLeast(0)
        val right=(points.maxOf { it.first }.toInt()+pad+1).coerceAtMost(width)
        val bottom=(points.maxOf { it.second }.toInt()+pad+1).coerceAtMost(height)
        val bw=right-left; val bh=bottom-top
        if (bw<=0 || bh<=0) return

        val brush=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(bw*bh)
        val original=IntArray(bw*bh)
        try {
            val path=Path()
            points.forEachIndexed { index,p ->
                if (index==0) path.moveTo(p.first-left,p.second-top)
                else path.lineTo(p.first-left,p.second-top)
            }
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.WHITE
                style=Paint.Style.STROKE
                strokeWidth=r*2
                strokeJoin=Paint.Join.ROUND
                strokeCap=Paint.Cap.ROUND
                maskFilter=BlurMaskFilter(max(1f,r*.18f),BlurMaskFilter.Blur.NORMAL)
            }
            val canvas=Canvas(brush)
            if(points.size==1) {
                paint.style=Paint.Style.FILL
                canvas.drawCircle(points[0].first-left,points[0].second-top,r,paint)
            } else canvas.drawPath(path,paint)
            brush.getPixels(pixels,0,bw,0,0,bw,bh)
            layer.getPixels(original,0,bw,left,top,bw,bh)

            val first=points.first()
            val dx:Int
            val dy:Int
            if (stroke.donor != null) {
                dx=(stroke.donor.x*width-first.first).roundToInt()
                dy=(stroke.donor.y*height-first.second).roundToInt()
            } else {
                val automatic=findCleanDonor(layer,first.first,first.second,r)
                dx=automatic.first
                dy=automatic.second
            }
            // A source swatch only from the clean region: never extend from
            // the painted region by mirroring or distorting pixels.
            val donorLeft=(left+dx).coerceIn(0,width)
            val donorTop=(top+dy).coerceIn(0,height)
            val donorRight=(right+dx).coerceIn(0,width)
            val donorBottom=(bottom+dy).coerceIn(0,height)
            val donorWidth=donorRight-donorLeft
            val donorHeight=donorBottom-donorTop
            if (donorWidth<=0 || donorHeight<=0) return
            val donor=IntArray(donorWidth*donorHeight)
            layer.getPixels(donor,0,donorWidth,donorLeft,donorTop,donorWidth,donorHeight)
            fun donorAt(x:Int,y:Int):Int {
                val sx=(x+dx).coerceIn(donorLeft,donorRight-1)-donorLeft
                val sy=(y+dy).coerceIn(donorTop,donorBottom-1)-donorTop
                return donor[sy*donorWidth+sx]
            }
            // Match only low-frequency color from a narrow rim around the
            // painted area. Do NOT blur the donor texture or original skin.
            var nr=0L;var ng=0L;var nb=0L
            var dr=0L;var dg=0L;var db=0L;var count=0
            val step=max(1,(r/4).roundToInt())
            for(iy in 0 until bh step step) for(ix in 0 until bw step step) {
                val ma=Color.alpha(pixels[iy*bw+ix])
                if(ma>=8) continue
                // Rim close to a painted pixel (but outside the brush).
                var adjacent=false
                for(off in arrayOf(Pair(-step,0),Pair(step,0),Pair(0,-step),Pair(0,step))) {
                    val xx=ix+off.first;val yy=iy+off.second
                    if(xx in 0 until bw && yy in 0 until bh &&
                        Color.alpha(pixels[yy*bw+xx])>96) adjacent=true
                }
                if(!adjacent) continue
                val existing=original[iy*bw+ix]
                val sample=donorAt(left+ix,top+iy)
                nr+=Color.red(existing); ng+=Color.green(existing); nb+=Color.blue(existing)
                dr+=Color.red(sample); dg+=Color.green(sample); db+=Color.blue(sample)
                count++
            }
            fun offset(from:Long,to:Long):Int=if(count>0) {
                ((from-to)/count.toFloat()).roundToInt().coerceIn(-28,28)
            } else 0
            val cr=offset(nr,dr)
            val cg=offset(ng,dg)
            val cb=offset(nb,db)
            var written=0
            for(iy in 0 until bh) for(ix in 0 until bw) {
                val pos=iy*bw+ix
                val alpha=Color.alpha(pixels[pos])/255f
                if(alpha<=.001f) continue
                val existing=original[pos]
                val donorColor=donorAt(left+ix,top+iy)
                fun mix(old:Int,other:Int,adjust:Int):Int {
                    val healed=(other+adjust).coerceIn(0,255)
                    return (old+(healed-old)*alpha).roundToInt().coerceIn(0,255)
                }
                original[pos]=Color.argb(Color.alpha(existing),
                    mix(Color.red(existing),Color.red(donorColor),cr),
                    mix(Color.green(existing),Color.green(donorColor),cg),
                    mix(Color.blue(existing),Color.blue(donorColor),cb))
                written++
            }
            // Never write mask pixels as image data: preserve every
            // untouched pixel in the bounding box exactly.
            if(written>0) layer.setPixels(original,0,bw,left,top,bw,bh)
        } finally {
            brush.recycle()
        }
    }

    /**
     * Nearby donor selection favors matching skin color and lower local
     * hair/edge energy. The user can override it by picking a clean swatch.
     */
    private fun findCleanDonor(layer:Bitmap,x:Float,y:Float,r:Float):Pair<Int,Int> {
        val width=layer.width
        val height=layer.height
        val px=x.roundToInt().coerceIn(0,width-1)
        val py=y.roundToInt().coerceIn(0,height-1)
        val dist=(r*2.8f).roundToInt().coerceAtLeast(12)
        val probe=(r*.32f).roundToInt().coerceIn(3,15)
        val ring = listOf(Pair(-dist/2,-dist/2),Pair(dist/2,-dist/2),
            Pair(-dist/2,dist/2),Pair(dist/2,dist/2))
        val expected=ring.mapNotNull { (dx,dy) ->
            val xx=px+dx;val yy=py+dy
            if(xx in 0 until width && yy in 0 until height) layer.getPixel(xx,yy) else null
        }
        val guide=if(expected.isNotEmpty()) {
            val a=expected.map { Color.red(it) }.average()
            val b=expected.map { Color.green(it) }.average()
            val c=expected.map { Color.blue(it) }.average()
            Triple(a,b,c)
        } else Triple(128.0,115.0,105.0)
        var best=Pair(0,-dist)
        var bestScore=Double.POSITIVE_INFINITY
        for(i in 0..15) {
            val theta=2*Math.PI*i/16.0
            val dx=(cos(theta)*dist).roundToInt()
            val dy=(sin(theta)*dist).roundToInt()
            val cx=px+dx;val cy=py+dy
            if(cx-probe<0 || cy-probe<0 || cx+probe>=width || cy+probe>=height)continue
            var red=0.0; var green=0.0; var blue=0.0
            var texture=0.0; var count=0
            val sampleStep=max(2,probe/2)
            for(oy in -probe..probe step sampleStep)
                for(ox in -probe..probe step sampleStep) {
                    val color=layer.getPixel(cx+ox,cy+oy)
                    red+=Color.red(color)
                    green+=Color.green(color)
                    blue+=Color.blue(color)
                    val other=layer.getPixel(cx+ox+1,cy+oy+1)
                    texture+=abs(Color.red(other)-Color.red(color))+
                        abs(Color.green(other)-Color.green(color))+
                        abs(Color.blue(other)-Color.blue(color))
                    count++
                }
            red/=count;green/=count;blue/=count
            val colorGap=(abs(red-guide.first)+abs(green-guide.second)+
                abs(blue-guide.third))*.28
            val score=texture/count*2.4+colorGap+
                if(dy>0)4.0 else 0.0 // prefer nearby cheek above a moustache
            if(score<bestScore) {bestScore=score;best=Pair(dx,dy)}
        }
        return best
    }
}
