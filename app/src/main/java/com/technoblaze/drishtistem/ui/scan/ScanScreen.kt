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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.core.vision.GemmaVision
import com.technoblaze.drishtistem.data.ScannedConceptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Upload screen: pick a photo of a molecular structure or line graph from the
 * device (via the system photo picker — no storage permission needed), and the
 * on-device Gemma 3n model ([GemmaVision]) turns it into an explorable molecule
 * or graph the existing explorers render. All processing is local.
 */
@Composable
fun ScanScreen(
    engines: Engines,
    onScanReady: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            engines.speech.announce("No image chosen.")
            return@rememberLauncherForActivityResult
        }
        if (working) return@rememberLauncherForActivityResult
        working = true
        val firstScan = !GemmaVision.engineReady
        engines.speech.announce(
            if (firstScan && GemmaVision.isModelPresent(context))
                "Image chosen. Loading the scanner model and reading the image. This can take a moment."
            else "Image chosen. Reading the image, please wait."
        )
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
            if (bitmap == null) {
                engines.haptics.tick()
                engines.speech.announce("I could not open that image. Try another one.", interrupt = true)
                working = false
                return@launch
            }
            preview = bitmap.asImageBitmap()
            when (val result = GemmaVision.analyze(context, bitmap)) {
                is GemmaVision.Result.MoleculeResult -> {
                    ScannedConceptStore.current = result.concept
                    engines.haptics.pulse()
                    engines.speech.announce(result.spoken, interrupt = true)
                    onScanReady()
                }
                is GemmaVision.Result.GraphResult -> {
                    ScannedConceptStore.current = result.concept
                    engines.haptics.pulse()
                    engines.speech.announce(result.spoken, interrupt = true)
                    onScanReady()
                }
                is GemmaVision.Result.Failure -> {
                    engines.haptics.tick()
                    engines.speech.announce(result.message, interrupt = true)
                }
                GemmaVision.Result.ModelMissing -> {
                    engines.haptics.tick()
                    engines.speech.announce(
                        "The scanner model is not installed on this device. " +
                            "Ask your helper to install it, then try again.",
                        interrupt = true
                    )
                }
            }
            working = false
        }
    }

    LaunchedEffect(Unit) {
        engines.speech.announce(
            "Upload a structure. Choose a photo of a molecular structure or a graph from your device, " +
                "and it will be read into something you can explore by touch. Press choose image.",
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
                modifier = Modifier
                    .size(56.dp)
                    .semantics { contentDescription = "Back to home" }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFE8C49A))
            }
            Text("Upload a structure", color = Color(0xFFE8C49A), fontSize = 18.sp)
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val img = preview
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = "The image you chose",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "Choose a photo of a molecule or graph to read.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 16.sp
                )
            }
        }

        Button(
            onClick = {
                if (working) return@Button
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5651D)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(76.dp)
                .semantics { contentDescription = "Choose image to read" }
        ) {
            Text(if (working) "Reading…" else "🖼  Choose image", fontSize = 20.sp, color = Color.White)
        }
    }
}

/** Decode a picked image URI into a software ARGB bitmap the model can read. */
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
