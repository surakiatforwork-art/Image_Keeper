package com.branchphotovault.retouch

import android.graphics.Color
import kotlin.math.*

/**
 * Conservative learned-mask-guided MICRO-repair (not generative inpainting).
 *
 * Dense full-beard patches do NOT receive donor color fills: those produce
 * unnatural flat skin and destroy the original photo's tonal structure.
 * Only small, isolated residual islands receive bounded, feathered repair.
 * The caller's source, mask and generated arrays remain unmodified.
 */
internal object NeuralHairReconstruction {
    private const val GRID = 224
    private const val MAX_ISLAND_CELLS = 72
    private const val MAX_ISLAND_WIDTH = 14
    data class Stats(val pixels: Int, val filledCells: Int, val skippedCells: Int)

    fun refine(
        source: IntArray, generated: IntArray, width: Int, height: Int,
        beard: BeardSegmentationTool.BeardMask,
        permitted: (Int,Int)->Boolean,
        isNeck: (Int,Int)->Boolean
    ): Pair<IntArray,Stats> {
        require(source.size == width*height && generated.size == source.size)
        val out=generated.copyOf()
        if(beard.detections<=0) return out to Stats(0,0,0)
        val count=GRID*GRID
        val colors=IntArray(count)
        val valid=BooleanArray(count)
        val hole=BooleanArray(count)
        for(y in 0 until GRID)for(x in 0 until GRID) {
            val sx=(beard.left+(x+.5f)*beard.side/GRID).toInt()
            val sy=(beard.top+(y+.5f)*beard.side/GRID).toInt()
            val i=y*GRID+x
            if(sx !in 0 until width || sy !in 0 until height) continue
            valid[i]=true
            colors[i]=generated[sy*width+sx]
            hole[i]=permitted(sx,sy) && beard.at(sx,sy)>.50f
        }
        val repairable=BooleanArray(count)
        val visited=BooleanArray(count)
        val dx=intArrayOf(-1,1,0,0,-1,-1,1,1)
        val dy=intArrayOf(0,0,-1,1,-1,1,-1,1)
        val queue=IntArray(count)
        var accepted=0
        var skipped=0
        for(i in 0 until count) {
            if(!hole[i] || visited[i]) continue
            var start=0;var end=1
            queue[0]=i;visited[i]=true
            var minX=i%GRID;var maxX=minX;var minY=i/GRID;var maxY=minY
            while(start<end) {
                val current=queue[start++]
                val x=current%GRID;val y=current/GRID
                minX=min(minX,x);maxX=max(maxX,x)
                minY=min(minY,y);maxY=max(maxY,y)
                for(k in 0..7) {
                    val nx=x+dx[k];val ny=y+dy[k]
                    if(nx !in 1 until GRID-1 || ny !in 1 until GRID-1)continue
                    val next=ny*GRID+nx
                    if(hole[next] && !visited[next]) {
                        visited[next]=true
                        queue[end++]=next
                    }
                }
            }
            // Never inpaint a connected full moustache/sideburn/jaw/neck band.
            val safe=end<=MAX_ISLAND_CELLS &&
                maxX-minX<MAX_ISLAND_WIDTH && maxY-minY<MAX_ISLAND_WIDTH
            if(safe) {
                for(j in 0 until end) repairable[queue[j]]=true
                accepted+=end
            } else skipped+=end
        }
        if(accepted<1) return out to Stats(0,0,skipped)

        val filled=BooleanArray(count) { valid[it] && !hole[it] }
        val work=colors.copyOf()
        var filledCount=0
        for(pass in 0 until MAX_ISLAND_WIDTH+3) {
            val updates=IntArray(count)
            val active=BooleanArray(count)
            var changed=0
            for(y in 1 until GRID-1)for(x in 1 until GRID-1) {
                val i=y*GRID+x
                if(!repairable[i] || filled[i]) continue
                var red=0;var green=0;var blue=0;var n=0
                for(k in 0..7) {
                    val j=(y+dy[k])*GRID+x+dx[k]
                    if(!valid[j] || !filled[j]) continue
                    val c=work[j]
                    red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c);n++
                }
                if(n>=2) {
                    updates[i]=Color.rgb(red/n,green/n,blue/n)
                    active[i]=true
                    changed++
                }
            }
            if(changed==0) break
            for(i in 0 until count)if(active[i]) {
                work[i]=updates[i];filled[i]=true
                filledCount++
            }
        }

        var changedPixels=0
        val minX=max(0,beard.left.toInt())
        val maxX=min(width,(beard.left+beard.side).toInt()+1)
        val minY=max(0,beard.top.toInt())
        val maxY=min(height,(beard.top+beard.side).toInt()+1)
        for(y in minY until maxY)for(x in minX until maxX) {
            if(!permitted(x,y)) continue
            val confidence=beard.at(x,y)
            if(confidence<.53f) continue
            val gx=((x-beard.left)/beard.side*GRID).toInt()
            val gy=((y-beard.top)/beard.side*GRID).toInt()
            if(gx !in 1 until GRID-1 || gy !in 1 until GRID-1) continue
            val gridIndex=gy*GRID+gx
            if(!repairable[gridIndex] || !filled[gridIndex]) continue
            val i=y*width+x
            val originalColor=generated[i]
            val donor=work[gridIndex]
            val originalLuma=Color.red(originalColor)*.299f+
                Color.green(originalColor)*.587f+Color.blue(originalColor)*.114f
            val donorLuma=Color.red(donor)*.299f+
                Color.green(donor)*.587f+Color.blue(donor)*.114f
            if(donorLuma-originalLuma < 7f || donorLuma-originalLuma > 45f) continue
            val amount=((confidence-.49f)/.39f).coerceIn(0f,1f)*
                (if(isNeck(x,y)).23f else .34f)
            if(amount<=0f)continue
            fun channel(current:Int,next:Int):Int =
                (current+(next-current).coerceIn(-8,28)*amount)
                    .roundToInt().coerceIn(0,255)
            val repaired=Color.rgb(
                channel(Color.red(originalColor),Color.red(donor)),
                channel(Color.green(originalColor),Color.green(donor)),
                channel(Color.blue(originalColor),Color.blue(donor))
            )
            if(repaired!=originalColor) {out[i]=repaired;changedPixels++}
        }
        return out to Stats(changedPixels,filledCount,skipped)
    }
}
