package com.sonari.app.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val IDLE = Color(0xFF4FC3F7)
private val LISTENING = Color(0xFFEF5350)
private val PROCESSING = Color(0xFFFFC107)

/**
 * States for the voice button visual.
 */
enum class VoiceButtonState { IDLE, LISTENING, PROCESSING }

/**
 * Large floating-action-style microphone button at 72dp (up from 48dp).
 *
 * - [onResult] receives the recognized speech text when the user speaks.
 * - [onError] receives error descriptions (skips normal timeouts/no-match).
 * - [buttonState] controls the visual state — IDLE, LISTENING, or PROCESSING.
 *
 * Handles RECORD_AUDIO runtime permission automatically.
 * Uses [SpeechRecognizer] with [RecognizerIntent.EXTRA_PREFER_OFFLINE] for
 * fully on-device recognition. The recognizer is destroyed when the composable
 * leaves composition.
 */
@Composable
fun VoiceButton(
    onResult: (String) -> Unit,
    onError: (String) -> Unit = {},
    buttonState: VoiceButtonState = VoiceButtonState.IDLE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var internalListening by remember { mutableStateOf(false) }
    val recognizerState = remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Expose internal listening to the outer state — PROCESSING always wins.
    val effectiveState = when {
        buttonState == VoiceButtonState.PROCESSING -> VoiceButtonState.PROCESSING
        internalListening -> VoiceButtonState.LISTENING
        else -> VoiceButtonState.IDLE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoice(context, recognizerState, onResult, onError) { internalListening = it }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizerState.value?.destroy()
            recognizerState.value = null
        }
    }

    val circleColor = when (effectiveState) {
        VoiceButtonState.PROCESSING -> PROCESSING
        VoiceButtonState.LISTENING -> LISTENING
        VoiceButtonState.IDLE -> IDLE
    }

    val desc = when (effectiveState) {
        VoiceButtonState.PROCESSING -> "Processing voice command"
        VoiceButtonState.LISTENING -> "Stop listening"
        VoiceButtonState.IDLE -> "Voice command"
    }

    IconButton(
        onClick = {
            if (effectiveState == VoiceButtonState.PROCESSING) return@IconButton

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@IconButton
            }

            if (internalListening) {
                recognizerState.value?.stopListening()
                recognizerState.value?.destroy()
                recognizerState.value = null
                internalListening = false
            } else {
                startVoice(context, recognizerState, onResult, onError) { internalListening = it }
            }
        },
        modifier = modifier
            .size(72.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = desc }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (effectiveState == VoiceButtonState.PROCESSING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ─── Internal ───────────────────────────────────────────────────────────────

private fun startVoice(
    context: android.content.Context,
    recognizerState: MutableState<SpeechRecognizer?>,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    setListening: (Boolean) -> Unit
) {
    val sr = SpeechRecognizer.createSpeechRecognizer(context)
    if (sr == null) {
        onError("Speech recognition not available on this device")
        return
    }
    recognizerState.value = sr

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    sr.setRecognitionListener(object : RecognitionListener {
        override fun onResults(bundle: Bundle) {
            val text = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text != null) onResult(text)
            sr.destroy()
            recognizerState.value = null
            setListening(false)
        }

        override fun onError(error: Int) {
            if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                onError("Voice error ($error)")
            }
            sr.destroy()
            recognizerState.value = null
            setListening(false)
        }

        override fun onReadyForSpeech(p0: Bundle) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(p0: Float) {}
        override fun onBufferReceived(p0: ByteArray) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p0: Bundle) {}
        override fun onEvent(p0: Int, p1: Bundle) {}
    })

    sr.startListening(intent)
    setListening(true)
}
