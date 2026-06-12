package com.technoblaze.drishtistem.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Maps abstract "feel" intensities and named patterns onto the device vibrator.
 *
 * Continuous exploration feedback uses short repeated one-shots whose amplitude
 * tracks the finger's context (e.g. curve slope). Devices without amplitude
 * control fall back to modulating pulse duration instead.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private val hasAmplitudeControl = vibrator.hasAmplitudeControl()
    private var lastFeelAt = 0L

    /**
     * Continuous feedback while the finger is on a feature.
     * [intensity] in 0..1; 0 stops vibration. Throttled so it can be called
     * on every touch event without flooding the vibrator service.
     */
    fun feel(intensity: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFeelAt < FEEL_PERIOD_MS) return
        lastFeelAt = now

        val clamped = intensity.coerceIn(0f, 1f)
        if (clamped < 0.02f) {
            vibrator.cancel()
            return
        }
        if (hasAmplitudeControl) {
            val amplitude = (1 + clamped * 254).toInt()
            vibrator.vibrate(VibrationEffect.createOneShot(FEEL_PERIOD_MS + 10, amplitude))
        } else {
            // No amplitude control: shorter buzz = lighter feel.
            val duration = (8 + clamped * (FEEL_PERIOD_MS + 2)).toLong()
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Strong single pulse: corners, peaks, found targets. */
    fun pulse() {
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createOneShot(90, 255))
    }

    /** Light tick: axis crossings, list focus changes. */
    fun tick() {
        vibrator.vibrate(VibrationEffect.createOneShot(25, if (hasAmplitudeControl) 160 else VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Element/bond signature patterns. Timings and amplitudes per createWaveform contract. */
    fun pattern(timings: LongArray, amplitudes: IntArray) {
        vibrator.cancel()
        if (hasAmplitudeControl) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }

    fun stop() {
        vibrator.cancel()
    }

    private companion object {
        const val FEEL_PERIOD_MS = 35L
    }
}
