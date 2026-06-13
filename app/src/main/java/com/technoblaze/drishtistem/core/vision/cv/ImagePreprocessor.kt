package com.technoblaze.drishtistem.core.vision.cv

import android.graphics.Bitmap

/**
 * Binary ink mask cropped to the graph region. [dark] is row-major of size
 * [width]*[height]; `true` means a dark (curve-candidate) pixel.
 */
class Preprocessed(
    val dark: BooleanArray,
    val width: Int,
    val height: Int
)

/**
 * Stage 2 of the deterministic CV pipeline: turn a photo into a clean binary
 * ink mask cropped to the graph area. Pure integer work — no intermediate
 * Bitmaps, no ML. Handles uneven lighting via a local adaptive threshold and
 * crops away the phone/desk/fingers via a background flood-fill.
 */
object ImagePreprocessor {

    private const val MAX_DIM = 1920
    private const val BLOCK = 16
    private const val THRESHOLD_BIAS = 15
    private const val ROI_PAD = 0.05f

    fun process(source: Bitmap): Preprocessed {
        val bmp = downscale(source)
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val grey = IntArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        val dark = adaptiveThreshold(grey, w, h)
        val roi = detectRoi(dark, w, h)
        return crop(dark, w, roi)
    }

    /** 2B — local adaptive threshold: dark if value < (block mean − bias). */
    private fun adaptiveThreshold(grey: IntArray, w: Int, h: Int): BooleanArray {
        val blocksX = (w + BLOCK - 1) / BLOCK
        val blocksY = (h + BLOCK - 1) / BLOCK
        val blockMean = IntArray(blocksX * blocksY)
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var sum = 0L
                var count = 0
                val x0 = bx * BLOCK
                val y0 = by * BLOCK
                for (y in y0 until minOf(y0 + BLOCK, h)) {
                    val row = y * w
                    for (x in x0 until minOf(x0 + BLOCK, w)) {
                        sum += grey[row + x]; count++
                    }
                }
                blockMean[by * blocksX + bx] = if (count > 0) (sum / count).toInt() else 128
            }
        }
        val dark = BooleanArray(w * h)
        for (y in 0 until h) {
            val by = y / BLOCK
            val row = y * w
            for (x in 0 until w) {
                val bx = x / BLOCK
                val t = blockMean[by * blocksX + bx] - THRESHOLD_BIAS
                dark[row + x] = grey[row + x] < t
            }
        }
        return dark
    }

    /**
     * 2C — flood-fill the light "background" inward from the four corners; the
     * ROI is the bounding box of everything not reached, padded 5%. Falls back
     * to the centre 80% if the detected box is implausibly small.
     */
    private fun detectRoi(dark: BooleanArray, w: Int, h: Int): IntArray {
        val bg = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        val seeds = intArrayOf(0, w - 1, (h - 1) * w, h * w - 1)
        for (s in seeds) {
            if (!dark[s] && !bg[s]) { bg[s] = true; stack[sp++] = s }
        }
        while (sp > 0) {
            val idx = stack[--sp]
            val x = idx % w
            val y = idx / w
            // 4-neighbour flood over light pixels.
            if (x > 0) { val n = idx - 1; if (!dark[n] && !bg[n]) { bg[n] = true; stack[sp++] = n } }
            if (x < w - 1) { val n = idx + 1; if (!dark[n] && !bg[n]) { bg[n] = true; stack[sp++] = n } }
            if (y > 0) { val n = idx - w; if (!dark[n] && !bg[n]) { bg[n] = true; stack[sp++] = n } }
            if (y < h - 1) { val n = idx + w; if (!dark[n] && !bg[n]) { bg[n] = true; stack[sp++] = n } }
        }

        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (!bg[row + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val plausible = maxX >= minX && maxY >= minY &&
            (maxX - minX) > w * 0.3f && (maxY - minY) > h * 0.3f
        if (!plausible) {
            // Centre 80% fallback.
            return intArrayOf((w * 0.1f).toInt(), (h * 0.1f).toInt(), (w * 0.9f).toInt(), (h * 0.9f).toInt())
        }
        val padX = ((maxX - minX) * ROI_PAD).toInt()
        val padY = ((maxY - minY) * ROI_PAD).toInt()
        return intArrayOf(
            (minX - padX).coerceAtLeast(0),
            (minY - padY).coerceAtLeast(0),
            (maxX + padX).coerceAtMost(w - 1),
            (maxY + padY).coerceAtMost(h - 1)
        )
    }

    private fun crop(dark: BooleanArray, w: Int, roi: IntArray): Preprocessed {
        val (x0, y0, x1, y1) = roi
        val rw = x1 - x0 + 1
        val rh = y1 - y0 + 1
        val out = BooleanArray(rw * rh)
        for (y in 0 until rh) {
            val srcRow = (y0 + y) * w + x0
            val dstRow = y * rw
            for (x in 0 until rw) out[dstRow + x] = dark[srcRow + x]
        }
        return Preprocessed(out, rw, rh)
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]

    private fun downscale(src: Bitmap): Bitmap {
        val max = maxOf(src.width, src.height)
        if (max <= MAX_DIM) return ensureArgb(src)
        val scale = MAX_DIM.toFloat() / max
        val scaled = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
        return ensureArgb(scaled)
    }

    /** getPixels needs a software ARGB bitmap; picked/hardware bitmaps may not be. */
    private fun ensureArgb(src: Bitmap): Bitmap =
        if (src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, false)
}
