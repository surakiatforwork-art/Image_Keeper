package com.branchphotovault.retouch

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Registration for generated/cloud portraits. A cloud may return a square
 * canvas even when Keeper submitted a portrait crop. Never stretch that
 * output to the crop's aspect ratio: fit one uniform scale + rotation
 * + translation from corresponding face landmarks instead.
 *
 * Image processing is deliberately independent of the model/API so this
 * geometry can be unit tested without requesting paid cloud jobs.
 */
internal object CloudFaceAlignment {
    data class Point(val x: Double, val y: Double)

    data class Fit(
        val a: Double,
        val b: Double,
        val tx: Double,
        val ty: Double,
        val meanResidual: Double,
        val worstResidual: Double,
        val scale: Double
    ) {
        // [ a -b tx ]   [x]
        // [ b  a ty ] * [y]
        fun map(p: Point): Point = Point(
            a * p.x - b * p.y + tx,
            b * p.x + a * p.y + ty
        )
    }

    /**
     * Least-squares similarity transform (isotropic; no squeeze/stretch).
     * The supplied pairs should be stable facial landmarks (eye corners,
     * nose and mouth corners). Destination coordinates are in the
     * ORIGINAL crop local coordinate system.
     */
    fun fit(remote: List<Point>, target: List<Point>): Fit {
        require(remote.size == target.size && remote.size >= 3) { "Not enough facial landmarks" }
        val n = remote.size.toDouble()
        val rx = remote.sumOf { it.x } / n
        val ry = remote.sumOf { it.y } / n
        val tx = target.sumOf { it.x } / n
        val ty = target.sumOf { it.y } / n
        var crossX = 0.0
        var crossY = 0.0
        var denominator = 0.0
        for (i in remote.indices) {
            val x = remote[i].x - rx
            val y = remote[i].y - ry
            val u = target[i].x - tx
            val v = target[i].y - ty
            crossX += x*u + y*v
            crossY += x*v - y*u
            denominator += x*x + y*y
        }
        require(denominator > 1e-4) { "Facial landmarks overlap" }
        val a = crossX / denominator
        val b = crossY / denominator
        val offsetX = tx - a * rx + b * ry
        val offsetY = ty - b * rx - a * ry
        val scale = sqrt(a*a+b*b)
        require(scale.isFinite() && scale in 0.1..10.0) { "Cloud face scale is abnormal" }
        val proto = Fit(a,b,offsetX,offsetY,0.0,0.0,scale)
        val errors = remote.indices.map { i ->
            val p = proto.map(remote[i])
            hypot(p.x-target[i].x,p.y-target[i].y)
        }
        return proto.copy(
            meanResidual=errors.average(),
            worstResidual=errors.max()
        )
    }
}
