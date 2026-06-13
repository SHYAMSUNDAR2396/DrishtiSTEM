package com.technoblaze.drishtistem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.technoblaze.drishtistem.core.vision.GraphVision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feeds GraphVision synthetic graph images (drawn into a Bitmap) so the parsing
 * pipeline can be verified without a physical camera.
 */
@RunWith(AndroidJUnit4::class)
class GraphVisionTest {

    private fun blankGraph(): Pair<Bitmap, Canvas> {
        val w = 480
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        // Axes (full-length lines that the pipeline should suppress as structural).
        val axis = Paint().apply { color = Color.BLACK; strokeWidth = 3f }
        canvas.drawLine(40f, 20f, 40f, (h - 20).toFloat(), axis)          // y-axis
        canvas.drawLine(20f, (h - 40).toFloat(), (w - 20).toFloat(), (h - 40).toFloat(), axis) // x-axis
        return bmp to canvas
    }

    private fun curvePaint() = Paint().apply {
        color = Color.BLACK
        strokeWidth = 6f
        isAntiAlias = true
    }

    @Test
    fun parsesUpwardParabola() {
        val (bmp, canvas) = blankGraph()
        val w = bmp.width
        val h = bmp.height
        val paint = curvePaint()
        // U-shape: vertex at bottom-centre, arms rising to the top edges.
        var prevX = 0f
        var prevY = 0f
        for (px in 50 until w - 30) {
            val t = (px - 50f) / (w - 80f)        // 0..1 across plot
            val centred = (t - 0.5f) * 2f          // -1..1
            val yNorm = centred * centred          // 0 at centre, 1 at edges
            val py = (h - 50) - yNorm * (h - 110)  // large row at vertex, small at arms
            if (px > 50) canvas.drawLine(prevX, prevY, px.toFloat(), py, paint)
            prevX = px.toFloat(); prevY = py
        }

        val result = GraphVision.analyze(bmp)
        assertTrue("Expected a graph to be detected", result is GraphVision.Result.Success)
        val concept = (result as GraphVision.Result.Success).concept
        val curve = concept.curves.first().f

        val left = curve(0.5f)
        val middle = curve(5f)
        val right = curve(9.5f)
        // Parabola: the middle should sit well below both ends.
        assertTrue("middle ($middle) should be below left ($left)", middle < left - 2f)
        assertTrue("middle ($middle) should be below right ($right)", middle < right - 2f)
    }

    @Test
    fun parsesRisingLine() {
        val (bmp, canvas) = blankGraph()
        val w = bmp.width
        val h = bmp.height
        val paint = curvePaint()
        // Straight rising line from bottom-left to top-right of the plot.
        canvas.drawLine(50f, (h - 50).toFloat(), (w - 30).toFloat(), 50f, paint)

        val result = GraphVision.analyze(bmp)
        assertTrue("Expected a graph to be detected", result is GraphVision.Result.Success)
        val curve = (result as GraphVision.Result.Success).concept.curves.first().f
        // Monotonic increase: end clearly higher than start.
        assertTrue("line should rise", curve(9.5f) > curve(0.5f) + 4f)
    }

    @Test
    fun rejectsBlankPage() {
        val bmp = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        val result = GraphVision.analyze(bmp)
        assertEquals(GraphVision.Result.Failure::class, result::class)
    }
}
