package com.technoblaze.drishtistem.core.vision.cv

/**
 * The pipeline's mathematical output: a fixed-resolution curve in haptic space
 * plus a confidence estimate.
 */
class ProcessedCurve(
    val normalised: FloatArray, // exactly 300 samples, range [-1, 1]
    val confidence: Float       // 0..1
)

/**
 * Stage 4: turn raw per-column pixel y-values into a clean 300-sample curve in
 * [-1, 1] haptic space. Maps pixels to maths coordinates around the detected
 * origin, resamples to a fixed width, and smooths with a Savitzky–Golay filter
 * (which preserves peaks/troughs, unlike a moving average).
 */
object CurveNormaliser {

    const val SAMPLES = 300

    // Savitzky–Golay, window 11, cubic, smoothing (0th derivative).
    private val SG = intArrayOf(-36, 9, 44, 69, 84, 89, 84, 69, 44, 9, -36)
    private const val SG_NORM = 429f
    private const val SG_HALF = 5

    fun normalise(e: Extracted): ProcessedCurve {
        val originY = if (e.yAxisPixel >= 0) e.yAxisPixel else e.height / 2
        val scale = (e.height / 2f).coerceAtLeast(1f)

        // 4A+4B — resample width → 300, mapping pixel rows to [-1, 1] (up = +).
        val resampled = FloatArray(SAMPLES)
        for (i in 0 until SAMPLES) {
            val srcPos = if (SAMPLES == 1) 0f else i.toFloat() / (SAMPLES - 1) * (e.width - 1)
            val lo = srcPos.toInt().coerceIn(0, e.width - 1)
            val hi = (lo + 1).coerceAtMost(e.width - 1)
            val frac = srcPos - lo
            val py = e.curveY[lo] * (1 - frac) + e.curveY[hi] * frac
            resampled[i] = ((originY - py) / scale).coerceIn(-1f, 1f)
        }

        val smoothed = savitzkyGolay(resampled)

        // Confidence scoring.
        var confidence = 1.0f
        if (e.yAxisPixel < 0 && e.xAxisPixel < 0) confidence -= 0.2f
        confidence -= (e.gapFraction / 0.1f).toInt() * 0.1f
        confidence = confidence.coerceIn(0f, 1f)

        return ProcessedCurve(smoothed, confidence)
    }

    private fun savitzkyGolay(a: FloatArray): FloatArray {
        val n = a.size
        val out = FloatArray(n)
        for (i in 0 until n) {
            if (i < SG_HALF || i >= n - SG_HALF) {
                out[i] = a[i] // edges: leave raw (window doesn't fit)
                continue
            }
            var acc = 0f
            for (k in -SG_HALF..SG_HALF) acc += SG[k + SG_HALF] * a[i + k]
            out[i] = (acc / SG_NORM).coerceIn(-1f, 1f)
        }
        return out
    }
}
