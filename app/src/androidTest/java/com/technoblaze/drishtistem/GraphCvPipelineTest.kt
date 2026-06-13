package com.technoblaze.drishtistem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.technoblaze.drishtistem.core.vision.cv.CurveExtractor
import com.technoblaze.drishtistem.core.vision.cv.CurveNormaliser
import com.technoblaze.drishtistem.core.vision.cv.ImagePreprocessor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the deterministic CV stages on synthetic graph bitmaps so the curve
 * extraction is verified without a camera or any model. Mirrors the real
 * pipeline (preprocess → extract → normalise).
 */
@RunWith(AndroidJUnit4::class)
class GraphCvPipelineTest {

    private fun blank(w: Int = 600, h: Int = 480): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        // Axis lines (long straight → should be removed by the extractor).
        val axis = Paint().apply { color = Color.BLACK; strokeWidth = 2f }
        c.drawLine(40f, h - 40f, w - 20f, h - 40f, axis)  // x-axis
        c.drawLine(40f, 20f, 40f, h - 20f, axis)          // y-axis
        return bmp to c
    }

    private fun curvePaint() = Paint().apply {
        color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true
    }

    private fun process(bmp: Bitmap): FloatArray {
        val pre = ImagePreprocessor.process(bmp)
        val raw = CurveExtractor.extract(pre)
        return CurveNormaliser.normalise(raw).normalised
    }

    @Test
    fun parabolaComesOutUShaped() {
        val (bmp, c) = blank()
        val w = bmp.width; val h = bmp.height
        val paint = curvePaint()
        var prevX = 0f; var prevY = 0f
        for (px in 60 until w - 30) {
            val t = (px - 60f) / (w - 90f)       // 0..1
            val centred = (t - 0.5f) * 2f         // -1..1
            // U-shape: vertex near bottom-centre, arms rise toward the top.
            val yVal = centred * centred          // 0 centre, 1 edges
            val drawY = (h - 70f) - yVal * (h - 140f)
            if (px > 60) c.drawLine(prevX, prevY, px.toFloat(), drawY, paint)
            prevX = px.toFloat(); prevY = drawY
        }

        val curve = process(bmp)
        val left = curve[20]
        val mid = curve[150]
        val right = curve[279]
        assertTrue("mid ($mid) should sit below left ($left)", mid < left - 0.2f)
        assertTrue("mid ($mid) should sit below right ($right)", mid < right - 0.2f)
    }

    @Test
    fun risingLineIsMonotonic() {
        val (bmp, c) = blank()
        val w = bmp.width; val h = bmp.height
        c.drawLine(60f, h - 70f, (w - 30).toFloat(), 70f, curvePaint())

        val curve = process(bmp)
        assertTrue("line should rise overall", curve[279] > curve[20] + 0.3f)
    }
}
