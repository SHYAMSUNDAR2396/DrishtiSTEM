package com.technoblaze.drishtistem.core.vision.cv

import android.graphics.Bitmap
import com.technoblaze.drishtistem.model.GraphConcept
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout

/** Progress + outcome of the deterministic graph-extraction pipeline. */
sealed interface ExtractionState {
    data object Idle : ExtractionState
    data class Processing(val stage: String, val progress: Float) : ExtractionState
    data class Success(val concept: GraphConcept, val confidence: Float) : ExtractionState
    data class LowConfidence(val concept: GraphConcept, val reason: String) : ExtractionState
    data class Failure(val reason: String) : ExtractionState
}

/**
 * Orchestrates the 5 CV stages into a single observable flow. Everything runs
 * on [Dispatchers.Default]; each stage has a hard timeout; any error becomes a
 * graceful [ExtractionState.Failure] (never a crash). The UI observes this and
 * can always fall back to the demo graph.
 */
object GraphExtractionPipeline {

    private const val MIN_INK_FRACTION = 0.0015f

    fun extract(bitmap: Bitmap): Flow<ExtractionState> = flow {
        emit(ExtractionState.Processing("Enhancing image…", 0.15f))
        val pre = withTimeout(6000) { ImagePreprocessor.process(bitmap) }

        // Almost no ink → there is no graph here (blank wall, etc.).
        val inkFraction = pre.dark.count { it }.toFloat() / pre.dark.size
        if (inkFraction < MIN_INK_FRACTION) {
            emit(ExtractionState.Failure("No graph found. Point at a printed graph and try again."))
            return@flow
        }

        emit(ExtractionState.Processing("Detecting curve…", 0.5f))
        val raw = withTimeout(4000) { CurveExtractor.extract(pre) }

        emit(ExtractionState.Processing("Normalising…", 0.75f))
        val processed = withTimeout(3000) { CurveNormaliser.normalise(raw) }

        emit(ExtractionState.Processing("Analysing…", 0.9f))
        val concept = ScannedGraphFactory.fromProcessed(processed)

        if (processed.confidence < 0.5f) {
            emit(ExtractionState.LowConfidence(concept, "Low contrast or unclear graph"))
        } else {
            emit(ExtractionState.Success(concept, processed.confidence))
        }
    }.catch { e ->
        emit(ExtractionState.Failure(e.message ?: "Could not read the image"))
    }.flowOn(Dispatchers.Default)
}
