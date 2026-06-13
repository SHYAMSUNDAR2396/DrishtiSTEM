package com.technoblaze.drishtistem.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.core.vision.GraphVision
import com.technoblaze.drishtistem.data.ScannedGraphStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Camera screen for Phase 1.5: point at a printed line graph, capture, and the
 * on-device [GraphVision] pipeline turns it into a [GraphConcept] that the
 * existing explorer renders. All processing is local — no image leaves the phone.
 */
@Composable
fun CameraScreen(
    engines: Engines,
    onGraphReady: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            engines.speech.announce(
                "Camera permission is needed to scan a graph. " +
                    "You can grant it, or go back to the built-in concepts."
            )
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            engines.speech.announce(
                "Scan a graph. Point the camera at a printed line graph so it fills the screen, " +
                    "then press the large capture button at the bottom.",
                interrupt = true
            )
        } else {
            engines.speech.announce(
                "Scan a graph needs camera access. Press grant camera access to continue.",
                interrupt = true
            )
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
            Text("Scan a graph", color = Color(0xFFE8C49A), fontSize = 18.sp)
        }

        if (hasPermission) {
            CameraCapture(engines, onGraphReady)
        } else {
            PermissionPrompt { permissionLauncher.launch(Manifest.permission.CAMERA) }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "DrishtiSTEM needs the camera to scan printed graphs. " +
                "The image is processed on your phone and never uploaded.",
            color = Color(0xFFBDBDBD),
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5651D)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { contentDescription = "Grant camera access" }
        ) {
            Text("Grant camera access", fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
private fun CameraCapture(engines: Engines, onGraphReady: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val provider = suspendCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                if (working) return@Button
                working = true
                engines.speech.announce("Capturing. Hold steady.")
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toUprightBitmap()
                            image.close()
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    GraphVision.analyze(bitmap)
                                }
                                when (result) {
                                    is GraphVision.Result.Success -> {
                                        ScannedGraphStore.current = result.concept
                                        engines.haptics.pulse()
                                        engines.speech.announce(result.spokenSummary, interrupt = true)
                                        onGraphReady()
                                    }
                                    is GraphVision.Result.Failure -> {
                                        engines.haptics.tick()
                                        engines.speech.announce(result.message, interrupt = true)
                                    }
                                }
                                working = false
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            engines.speech.announce("Capture failed. Please try again.")
                            working = false
                        }
                    }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5651D)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .height(76.dp)
                .semantics { contentDescription = "Capture graph" }
        ) {
            Text(if (working) "Reading graph…" else "◉  Capture graph", fontSize = 20.sp, color = Color.White)
        }
    }
}

/** Decode the captured JPEG and rotate it upright per the sensor metadata. */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
