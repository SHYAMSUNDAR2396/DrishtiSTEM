package com.sonari.app.audio

import com.sonari.app.a11y.Announcer
import com.sonari.app.engine.DefaultMappingEngine
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.BarChart
import com.sonari.app.model.LineChart
import com.sonari.app.model.Renderable
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow

class SweepPlayer(
    private val sonifier: Sonifier,
    private val haptics: Haptics,
    private val announcer: Announcer
) {

    // Sweep left→right. Suspend until sweep completes or coroutine is cancelled.
    // onProgress: called each frame with normX in [0,1] for UI playhead.
    suspend fun sweep(
        renderable: Renderable,
        durationMs: Long = 5_000L,
        onProgress: (Float) -> Unit
    ) {
        when (renderable) {
            is LineChart -> sweepLineChart(renderable, durationMs, onProgress)
            is BarChart -> sweepBarChart(renderable, durationMs, onProgress)
            else -> return
        }
    }

    private suspend fun sweepLineChart(
        renderable: LineChart,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ) {
        val stepMs = 20L
        val steps = (durationMs / stepMs).toInt()
        val firedLandmarks = mutableSetOf<Int>()

        for (step in 0..steps) {
            val normX = step.toDouble() / steps
            val normY = curveNormY(normX, renderable)
            val fLow = DefaultMappingEngine.freqLow
            val fHigh = DefaultMappingEngine.freqHigh
            val freqHz = fLow * (fHigh / fLow).pow(normY)

            // In Overview, pan = 0 — works on device speaker, no headphones required.
            sonifier.setCue(freqHz, pan = 0.0, active = true)
            onProgress(normX.toFloat())

            // Landmark earcon: haptic pulse when sweep crosses a landmark.
            renderable.landmarks.forEachIndexed { idx, lm ->
                if (idx !in firedLandmarks && abs(normX - lm.normX) < 0.018) {
                    firedLandmarks += idx
                    haptics.landmark()
                    announcer.landmark(lm)
                }
            }

            delay(stepMs)
        }

        sonifier.setCue(440.0, 0.0, active = false)
        onProgress(0f)
    }

    private suspend fun sweepBarChart(
        renderable: BarChart,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ) {
        if (renderable.bars.isEmpty()) return
        val barCount = renderable.bars.size
        val perBarMs = durationMs / barCount
        val yRange = renderable.yMax - renderable.yMin

        renderable.bars.forEachIndexed { i, bar ->
            val normY = if (yRange > 0) ((bar.value - renderable.yMin) / yRange).coerceIn(0.0, 1.0) else 0.5
            val fLow = DefaultMappingEngine.freqLow
            val fHigh = DefaultMappingEngine.freqHigh
            val freqHz = fLow * (fHigh / fLow).pow(normY)
            val normX = ((i + 0.5) / barCount).toFloat()
            sonifier.setCue(freqHz, pan = 0.0, active = true)
            onProgress(normX)
            haptics.landmark()
            announcer.landmark(renderable.landmarks[i])
            delay(perBarMs)
        }
        sonifier.setCue(440.0, 0.0, active = false)
        onProgress(0f)
    }

    private fun curveNormY(normX: Double, chart: LineChart): Double {
        val rawX = chart.xMin + normX * (chart.xMax - chart.xMin)
        val idx = chart.samples.indexOfFirst { it.x >= rawX }
        val rawY = when {
            idx <= 0 -> chart.samples.first().y
            idx >= chart.samples.size -> chart.samples.last().y
            else -> {
                val lo = chart.samples[idx - 1]
                val hi = chart.samples[idx]
                lo.y + (rawX - lo.x) / (hi.x - lo.x) * (hi.y - lo.y)
            }
        }
        return ((rawY - chart.yMin) / (chart.yMax - chart.yMin)).coerceIn(0.0, 1.0)
    }
}
