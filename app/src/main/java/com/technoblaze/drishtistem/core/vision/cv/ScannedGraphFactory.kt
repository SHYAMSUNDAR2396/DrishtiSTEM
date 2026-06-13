package com.technoblaze.drishtistem.core.vision.cv

import com.technoblaze.drishtistem.model.Curve
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.Subject
import kotlin.math.abs

/**
 * Stage 5 (rule-based): wrap an extracted curve in a [GraphConcept] the existing
 * GraphExplorerScreen can render, with a plain-language spoken description of the
 * shape. No ML — a small set of deterministic rules names the curve. The
 * explorer derives landmarks (peaks/troughs/roots) itself from the curve.
 */
object ScannedGraphFactory {

    private const val SAMPLES = CurveNormaliser.SAMPLES

    fun fromProcessed(p: ProcessedCurve): GraphConcept =
        build(p.normalised, describe(p.normalised))

    /** Synthetic U-shaped parabola for the always-available demo fallback. */
    fun demoParabola(): GraphConcept {
        val samples = FloatArray(SAMPLES) { i ->
            val t = i.toFloat() / (SAMPLES - 1) * 2f - 1f // x in [-1, 1]
            2f * t * t - 1f                               // U: -1 centre, +1 edges
        }
        return build(samples, "Demo parabola. A U shaped curve, lowest in the middle, rising on both sides.")
    }

    private fun build(samples: FloatArray, description: String): GraphConcept =
        GraphConcept(
            id = "scanned",
            subject = Subject.MATHS,
            title = "Scanned graph",
            spokenIntro = description,
            curves = listOf(Curve("scanned curve") { x -> interp(samples, x) }),
            xMin = -1f, xMax = 1f, yMin = -1f, yMax = 1f,
            xAxisLabel = "position", yAxisLabel = "height"
        )

    /** Sample the [-1,1]-domain curve by linear interpolation over the 300 points. */
    private fun interp(samples: FloatArray, x: Float): Float {
        val t = ((x + 1f) / 2f).coerceIn(0f, 1f)
        val pos = t * (samples.size - 1)
        val lo = pos.toInt()
        if (lo >= samples.size - 1) return samples.last()
        val frac = pos - lo
        return samples[lo] * (1 - frac) + samples[lo + 1] * frac
    }

    private fun describe(s: FloatArray): String {
        val eps = 0.02f
        val rising = s.toList().zipWithNext().all { (a, b) -> b >= a - eps }
        val falling = s.toList().zipWithNext().all { (a, b) -> b <= a + eps }

        var minima = 0
        var maxima = 0
        var lastExtremum = -100
        for (i in 1 until s.size - 1) {
            if (i - lastExtremum < 20) continue
            if (s[i] < s[i - 1] && s[i] < s[i + 1]) { minima++; lastExtremum = i }
            else if (s[i] > s[i - 1] && s[i] > s[i + 1]) { maxima++; lastExtremum = i }
        }
        var zeroCrossings = 0
        for (i in 0 until s.size - 1) if (s[i] * s[i + 1] < 0) zeroCrossings++

        return when {
            minima == 1 && maxima == 0 ->
                "A U shaped curve, lowest in the middle and rising on both sides. Likely a parabola."
            maxima == 1 && minima == 0 ->
                "An n shaped curve, highest in the middle and falling on both sides. Likely a downward parabola."
            rising && !falling ->
                "A curve that rises steadily from left to right."
            falling && !rising ->
                "A curve that falls steadily from left to right."
            zeroCrossings >= 2 ->
                "A wave like curve that rises and falls several times. Possibly a sine or cosine wave."
            abs(s.first() - s.last()) < 0.1f && maxima + minima == 0 ->
                "An almost flat, level curve."
            else ->
                "A curve with ${maxima + minima} turning points."
        }
    }
}
