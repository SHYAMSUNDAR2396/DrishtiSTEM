package com.technoblaze.drishtistem.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.core.vision.cv.ExtractionState
import com.technoblaze.drishtistem.core.vision.cv.GraphExtractionPipeline
import com.technoblaze.drishtistem.core.vision.cv.ScannedGraphFactory
import com.technoblaze.drishtistem.data.ScannedConceptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Graph upload screen (Phase 1.7). Pick a photo of a printed line graph; the
 * deterministic CV pipeline ([GraphExtractionPipeline]) traces the curve into a
 * GraphConcept the existing explorer renders. A "Use demo graph" button is
 * always available as the demo safety net. Fully offline, no camera, no model.
 */
@Composable
fun GraphScanScreen(
    engines: Engines,
    onScanReady: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Choose a photo of a printed graph to read.") }
    var busy by remember { mutableStateOf(false) }

    fun openDemo() {
        ScannedConceptStore.current = ScannedGraphFactory.demoParabola()
        engines.haptics.pulse()
        engines.speech.announce("Demo graph loaded. A U shaped parabola.", interrupt = true)
        onScanReady()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true
        engines.speech.announce("Image chosen. Reading the graph.")
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
            if (bitmap == null) {
                engines.haptics.tick()
                status = "Could not open that image. Try another."
                engines.speech.announce(status, interrupt = true)
                busy = false
                return@launch
            }
            GraphExtractionPipeline.extract(bitmap).collectLatest { state ->
                when (state) {
                    is ExtractionState.Processing -> { status = state.stage }
                    is ExtractionState.Success -> {
                        ScannedConceptStore.current = state.concept
                        engines.haptics.pulse()
                        engines.speech.announce(
                            "Graph read. ${state.concept.spokenIntro}", interrupt = true
                        )
                        busy = false
                        onScanReady()
                    }
                    is ExtractionState.LowConfidence -> {
                        ScannedConceptStore.current = state.concept
                        engines.haptics.tick()
                        engines.speech.announce(
                            "Low confidence: ${state.reason}. Showing my best guess. " +
                                "${state.concept.spokenIntro}", interrupt = true
                        )
                        busy = false
                        onScanReady()
                    }
                    is ExtractionState.Failure -> {
                        engines.haptics.tick()
                        status = state.reason
                        engines.speech.announce(
                            "${state.reason} You can try another photo, or use the demo graph.",
                            interrupt = true
                        )
                        busy = false
                    }
                    ExtractionState.Idle -> {}
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        engines.speech.announce(
            "Upload a graph. Choose a photo of a printed line graph, and it will be traced into a " +
                "curve you can explore by touch. Or press use demo graph.",
            interrupt = true
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1B1E))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { engines.quietAll(); onBack() },
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Back to home" }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFE8C49A))
            }
            Text("Upload a graph", color = Color(0xFFE8C49A), fontSize = 18.sp)
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (busy) {
                    CircularProgressIndicator(color = Color(0xFFB5651D))
                    Text(
                        status,
                        color = Color(0xFFE8C49A),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                } else {
                    Text(status, color = Color(0xFF9E9E9E), fontSize = 16.sp)
                }
            }
        }

        Button(
            onClick = {
                if (busy) return@Button
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5651D)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(72.dp)
                .semantics { contentDescription = "Choose a graph photo to read" }
        ) {
            Text(if (busy) "Reading…" else "📈  Choose graph photo", fontSize = 20.sp, color = Color.White)
        }

        // Demo fallback — always available, never hidden.
        OutlinedButton(
            onClick = { if (!busy) openDemo() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(60.dp)
                .semantics { contentDescription = "Use demo graph instead" }
        ) {
            Text("Use demo graph", fontSize = 17.sp, color = Color(0xFFE8C49A))
        }
    }
}

/** Decode a picked image URI into a software ARGB bitmap. */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    null
}
