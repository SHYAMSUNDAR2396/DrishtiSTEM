package com.technoblaze.drishtistem.core.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.MoleculeConcept
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device multimodal scanner backed by Gemma 3n E2B (int4 .litertlm) via the
 * MediaPipe LLM Inference API. An uploaded photo plus a strict-JSON prompt yields
 * either a molecular structure or a line graph, which [GemmaMoleculeMapper]
 * converts into the app's existing explorable concepts.
 *
 * The ~3.4 GB model is side-loaded to the app's external files dir (see README),
 * never bundled and never downloaded — so the offline guarantee is preserved.
 */
object GemmaVision {

    const val MODEL_FILENAME = "gemma-3n-E2B-it-int4.litertlm"

    private const val PROMPT = """You help blind students explore science. Look at the image.
If it is a molecular structure, reply with ONLY this JSON:
{"type":"molecule","name":"<name>","atoms":[{"element":"O"},{"element":"H"}],"bonds":[{"from":0,"to":1,"order":1}]}
List every atom; "element" is the chemical symbol. Index atoms from 0 in listing order. "order" is the bond order 1, 2 or 3.
If it is a line graph or function plot, reply with ONLY this JSON:
{"type":"graph","points":[<15 to 25 numbers between 0 and 10 sampling the curve height from left to right>]}
Reply with JSON only. No prose, no markdown fences."""

    private const val MAX_IMAGE_DIM = 512

    sealed interface Result {
        data class MoleculeResult(val concept: MoleculeConcept, val spoken: String) : Result
        data class GraphResult(val concept: GraphConcept, val spoken: String) : Result
        data class Failure(val message: String) : Result
        data object ModelMissing : Result
    }

    fun modelFile(context: Context): File =
        File(context.getExternalFilesDir(null), "llm/$MODEL_FILENAME")

    fun isModelPresent(context: Context): Boolean = modelFile(context).exists()

    // Loading the 3.4 GB model is expensive; build the engine once and reuse it.
    @Volatile
    private var engine: LlmInference? = null

    /** True once the model is loaded, so the UI can warn about first-scan latency. */
    val engineReady: Boolean get() = engine != null

    private fun engine(context: Context): LlmInference {
        return engine ?: synchronized(this) {
            engine ?: run {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile(context).absolutePath)
                    .setMaxTokens(1024)
                    .setMaxNumImages(1)
                    .build()
                LlmInference.createFromOptions(context, options).also { engine = it }
            }
        }
    }

    suspend fun analyze(context: Context, bitmap: Bitmap): Result = withContext(Dispatchers.Default) {
        if (!isModelPresent(context)) return@withContext Result.ModelMissing
        try {
            val raw = runInference(context, downscale(bitmap))
            parse(raw)
        } catch (e: Exception) {
            Result.Failure(
                "The scanner had trouble reading that. Hold steady, fill the frame, and try again."
            )
        }
    }

    private fun runInference(context: Context, bitmap: Bitmap): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(10)
            .setTemperature(0.2f)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        return LlmInferenceSession.createFromOptions(engine(context), sessionOptions).use { session ->
            session.addQueryChunk(PROMPT)
            session.addImage(BitmapImageBuilder(bitmap).build())
            session.generateResponse()
        }
    }

    private fun parse(raw: String): Result {
        val json = GemmaMoleculeMapper.extractJson(raw)
            ?: return Result.Failure(retryMessage())
        return try {
            when (json.optString("type")) {
                "molecule" -> {
                    val concept = GemmaMoleculeMapper.moleculeFromJson(json)
                    Result.MoleculeResult(concept, concept.spokenIntro)
                }
                "graph" -> {
                    val concept = GemmaMoleculeMapper.graphFromJson(json)
                    Result.GraphResult(concept, "Graph captured. Trace it with your finger to explore.")
                }
                else -> Result.Failure(retryMessage())
            }
        } catch (e: Exception) {
            Result.Failure(retryMessage())
        }
    }

    private fun retryMessage() =
        "I could not make sense of that image. Point at a clear molecular structure or graph and try again."

    private fun downscale(src: Bitmap): Bitmap {
        val max = maxOf(src.width, src.height)
        if (max <= MAX_IMAGE_DIM) return src
        val scale = MAX_IMAGE_DIM.toFloat() / max
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
