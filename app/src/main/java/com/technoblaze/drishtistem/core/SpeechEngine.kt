package com.technoblaze.drishtistem.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin queueing wrapper over the platform [TextToSpeech].
 * Announcements requested before the engine is ready are buffered and
 * flushed on init, so screens can speak immediately on entry.
 */
class SpeechEngine(context: Context) {

    private var ready = false
    private val pending = mutableListOf<Pair<String, Boolean>>()
    private var utteranceId = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.language = Locale.US
            tts.setSpeechRate(0.95f)
            pending.forEach { (text, interrupt) -> speakNow(text, interrupt) }
            pending.clear()
        }
    }

    /**
     * Speak [text]. With [interrupt] the queue is flushed first — used for
     * landmark callouts so stale announcements never pile up under the finger.
     */
    fun announce(text: String, interrupt: Boolean = true) {
        if (!ready) {
            if (interrupt) pending.clear()
            pending.add(text to interrupt)
            return
        }
        speakNow(text, interrupt)
    }

    fun stopSpeaking() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun speakNow(text: String, interrupt: Boolean) {
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, mode, null, "drishti-${utteranceId++}")
    }
}
