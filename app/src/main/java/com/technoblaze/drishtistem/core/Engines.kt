package com.technoblaze.drishtistem.core

import android.content.Context

/** Bundles the three sensory engines; one instance lives for the whole activity. */
class Engines(context: Context) {
    val haptics = HapticEngine(context)
    val tone = ToneEngine()
    val speech = SpeechEngine(context)

    fun start() {
        tone.start()
    }

    fun quietAll() {
        tone.mute()
        haptics.stop()
        speech.stopSpeaking()
    }

    fun release() {
        tone.release()
        haptics.stop()
        speech.shutdown()
    }
}
