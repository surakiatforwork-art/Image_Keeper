package com.branchphotovault.retouch

import kotlin.math.min
import kotlin.math.max

/**
 * Prevent a detected-beard island from becoming a noticeably different
 * skin colour. Works in OpenCV Lab scale [0..255].
 *
 * A clean-skin reference is gathered from existing unaffected cheek areas.
 * Stronger output is permitted only with high beard confidence; the transition
 * from regular SkinToneGuard is continuous (no threshold colour step).
 */
internal object SkinColorContinuityGuard {
    fun harmonize(
        source:FloatArray,
        proposed:FloatArray,
        cleanSkinReference:FloatArray?,
        beardConfidence:Float,
        jawWeight:Float
    ):FloatArray {
        val conservative=SkinToneGuard.apply(source,proposed)
        if(cleanSkinReference==null || cleanSkinReference.size!=3 ||
            beardConfidence<=.18f || jawWeight<=.1f) return conservative
        if(!cleanSkinReference.all { it.isFinite() && it in 0f..255f }) return conservative

        val t=smoothed((beardConfidence-.18f)/.55f)*jawWeight.coerceIn(0f,1f)
        if(t<=0f)return conservative

        val ref=cleanSkinReference
        val upTo=max(source[0],min(source[0]+78f,ref[0]+14f))
        val desiredLight=proposed[0].coerceIn(
            max(0f,source[0]-5f),upTo.coerceIn(0f,255f))
        // Preserve colour of surrounding real skin, not the colour of hair.
        val desiredA=proposed[1].coerceIn(ref[1]-7f,ref[1]+7f).coerceIn(0f,255f)
        val desiredB=proposed[2].coerceIn(ref[2]-7f,ref[2]+7f).coerceIn(0f,255f)
        return floatArrayOf(
            mix(conservative[0],desiredLight,t),
            mix(conservative[1],desiredA,t),
            mix(conservative[2],desiredB,t)
        )
    }

    fun smoothed(value:Float):Float {
        val x=value.coerceIn(0f,1f)
        return x*x*(3f-2f*x)
    }

    private fun mix(a:Float,b:Float,t:Float)=(a*(1f-t)+b*t).coerceIn(0f,255f)
}
