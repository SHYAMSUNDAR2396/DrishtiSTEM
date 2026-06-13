package com.technoblaze.drishtistem.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * One-shot speech capture for the "find the point" hook. Prefers on-device
 * recognition (so it can work offline where a language pack is installed) and
 * always degrades gracefully: if recognition is unavailable or errors, the
 * caller falls back to the on-screen button. Recognises code-mixed Hinglish
 * intent words ("dikhao", "point", "intersection", …) by keyword, not grammar.
 */
object VoiceCommand {

    private val INTENT_WORDS = listOf(
        "intersect", "point", "peak", "trough", "root", "cross", "find", "key", "maximum", "minimum",
        // Hinglish
        "dikha", "dhoond", "batao", "kahan", "nikaal"
    )

    fun isAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /** True if [text] looks like a "guide me to the key point" request. */
    fun isGuideIntent(text: String): Boolean {
        val t = text.lowercase()
        return INTENT_WORDS.any { t.contains(it) }
    }

    /**
     * Listen once on the main thread. [onResult] gets the best transcript, or
     * null if recognition is unavailable / failed. The recognizer is destroyed
     * after the single result.
     */
    fun listenOnce(context: Context, onResult: (String?) -> Unit) {
        if (!isAvailable(context)) { onResult(null); return }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        var done = false
        fun finish(text: String?) {
            if (done) return
            done = true
            onResult(text)
            recognizer.destroy()
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish(text)
            }
            override fun onError(error: Int) = finish(null)
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }
}
