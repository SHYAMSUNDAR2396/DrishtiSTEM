package com.sonari.app.engine

import com.sonari.app.model.Atom
import com.sonari.app.model.Bond
import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.MoleculeGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class MappingEngineTest {

    // y = x, sampled at x=0..1 in normalized space (xMin=0, xMax=1, yMin=0, yMax=1)
    private val linearChart = run {
        val samples = (0..100).map { i ->
            val x = i / 100.0
            DataPoint(x, x)
        }
        LineChart(xMin = 0.0, xMax = 1.0, yMin = 0.0, yMax = 1.0, samples = samples,
            landmarks = listOf(Landmark(0.0, 0.0, Landmark.Type.INTERCEPT, "origin")))
    }

    // y = x², sampled over x = -3..3, y range 0..9
    private val parabolaChart = run {
        val samples = (-300..300).map { i ->
            val x = i / 100.0
            DataPoint(x, x * x)
        }
        LineChart(xMin = -3.0, xMax = 3.0, yMin = 0.0, yMax = 9.0, samples = samples,
            landmarks = listOf(
                Landmark(0.5, 0.0, Landmark.Type.EXTREMUM, "vertex"),
                Landmark(0.0, 1.0, Landmark.Type.INTERCEPT, "left zero"),
                Landmark(1.0, 1.0, Landmark.Type.INTERCEPT, "right zero")
            ))
    }

    private val engine = DefaultMappingEngine

    // --- Pitch bounds ---

    @Test
    fun `pitch at normY=0 is 200 Hz`() {
        // y=x at normX=0 → curve value = 0 → featureNY=0 → 200 Hz
        val cue = engine.cueAt(0.0, 0.0, linearChart)
        assertEquals(200.0, cue.freqHz, 1.0)
    }

    @Test
    fun `pitch at normY=1 is 1000 Hz`() {
        // Place finger on normX=1, normY=1 → y=1 → featureNY=1 → 1000 Hz
        val cue = engine.cueAt(1.0, 1.0, linearChart)
        assertEquals(1000.0, cue.freqHz, 1.0)
    }

    @Test
    fun `pitch is log scale - midpoint is geometric mean`() {
        // At normY=0.5, freq should be sqrt(200*1000) ≈ 447.2
        val expected = 200.0 * (1000.0 / 200.0).pow(0.5)
        val cue = engine.cueAt(0.5, 0.5, linearChart)
        assertEquals(expected, cue.freqHz, 2.0)
    }

    // --- Pan ---

    @Test
    fun `pan at normX=0 is -1`() {
        val cue = engine.cueAt(0.0, 0.5, linearChart)
        assertEquals(-1.0, cue.pan, 0.001)
    }

    @Test
    fun `pan at normX=1 is +1`() {
        val cue = engine.cueAt(1.0, 0.5, linearChart)
        assertEquals(1.0, cue.pan, 0.001)
    }

    @Test
    fun `pan at normX=0_5 is 0`() {
        val cue = engine.cueAt(0.5, 0.5, linearChart)
        assertEquals(0.0, cue.pan, 0.001)
    }

    // --- onFeature for line chart ---

    @Test
    fun `onFeature true when finger is on the y=x line`() {
        // On y=x: normX=0.5, normY=0.5 → featureNY=0.5, delta=0 < 0.04
        val cue = engine.cueAt(0.5, 0.5, linearChart)
        assertTrue(cue.onFeature)
    }

    @Test
    fun `onFeature false when finger is far from the line`() {
        // On y=x: normX=0.5, curve is at normY=0.5; finger at normY=0.0 → delta=0.5 > 0.04
        val cue = engine.cueAt(0.5, 0.0, linearChart)
        assertFalse(cue.onFeature)
    }

    @Test
    fun `onFeature at parabola vertex`() {
        // normX=0.5 → rawX=0.0 → y=0² = 0 → featureNY = (0-0)/(9-0) = 0.0
        // finger normY=0.02 → delta=0.02 < 0.04 → onFeature
        val cue = engine.cueAt(0.5, 0.02, parabolaChart)
        assertTrue(cue.onFeature)
    }

    // --- Quadrants ---

    @Test
    fun `quadrant 1 is top-right`() {
        val cue = engine.cueAt(0.75, 0.75, linearChart)
        assertEquals(1, cue.quadrant)
    }

    @Test
    fun `quadrant 2 is top-left`() {
        val cue = engine.cueAt(0.25, 0.75, linearChart)
        assertEquals(2, cue.quadrant)
    }

    @Test
    fun `quadrant 3 is bottom-left`() {
        val cue = engine.cueAt(0.25, 0.25, linearChart)
        assertEquals(3, cue.quadrant)
    }

    @Test
    fun `quadrant 4 is bottom-right`() {
        val cue = engine.cueAt(0.75, 0.25, linearChart)
        assertEquals(4, cue.quadrant)
    }

    // --- Molecule ---

    private val waterMolecule = MoleculeGraph(
        atoms = listOf(
            Atom("O", 0.5, 0.5),
            Atom("H", 0.3, 0.7),
            Atom("H", 0.7, 0.7)
        ),
        bonds = listOf(
            Bond(0, 1, 1),
            Bond(0, 2, 1)
        )
    )

    @Test
    fun `molecule onFeature true when finger is on atom`() {
        // Finger at exact O position
        val cue = engine.cueAt(0.5, 0.5, waterMolecule)
        assertTrue(cue.onFeature)
    }

    @Test
    fun `molecule onFeature false in empty space`() {
        // Far from any atom or bond
        val cue = engine.cueAt(0.1, 0.1, waterMolecule)
        assertFalse(cue.onFeature)
    }

    @Test
    fun `molecule landmark returned near atom`() {
        val cue = engine.cueAt(0.5, 0.5, waterMolecule)
        assertNotNull(cue.landmark)
        assertEquals("O", cue.landmark!!.label)
    }

    @Test
    fun `molecule no landmark far from atoms`() {
        val cue = engine.cueAt(0.1, 0.1, waterMolecule)
        assertNull(cue.landmark)
    }

    @Test
    fun `molecule onFeature true on bond segment`() {
        // Midpoint between O(0.5,0.5) and H(0.3,0.7) → (0.4, 0.6) — on the bond
        val cue = engine.cueAt(0.4, 0.6, waterMolecule)
        assertTrue(cue.onFeature)
    }

    // --- Clamping ---

    @Test
    fun `clamped false when y in visible range`() {
        val cue = engine.cueAt(0.5, 0.5, linearChart)
        assertFalse(cue.clamped)
    }

    @Test
    fun `clamped true when curve value is above yMax`() {
        // Parabola at normX=1.0 → rawX=3.0 → y=9.0 → featureNY=1.0; at normX=1.0+epsilon this would clamp
        // Use a chart where curve exceeds display range: yMax=4, curve at x=3 → y=9 > 4
        val chart = LineChart(
            xMin = -3.0, xMax = 3.0, yMin = 0.0, yMax = 4.0,
            samples = (-300..300).map { i -> DataPoint(i / 100.0, (i / 100.0) * (i / 100.0)) },
            landmarks = emptyList()
        )
        // normX=1.0 → rawX=3.0 → y=9, yMax=4 → featureNY = (9-0)/(4-0) = 2.25 > 1 → clamped
        val cue = engine.cueAt(1.0, 0.5, chart)
        assertTrue(cue.clamped)
    }
}
