package com.branchphotovault.retouch

import kotlin.math.max

/**
 * V6.8 private experiment: reduce chin-silhouette artifacts without clipping
 * the dense beard hard at the MediaPipe oval. A hard oval cut restores a
 * visible black beard band; this weight fades generated skin gradually.
 *
 * Inference only: this is NOT proof that a hidden actual chin was recovered.
 * It does not change source pixels, face landmarks or model outputs.
 */
internal object ChinContourComposite {
    private fun smooth01(value:Float):Float {
        val t=value.coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }

    /**
     * [hairProbability] from original-photo facial hair classifier (0..1).
     * [signedDistance] from original facial contour: + inside, - outside.
     * [mouthSpan] measured in ORIGINAL photo pixels.
     *
     * Strong detected hair remains editable even slightly outside the oval,
     * while weak evidence outside is suppressed to protect the neckline.
     */
    fun weight(
        hairProbability:Float,
        signedDistance:Float,
        mouthSpan:Float,
        strength:Float=1f
    ):Float {
        require(hairProbability.isFinite() && signedDistance.isFinite() &&
            mouthSpan.isFinite() && mouthSpan>0f && strength.isFinite())
        val evidence=smooth01((hairProbability-.08f)/.52f)
        val hairWeight=.48f+.52f*evidence
        val transition=max(2f,mouthSpan*.10f)
        // The same boundary value is used on both sides. Smoothstep's
        // zero end slope prevents a visible jump at the photographed oval.
        val edgeWeight=.14f+.44f*evidence
        val contourWeight=if(signedDistance>=0f) {
            edgeWeight+(1f-edgeWeight)*smooth01(signedDistance/transition)
        } else {
            // Genuine dense hair can protrude below the landmark outline.
            // Attenuate it smoothly rather than restoring a hard beard band.
            edgeWeight*(1f-.35f*smooth01((-signedDistance)/(2f*transition)))
        }
        return (hairWeight*contourWeight*strength.coerceIn(0f,1f))
            .coerceIn(0f,1f)
    }

    /** Bounded application of signed model residual to an existing channel. */
    fun compositeChannel(current:Int, proposedResidual:Int, weight:Float):Int {
        require(current in 0..255 && proposedResidual in -128..127 &&
            weight.isFinite())
        return (current+proposedResidual*weight.coerceIn(0f,1f))
            .toInt().coerceIn(0,255)
    }
}
