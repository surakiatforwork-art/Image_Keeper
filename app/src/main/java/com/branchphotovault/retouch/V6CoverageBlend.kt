package com.branchphotovault.retouch

import kotlin.math.max

/**
 * V6.1: Raise the amount of an existing AttGAN proposal only where there
 * is source hair evidence or a reliable color rise within a face-skin mask.
 * This never increases the editable mask. LipSafe, parsing and jaw guards
 * must be applied by the caller BEFORE and AFTER this confidence.
 */
internal object V6CoverageBlend {
    fun confidence(
        baseline: Float, modelHair: Float, faceLabel: Int,
        proposedLumaRise: Float, inHairZone: Boolean, strength: Float
    ): Float {
        val safe=baseline.coerceIn(0f,1f)
        val amount=strength.coerceIn(0f,1f)
        if(!inHairZone || amount<=0f) return safe
        if(faceLabel!=1 && faceLabel!=14 && faceLabel!=17) return safe
        // No speculative brightening of smooth shadows without any evidence.
        val hair=((modelHair-.09f)/.38f).coerceIn(0f,1f)
        val candidate=((proposedLumaRise-5f)/22f).coerceIn(0f,1f)
        if(hair<=0f && candidate<.22f) return safe
        // For neck/edge, learned hair must agree; preserve cast shadows.
        if(faceLabel!=1 && hair<.55f) return safe
        val evidence=max(hair,candidate*.75f)
        val target=if(faceLabel==1) .74f+.24f*evidence
                   else .54f+.22f*hair
        return (safe+(target-safe).coerceAtLeast(0f)*amount).coerceIn(0f,1f)
    }
}
