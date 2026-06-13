package com.sonari.app.data

import com.sonari.app.model.BarChart
import com.sonari.app.model.LineChart
import com.sonari.app.model.ScatterChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvLoaderTest {

    // ─── Numeric x,y → LineChart (20+ uniformly spaced points) ─────────────────

    @Test
    fun `uniform 20-point numeric CSV produces LineChart`() {
        val csv = buildString {
            appendLine("x,y")
            for (i in 0..19) appendLine("$i,${i * i}")
        }
        val result = CsvLoader.load(csv)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is LineChart)
    }

    @Test
    fun `LineChart has correct xMin and xMax`() {
        val csv = buildString {
            appendLine("x,y")
            for (i in 0..20) appendLine("${i.toDouble()},${i.toDouble()}")
        }
        val chart = CsvLoader.load(csv).getOrThrow() as LineChart
        assertEquals(0.0, chart.xMin, 0.001)
        assertEquals(20.0, chart.xMax, 0.001)
    }

    @Test
    fun `LineChart has correct yMin and yMax`() {
        val csv = buildString {
            appendLine("x,y")
            for (i in 0..20) appendLine("$i,${i * 2}")
        }
        val chart = CsvLoader.load(csv).getOrThrow() as LineChart
        assertEquals(0.0, chart.yMin, 0.001)
        assertEquals(40.0, chart.yMax, 0.001)
    }

    @Test
    fun `x-intercept landmark added when curve crosses zero`() {
        // y = x - 5, crosses 0 at x=5
        val csv = buildString {
            appendLine("x,y")
            for (i in 0..20) appendLine("$i,${i - 5}")
        }
        val chart = CsvLoader.load(csv).getOrThrow() as LineChart
        assertTrue(chart.landmarks.any { it.type.name == "INTERCEPT" })
    }

    // ─── Few-point numeric x,y → ScatterChart ───────────────────────────────────

    @Test
    fun `sparse numeric CSV produces ScatterChart`() {
        val csv = "x,y\n1,2\n3,4\n5,6"
        val result = CsvLoader.load(csv)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is ScatterChart)
    }

    @Test
    fun `ScatterChart preserves all points`() {
        val csv = "x,y\n1,10\n2,20\n3,30"
        val chart = CsvLoader.load(csv).getOrThrow() as ScatterChart
        assertEquals(3, chart.points.size)
    }

    @Test
    fun `ScatterChart correct x range`() {
        val csv = "x,y\n2,5\n4,8\n6,3"
        val chart = CsvLoader.load(csv).getOrThrow() as ScatterChart
        assertEquals(2.0, chart.xMin, 0.001)
        assertEquals(6.0, chart.xMax, 0.001)
    }

    // ─── Category,value → BarChart ───────────────────────────────────────────────

    @Test
    fun `category-value CSV produces BarChart`() {
        val csv = "category,value\nMath,85\nScience,92\nArt,78"
        val result = CsvLoader.load(csv)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is BarChart)
    }

    @Test
    fun `BarChart has correct bar count`() {
        val csv = "subject,score\nMath,85\nScience,92\nArt,78\nHistory,70"
        val chart = CsvLoader.load(csv).getOrThrow() as BarChart
        assertEquals(4, chart.bars.size)
    }

    @Test
    fun `BarChart yMax equals max bar value`() {
        val csv = "name,value\nA,10\nB,50\nC,30"
        val chart = CsvLoader.load(csv).getOrThrow() as BarChart
        assertEquals(50.0, chart.yMax, 0.001)
    }

    @Test
    fun `BarChart landmarks have BAR_TOP type`() {
        val csv = "name,value\nAlpha,100\nBeta,200"
        val chart = CsvLoader.load(csv).getOrThrow() as BarChart
        assertTrue(chart.landmarks.all { it.type.name == "BAR_TOP" })
        assertEquals(2, chart.landmarks.size)
    }

    @Test
    fun `BarChart landmark label contains category name`() {
        val csv = "name,value\nGamma,42"
        val chart = CsvLoader.load(csv).getOrThrow() as BarChart
        assertTrue(chart.landmarks.first().label.contains("Gamma"))
    }

    // ─── Error cases ──────────────────────────────────────────────────────────────

    @Test
    fun `empty CSV returns failure`() {
        val result = CsvLoader.load("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `single column CSV returns failure`() {
        val result = CsvLoader.load("x\n1\n2\n3")
        assertTrue(result.isFailure)
    }

    @Test
    fun `mixed non-numeric columns return failure when second column non-numeric`() {
        val result = CsvLoader.load("name,desc\nFoo,Bar\nBaz,Qux")
        assertTrue(result.isFailure)
    }

    @Test
    fun `comment lines are ignored`() {
        val csv = """
            # This is a comment
            x,y
            1,2
            # another comment
            2,4
            3,6
        """.trimIndent()
        val result = CsvLoader.load(csv)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `quoted commas in category are handled`() {
        val csv = "category,value\n\"Math, Science\",85\nArt,78"
        val result = CsvLoader.load(csv)
        assertTrue(result.isSuccess)
        val chart = result.getOrThrow() as BarChart
        assertEquals(2, chart.bars.size)
        assertEquals("Math, Science", chart.bars[0].category)
    }
}
