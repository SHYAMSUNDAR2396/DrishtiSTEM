package com.sonari.app.voice

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * On-device voice command parser powered by Gemma 3n E2B via MediaPipe.
 *
 * Loads the model lazily on the first [parse] call. Subsequent calls reuse the
 * loaded session. Call [close] when the app's LLM inference is no longer needed
 * (e.g. in [android.app.Activity.onDestroy]).
 *
 * The model file must be at the path returned by [modelFile] — by default
 * `{externalFilesDir}/llm/gemma-3n-E2B-it-int4.litertlm`.
 */
class GemmaVoiceBrain(private val context: Context) {

    private var inference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var loaded = false

    /** Absolute path to the Gemma 3n model file in the app's external files dir. */
    private val modelFile: File
        get() = File(context.getExternalFilesDir("llm"), "gemma-3n-E2B-it-int4.litertlm")

    /**
     * Returns `true` if the model file exists on disk (not necessarily loaded into memory yet).
     * Call [ensureLoaded] before [parse] to load it.
     */
    fun modelExists(): Boolean = modelFile.exists()

    /**
     * Loads the model into memory. Call this once before [parse] to share the
     * loading delay across all subsequent calls. Idempotent — safe to call multiple times.
     */
    suspend fun ensureLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (loaded) return@withContext true
        try {
            Log.i(TAG, "Loading Gemma 3n model from ${modelFile.absolutePath}")
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()
            inference = LlmInference.createFromOptions(context, opts)

            val sessionOpts = LlmInferenceSessionOptions.builder()
                .setTemperature(0.0f)       // deterministic — no creativity
                .setTopK(1)                  // greedy decoding
                .setTopP(1.0f)
                .build()
            session = LlmInferenceSession.createFromOptions(inference!!, sessionOpts)
            loaded = true
            Log.i(TAG, "Gemma 3n model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Gemma model", e)
            false
        }
    }

    /**
     * Parses a recognized speech [userInput] into a [VoiceCommand] using the
     * Gemma 3n LLM. Returns `null` when parsing fails or the model isn't loaded.
     *
     * This is a heavy call — runs on [Dispatchers.IO] and takes 1-5 seconds.
     */
    suspend fun parse(userInput: String): VoiceCommand? = withContext(Dispatchers.IO) {
        if (!loaded) return@withContext null
        val s = session ?: return@withContext null

        try {
            val prompt = buildPrompt(userInput)
            s.addQueryChunk(prompt)
            val raw = s.generateResponse()
            Log.d(TAG, "Gemma response: $raw")
            parseJsonResponse(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            null
        }
    }

    /** Release model resources. Call from Activity.onDestroy. */
    fun close() {
        try {
            session?.close()
        } catch (_: Exception) {}
        try {
            inference?.close()
        } catch (_: Exception) {}
        session = null
        inference = null
        loaded = false
    }

    // ── Prompt building ───────────────────────────────────────────────────

    private fun buildPrompt(input: String): String = """
        You are a command parser for a STEM education accessibility app called Sonari.
        Parse the user's spoken request into a structured command.
        Respond ONLY with a JSON object. No preamble, no explanation.

        Actions:
        LOAD_MOLECULE  — view/explore a molecule         → {"action":"LOAD_MOLECULE","params":{"name":"..."}}
        LOAD_EQUATION  — plot a maths equation            → {"action":"LOAD_EQUATION","params":{"expr":"..."}}
        NAVIGATE_HOME  — go to the home/main screen       → {"action":"NAVIGATE_HOME","params":{}}
        NAVIGATE_BACK  — go back to previous screen       → {"action":"NAVIGATE_BACK","params":{}}
        OPEN_TUTORIAL  — show help, tutorial, how-to      → {"action":"OPEN_TUTORIAL","params":{}}
        OPEN_SETTINGS  — open settings or preferences     → {"action":"OPEN_SETTINGS","params":{}}

        Examples:
        - "show me water"                → {"action":"LOAD_MOLECULE","params":{"name":"water"}}
        - "I want to see methane"        → {"action":"LOAD_MOLECULE","params":{"name":"methane"}}
        - "plot x squared plus 3"        → {"action":"LOAD_EQUATION","params":{"expr":"x^2+3"}}
        - "graph sin(x)"                 → {"action":"LOAD_EQUATION","params":{"expr":"sin(x)"}}
        - "go back"                      → {"action":"NAVIGATE_BACK","params":{}}
        - "home screen"                  → {"action":"NAVIGATE_HOME","params":{}}
        - "help"                         → {"action":"OPEN_TUTORIAL","params":{}}
        - "settings"                     → {"action":"OPEN_SETTINGS","params":{}}

        User said: "$input"
    """.trimIndent()

    // ── Response parsing ──────────────────────────────────────────────────

    private fun parseJsonResponse(raw: String): VoiceCommand? {
        val json = extractJson(raw) ?: return null
        return try {
            val obj = JSONObject(json)
            val action = obj.getString("action")
            val params = obj.optJSONObject("params") ?: JSONObject()
            when (action) {
                "LOAD_MOLECULE" -> {
                    val name = params.optString("name", "").trim()
                    if (name.isBlank()) null else VoiceCommand.LoadMolecule(name)
                }
                "LOAD_EQUATION" -> {
                    val expr = params.optString("expr", "").trim()
                    if (expr.isBlank()) null else VoiceCommand.LoadEquation(expr)
                }
                "NAVIGATE_HOME" -> VoiceCommand.NavigateHome
                "NAVIGATE_BACK" -> VoiceCommand.NavigateBack
                "OPEN_TUTORIAL" -> VoiceCommand.OpenTutorial
                "OPEN_SETTINGS" -> VoiceCommand.OpenSettings
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemma JSON: $json", e)
            null
        }
    }

    companion object {
        private const val TAG = "GemmaVoiceBrain"
    }
}

/**
 * Extracts the first JSON object `{...}` from a string, handling
 * markdown fences, leading/trailing prose, and whitespace.
 */
internal fun extractJson(raw: String): String? {
    val start = raw.indexOf('{')
    if (start < 0) return null
    val end = raw.lastIndexOf('}')
    if (end < 0 || end <= start) return null
    return raw.substring(start, end + 1).trim()
}
