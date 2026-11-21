package com.branchphotovault.retouch

/**
 * Conservative detail gate for AttGAN's optional second view.
 *
 * Only dark, line-shaped local contrast can receive additional lightening.
 * A broad smooth-skin region has no hair-like texture, so the second model
 * may NOT paint large matte/gray patches over that area.
 *
 * This deliberately does not edit the mask or assume that a dark pixel is
 * definitely hair. The engine still protects lips, jawline and outside-mask.
 */
internal object ResidualHairDetailGate {
    fun weight(pixels: IntArray, width: Int, height: Int,
               x: Int, y: Int, radius: Int): Float {
        if(width<3 || height<3 || x !in 0 until width || y !in 0 until height) return 0f
        val r=radius.coerceIn(2,24)
        val center=luma(pixels[y*width+x])
        var total=0f
        val offsets=intArrayOf(
            -r,0, r,0, 0,-r, 0,r,
            -r,-r, r,-r, -r,r, r,r
        )
        for (i in offsets.indices step 2) {
            val xx=(x+offsets[i]).coerceIn(0,width-1)
            val yy=(y+offsets[i+1]).coerceIn(0,height-1)
            total+=luma(pixels[yy*width+xx])
        }
        // Three luminance units of local texture are tolerated as natural
        // skin noise; dark stubble should stand out above surrounding skin.
        val t=((total/8f-center-3f)/9f).coerceIn(0f,1f)
        // C1 smoothstep avoids hard threshold seams around hair strands.
        return t*t*(3f-2f*t)
    }

    private fun luma(color: Int): Float {
        val r=(color ushr 16) and 255
        val g=(color ushr 8) and 255
        val b=color and 255
        return .2126f*r+.7152f*g+.0722f*b
    }
}
