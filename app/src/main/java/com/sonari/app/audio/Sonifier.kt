package com.sonari.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class Sonifier {

    private val sampleRate = 44100
    private val framesPerChunk = 512
    private val buffer = FloatArray(framesPerChunk * 2) // stereo interleaved

    @Volatile private var targetFreq = 440.0
    @Volatile private var targetPan = 0.0
    @Volatile private var isActive = false
    @Volatile private var running = false

    private var phase = 0.0
    private var smoothFreq = 440.0
    private var smoothPan = 0.0

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ),
                framesPerChunk * 2 * 4
            )
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val thread = Thread(::generateLoop, "Sonari-Audio").also { it.isDaemon = true }

    init {
        track.play()
        running = true
        thread.start()
    }

    fun setCue(freqHz: Double, pan: Double, active: Boolean) {
        targetFreq = freqHz.coerceIn(20.0, 20_000.0)
        targetPan = pan.coerceIn(-1.0, 1.0)
        isActive = active
    }

    private fun generateLoop() {
        val twoPi = 2.0 * PI
        while (running) {
            // Smooth frequency and pan to avoid clicks.
            smoothFreq += (targetFreq - smoothFreq) * 0.04
            smoothPan += (targetPan - smoothPan) * 0.12

            val amplitude = if (isActive) 0.35f else 0.0f
            val leftGain = ((1.0 - smoothPan) / 2.0).toFloat().coerceIn(0f, 1f)
            val rightGain = ((1.0 + smoothPan) / 2.0).toFloat().coerceIn(0f, 1f)
            val phaseInc = twoPi * smoothFreq / sampleRate

            for (i in 0 until framesPerChunk) {
                val s = (sin(phase) * amplitude).toFloat()
                buffer[i * 2] = s * leftGain
                buffer[i * 2 + 1] = s * rightGain
                phase += phaseInc
            }
            // Wrap phase to avoid floating-point drift.
            if (phase > twoPi * 100) phase -= twoPi * 100

            val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
        }
    }

    fun release() {
        running = false
        track.pause()
        track.flush()
        thread.join(300)
        track.release()
    }
}
