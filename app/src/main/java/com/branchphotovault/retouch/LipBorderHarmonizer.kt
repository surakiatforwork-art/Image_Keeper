package com.branchphotovault.retouch

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * Beta 31, private offline lip-border harmonizer.
 *
 * Problem: the V6 lip safety mask deliberately preserves original pixels,
 * but the first rows of adjacent skin sometimes remain dark while farther
 * out the GAN has generated skin with different colour/texture.
 *
 * ONLY pixels outside the ORIGINAL vermilion contour and within a very
 * narrow lip-local band may be corrected. Instead of repeatedly blurring
 * the image, skin is extrapolated from ALREADY EDITED adjacent skin in
 * the final image (after V6 + Chin Student). Requires actual donor edits.
 *
 * This is a conservative colour/skin proposal, not a new GAN inference.
 * Original bitmap is immutable; lips, other face areas, background and
 * pixels outside the annulus remain byte-exact.
 */
internal object LipBorderHarmonizer {
    data class Stats(
        val examined:Int,
        val eligible:Int,
        val changed:Int,
        val noEditedDonor:Int,
        val elapsedMs:Long
    )

    private val OUTER=intArrayOf(61,40,37,0,267,270,291,321,314,17,84,91)
    private fun channel(c:Int,idx:Int):Int=when(idx) {
        0->Color.red(c);1->Color.green(c);else->Color.blue(c)
    }
    private fun luma(c:Int):Float=
        .299f*Color.red(c)+.587f*Color.green(c)+.114f*Color.blue(c)
    private fun smooth(t:Float):Float {
        val x=t.coerceIn(0f,1f)
        return x*x*(3f-2f*x)
    }
    private fun interp(a:Float,b:Float,t:Float):Float=a+(b-a)*t

    private data class Nearest(val point:PointF,val tangentX:Float,val tangentY:Float,
                               val distance:Float)

    private fun nearest(x:Float,y:Float,pts:List<PointF>):Nearest {
        var distance=Float.POSITIVE_INFINITY
        var at=PointF()
        var tangentX=1f
        var tangentY=0f
        for(i in pts.indices) {
            val a=pts[i];val b=pts[(i+1)%pts.size]
            val dx=b.x-a.x;val dy=b.y-a.y
            val len=dx*dx+dy*dy
            val t=if(len<1e-8f) 0f else
                (((x-a.x)*dx+(y-a.y)*dy)/len).coerceIn(0f,1f)
            val qx=a.x+dx*t;val qy=a.y+dy*t
            val r=hypot(x-qx,y-qy)
            if(r<distance) {
                distance=r
                at=PointF(qx,qy)
                val norm=sqrt(len).coerceAtLeast(1e-4f)
                tangentX=dx/norm
                tangentY=dy/norm
            }
        }
        return Nearest(at,tangentX,tangentY,distance)
    }

    fun refine(
        original:Bitmap,
        prepared:Bitmap,
        face:List<NormalizedLandmark>,
        maxStrength:Float=.87f
    ):Pair<Bitmap,Stats> {
        require(original.width==prepared.width && original.height==prepared.height)
        require(!original.isRecycled && !prepared.isRecycled && face.size>454)
        val start=SystemClock.elapsedRealtime()
        val w=original.width;val h=original.height
        val source=IntArray(w*h)
        val current=IntArray(w*h)
        original.getPixels(source,0,w,0,0,w,h)
        prepared.getPixels(current,0,w,0,0,w,h)
        val guard=LipSafeRasterGuard.fromFace(face,w,h)
        val contour=OUTER.map {PointF(face[it].x()*w,face[it].y()*h)}
        val left=contour.first();val right=contour[6]
        val mouthSpan=hypot(left.x-right.x,left.y-right.y).coerceAtLeast(5f)
        val ringStart=max(1.5f,mouthSpan*.016f)
        val ringEnd=max(ringStart+4f,mouthSpan*.17f)
        val donorStart=max(ringStart+3f,mouthSpan*.10f)
        val donorEnd=max(donorStart+2f,mouthSpan*.18f)
        val pad=ringEnd+4f
        val x0=(contour.minOf{it.x}-pad).toInt().coerceIn(0,w-1)
        val x1=(contour.maxOf{it.x}+pad).toInt().coerceIn(0,w-1)
        val y0=(contour.minOf{it.y}-pad).toInt().coerceIn(0,h-1)
        val y1=(contour.maxOf{it.y}+pad).toInt().coerceIn(0,h-1)
        var examined=0;var eligible=0;var changed=0;var missing=0
        val out=current.copyOf()
        for(y in y0..y1)for(x in x0..x1) {
            if(guard.at(x,y))continue
            val point=nearest(x+.5f,y+.5f,contour)
            val r=point.distance
            if(r<ringStart || r>ringEnd)continue
            examined++
            val vx=x+.5f-point.point.x
            val vy=y+.5f-point.point.y
            if(vx*vx+vy*vy<.01f)continue
            val dist=hypot(vx,vy)
            val nx=vx/dist;val ny=vy/dist
            val qIndex=y*w+x
            val originalPx=source[qIndex]
            val resultPx=current[qIndex]

            // Read only adjacent skin ALREADY transformed by AI. Don't
            // create an edited skin region if there is no useful donor.
            var total=0f
            val skin=FloatArray(3)
            val donorSrc=FloatArray(3)
            for(rad in floatArrayOf(donorStart,donorEnd)) {
                for(side in intArrayOf(-1,0,1)) {
                    val sx=(point.point.x+nx*rad+point.tangentX*side*mouthSpan*.025f)
                        .roundToInt()
                    val sy=(point.point.y+ny*rad+point.tangentY*side*mouthSpan*.025f)
                        .roundToInt()
                    if(sx !in 0 until w || sy !in 0 until h || guard.at(sx,sy))
                        continue
                    val idx=sy*w+sx
                    val src=source[idx];val edited=current[idx]
                    // Local edited-skin evidence. Dark shadows alone in
                    // the original never authorize filling untouched skin.
                    val delta=luma(edited)-luma(src)
                    val colourDelta=(0..2).sumOf {
                        abs(channel(edited,it)-channel(src,it))
                    }
                    if(delta<3f || colourDelta<15)continue
                    val editedLuma=luma(edited)
                    if(editedLuma<35f || editedLuma>235f)continue
                    val strength=(delta/22f).coerceIn(.20f,1f)
                    val proximity=if(side==0) 1f else .73f
                    val weight=strength*proximity
                    for(c in 0..2) {
                        skin[c]+=channel(edited,c)*weight
                        donorSrc[c]+=channel(src,c)*weight
                    }
                    total+=weight
                }
            }
            if(total<.25f){missing++;continue}
            eligible++
            for(c in 0..2){skin[c]/=total;donorSrc[c]/=total}
            val sourceLum=luma(originalPx)
            val targetLum=skin[0]*.299f+skin[1]*.587f+skin[2]*.114f
            val actualLum=luma(resultPx)
            val originalDonorLum=donorSrc[0]*.299f+
                donorSrc[1]*.587f+donorSrc[2]*.114f
            // Require either source-root darkness or a local seam against
            // already-clean AI skin; do not recolour normal skin gratuitously.
            val hairEvidence=originalDonorLum-sourceLum>4.5f
            val seam=targetLum-actualLum>9f
            if(!hairEvidence && !seam)continue

            // At the lip's protected edge do NOT blend in dark source roots:
            // the source-exact lip remains unchanged, its surrounding skin
            // inherits the cleaned donor, and influence fades outward.
            val fade=smooth((ringEnd-r)/(ringEnd-ringStart))
            val contrast=max(originalDonorLum-sourceLum,targetLum-actualLum)
            val evidence=smooth((contrast-3f)/22f)
            val opacity=(maxStrength.coerceIn(0f,1f)*fade*(.55f+.45f*evidence))
            if(opacity<.03f)continue

            val finalRGB=IntArray(3){c->
                val localVariation=(channel(originalPx,c)-donorSrc[c])
                    .coerceIn(-9f,9f)*.20f
                val predicted=(skin[c]+localVariation).coerceIn(0f,255f)
                val now=channel(resultPx,c).toFloat()
                // Never create extreme gamma/hue jumps at the lip.
                val corrected=now+(predicted-now).coerceIn(-30f,105f)*opacity
                corrected.roundToInt().coerceIn(0,255)
            }
            val blended=Color.rgb(finalRGB[0],finalRGB[1],finalRGB[2])
            if(blended!=resultPx){out[qIndex]=blended;changed++}
        }
        // Verifiable hard safety invariant. No AI may repaint vermilion,
        // including for the narrow donor extrapolation stage.
        val restored=guard.protectOriginal(source,out)
        if(changed==0 && restored==0)
            return prepared to Stats(examined,eligible,0,missing,
                SystemClock.elapsedRealtime()-start)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(out,0,w,0,0,w,h)
        val stats=Stats(examined,eligible,changed,missing,
            SystemClock.elapsedRealtime()-start)
        Log.i("KeeperLipBeta31",
            "lip_ring_examined="+stats.examined+" eligible="+stats.eligible+
            " changed="+stats.changed+" no_donor="+stats.noEditedDonor+
            " ms="+stats.elapsedMs)
        return bitmap to stats
    }
}
