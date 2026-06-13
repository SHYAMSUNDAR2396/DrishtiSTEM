package com.technoblaze.drishtistem.core.vision.cv

/**
 * The extracted curve as one pixel y-value per x-column, plus detected axis
 * positions and how much of the curve had to be interpolated over gaps.
 */
class Extracted(
    val curveY: IntArray,
    val width: Int,
    val height: Int,
    val xAxisPixel: Int,
    val yAxisPixel: Int,
    val gapFraction: Float
)

/**
 * Stage 3: trace the curve out of the binary ink mask. Removes the long
 * straight axis lines, then walks column by column picking the dark run that
 * best continues the previous column — so it follows one curve through grid
 * noise and fills small gaps by interpolation.
 */
object CurveExtractor {

    private const val AXIS_FRACTION = 0.70f
    private const val AXIS_MASK_RADIUS = 3

    fun extract(p: Preprocessed): Extracted {
        val w = p.width
        val h = p.height
        // Work on a mutable copy so axis masking doesn't mutate the input.
        val dark = p.dark.copyOf()

        // 3A — detect axis lines (rows/cols that are mostly ink) and mask them.
        var yAxisPixel = -1
        var yAxisBest = 0
        for (y in 0 until h) {
            var count = 0
            val row = y * w
            for (x in 0 until w) if (dark[row + x]) count++
            if (count > w * AXIS_FRACTION && count > yAxisBest) { yAxisBest = count; yAxisPixel = y }
        }
        var xAxisPixel = -1
        var xAxisBest = 0
        for (x in 0 until w) {
            var count = 0
            for (y in 0 until h) if (dark[y * w + x]) count++
            if (count > h * AXIS_FRACTION && count > xAxisBest) { xAxisBest = count; xAxisPixel = x }
        }
        maskRows(dark, w, h, yAxisPixel)
        maskCols(dark, w, h, xAxisPixel)
        // Also mask ALL axis-like rows/cols (there can be a frame on both sides).
        for (y in 0 until h) {
            var count = 0; val row = y * w
            for (x in 0 until w) if (dark[row + x]) count++
            if (count > w * AXIS_FRACTION) maskRows(dark, w, h, y)
        }
        for (x in 0 until w) {
            var count = 0
            for (y in 0 until h) if (dark[y * w + x]) count++
            if (count > h * AXIS_FRACTION) maskCols(dark, w, h, x)
        }

        // 3C — column-by-column trace.
        val curveY = IntArray(w) { -1 }
        var prevY = -1
        for (x in 0 until w) {
            val runs = darkRuns(dark, w, h, x)
            if (runs.isEmpty()) continue
            val chosen = if (prevY < 0) {
                runs.maxByOrNull { it.second - it.first }!!   // longest on first column
            } else {
                runs.minByOrNull { kotlin.math.abs((it.first + it.second) / 2 - prevY) }!!
            }
            val mid = (chosen.first + chosen.second) / 2
            curveY[x] = mid
            prevY = mid
        }

        val gapFraction = curveY.count { it < 0 }.toFloat() / w
        fillGaps(curveY)
        return Extracted(curveY, w, h, xAxisPixel, yAxisPixel, gapFraction)
    }

    /** Vertical dark runs in column [x] as (startY, endY) inclusive pairs. */
    private fun darkRuns(dark: BooleanArray, w: Int, h: Int, x: Int): List<Pair<Int, Int>> {
        val runs = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (y in 0 until h) {
            val isDark = dark[y * w + x]
            if (isDark && start < 0) start = y
            if (!isDark && start >= 0) { runs.add(start to y - 1); start = -1 }
        }
        if (start >= 0) runs.add(start to h - 1)
        return runs
    }

    private fun maskRows(dark: BooleanArray, w: Int, h: Int, y: Int) {
        if (y < 0) return
        for (dy in -AXIS_MASK_RADIUS..AXIS_MASK_RADIUS) {
            val yy = y + dy
            if (yy in 0 until h) {
                val row = yy * w
                for (x in 0 until w) dark[row + x] = false
            }
        }
    }

    private fun maskCols(dark: BooleanArray, w: Int, h: Int, x: Int) {
        if (x < 0) return
        for (dx in -AXIS_MASK_RADIUS..AXIS_MASK_RADIUS) {
            val xx = x + dx
            if (xx in 0 until w) {
                for (y in 0 until h) dark[y * w + xx] = false
            }
        }
    }

    /** Linear-interpolate -1 gaps; clamp leading/trailing gaps to nearest known. */
    private fun fillGaps(a: IntArray) {
        val n = a.size
        var first = -1
        for (i in 0 until n) if (a[i] >= 0) { first = i; break }
        if (first < 0) return
        for (i in 0 until first) a[i] = a[first]
        var last = first
        var i = first + 1
        while (i < n) {
            if (a[i] >= 0) {
                if (i > last + 1) {
                    val span = i - last
                    for (j in last + 1 until i) {
                        a[j] = a[last] + (a[i] - a[last]) * (j - last) / span
                    }
                }
                last = i
            }
            i++
        }
        for (j in last + 1 until n) a[j] = a[last]
    }
}
