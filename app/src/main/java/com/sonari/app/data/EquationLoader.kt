package com.sonari.app.data

import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import net.objecthunter.exp4j.ExpressionBuilder
import kotlin.math.abs

object EquationLoader {

    private const val N_SAMPLES = 600
    private const val MIN_LANDMARK_GAP = 0.05  // minimum normX separation between landmarks

    fun load(
        expression: String,
        xMin: Double = -10.0,
        xMax: Double = 10.0
    ): Result<LineChart> {
        if (xMin >= xMax) {
            return Result.failure(IllegalArgumentException("xMin must be less than xMax"))
        }
        val expr = try {
            ExpressionBuilder(expression.trim())
                .variable("x")
                .build()
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Cannot parse: ${e.message}"))
        }

        val pts = mutableListOf<DataPoint>()
        val xRange = xMax - xMin
        for (i in 0..N_SAMPLES) {
            val x = xMin + i.toDouble() / N_SAMPLES * xRange
            try {
                val y = expr.setVariable("x", x).evaluate()
                if (y.isFinite()) pts.add(DataPoint(x, y))
            } catch (_: Exception) { /* skip singular points */ }
        }

        if (pts.size < 2) {
            return Result.failure(IllegalStateException("Equation produces no plottable values in [$xMin, $xMax]"))
        }

        val yMin = pts.minOf { it.y }
        val yMax = pts.maxOf { it.y }
        val yRange = (yMax - yMin).let { if (it < 1e-9) 1.0 else it }

        val landmarks = detectLandmarks(pts, xMin, xMax, yMin, yRange)

        return Result.success(LineChart(xMin, xMax, yMin, yMax, pts, landmarks))
    }

    private fun detectLandmarks(
        pts: List<DataPoint>,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yRange: Double
    ): List<Landmark> {
        val xRange = xMax - xMin
        val result = mutableListOf<Landmark>()

        fun normX(x: Double) = ((x - xMin) / xRange).coerceIn(0.0, 1.0)
        fun normY(y: Double) = ((y - yMin) / yRange).coerceIn(0.0, 1.0)
        fun tooClose(nx: Double) = result.any { abs(it.normX - nx) < MIN_LANDMARK_GAP }

        // 1. Local extrema first — highest information value for shape understanding.
        val diffs = pts.zipWithNext { a, b -> b.y - a.y }
        for (i in 1 until diffs.size) {
            if (diffs[i - 1] * diffs[i] < 0) {
                val pt = pts[i]
                val nx = normX(pt.x)
                if (!tooClose(nx)) {
                    val label = if (diffs[i - 1] > 0)
                        "local maximum %.2f at x equals %.2f".format(pt.y, pt.x)
                    else
                        "local minimum %.2f at x equals %.2f".format(pt.y, pt.x)
                    result += Landmark(nx, normY(pt.y), Landmark.Type.EXTREMUM, label)
                }
            }
        }

        // 2. x-intercepts: sign changes between adjacent samples.
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            if (a.y * b.y < 0) {
                val t = a.y / (a.y - b.y)
                val zeroX = a.x + t * (b.x - a.x)
                val nx = normX(zeroX)
                if (!tooClose(nx)) {
                    result += Landmark(nx, normY(0.0), Landmark.Type.INTERCEPT,
                        "x intercept at x equals %.2f".format(zeroX))
                }
            }
        }

        // 3. y-intercept: value at x closest to 0.
        val atZero = pts.minByOrNull { abs(it.x) }
        if (atZero != null && abs(atZero.x) < xRange / N_SAMPLES * 2) {
            val nx = normX(0.0)
            if (!tooClose(nx)) {
                result += Landmark(nx, normY(atZero.y), Landmark.Type.INTERCEPT,
                    "y intercept at y equals %.2f".format(atZero.y))
            }
        }

        return result
    }
}
