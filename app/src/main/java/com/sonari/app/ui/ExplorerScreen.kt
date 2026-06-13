package com.sonari.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitPointerEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.engine.Cue
import com.sonari.app.engine.DefaultMappingEngine
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.Renderable
import kotlinx.coroutines.delay

private val BG = Color(0xFF1A1B1E)
private val GRID = Color(0xFF2C2D31)
private val AXIS = Color(0xFF4A4B50)
private val CURVE_OFF = Color(0xFFCCCCCC)
private val CURVE_ON = Color(0xFF4FC3F7)
private val FINGER_DOT = Color(0xFFFFEB3B)
private val FEATURE_RING = Color(0xFF4FC3F7)

@Composable
fun ExplorerScreen(
    renderable: Renderable,
    sonifier: Sonifier,
    haptics: Haptics,
    announcer: Announcer,
    modifier: Modifier = Modifier
) {
    var normPos by remember { mutableStateOf<Offset?>(null) }
    var isTouching by remember { mutableStateOf(false) }
    var lastLandmark by remember { mutableStateOf<Landmark?>(null) }
    var lastContactMs by remember { mutableLongStateOf(0L) }

    val cue: Cue? = remember(normPos, renderable) {
        normPos?.let { DefaultMappingEngine.cueAt(it.x.toDouble(), it.y.toDouble(), renderable) }
    }

    // Drive audio from cue changes.
    LaunchedEffect(cue, isTouching) {
        if (isTouching && cue != null) {
            sonifier.setCue(cue.freqHz, cue.pan, active = true)
        } else {
            sonifier.setCue(440.0, 0.0, active = false)
        }
    }

    // Fire landmark haptic + TTS once on entry; reset on exit.
    LaunchedEffect(cue?.landmark) {
        val lm = cue?.landmark
        if (lm != null && lm != lastLandmark) {
            haptics.landmark()
            announcer.landmark(lm)
        }
        lastLandmark = lm
    }

    // Contact haptic loop while touching.
    LaunchedEffect(isTouching) {
        while (isTouching) {
            delay(300)
            val now = System.currentTimeMillis()
            if (cue?.onFeature == true && now - lastContactMs >= 280) {
                haptics.contact()
                lastContactMs = now
            }
        }
        haptics.cancel()
    }

    // Release audio silence when composable leaves.
    DisposableEffect(Unit) {
        onDispose {
            sonifier.setCue(440.0, 0.0, active = false)
            haptics.cancel()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
            .padding(top = 12.dp)
    ) {
        Header(cue = cue, isTouching = isTouching)

        ExploreCanvas(
            renderable = renderable,
            cue = cue,
            normPos = normPos,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onTouchStart = {
                isTouching = true
                normPos = it
            },
            onTouchMove = { normPos = it },
            onTouchEnd = {
                isTouching = false
                normPos = null
            }
        )

        TwoFingerHint(
            onTwoFingerTap = { nx, ny ->
                announcer.coordinates(nx.toDouble(), ny.toDouble(), renderable)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Header(cue: Cue?, isTouching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text("Sonari", color = Color.White, fontSize = 20.sp)
        val status = when {
            !isTouching -> "Drag to explore"
            cue?.onFeature == true -> "On curve — %.0f Hz".format(cue.freqHz)
            cue != null -> "Off curve — %.0f Hz".format(cue.freqHz)
            else -> ""
        }
        Text(status, color = Color(0xFFAAAAAA), fontSize = 13.sp)
    }
}

@Composable
private fun ExploreCanvas(
    renderable: Renderable,
    cue: Cue?,
    normPos: Offset?,
    modifier: Modifier,
    onTouchStart: (Offset) -> Unit,
    onTouchMove: (Offset) -> Unit,
    onTouchEnd: () -> Unit
) {
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription =
                    "Interactive function canvas. Drag a finger to hear the shape. " +
                    "A buzz confirms you are on the curve. Two-finger tap speaks coordinates."
            }
            .pointerInput(renderable) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startNorm = Offset(
                        (down.position.x / size.width).toFloat().coerceIn(0f, 1f),
                        (down.position.y / size.height).toFloat().coerceIn(0f, 1f)
                    )
                    onTouchStart(startNorm)

                    while (true) {
                        val event = awaitPointerEvent()
                        val primary = event.changes.firstOrNull { it.pressed } ?: break
                        primary.consume()
                        val norm = Offset(
                            (primary.position.x / size.width).toFloat().coerceIn(0f, 1f),
                            (primary.position.y / size.height).toFloat().coerceIn(0f, 1f)
                        )
                        onTouchMove(norm)
                    }
                    onTouchEnd()
                }
            }
    ) {
        drawBackground()
        drawGrid(renderable)
        drawCurve(renderable, cue)
        normPos?.let { drawFinger(it, cue) }
    }
}

private fun DrawScope.drawBackground() {
    drawRect(BG)
}

private fun DrawScope.drawGrid(r: Renderable) {
    val xRange = r.xMax - r.xMin
    val yRange = r.yMax - r.yMin

    // Vertical grid lines.
    val xStep = niceStep(xRange / 6)
    var gx = Math.ceil(r.xMin / xStep) * xStep
    while (gx <= r.xMax) {
        val px = ((gx - r.xMin) / xRange * size.width).toFloat()
        val isAxis = Math.abs(gx) < xStep * 0.01
        drawLine(
            color = if (isAxis) AXIS else GRID,
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = if (isAxis) 2f else 1f
        )
        gx += xStep
    }

    // Horizontal grid lines.
    val yStep = niceStep(yRange / 6)
    var gy = Math.ceil(r.yMin / yStep) * yStep
    while (gy <= r.yMax) {
        val py = ((1.0 - (gy - r.yMin) / yRange) * size.height).toFloat()
        val isAxis = Math.abs(gy) < yStep * 0.01
        drawLine(
            color = if (isAxis) AXIS else GRID,
            start = Offset(0f, py),
            end = Offset(size.width, py),
            strokeWidth = if (isAxis) 2f else 1f
        )
        gy += yStep
    }
}

private fun DrawScope.drawCurve(r: Renderable, cue: Cue?) {
    if (r !is LineChart || r.samples.isEmpty()) return
    val xRange = r.xMax - r.xMin
    val yRange = r.yMax - r.yMin

    val path = Path()
    var first = true
    for (pt in r.samples) {
        val px = ((pt.x - r.xMin) / xRange * size.width).toFloat()
        val normY = (pt.y - r.yMin) / yRange
        if (normY < -0.2 || normY > 1.2) { first = true; continue }
        val py = ((1.0 - normY) * size.height).toFloat()
        if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
    }
    val onFeature = cue?.onFeature == true
    drawPath(
        path,
        color = if (onFeature) CURVE_ON else CURVE_OFF,
        style = Stroke(width = if (onFeature) 5f else 3f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawFinger(normPos: Offset, cue: Cue?) {
    val cx = normPos.x * size.width
    val cy = normPos.y * size.height
    val onFeature = cue?.onFeature == true

    // Vertical guide line at finger x.
    drawLine(
        color = FINGER_DOT.copy(alpha = 0.3f),
        start = Offset(cx, 0f),
        end = Offset(cx, size.height),
        strokeWidth = 1f
    )

    if (onFeature) {
        drawCircle(FEATURE_RING.copy(alpha = 0.25f), radius = 32f, center = Offset(cx, cy))
    }
    drawCircle(FINGER_DOT, radius = 14f, center = Offset(cx, cy))
}

@Composable
private fun TwoFingerHint(onTwoFingerTap: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row {
            Text(
                text = "Two-finger tap = speak coordinates",
                color = Color(0xFF666666),
                fontSize = 11.sp
            )
        }
    }
}

private fun niceStep(rough: Double): Double {
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rough)))
    return when {
        rough / magnitude < 2 -> magnitude
        rough / magnitude < 5 -> 2 * magnitude
        else -> 5 * magnitude
    }
}
