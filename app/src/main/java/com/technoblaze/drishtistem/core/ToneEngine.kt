package com.technoblaze.drishtistem.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time sine synthesizer driven by finger position:
 * pitch encodes the y-value, stereo pan encodes the x-position.
 *
 * A single background thread streams audio continuously; frequency, pan and
 * volume are written from the UI thread and picked up per-buffer. Volume is
 * smoothed per-sample to avoid clicks on touch down/up.
 */
class ToneEngine {

    @Volatile private var frequency = 440f
    @Volatile private var pan = 0.5f // 0 = hard left, 1 = hard right
    @Volatile private var targetVolume = 0f
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::audioLoop, "ToneEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun release() {
        running = false
        thread?.join(500)
        thread = null
    }

    /** Unmute; call on touch down. */
    fun play() {
        targetVolume = 0.85f
    }

    /** Fade out; call on touch up. */
    fun mute() {
        targetVolume = 0f
    }

    /** Map a data value within [min, max] onto a log-spaced 200–1200 Hz pitch. */
    fun setPitchFromValue(value: Float, min: Float, max: Float) {
        val t = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0.5f
        frequency = (FREQ_MIN * (FREQ_MAX / FREQ_MIN).toDouble().pow(t.toDouble())).toFloat()
    }

    /** Direct frequency control (Wave Lab). */
    fun setFrequency(hz: Float) {
        frequency = hz.coerceIn(40f, 4000f)
    }

    /** Horizontal position 0..1 across the screen. */
    fun setPan(x01: Float) {
        pan = x01.coerceIn(0f, 1f)
    }

    private fun audioLoop() {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val frames = 512
        val buffer = ShortArray(frames * 2)
        var phase = 0.0
        var volume = 0f

        while (running) {
            val freq = frequency
            val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
            val leftGain = (1f - pan).coerceIn(0.12f, 1f)
            val rightGain = pan.coerceIn(0.12f, 1f)

            for (i in 0 until frames) {
                // ~6 ms attack/release at 44.1 kHz keeps transitions click-free.
                volume += (targetVolume - volume) * 0.004f
                val sample = (sin(phase) * volume * Short.MAX_VALUE).toInt()
                buffer[i * 2] = (sample * leftGain).toInt().toShort()
                buffer[i * 2 + 1] = (sample * rightGain).toInt().toShort()
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            track.write(buffer, 0, buffer.size)
        }

        track.stop()
        track.release()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val FREQ_MIN = 200f
        const val FREQ_MAX = 1200f
    }
}
