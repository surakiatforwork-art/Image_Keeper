package com.branchphotovault.retouch

import kotlin.math.roundToInt

/**
 * Bounded, optional two-view correction atop the proven one-pass AttGAN.
 * No detector/model scores are used as a quality claim.
 *
 * The source single-pass result is NEVER modified beyond 20 levels per channel;
 * a zero weight returns an identical channel. The caller is responsible for
 * enforcing the anatomical edit mask and jawline protection.
 */
internal object AttganDetailBlend {
    fun channel(singlePass: Int, referenceModel: Float, shiftedModel: Float, weight: Float): Int {
        require(singlePass in 0..255)
        if (weight <= 0f || !referenceModel.isFinite() || !shiftedModel.isFinite()) {
            return singlePass
        }
        val delta = ((shiftedModel - referenceModel) * weight).coerceIn(-20f,20f)
        return (singlePass + delta).roundToInt().coerceIn(0,255)
    }
}
