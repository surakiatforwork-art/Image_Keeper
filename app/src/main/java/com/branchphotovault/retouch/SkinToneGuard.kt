package com.branchphotovault.retouch

/** Bounded colour tool; limits are in the OpenCV-compatible Lab scale (0..255). */
internal object SkinToneGuard {
    fun apply(source: FloatArray, proposed: FloatArray): FloatArray {
        require(source.size == 3 && proposed.size == 3)
        require(source.all { it.isFinite() } && proposed.all { it.isFinite() })
        return floatArrayOf(
            proposed[0].coerceIn(source[0]-6f,source[0]+36f).coerceIn(0f,255f),
            proposed[1].coerceIn(source[1]-5f,source[1]+5f).coerceIn(0f,255f),
            proposed[2].coerceIn(source[2]-5f,source[2]+5f).coerceIn(0f,255f))
    }
}
