package com.branchphotovault.retouch

import kotlin.math.roundToInt

/**
 * Optional two-view correction atop the unchanged one-pass AttGAN.
 *
 * ATTENTION: ONNX RGB channels are normalized [-1..1], NOT [0..255].
 * A separate normalised comparison must be converted through 127.5.
 * For natural skin colour, preserve the single-pass skin chroma and use
 * only the luminance component of the second model's correction, with a
 * ZERO chroma contribution. Exact jaw/lip mask comes from caller.
 */
internal object AttganDetailBlend {
    // Kept for older tests and backward-compatible experiments.
    fun channel(singlePass: Int, referenceModel: Float, shiftedModel: Float, weight: Float): Int {
        require(singlePass in 0..255)
        if (weight <= 0f || !referenceModel.isFinite() || !shiftedModel.isFinite())
            return singlePass
        val delta = ((shiftedModel - referenceModel) * weight).coerceIn(-20f,20f)
        return (singlePass + delta).roundToInt().coerceIn(0,255)
    }

    /**
     * Apply strictly bounded, skin-color-preserving refinement.
     * Equal ΔR,ΔG,ΔB means no change in RGB chromatic difference (except
     * numeric clipping). Natural local skin hue stays close to the baseline.
     */
    fun balancedPixel(
        original: Int,
        refR: Float, refG: Float, refB: Float,
        movedR: Float, movedG: Float, movedB: Float,
        weight: Float
    ): Int {
        if(weight<=0f || !weight.isFinite() ||
            !refR.isFinite() || !refG.isFinite() || !refB.isFinite() ||
            !movedR.isFinite() || !movedG.isFinite() || !movedB.isFinite())
            return original

        val dr=(movedR-refR)*127.5f
        val dg=(movedG-refG)*127.5f
        val db=(movedB-refB)*127.5f
        // Approximate video/sRGB luminance. A low chroma fraction avoids
        // green, red and gray patches caused by independent RGB corrections.
        // Never darken a suspected residual hair. The changed pixels must
        // remove dark strands, not impose new shadows/matte patches.
        val luminosity=(.2126f*dr+.7152f*dg+.0722f*db).coerceAtLeast(0f)
        val w=weight.coerceIn(0f,1f)
        fun blended(value:Int,delta:Float):Int {
            val neutral=luminosity*w
            return (value+neutral.coerceIn(-9f,9f)).roundToInt().coerceIn(0,255)
        }
        // Avoid android.graphics in the blend math; can be tested on JVM.
        val alpha=(original ushr 24) and 255
        val r=(original ushr 16) and 255
        val g=(original ushr 8) and 255
        val b=original and 255
        return (alpha shl 24) or
            (blended(r,dr) shl 16) or
            (blended(g,dg) shl 8) or blended(b,db)
    }
}
