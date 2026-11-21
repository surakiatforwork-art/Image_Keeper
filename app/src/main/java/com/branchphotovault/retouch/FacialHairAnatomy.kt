package com.branchphotovault.retouch

import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * Aligned anatomical safety envelope for a real beard segmentation proposal.
 * Geometry provides guard rails; the learned beard probability decides where
 * to expand the original mask. Neither landmarks nor image darkness alone
 * authorize applying the generative effect outside the face/neck envelope.
 */
internal class FacialHairAnatomy(
    private val face: List<NormalizedLandmark>, private val width: Int, private val height: Int,
    private val a: Float, private val b: Float,
    private val tx: Float, private val ty: Float,
    private val region: RetouchRegion
) {
    private fun p(i:Int) = PointF(face[i].x()*width, face[i].y()*height)
    private fun u(q:PointF)=a*q.x+b*q.y+tx
    private fun v(q:PointF)=-b*q.x+a*q.y+ty
    private val mouthLeft=u(p(61))
    private val mouthRight=u(p(291))
    private val mouthMin=min(mouthLeft,mouthRight)
    private val mouthMax=max(mouthLeft,mouthRight)
    private val mouthW=(mouthMax-mouthMin).coerceAtLeast(8f)
    private val noseV=v(p(2))
    private val lipV=v(p(13))
    private val lowerLipV=v(p(17))
    private val chinV=v(p(152))
    private val cheekLeft=min(u(p(234)),u(p(172)))
    private val cheekRight=max(u(p(454)),u(p(397)))
    private val chinU=u(p(152))
    private val faceW=(cheekRight-cheekLeft).coerceAtLeast(mouthW*1.5f)
    private val bottomV=chinV+mouthW*.58f

    fun maskZone(x: Int, y: Int): Boolean {
        val ux=a*x+b*y+tx
        val vy=-b*x+a*y+ty
        return isMoustache(ux,vy) || isBeard(ux,vy)
    }

    fun isNeck(x:Int,y:Int):Boolean {
        val vy=-b*x+a*y+ty
        return vy>=chinV && vy<=bottomV
    }

    fun isMoustache(x:Int,y:Int):Boolean {
        if(region == RetouchRegion.BEARD) return false
        return isMoustache(a*x+b*y+tx,-b*x+a*y+ty)
    }

    private fun isMoustache(u:Float,v:Float):Boolean {
        if(region == RetouchRegion.BEARD) return false
        val gap=abs(lipV-noseV).coerceAtLeast(8f)
        val left=mouthMin-mouthW*.36f
        val right=mouthMax+mouthW*.36f
        return u in left..right && v in (min(noseV,lipV)+gap*.09f)..(max(noseV,lipV)+gap*.26f)
    }

    private fun isBeard(u:Float,v:Float):Boolean {
        if(region == RetouchRegion.MOUSTACHE) return false
        val top=min(lowerLipV,lipV)-mouthW*.12f
        if(v < top || v > bottomV) return false
        val neckPart=((v-chinV)/(bottomV-chinV).coerceAtLeast(1f)).coerceIn(0f,1f)
        val center=(cheekLeft+cheekRight)*.5f
        val baseHalf=faceW*.53f
        val neckHalf=mouthW*.76f
        val half=baseHalf*(1f-neckPart)+neckHalf*neckPart
        val left=center-half
        val right=center+half
        val neckCenter=chinU
        val safeCenter=center*(1f-neckPart)+neckCenter*neckPart
        return u in (safeCenter-half)..(safeCenter+half) &&
            u in (left-faceW*.08f)..(right+faceW*.08f)
    }
}
