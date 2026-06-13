package com.technoblaze.drishtistem.core.vision

import android.graphics.Bitmap
import com.technoblaze.drishtistem.model.Curve
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.Subject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device, pure-Kotlin vision pipeline that turns a photo of a printed line
 * graph into an explorable [GraphConcept]. No native libraries and no network:
 * the captured image never leaves the device, preserving the offline guarantee.
 *
 * Pipeline (mirrors the deck's architecture, implemented in software):
 *   1. Downscale for speed.
 *   2. Grayscale + Otsu threshold -> ink / paper, polarity auto-detected.
 *   3. Suppress axes and grid lines (rows/columns that are almost fully ink).
 *   4. Extract one curve point per column as the median ink row.
 *   5. Fill gaps, smooth, and normalise into graph coordinates.
 *   6. Wrap the samples in an interpolating [Curve] inside a [GraphConcept].
 *
 * The existing GraphExplorerScreen then renders it and auto-detects landmarks.
 */
object GraphVision {

    /** Output domain of every scanned graph: x is "position", y is "relative height". */
    private const val X_MAX = 10f
    private const val Y_MAX = 10f
    private const val WORK_WIDTH = 320

    sealed interface Result {
        /** A usable graph was found. [spokenSummary] describes its shape for the user. */
        data class Success(val concept: GraphConcept, val spokenSummary: String) : Result
        /** Nothing graph-like was found; [message] tells the user how to retry. */
        data class Failure(val message: String) : Result
    }

    fun analyze(source: Bitmap): Result {
        val bmp = downscale(source)
        val w = bmp.width
        val h = bmp.height
        if (w < 16 || h < 16) {
            return Result.Failure(retryMessage())
        }

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma.
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        val threshold = otsu(gray)
        var darkCount = 0
        for (v in gray) if (v < threshold) darkCount++
        // Ink is the minority class: dark ink on light paper, or vice-versa.
        val inkIsDark = darkCount <= w * h / 2
        val ink = BooleanArray(w * h) { i ->
            if (inkIsDark) gray[i] < threshold else gray[i] > threshold
        }

        // Work inside an inset to drop the photo's frame / page edges.
        val insetX = (w * 0.06f).toInt()
        val insetY = (h * 0.06f).toInt()
        val left = insetX
        val right = w - insetX
        val top = insetY
        val bottom = h - insetY
        val regionW = right - left
        val regionH = bottom - top
        if (regionW < 8 || regionH < 8) return Result.Failure(retryMessage())

        // Mark structural rows/columns (axes, borders, dense grid lines) so the
        // curve extractor ignores them. A real curve rarely spans a full row.
        val structuralRow = BooleanArray(h)
        for (y in top until bottom) {
            var count = 0
            for (x in left until right) if (ink[y * w + x]) count++
            if (count > regionW * 0.55f) structuralRow[y] = true
        }
        val structuralCol = BooleanArray(w)
        for (x in left until right) {
            var count = 0
            for (y in top until bottom) if (ink[y * w + x]) count++
            if (count > regionH * 0.55f) structuralCol[x] = true
        }

        // One sample per column: the median row of non-structural ink.
        val m = regionW
        val rawRow = FloatArray(m) { Float.NaN }
        var columnsWithInk = 0
        for (cx in 0 until m) {
            val x = left + cx
            if (structuralCol[x]) continue
            val rows = ArrayList<Int>()
            for (y in top until bottom) {
                if (structuralRow[y]) continue
                if (ink[y * w + x]) rows.add(y)
            }
            if (rows.isNotEmpty()) {
                rows.sort()
                rawRow[cx] = rows[rows.size / 2].toFloat()
                columnsWithInk++
            }
        }

        val confidence = columnsWithInk.toFloat() / m
        if (confidence < 0.25f) {
            return Result.Failure(retryMessage())
        }

        fillGaps(rawRow)
        val smoothed = movingAverage(rawRow, window = 5)

        // Convert image rows (y grows downward) into heights (up is larger),
        // then normalise the detected band into 0..Y_MAX.
        var minRow = Float.MAX_VALUE
        var maxRow = -Float.MAX_VALUE
        for (v in smoothed) {
            if (v.isNaN()) continue
            minRow = min(minRow, v)
            maxRow = max(maxRow, v)
        }
        val span = (maxRow - minRow).coerceAtLeast(1f)
        val samples = FloatArray(m) { i ->
            // High ink row (small value) -> tall on graph.
            ((maxRow - smoothed[i]) / span) * Y_MAX
        }

        val concept = GraphConcept(
            id = "scanned",
            subject = Subject.MATHS,
            title = "Scanned graph",
            spokenIntro = "Scanned graph.",
            curves = listOf(Curve("scanned curve") { x -> sampleAt(samples, x) }),
            xMin = 0f, xMax = X_MAX,
            yMin = 0f, yMax = Y_MAX,
            xAxisLabel = "position",
            yAxisLabel = "relative height"
        )
        return Result.Success(concept, describe(samples))
    }

    /** Linear interpolation of the captured samples across x in [0, X_MAX]. */
    private fun sampleAt(samples: FloatArray, x: Float): Float {
        if (samples.isEmpty()) return 0f
        val t = (x / X_MAX).coerceIn(0f, 1f)
        val pos = t * (samples.size - 1)
        val i = pos.toInt()
        if (i >= samples.size - 1) return samples.last()
        val frac = pos - i
        return samples[i] * (1 - frac) + samples[i + 1] * frac
    }

    /** Replace NaN gaps with linear interpolation between known neighbours. */
    private fun fillGaps(a: FloatArray) {
        val n = a.size
        var firstKnown = -1
        for (i in 0 until n) if (!a[i].isNaN()) { firstKnown = i; break }
        if (firstKnown == -1) return
        // Clamp leading/trailing gaps to the nearest known value.
        for (i in 0 until firstKnown) a[i] = a[firstKnown]
        var lastKnown = firstKnown
        var i = firstKnown + 1
        while (i < n) {
            if (!a[i].isNaN()) {
                if (i > lastKnown + 1) {
                    val step = (a[i] - a[lastKnown]) / (i - lastKnown)
                    for (j in lastKnown + 1 until i) a[j] = a[lastKnown] + step * (j - lastKnown)
                }
                lastKnown = i
            }
            i++
        }
        for (j in lastKnown + 1 until n) a[j] = a[lastKnown]
    }

    private fun movingAverage(a: FloatArray, window: Int): FloatArray {
        val n = a.size
        val half = window / 2
        return FloatArray(n) { idx ->
            var sum = 0f
            var count = 0
            for (k in (idx - half)..(idx + half)) {
                if (k in 0 until n && !a[k].isNaN()) { sum += a[k]; count++ }
            }
            if (count == 0) a[idx] else sum / count
        }
    }

    /** Otsu's method: the grayscale threshold maximising between-class variance. */
    private fun otsu(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v]++
        val total = gray.size
        var sumAll = 0.0
        for (t in 0..255) sumAll += t.toDouble() * hist[t]
        var sumB = 0.0
        var wB = 0
        var maxVar = -1.0
        var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = t }
        }
        return threshold
    }

    /** Plain-language description of the captured shape for the spoken callout. */
    private fun describe(samples: FloatArray): String {
        if (samples.size < 2) return "Graph captured. Trace it with your finger to explore."
        // Downsample so noise doesn't read as extra peaks.
        val d = 24
        val coarse = FloatArray(d) { k ->
            val from = (k * samples.size) / d
            val to = max(from + 1, ((k + 1) * samples.size) / d)
            var s = 0f
            for (j in from until to) s += samples[j]
            s / (to - from)
        }
        val trendDelta = coarse.last() - coarse.first()
        val trend = when {
            trendDelta > 1.5f -> "rises overall"
            trendDelta < -1.5f -> "falls overall"
            else -> "stays fairly level overall"
        }

        var peaks = 0
        var dips = 0
        var dir = 0 // -1 down, +1 up
        val deadband = 0.7f
        for (i in 1 until d) {
            val diff = coarse[i] - coarse[i - 1]
            if (abs(diff) < deadband) continue
            val newDir = if (diff > 0) 1 else -1
            if (dir > 0 && newDir < 0) peaks++
            if (dir < 0 && newDir > 0) dips++
            dir = newDir
        }

        val shape = buildString {
            append("The line ")
            append(trend)
            if (peaks > 0) append(", with ${count(peaks, "peak")}")
            if (dips > 0) append("${if (peaks > 0) " and" else ", with"} ${count(dips, "dip")}")
            append(".")
        }
        return "Graph captured. $shape Trace it with your finger to explore."
    }

    private fun count(n: Int, noun: String) = "$n $noun${if (n == 1) "" else "s"}"

    private fun retryMessage() =
        "I could not find a clear graph. Hold the phone steady, fill the frame with the graph, " +
            "and make sure the line stands out from the background, then try again."

    private fun downscale(src: Bitmap): Bitmap {
        if (src.width <= WORK_WIDTH) return src
        val scale = WORK_WIDTH.toFloat() / src.width
        val h = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, WORK_WIDTH, h, true)
    }
}
