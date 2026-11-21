package com.branchphotovault.retouch

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max

/**
 * Conservative, fully offline experimental compositing guard.
 *
 * Inspired by the separation of face ROI, facial hair and the asynchronous
 * regeneration task observed in Meitu 12.23.5, but implemented independently.
 *
 * This is *not* an ML beard-segmentation model. It measures local strand texture
 * from the ORIGINAL image, before AttGAN can smooth the evidence away.
 * Natural smooth shadows are protected; no edits occur outside the original
 * anatomical face-parsing mask selected by the retoucher.
 */
internal class FacialHairRegionGuard(
    private val original: IntArray,
    private val width: Int,
    private val height: Int,
    faceSpan: Float
) {
    init {
        require(width > 4 && height > 4 && original.size == width*height)
    }

    private val step = (faceSpan*.018f).toInt().coerceIn(2,12)
    private fun light(color: Int): Float =
        Color.red(color)*.299f + Color.green(color)*.587f + Color.blue(color)*.114f

    private fun lightAt(x: Int,y: Int): Float =
        light(original[y.coerceIn(0,height-1)*width+x.coerceIn(0,width-1)])

    /**
     * Output coefficient for a pre-selected, anatomically editable pixel.
     * Full strength remains available for strand-like details; smooth and
     * dark chin/neck shading receives less of the generative proposal.
     */
    /**
     * V2.2: within an independently verified upper-lip ROI, give the model's
     * output more weight only when small-scale dark hair texture is present.
     * Smooth broad shadows continue to use V2 protection.
     */
    fun moustacheConfidence(
        x: Int, y: Int, label: Int, source: Int, proposal: FloatArray
    ): Float {
        val safe = confidence(x,y,label,source,proposal)
        if (label != 1 && label != 17) return safe
        val center=light(source)
        val surrounding=(lightAt(x-step,y)+lightAt(x+step,y)+
            lightAt(x,y-step)+lightAt(x,y+step))*.25f
        val contrast=surrounding-center
        val target=proposal[0]*.299f+proposal[1]*.587f+proposal[2]*.114f
        // Require localized dark texture before strengthening the composite.
        // Do not brighten smooth black shadow as a substitute for detection.
        if (contrast < 7f || target < center + 4f) return safe
        return max(safe,(.58f+(contrast-7f)/60f).coerceIn(.58f,.92f))
    }

    fun confidence(x: Int,y: Int,label: Int,source: Int,proposal: FloatArray): Float {
        require(proposal.size == 3)
        if (label != 1 && label != 14 && label != 17) return 0f
        val center = light(source)
        val next = proposal[0]*.299f+proposal[1]*.587f+proposal[2]*.114f
        val rise = next-center

        val neighborhood = floatArrayOf(
            lightAt(x-step,y),lightAt(x+step,y),
            lightAt(x,y-step),lightAt(x,y+step),
            lightAt(x-step,y-step),lightAt(x+step,y-step),
            lightAt(x-step,y+step),lightAt(x+step,y+step))
        val mean = neighborhood.average().toFloat()
        val localTexture = neighborhood.sumOf { abs(it-center).toDouble() }.toFloat()/8f
        val darkerStrand = (mean-center).coerceAtLeast(0f)

        // When the source patch is smooth, a major lightening is more likely
        // to erase illumination/shadow than an individual dark hair strand.
        val strandEvidence = (darkerStrand/20f*.55f+localTexture/17f*.45f)
            .coerceIn(0f,1f)

        if (rise > 6f && localTexture < 5f && darkerStrand < 5f) {
            // Smooth continuous shadow: do not flatten it. Strongest guard at
            // the neck boundary where shading defines jaw geometry.
            return if (label == 1) .22f else .08f
        }
        return if (label == 1) {
            (.55f+.45f*strandEvidence).coerceIn(.55f,1f)
        } else {
            // Neck/hair anatomy is allowed in our baseline mask, but the V2
            // experiment conservatively protects its natural boundary.
            (.16f+.49f*strandEvidence).coerceIn(.16f,.65f)
        }
    }
}
