package com.sonari.app.engine

import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.MoleculeGraph
import com.sonari.app.model.Renderable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

data class Cue(
    val freqHz: Double,
    val pan: Double,
    val onFeature: Boolean,
    val landmark: Landmark?,
    val quadrant: Int,
    val clamped: Boolean,
    val touchedAtom: String? = null,
    val touchedBond: Int? = null
)

interface MappingEngine {
    fun cueAt(normX: Double, normY: Double, r: Renderable): Cue
}

object DefaultMappingEngine : MappingEngine {

    private const val FREQ_LOW = 200.0
    private const val FREQ_HIGH = 1000.0
    private const val FEATURE_TOL = 0.04
    private const val ATOM_TOL = 0.08
    private const val BOND_TOL = 0.04
    private const val LANDMARK_TOL = 0.03

    override fun cueAt(normX: Double, normY: Double, r: Renderable): Cue {
        val nx = normX.coerceIn(0.0, 1.0)
        val ny = normY.coerceIn(0.0, 1.0)
        return when (r) {
            is LineChart -> lineChartCue(nx, ny, r)
            is MoleculeGraph -> moleculeCue(nx, ny, r)
        }
    }

    private fun lineChartCue(nx: Double, ny: Double, r: LineChart): Cue {
        val rawY = interpolate(r.samples, nx, r.xMin, r.xMax)
        val featureNY = (rawY - r.yMin) / (r.yMax - r.yMin)
        val clamped = featureNY < 0.0 || featureNY > 1.0
        val cFNY = featureNY.coerceIn(0.0, 1.0)
        val freqHz = FREQ_LOW * (FREQ_HIGH / FREQ_LOW).pow(cFNY)
        val curveYInScreenSpace = 1.0 - cFNY
        val onFeature = abs(ny - curveYInScreenSpace) < FEATURE_TOL
        val nearestLandmark = r.landmarks.firstOrNull { abs(nx - it.normX) < LANDMARK_TOL }
        return Cue(freqHz, nx * 2.0 - 1.0, onFeature, nearestLandmark, quadrantOf(nx, ny), clamped)
    }

    private fun moleculeCue(nx: Double, ny: Double, r: MoleculeGraph): Cue {
        val nearestAtomIdx = r.atoms.indices.minByOrNull { i ->
            hypot(nx - r.atoms[i].normX, ny - r.atoms[i].normY)
        }
        val onAtom = nearestAtomIdx != null &&
            hypot(nx - r.atoms[nearestAtomIdx].normX, ny - r.atoms[nearestAtomIdx].normY) < ATOM_TOL

        val nearestBond = r.bonds.minByOrNull { b ->
            val ax = r.atoms[b.fromIndex].normX; val ay = r.atoms[b.fromIndex].normY
            val bx = r.atoms[b.toIndex].normX; val by = r.atoms[b.toIndex].normY
            pointToSegDist(nx, ny, ax, ay, bx, by)
        }.takeIf { b ->
            if (b == null) return@takeIf false
            val ax = r.atoms[b.fromIndex].normX; val ay = r.atoms[b.fromIndex].normY
            val bx = r.atoms[b.toIndex].normX; val by = r.atoms[b.toIndex].normY
            pointToSegDist(nx, ny, ax, ay, bx, by) < BOND_TOL
        }

        val featureY = if (onAtom) {
            r.atoms[nearestAtomIdx!!].normY
        } else if (nearestBond != null) {
            val ai = r.atoms[nearestBond.fromIndex]
            val bi = r.atoms[nearestBond.toIndex]
            (ai.normY + bi.normY) / 2.0
        } else {
            ny
        }

        val freqHz = FREQ_LOW * (FREQ_HIGH / FREQ_LOW).pow(featureY.coerceIn(0.0, 1.0))
        val nearestLandmark = r.landmarks.firstOrNull { hypot(nx - it.normX, ny - it.normY) < LANDMARK_TOL }

        val touchedAtom = if (onAtom) r.atoms[nearestAtomIdx!!].element else null
        val touchedBond = nearestBond?.order

        return Cue(
            freqHz, nx * 2.0 - 1.0, onAtom || nearestBond != null,
            nearestLandmark, quadrantOf(nx, ny), false,
            touchedAtom = touchedAtom, touchedBond = touchedBond
        )
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
