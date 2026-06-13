package com.sonari.app.audio

import com.sonari.app.a11y.Announcer
import com.sonari.app.haptic.Haptics
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
    private val freqLow = 200.0
    private val freqHigh = 1000.0

    // Sweep left→right. Suspend until sweep completes or coroutine is cancelled.
    // onProgress: called each frame with normX in [0,1] for UI playhead.
    suspend fun sweep(
        renderable: Renderable,
        durationMs: Long = 5_000L,
        onProgress: (Float) -> Unit
    ) {
        if (renderable !is LineChart) return

        val stepMs = 20L
        val steps = (durationMs / stepMs).toInt()
        val firedLandmarks = mutableSetOf<Int>()

        for (step in 0..steps) {
            val normX = step.toDouble() / steps
            val normY = curveNormY(normX, renderable)
            val freqHz = freqLow * (freqHigh / freqLow).pow(normY)

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
