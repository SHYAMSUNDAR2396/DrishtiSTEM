package com.sonari.app.a11y

import android.content.Context
import android.speech.tts.TextToSpeech
import com.sonari.app.model.Landmark
import com.sonari.app.model.Renderable
import java.util.Locale

class Announcer(context: Context) {

    private var ready = false
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
        }
    }

    init {
        tts.language = Locale.US
    }

    fun announce(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sonari-${text.hashCode()}")
    }

    fun coordinates(normX: Double, normY: Double, r: Renderable) {
        val x = r.xMin + normX * (r.xMax - r.xMin)
        val y = r.yMin + normY * (r.yMax - r.yMin)
        announce("x %.2f, y %.2f".format(x, y))
    }

    fun landmark(l: Landmark) = announce(l.label)

    fun release() = tts.shutdown()
}
