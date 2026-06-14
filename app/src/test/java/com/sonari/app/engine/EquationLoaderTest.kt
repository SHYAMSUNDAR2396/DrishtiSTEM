package com.sonari.app.engine

import com.sonari.app.data.EquationLoader
import com.sonari.app.model.Landmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationLoaderTest {

    @Test
    fun `parabola x^2 loads successfully`() {
        val chart = EquationLoader.load("x^2", xMin = -3.0, xMax = 3.0).getOrThrow()
        assertEquals(-3.0, chart.xMin, 0.001)
        assertEquals(3.0, chart.xMax, 0.001)
        assertTrue(chart.yMin >= 0.0)
        assertTrue(chart.samples.isNotEmpty())
    }

    @Test
    fun `parabola yMin is near 0`() {
        val chart = EquationLoader.load("x^2", -3.0, 3.0).getOrThrow()
        assertEquals(0.0, chart.yMin, 0.1)
    }

    @Test
    fun `parabola yMax is near 9`() {
        val chart = EquationLoader.load("x^2", -3.0, 3.0).getOrThrow()
        assertEquals(9.0, chart.yMax, 0.1)
    }

    @Test
    fun `parabola has vertex landmark near normX=0_5`() {
        val chart = EquationLoader.load("x^2", -3.0, 3.0).getOrThrow()
        val vertex = chart.landmarks.firstOrNull { it.type == Landmark.Type.EXTREMUM }
        assertNotNull("Expected vertex landmark", vertex)
        assertEquals(0.5, vertex!!.normX, 0.05)
        assertEquals(0.0, vertex.normY, 0.05)
    }

    @Test
    fun `linear y=2x has x-intercept at normX=0_5`() {
        val chart = EquationLoader.load("2*x", -5.0, 5.0).getOrThrow()
        val xIntercept = chart.landmarks.firstOrNull { it.type == Landmark.Type.INTERCEPT }
        assertNotNull(xIntercept)
        assertEquals(0.5, xIntercept!!.normX, 0.05)
    }

    @Test
    fun `sin(x) has multiple x-intercepts`() {
        val chart = EquationLoader.load("sin(x)", -10.0, 10.0).getOrThrow()
        val intercepts = chart.landmarks.filter { it.type == Landmark.Type.INTERCEPT }
        assertTrue("Expected ≥3 intercepts for sin(x) over -10..10", intercepts.size >= 3)
    }

    @Test
    fun `sin(x) yMin near -1 yMax near 1`() {
        val chart = EquationLoader.load("sin(x)", -10.0, 10.0).getOrThrow()
        assertEquals(-1.0, chart.yMin, 0.05)
        assertEquals(1.0, chart.yMax, 0.05)
    }

    @Test
    fun `invalid expression returns failure`() {
        val result = EquationLoader.load("not_an_equation")
        assertTrue(result.isFailure)
    }

    @Test
    fun `domain where xMin=xMax returns failure`() {
        val result = EquationLoader.load("x^2", 5.0, 5.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun `samples count is reasonable`() {
        val chart = EquationLoader.load("x^2", -3.0, 3.0).getOrThrow()
        assertTrue("Expected ≥200 samples", chart.samples.size >= 200)
    }
}
