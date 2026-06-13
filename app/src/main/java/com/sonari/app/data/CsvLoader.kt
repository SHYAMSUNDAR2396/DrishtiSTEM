package com.sonari.app.data

import com.sonari.app.model.Bar
import com.sonari.app.model.BarChart
import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.Renderable
import com.sonari.app.model.ScatterChart
import kotlin.math.abs

object CsvLoader {

    fun load(csv: String): Result<Renderable> = runCatching {
        val rows = parseCsv(csv)
        require(rows.isNotEmpty()) { "CSV is empty" }

        val header = rows[0]
        val data = rows.drop(1).filter { it.size >= header.size }

        require(header.size >= 2) { "CSV must have at least 2 columns" }

        val col0AllNumeric = data.all { it[0].trim().toDoubleOrNull() != null }
        val col1AllNumeric = data.all { it.getOrNull(1)?.trim()?.toDoubleOrNull() != null }

        when {
            col0AllNumeric && col1AllNumeric -> buildLineOrScatter(header, data)
            !col0AllNumeric && col1AllNumeric -> buildBarChart(header, data)
            else -> error("CSV columns must be numeric x,y or category,value")
        }
    }

    private fun parseCsv(csv: String): List<List<String>> {
        return csv.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val cells = mutableListOf<String>()
                val sb = StringBuilder()
                var inQuotes = false
                for (ch in line) {
                    when {
                        ch == '"' -> inQuotes = !inQuotes
                        ch == ',' && !inQuotes -> { cells += sb.toString(); sb.clear() }
                        else -> sb.append(ch)
                    }
                }
                cells += sb.toString()
                cells
            }
    }

    private fun buildLineOrScatter(header: List<String>, data: List<List<String>>): Renderable {
        val points = data.mapNotNull { row ->
            val x = row[0].trim().toDoubleOrNull() ?: return@mapNotNull null
            val y = row[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            DataPoint(x, y)
        }.sortedBy { it.x }

        require(points.isNotEmpty()) { "No valid numeric rows" }

        val xMin = points.minOf { it.x }
        val xMax = points.maxOf { it.x }
        val yMin = points.minOf { it.y }
        val yMax = points.maxOf { it.y }

        val xRange = if (xMax > xMin) xMax - xMin else 1.0
        val yRange = if (yMax > yMin) yMax - yMin else 1.0

        // Decide: 20+ evenly spaced x values → LineChart, else ScatterChart.
        val isLine = points.size >= 20 && looksUniform(points, xRange)

        val landmarks = mutableListOf<Landmark>()
        if (isLine) {
            // x-intercepts (y sign changes)
            for (i in 1 until points.size) {
                val a = points[i - 1]; val b = points[i]
                if (a.y * b.y < 0.0 || (a.y == 0.0 && b.y != 0.0)) {
                    val nx = (a.x - xMin) / xRange
                    landmarks += Landmark(nx, 0.5, Landmark.Type.INTERCEPT,
                        "x-intercept at x=%.2f".format(a.x))
                }
            }
            // y-intercept
            val yInt = interpolate(points, 0.0)
            if (yInt != null) {
                val yIntNorm = (yInt - yMin) / yRange
                val tooClose = landmarks.any { abs(it.normX) < 0.02 }
                if (!tooClose) landmarks += Landmark(0.0, yIntNorm.coerceIn(0.0, 1.0),
                    Landmark.Type.INTERCEPT, "y-intercept=%.2f".format(yInt))
            }
        }

        return if (isLine) {
            LineChart(xMin, xMax, yMin, yMax, points, landmarks)
        } else {
            ScatterChart(xMin, xMax, yMin, yMax, points, landmarks)
        }
    }

    private fun looksUniform(points: List<DataPoint>, xRange: Double): Boolean {
        if (points.size < 2) return false
        val ideal = xRange / (points.size - 1)
        return points.zipWithNext().all { (a, b) ->
            abs((b.x - a.x) - ideal) < ideal * 0.3
        }
    }

    private fun interpolate(points: List<DataPoint>, x: Double): Double? {
        if (points.isEmpty()) return null
        val idx = points.indexOfFirst { it.x >= x }
        return when {
            idx <= 0 -> points.first().y
            idx >= points.size -> points.last().y
            else -> {
                val lo = points[idx - 1]; val hi = points[idx]
                if (hi.x == lo.x) lo.y
                else lo.y + (x - lo.x) / (hi.x - lo.x) * (hi.y - lo.y)
            }
        }
    }

    private fun buildBarChart(header: List<String>, data: List<List<String>>): BarChart {
        val bars = data.mapNotNull { row ->
            val category = row[0].trim()
            val value = row[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            Bar(category, value)
        }

        require(bars.isNotEmpty()) { "No valid rows in bar chart CSV" }

        val yMax = bars.maxOf { it.value }.coerceAtLeast(0.0)
        val yMin = bars.minOf { it.value }.coerceAtMost(0.0)
        val barCount = bars.size
        val barWidth = 1.0 / barCount

        val landmarks = bars.mapIndexed { i, bar ->
            val normX = (i + 0.5) * barWidth
            val yRange = if (yMax > yMin) yMax - yMin else 1.0
            val normY = ((bar.value - yMin) / yRange).coerceIn(0.0, 1.0)
            Landmark(normX, normY, Landmark.Type.BAR_TOP, "${bar.category}: %.2f".format(bar.value))
        }

        return BarChart(xMin = 0.0, xMax = 1.0, yMin = yMin, yMax = yMax, bars = bars,
            landmarks = landmarks)
    }
}
