package com.sonari.app.engine

import com.sonari.app.model.BarChart
import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.MoleculeGraph
import com.sonari.app.model.Renderable
import com.sonari.app.model.ScatterChart
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

data class Cue(
    val freqHz: Double,
    val pan: Double,
    val onFeature: Boolean,
    val landmark: Landmark?,
    val quadrant: Int,
    val clamped: Boolean
)

interface MappingEngine {
    fun cueAt(normX: Double, normY: Double, r: Renderable): Cue
}

object DefaultMappingEngine : MappingEngine {

    @Volatile var freqLow: Double = 200.0
    @Volatile var freqHigh: Double = 1000.0
    @Volatile var panEnabled: Boolean = true

    private const val FEATURE_TOL = 0.04
    private const val ATOM_TOL = 0.06
    private const val BOND_TOL = 0.04
    private const val LANDMARK_TOL = 0.03

    override fun cueAt(normX: Double, normY: Double, r: Renderable): Cue {
        val nx = normX.coerceIn(0.0, 1.0)
        val ny = normY.coerceIn(0.0, 1.0)
        return when (r) {
            is LineChart -> lineChartCue(nx, ny, r)
            is MoleculeGraph -> moleculeCue(nx, ny, r)
            is BarChart -> barChartCue(nx, ny, r)
            is ScatterChart -> scatterChartCue(nx, ny, r)
        }
    }

    private fun pan(nx: Double): Double = if (panEnabled) nx * 2.0 - 1.0 else 0.0

    private fun freq(normY: Double): Double =
        freqLow * (freqHigh / freqLow).pow(normY.coerceIn(0.0, 1.0))

    private fun lineChartCue(nx: Double, ny: Double, r: LineChart): Cue {
        val rawY = interpolate(r.samples, nx, r.xMin, r.xMax)
        val featureNY = (rawY - r.yMin) / (r.yMax - r.yMin)
        val clamped = featureNY < 0.0 || featureNY > 1.0
        val cFNY = featureNY.coerceIn(0.0, 1.0)
        val freqHz = freq(cFNY)
        val onFeature = abs(ny - cFNY) < FEATURE_TOL
        val nearestLandmark = r.landmarks.firstOrNull { abs(nx - it.normX) < LANDMARK_TOL }
        return Cue(freqHz, pan(nx), onFeature, nearestLandmark, quadrantOf(nx, ny), clamped)
    }

    private fun moleculeCue(nx: Double, ny: Double, r: MoleculeGraph): Cue {
        val onAtom = r.atoms.any { hypot(nx - it.normX, ny - it.normY) < ATOM_TOL }
        val onBond = !onAtom && r.bonds.any { b ->
            val ax = r.atoms[b.fromIndex].normX; val ay = r.atoms[b.fromIndex].normY
            val bx = r.atoms[b.toIndex].normX; val by = r.atoms[b.toIndex].normY
            pointToSegDist(nx, ny, ax, ay, bx, by) < BOND_TOL
        }
        val featureY = if (onAtom) {
            r.atoms.minByOrNull { hypot(nx - it.normX, ny - it.normY) }!!.normY
        } else {
            ny
        }
        val freqHz = freq(featureY)
        val nearestLandmark = r.landmarks.firstOrNull { hypot(nx - it.normX, ny - it.normY) < LANDMARK_TOL }
        return Cue(freqHz, pan(nx), onAtom || onBond, nearestLandmark, quadrantOf(nx, ny), false)
    }

    private fun barChartCue(nx: Double, ny: Double, r: BarChart): Cue {
        val barCount = r.bars.size
        if (barCount == 0) return Cue(freq(0.0), pan(nx), false, null, quadrantOf(nx, ny), false)

        val barWidth = 1.0 / barCount
        val barIdx = (nx / barWidth).toInt().coerceIn(0, barCount - 1)
        val bar = r.bars[barIdx]
        val barNormY = (bar.value - r.yMin) / (r.yMax - r.yMin)
        val clamped = barNormY < 0.0 || barNormY > 1.0
        val cBarNY = barNormY.coerceIn(0.0, 1.0)
        val freqHz = freq(cBarNY)
        val onFeature = ny >= cBarNY - FEATURE_TOL
        val barCenterNx = (barIdx + 0.5) * barWidth
        val nearestLandmark = r.landmarks.firstOrNull { abs(nx - it.normX) < barWidth * 0.6 }
        return Cue(freqHz, pan(barCenterNx), onFeature, nearestLandmark, quadrantOf(nx, ny), clamped)
    }

    private fun scatterChartCue(nx: Double, ny: Double, r: ScatterChart): Cue {
        if (r.points.isEmpty()) return Cue(freq(0.0), pan(nx), false, null, quadrantOf(nx, ny), false)

        val xRange = r.xMax - r.xMin
        val yRange = r.yMax - r.yMin
        val nearest = r.points.minByOrNull { pt ->
            val pnx = (pt.x - r.xMin) / xRange
            val pny = (pt.y - r.yMin) / yRange
            hypot(nx - pnx, ny - pny)
        }!!
        val ptNx = (nearest.x - r.xMin) / xRange
        val ptNy = (nearest.y - r.yMin) / yRange
        val onFeature = hypot(nx - ptNx, ny - ptNy) < FEATURE_TOL * 2
        val freqHz = freq(ptNy)
        val nearestLandmark = r.landmarks.firstOrNull { hypot(nx - it.normX, ny - it.normY) < LANDMARK_TOL }
        return Cue(freqHz, pan(ptNx), onFeature, nearestLandmark, quadrantOf(nx, ny), false)
    }

    private fun quadrantOf(nx: Double, ny: Double): Int = when {
        nx >= 0.5 && ny >= 0.5 -> 1
        nx < 0.5 && ny >= 0.5 -> 2
        nx < 0.5 && ny < 0.5 -> 3
        else -> 4
    }

    private fun interpolate(samples: List<DataPoint>, nx: Double, xMin: Double, xMax: Double): Double {
        if (samples.isEmpty()) return 0.0
        val rawX = xMin + nx * (xMax - xMin)
        val idx = samples.indexOfFirst { it.x >= rawX }
        return when {
            idx <= 0 -> samples.first().y
            idx >= samples.size -> samples.last().y
            else -> {
                val lo = samples[idx - 1]; val hi = samples[idx]
                val t = (rawX - lo.x) / (hi.x - lo.x)
                lo.y + t * (hi.y - lo.y)
            }
        }
    }

    private fun pointToSegDist(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Double {
        val abx = bx - ax; val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq == 0.0) return hypot(px - ax, py - ay)
        val t = ((px - ax) * abx + (py - ay) * aby).div(lenSq).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * abx), py - (ay + t * aby))
    }
}
