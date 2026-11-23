package com.branchphotovault.retouch

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Local residual-stubble correction. This is independent, deterministic, offline
 * interpolation, NOT a trained hair segmentation or generative inpainting model.
 *
 * A candidate must be dark relative to two opposite skin samples BOTH in the
 * original and in the V2 preview. Flat, continuous shadows lack that evidence.
 * The operation never touches pixels outside the protected anatomical mask.
 */
internal object ResidualHairRepair {
    internal data class Stats(val candidates: Int, val corrected: Int)

    fun refine(
        original: IntArray,
        prepared: IntArray,
        width: Int,
        height: Int,
        faceSpan: Float,
        radiusScale: Float = 1f,
        strengthScale: Float = 1f,
        strandOnly: Boolean = false,
        candidateEvidence: ((Int,Int)->Boolean)? = null,
        permitted: (Int, Int) -> Boolean
    ): Pair<IntArray, Stats> {
        require(width > 0 && height > 0 && original.size == width * height && prepared.size == original.size)
        val out = prepared.copyOf()
        val radius = (faceSpan * .014f * radiusScale).roundToInt().coerceIn(3, 12)
        val directions = intArrayOf(1, 0, 0, 1, 1, 1, 1, -1)
        val xMin = radius + 1
        val yMin = radius + 1
        if (width <= xMin * 2 || height <= yMin * 2) return out to Stats(0, 0)
        var candidates = 0
        var corrected = 0

        fun luma(px: Int): Float =
            Color.red(px) * .299f + Color.green(px) * .587f + Color.blue(px) * .114f
        fun pixelAt(arr: IntArray, x: Int, y: Int) = arr[y * width + x]

        for (y in yMin until height - yMin) for (x in xMin until width - xMin) {
            if (!permitted(x, y)) continue
            if (candidateEvidence?.invoke(x,y)==false) continue
            val i = y * width + x
            val source = original[i]
            val current = prepared[i]
            val sourceLum = luma(source)
            val currentLum = luma(current)
            if (sourceLum < 20f || currentLum < 20f || currentLum > 222f) continue

            var bestContrast = 0f
            var bestA = 0
            var bestB = 0
            var leastContrast=Float.POSITIVE_INFINITY
            var validDirections=0

            // Choose the cross-strand direction with least endpoint imbalance,
            // so smooth cast shadows/edges will not be treated like small hairs.
            for (d in 0..3) {
                val dx = directions[d * 2] * radius
                val dy = directions[d * 2 + 1] * radius
                val x1 = x - dx; val y1 = y - dy
                val x2 = x + dx; val y2 = y + dy
                if (!permitted(x1, y1) || !permitted(x2, y2)) continue
                val beforeA = pixelAt(original, x1, y1)
                val beforeB = pixelAt(original, x2, y2)
                val afterA = pixelAt(prepared, x1, y1)
                val afterB = pixelAt(prepared, x2, y2)
                val sourceA = luma(beforeA)
                val sourceB = luma(beforeB)
                val processedA = luma(afterA)
                val processedB = luma(afterB)
                val originalContrast = (sourceA + sourceB) * .5f - sourceLum
                val residualContrast = (processedA + processedB) * .5f - currentLum
                val imbalance = kotlin.math.abs(processedA - processedB)
                validDirections++
                leastContrast=min(leastContrast,min(originalContrast,residualContrast).coerceAtLeast(0f))
                // Both source and output must retain strand-like local contrast.
                if (originalContrast < 8f || residualContrast < 6f || imbalance > 22f) continue
                val score = min(originalContrast * .85f, residualContrast) - imbalance * .18f
                if (score > bestContrast) {
                    bestContrast = score
                    bestA = afterA
                    bestB = afterB
                }
            }
            if (bestContrast < 6f) continue
            // Round pores/moles are ambiguous; the focused lip cleanup requires directional strand evidence.
            if(strandOnly && (validDirections<2 || leastContrast>=bestContrast*.65f)) continue
            candidates++
            val a = bestA; val b = bestB
            val targetR = (Color.red(a) + Color.red(b)) * .5f
            val targetG = (Color.green(a) + Color.green(b)) * .5f
            val targetB = (Color.blue(a) + Color.blue(b)) * .5f
            val strength = (((bestContrast - 5f) / 20f).coerceIn(.18f, .75f)*
                strengthScale).coerceIn(0f,.85f)
            // Keep a bounded, soft per-pixel repair. Stronger changes require a
            // validated segmentation + generative model, not this heuristic.
            fun blend(now: Int, target: Float) =
                (now + ((target - now).coerceIn(-8f, 30f)) * strength).roundToInt().coerceIn(0, 255)
            val next = Color.rgb(
                blend(Color.red(current), targetR),
                blend(Color.green(current), targetG),
                blend(Color.blue(current), targetB)
            )
            if (next != current) {
                out[i] = next
                corrected++
            }
        }
        return out to Stats(candidates, corrected)
    }
}
