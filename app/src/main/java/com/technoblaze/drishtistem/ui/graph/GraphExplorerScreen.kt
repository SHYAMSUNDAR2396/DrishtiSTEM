package com.technoblaze.drishtistem.ui.graph

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.Landmark
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot

private val CurveColors = listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784))
private const val PI_HALF = (Math.PI / 2).toFloat()

/** Finger within this fraction of the y-range of the curve counts as "on the line". */
private const val ON_CURVE_TOL = 0.06f
/** Beyond this fraction away from the line, the guidance tone goes silent. */
private const val GUIDE_AUDIO_RANGE = 0.45f

/**
 * Full-screen tactile graph canvas. The phone vibrates continuously only while
 * the finger is actually on the line (slope drives the strength; tone pitch
 * follows y, stereo pan follows x). Off the line there is no constant buzz —
 * instead short pulses quicken as the finger nears the curve, guiding it back.
 * Landmarks (roots, peaks, intersections) fire pulses and spoken callouts.
 */
@Composable
fun GraphExplorerScreen(concept: GraphConcept, engines: Engines, onBack: () -> Unit) {
    var guidanceMode by remember { mutableStateOf(false) }
    var fingerScreen by remember { mutableStateOf<Offset?>(null) }
    var snappedScreen by remember { mutableStateOf<Offset?>(null) }
    var activeCurve by remember { mutableStateOf(0) }
    // Landmark id -> last spoken time, to debounce repeat callouts.
    val landmarkCooldown = remember { mutableMapOf<Int, Long>() }
    var guidanceArrived by remember { mutableStateOf(false) }
    // Last time an off-curve guidance pulse fired (uptimeMillis), in a holder so
    // the drag lambda can mutate it without triggering recomposition.
    val lastGuidePulse = remember { longArrayOf(0L) }

    LaunchedEffect(concept.id) {
        engines.speech.announce(
            "${concept.title}. ${concept.spokenIntro} ${concept.instructions}",
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
                    .semantics { contentDescription = "Back to concept list" }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color(0xFFE8C49A)
                )
            }
            Column {
                Text(
                    concept.title,
                    color = Color(0xFFE8C49A),
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                if (concept.guidanceTarget != null) {
                    Text(
                        if (guidanceMode) "Guidance mode ON — double tap to turn off"
                        else "Double tap for intersection guidance",
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription =
                        "Graph exploration area. ${concept.instructions}"
                }
                .pointerInput(concept.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (concept.guidanceTarget != null) {
                                guidanceMode = !guidanceMode
                                guidanceArrived = false
                                engines.speech.announce(
                                    if (guidanceMode)
                                        "Guidance on. Vibration grows stronger near the intersection."
                                    else "Guidance off."
                                )
                            } else {
                                engines.speech.announce(concept.spokenIntro, interrupt = true)
                            }
                        }
                    )
                }
                .pointerInput(concept.id, guidanceMode) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            fingerScreen = pos
                            engines.tone.play()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            fingerScreen = change.position
                            val mapper = GraphMapper(concept, size.width.toFloat(), size.height.toFloat())
                            val worldX = mapper.screenToWorldX(change.position.x)
                            val worldYFinger = mapper.screenToWorldY(change.position.y)

                            // Snap to the curve whose y is closest to the finger.
                            var best = 0
                            var bestDist = Float.MAX_VALUE
                            concept.curves.forEachIndexed { i, curve ->
                                val d = abs(curve.f(worldX) - worldYFinger)
                                if (d < bestDist) {
                                    bestDist = d; best = i
                                }
                            }
                            if (best != activeCurve) {
                                activeCurve = best
                                if (concept.curves.size > 1) {
                                    engines.speech.announce(
                                        "Now on ${concept.curves[best].label}",
                                        interrupt = true
                                    )
                                }
                            }
                            val curve = concept.curves[activeCurve]
                            val y = curve.f(worldX)
                            snappedScreen = Offset(
                                mapper.worldToScreenX(worldX),
                                mapper.worldToScreenY(y)
                            )

                            engines.tone.setPitchFromValue(y, concept.yMin, concept.yMax)
                            engines.tone.setPan(
                                (worldX - concept.xMin) / (concept.xMax - concept.xMin)
                            )

                            val target = concept.guidanceTarget
                            if (guidanceMode && target != null) {
                                // GPS-style homing: stronger vibration closer to the target.
                                val dist = hypot(
                                    (worldX - target.x) / (concept.xMax - concept.xMin),
                                    (y - target.y) / (concept.yMax - concept.yMin)
                                )
                                val proximity = (1f - dist / 0.5f).coerceIn(0f, 1f)
                                engines.haptics.feel(proximity * proximity)
                                if (dist < 0.04f && !guidanceArrived) {
                                    guidanceArrived = true
                                    engines.haptics.pulse()
                                    engines.speech.announce(target.announcement)
                                } else if (dist > 0.12f) {
                                    guidanceArrived = false
                                }
                            } else {
                                // How far the finger actually is from the curve at this x,
                                // as a fraction of the y-range. This is the key fix: only
                                // the line itself buzzes, not the whole canvas.
                                val dyNorm = abs(worldYFinger - y) / (concept.yMax - concept.yMin)

                                if (dyNorm <= ON_CURVE_TOL) {
                                    // ON the line: continuous slope-driven feel + tone + landmarks.
                                    val dx = (concept.xMax - concept.xMin) / 200f
                                    val slope = (curve.f(worldX + dx) - y) / dx
                                    val normalized = abs(
                                        atan(slope * (concept.xMax - concept.xMin) / (concept.yMax - concept.yMin))
                                    ) / PI_HALF
                                    engines.tone.play()
                                    engines.haptics.feel(0.25f + 0.75f * normalized)
                                    checkLandmarks(concept, worldX, y, landmarkCooldown, engines)
                                } else {
                                    // OFF the line: no continuous buzz. Guide the finger back
                                    // with short pulses that quicken as it nears the curve, and
                                    // an audio cue that fades in only when reasonably close.
                                    val proximity = (1f - dyNorm).coerceIn(0f, 1f)
                                    if (dyNorm > GUIDE_AUDIO_RANGE) engines.tone.mute() else engines.tone.play()
                                    val interval = (450L - 380L * proximity).toLong().coerceIn(70L, 450L)
                                    val now = SystemClock.uptimeMillis()
                                    if (now - lastGuidePulse[0] >= interval) {
                                        lastGuidePulse[0] = now
                                        engines.haptics.tick()
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            fingerScreen = null
                            snappedScreen = null
                            engines.tone.mute()
                            engines.haptics.stop()
                        },
                        onDragCancel = {
                            fingerScreen = null
                            snappedScreen = null
                            engines.tone.mute()
                            engines.haptics.stop()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val mapper = GraphMapper(concept, size.width, size.height)
                drawAxesAndGrid(mapper, concept)
                concept.curves.forEachIndexed { i, curve ->
                    val path = Path()
                    val steps = 300
                    for (s in 0..steps) {
                        val x = concept.xMin + (concept.xMax - concept.xMin) * s / steps
                        val sx = mapper.worldToScreenX(x)
                        val sy = mapper.worldToScreenY(curve.f(x).coerceIn(concept.yMin, concept.yMax))
                        if (s == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                    }
                    drawPath(
                        path,
                        color = CurveColors[i % CurveColors.size],
                        style = Stroke(width = if (i == activeCurve) 8f else 5f)
                    )
                }
                concept.guidanceTarget?.let { t ->
                    drawCircle(
                        Color(0xFFFFD54F),
                        radius = 18f,
                        center = Offset(mapper.worldToScreenX(t.x), mapper.worldToScreenY(t.y)),
                        style = Stroke(width = 4f)
                    )
                }
                snappedScreen?.let { drawCircle(Color.White, radius = 16f, center = it) }
                fingerScreen?.let {
                    drawCircle(Color(0x40FFFFFF), radius = 44f, center = it)
                }
            }
        }
    }
}

/** Announce + tick when the finger passes near a detected landmark. */
private fun checkLandmarks(
    concept: GraphConcept,
    worldX: Float,
    worldY: Float,
    cooldown: MutableMap<Int, Long>,
    engines: Engines
) {
    val now = System.currentTimeMillis()
    concept.landmarks.forEachIndexed { i, lm ->
        val dx = abs(worldX - lm.x) / (concept.xMax - concept.xMin)
        val dy = abs(worldY - lm.y) / (concept.yMax - concept.yMin)
        if (dx < 0.03f && dy < 0.06f && now - (cooldown[i] ?: 0) > 4000) {
            cooldown[i] = now
            if (lm.kind == Landmark.Kind.ROOT) engines.haptics.tick() else engines.haptics.pulse()
            engines.speech.announce(lm.announcement)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxesAndGrid(
    mapper: GraphMapper,
    concept: GraphConcept
) {
    val grid = Color(0xFF2E3136)
    val axis = Color(0xFF6B6F76)
    // Vertical grid lines at integer x, horizontal at integer y.
    var gx = kotlin.math.ceil(concept.xMin).toInt()
    while (gx <= concept.xMax) {
        val sx = mapper.worldToScreenX(gx.toFloat())
        drawLine(grid, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.5f)
        gx++
    }
    var gy = kotlin.math.ceil(concept.yMin).toInt()
    while (gy <= concept.yMax) {
        val sy = mapper.worldToScreenY(gy.toFloat())
        drawLine(grid, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1.5f)
        gy += if (concept.yMax - concept.yMin > 15f) 5 else 1
    }
    if (concept.yMin <= 0f && concept.yMax >= 0f) {
        val sy = mapper.worldToScreenY(0f)
        drawLine(axis, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 3f)
    }
    if (concept.xMin <= 0f && concept.xMax >= 0f) {
        val sx = mapper.worldToScreenX(0f)
        drawLine(axis, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 3f)
    }
}

/** World <-> screen coordinate transforms with uniform padding. */
private class GraphMapper(
    private val concept: GraphConcept,
    private val width: Float,
    private val height: Float,
    private val pad: Float = 40f
) {
    fun worldToScreenX(x: Float): Float =
        pad + (x - concept.xMin) / (concept.xMax - concept.xMin) * (width - 2 * pad)

    fun worldToScreenY(y: Float): Float =
        height - pad - (y - concept.yMin) / (concept.yMax - concept.yMin) * (height - 2 * pad)

    fun screenToWorldX(sx: Float): Float =
        (concept.xMin + (sx - pad) / (width - 2 * pad) * (concept.xMax - concept.xMin))
            .coerceIn(concept.xMin, concept.xMax)

    fun screenToWorldY(sy: Float): Float =
        (concept.yMin + (height - pad - sy) / (height - 2 * pad) * (concept.yMax - concept.yMin))
            .coerceIn(concept.yMin, concept.yMax)
}
