package com.branchphotovault.retouch

import kotlin.math.max
import kotlin.math.min

/**
 * One connected soft matte for dense beard, including hair crossing the
 * detected chin silhouette. Source Pixels OUTSIDE this matte remain exact.
 *
 * Operations: confidence threshold -> morphological close -> dilate ->
 * two separable box blur passes. Unlike a per-pixel probability threshold,
 * a connected beard gets full strength all the way to its inner boundary.
 */
internal class BeardFeatherMask(private val source:BeardSegmentationTool.BeardMask,
    private val preserveSourceBoundary:Boolean=false) {
    private val n=224
    private val m=n*n
    private val alpha:FloatArray

    init {
        val initial=ByteArray(m){if(source.probability[it]>.20f)1 else 0}
        fun morph(src:ByteArray,r:Int,dilate:Boolean):ByteArray {
            val dst=ByteArray(m)
            for(y in 0 until n) for(x in 0 until n) {
                var result=if(dilate)0 else 1
                outer@for(dy in -r..r)for(dx in -r..r) {
                    if(dx*dx+dy*dy>r*r)continue
                    val xx=x+dx
                    val yy=y+dy
                    val bit=if(xx in 0 until n && yy in 0 until n)
                        src[yy*n+xx].toInt() else 0
                    if(dilate && bit!=0){result=1;break@outer}
                    if(!dilate && bit==0){result=0;break@outer}
                }
                dst[y*n+x]=result.toByte()
            }
            return dst
        }
        // Closing fills holes caused by specular beard patches / low classifier
        // probability. Dilating covers visible border strands.
        val closed=morph(morph(initial,4,true),4,false)
        if(preserveSourceBoundary) {
            // Do not dilate generated skin into the background/neck outside the source beard.
            // Feather INWARD; retain connected low-confidence interior holes from closing.
            val distance=FloatArray(m) { if(closed[it].toInt()==0) 0f else 1e6f }
            for(y in 0 until n) for(x in 0 until n) {
                val i=y*n+x
                if(x==0 || y==0 || x==n-1 || y==n-1) distance[i]=0f
                if(x>0) distance[i]=min(distance[i],distance[i-1]+1f)
                if(y>0) distance[i]=min(distance[i],distance[i-n]+1f)
                if(x>0 && y>0) distance[i]=min(distance[i],distance[i-n-1]+1.414214f)
            }
            for(y in n-1 downTo 0) for(x in n-1 downTo 0) {
                val i=y*n+x
                if(x<n-1) distance[i]=min(distance[i],distance[i+1]+1f)
                if(y<n-1) distance[i]=min(distance[i],distance[i+n]+1f)
                if(x<n-1 && y<n-1) distance[i]=min(distance[i],distance[i+n+1]+1.414214f)
            }
            alpha=FloatArray(m) { val t=(distance[it]/2f).coerceIn(0f,1f); t*t*(3-2*t) }
        } else {
        val dilated=morph(closed,7,true)
        val horizontal=FloatArray(m)
        val out=FloatArray(m)
        val r=5
        for(y in 0 until n)for(x in 0 until n) {
            var sum=0f;var num=0
            for(dx in -r..r) {
                val xx=x+dx
                if(xx !in 0 until n)continue
                sum+=dilated[y*n+xx]
                num++
            }
            horizontal[y*n+x]=sum/num
        }
        for(y in 0 until n)for(x in 0 until n) {
            var sum=0f;var num=0
            for(dy in -r..r) {
                val yy=y+dy
                if(yy !in 0 until n)continue
                sum+=horizontal[yy*n+x]
                num++
            }
            out[y*n+x]=(sum/num).coerceIn(0f,1f)
        }
        alpha=out
        }
    }

    fun at(x:Int,y:Int):Float {
        val u=(x-source.left)/source.side*n-.5f
        val v=(y-source.top)/source.side*n-.5f
        if(u<0f||v<0f||u>n-1f||v>n-1f)return 0f
        val x0=u.toInt();val y0=v.toInt()
        val x1=min(n-1,x0+1);val y1=min(n-1,y0+1)
        val fx=u-x0;val fy=v-y0
        val top=alpha[y0*n+x0]*(1f-fx)+alpha[y0*n+x1]*fx
        val bot=alpha[y1*n+x0]*(1f-fx)+alpha[y1*n+x1]*fx
        return (top*(1f-fy)+bot*fy).coerceIn(0f,1f)
    }

    fun area():Int=alpha.count{it>.5f}
}
