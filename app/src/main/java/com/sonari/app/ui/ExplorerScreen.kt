package com.sonari.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitPointerEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.audio.SweepPlayer
import com.sonari.app.engine.Cue
import com.sonari.app.engine.DefaultMappingEngine
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.Atom
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.MoleculeGraph
import com.sonari.app.model.Renderable
import kotlin.math.hypot

// ─── Colors ──────────────────────────────────────────────────────────────────

private val BG = Color(0xFF1A1B1E)
private val GRID = Color(0xFF2C2D31)
private val AXIS = Color(0xFF4A4B50)
private val CURVE_IDLE = Color(0xFFCCCCCC)
private val CURVE_ACTIVE = Color(0xFF4FC3F7)
private val FINGER_DOT = Color(0xFFFFEB3B)
private val PLAYHEAD = Color(0xFF4FC3F7)
private val TAB_ACTIVE = Color(0xFF4FC3F7)
private val TAB_INACTIVE = Color(0xFF3A3B3E)

// ─── Entry point ─────────────────────────────────────────────────────────────

private enum class ExploreMode { OVERVIEW, EXPLORE }

@Composable
fun ExplorerScreen(
    renderable: Renderable,
    sonifier: Sonifier,
    haptics: Haptics,
    announcer: Announcer,
    onNavigateHome: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sweepPlayer = remember { SweepPlayer(sonifier, haptics, announcer) }
    var mode by remember { mutableStateOf(ExploreMode.OVERVIEW) }

    // Shared clean-up: silence audio when leaving.
    DisposableEffect(Unit) {
        onDispose { sonifier.setCue(440.0, 0.0, active = false); haptics.cancel() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
    ) {
        AppHeader(onNavigateHome = onNavigateHome, onNavigateSettings = onNavigateSettings)
        ModeSelector(
            current = mode,
            onSelect = { m ->
                if (m != mode) {
                    sonifier.setCue(440.0, 0.0, false)
                    haptics.cancel()
                    mode = m
                }
            }
        )
        when (mode) {
            ExploreMode.OVERVIEW -> OverviewContent(renderable, sonifier, haptics, announcer, sweepPlayer)
            ExploreMode.EXPLORE -> ExploreContent(renderable, sonifier, haptics, announcer)
        }
    }
}

// ─── Header & mode selector ──────────────────────────────────────────────────

@Composable
private fun AppHeader(onNavigateHome: () -> Unit, onNavigateSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Sonari", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
        Button(
            onClick = onNavigateHome,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier.semantics { contentDescription = "Back to home" }
        ) { Text("Home", color = Color(0xFF888888), fontSize = 13.sp) }
        Button(
            onClick = onNavigateSettings,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier.semantics { contentDescription = "Settings" }
        ) { Text("Settings", color = Color(0xFF888888), fontSize = 13.sp) }
    }
}

@Composable
private fun ModeSelector(current: ExploreMode, onSelect: (ExploreMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExploreMode.entries.forEach { m ->
            val active = m == current
            Button(
                onClick = { onSelect(m) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) TAB_ACTIVE else TAB_INACTIVE
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "${m.name.lowercase()} mode button" }
            ) {
                Text(
                    text = m.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                    color = if (active) Color.Black else Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ─── Overview mode ───────────────────────────────────────────────────────────

@Composable
private fun OverviewContent(
    renderable: Renderable,
    sonifier: Sonifier,
    haptics: Haptics,
    announcer: Announcer,
    sweepPlayer: SweepPlayer
) {
    // Molecules don't have a natural sweep — show the graph and redirect to Explore.
    if (renderable is MoleculeGraph) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            OverviewCanvas(renderable, 0f, Modifier.fillMaxWidth().weight(1f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Switch to Explore mode to trace atoms and bonds",
                color = Color(0xFF888888),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        return
    }

    var isPlaying by remember { mutableStateOf(false) }
    var sweepProgress by remember { mutableFloatStateOf(0f) }

    // Drive the sweep from a coroutine; cancels automatically when isPlaying → false.
    LaunchedEffect(isPlaying, renderable) {
        if (!isPlaying) {
            sonifier.setCue(440.0, 0.0, false)
            sweepProgress = 0f
            return@LaunchedEffect
        }
        sweepPlayer.sweep(renderable, durationMs = 5_000L) { progress ->
            sweepProgress = progress
        }
        isPlaying = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OverviewCanvas(
            renderable = renderable,
            playhead = sweepProgress,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Play / Pause button — large touch target for blind users.
        Button(
            onClick = { isPlaying = !isPlaying },
            colors = ButtonDefaults.buttonColors(containerColor = TAB_ACTIVE),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(width = 180.dp, height = 64.dp)
                .semantics {
                    contentDescription = if (isPlaying) "Pause overview" else "Play overview"
                    role = Role.Button
                }
        ) {
            Text(
                text = if (isPlaying) "Pause" else "Play overview",
                color = Color.Black,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isPlaying) "Sweep in progress — landmark pulses mark key points"
                   else "Tap Play to hear the full shape left to right",
            color = Color(0xFF888888),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun OverviewCanvas(renderable: Renderable, playhead: Float, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(
        modifier = modifier.semantics {
            contentDescription = when (renderable) {
                is MoleculeGraph -> "Molecule graph. Use Explore mode to trace atoms and bonds."
                else -> "Function graph visual. Press Play to hear it."
            }
        }
    ) {
        drawBackground()
        when (renderable) {
            is MoleculeGraph -> drawMolecule(renderable, null, tm)
            else -> {
                drawGrid(renderable)
                drawCurve(renderable, onFeature = false)
                if (playhead > 0f) drawPlayhead(playhead)
                drawLandmarkMarkers(renderable)
            }
        }
    }
}

private fun DrawScope.drawPlayhead(normX: Float) {
    val px = normX * size.width
    drawLine(PLAYHEAD, Offset(px, 0f), Offset(px, size.height), strokeWidth = 3f)
    // Small triangle indicator at top.
    drawCircle(PLAYHEAD, radius = 8f, center = Offset(px, 10f))
}

private fun DrawScope.drawLandmarkMarkers(renderable: Renderable) {
    val xRange = renderable.xMax - renderable.xMin
    val yRange = renderable.yMax - renderable.yMin
    for (lm in renderable.landmarks) {
        val px = (lm.normX * size.width).toFloat()
        val py = ((1.0 - lm.normY) * size.height).toFloat()
        drawCircle(Color(0xFFFFEB3B), radius = 7f, center = Offset(px, py))
    }
}

// ─── Explore mode ────────────────────────────────────────────────────────────

@Composable
private fun ExploreContent(
    renderable: Renderable,
    sonifier: Sonifier,
    haptics: Haptics,
    announcer: Announcer
) {
    var normPos by remember { mutableStateOf<Offset?>(null) }
    var isTouching by remember { mutableStateOf(false) }
    var lastLandmark by remember { mutableStateOf<Landmark?>(null) }
    var lastContactMs by remember { mutableLongStateOf(0L) }

    val cue: Cue? = remember(normPos, renderable) {
        normPos?.let { DefaultMappingEngine.cueAt(it.x.toDouble(), it.y.toDouble(), renderable) }
    }

    LaunchedEffect(cue, isTouching) {
        if (isTouching && cue != null) sonifier.setCue(cue.freqHz, cue.pan, true)
        else sonifier.setCue(440.0, 0.0, false)
    }

    LaunchedEffect(cue?.landmark) {
        val lm = cue?.landmark
        if (lm != null && lm != lastLandmark) { haptics.landmark(); announcer.landmark(lm) }
        lastLandmark = lm
    }

    LaunchedEffect(isTouching) {
        while (isTouching) {
            kotlinx.coroutines.delay(300)
            val now = System.currentTimeMillis()
            if (cue?.onFeature == true && now - lastContactMs >= 280) {
                haptics.contact()
                lastContactMs = now
            }
        }
        haptics.cancel()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ExploreStatusBar(cue, isTouching, renderable)

        ExploreCanvas(
            renderable = renderable,
            cue = cue,
            normPos = normPos,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onTouchStart = { isTouching = true; normPos = it },
            onTouchMove = { normPos = it },
            onTouchEnd = { isTouching = false; normPos = null },
            onTwoFingerTap = { nx, ny ->
                val nearest = renderable.landmarks.minByOrNull { lm ->
                    Math.hypot(nx - lm.normX, ny - lm.normY)
                }
                if (nearest != null) announcer.landmark(nearest)
                else announcer.announce("no landmarks")
            },
            onDoubleTap = { nx, ny ->
                announcer.coordinates(nx.toDouble(), ny.toDouble(), renderable)
            }
        )

        Text(
            text = "Drag · Two-finger tap = nearest landmark · Double-tap = coordinates",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ExploreStatusBar(cue: Cue?, isTouching: Boolean, renderable: Renderable? = null) {
    val isMolecule = renderable is MoleculeGraph
    val text = when {
        !isTouching -> if (isMolecule) "Drag to trace atoms and bonds" else "Lift off"
        cue?.onFeature == true && isMolecule -> "On atom/bond · %.0f Hz".format(cue.freqHz)
        cue?.onFeature == true -> "On curve · %.0f Hz".format(cue.freqHz)
        cue != null -> "Off · %.0f Hz".format(cue.freqHz)
        else -> ""
    }
    Text(
        text = text,
        color = if (cue?.onFeature == true) CURVE_ACTIVE else Color(0xFF888888),
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ExploreCanvas(
    renderable: Renderable,
    cue: Cue?,
    normPos: Offset?,
    modifier: Modifier,
    onTouchStart: (Offset) -> Unit,
    onTouchMove: (Offset) -> Unit,
    onTouchEnd: () -> Unit,
    onTwoFingerTap: (normX: Float, normY: Float) -> Unit,
    onDoubleTap: (normX: Float, normY: Float) -> Unit
) {
    val tm = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription =
                    "Interactive function canvas. Drag to hear the shape. " +
                    "Two-finger tap speaks coordinates. Double-tap speaks nearest landmark."
            }
            .pointerInput(renderable) {
                // lastTapMs/lastTapNorm persist across gestures (vars outside awaitEachGesture).
                var lastTapMs = 0L
                var lastTapNorm = Offset.Zero

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downMs = System.currentTimeMillis()
                    val downNorm = Offset(
                        (down.position.x / size.width).coerceIn(0f, 1f),
                        (down.position.y / size.height).coerceIn(0f, 1f)
                    )
                    val isDoubleTap = downMs - lastTapMs < 300 &&
                        (downNorm - lastTapNorm).getDistance() < 0.15f

                    down.consume()
                    onTouchStart(downNorm)

                    var twoFingers = false
                    var dragged = false

                    while (true) {
                        val event = awaitPointerEvent()

                        // Detect second finger joining.
                        if (event.changes.count { it.pressed } >= 2) twoFingers = true

                        val primary = event.changes.firstOrNull { it.pressed }
                        if (primary == null) {
                            // All fingers lifted — classify the gesture.
                            val upMs = System.currentTimeMillis()
                            val isTap = upMs - downMs < 250 && !dragged
                            when {
                                twoFingers && isTap ->
                                    onTwoFingerTap(downNorm.x, downNorm.y)
                                !twoFingers && isTap && isDoubleTap ->
                                    onDoubleTap(downNorm.x, downNorm.y)
                                !twoFingers && isTap -> {
                                    lastTapMs = upMs
                                    lastTapNorm = downNorm
                                }
                            }
                            onTouchEnd()
                            break
                        }

                        primary.consume()
                        val norm = Offset(
                            (primary.position.x / size.width).coerceIn(0f, 1f),
                            (primary.position.y / size.height).coerceIn(0f, 1f)
                        )
                        if ((norm - downNorm).getDistance() > 0.02f) dragged = true
                        onTouchMove(norm)
                    }
                }
            }
    ) {
        drawBackground()
        when (renderable) {
            is MoleculeGraph -> {
                drawMolecule(renderable, normPos, tm)
                normPos?.let { drawFinger(it, cue) }
            }
            else -> {
                drawGrid(renderable)
                drawCurve(renderable, onFeature = cue?.onFeature == true)
                normPos?.let { drawFinger(it, cue) }
            }
        }
    }
}

// ─── Shared canvas drawing ───────────────────────────────────────────────────

private fun DrawScope.drawBackground() = drawRect(BG)

private fun DrawScope.drawGrid(r: Renderable) {
    val xRange = r.xMax - r.xMin
    val yRange = r.yMax - r.yMin

    var gx = Math.ceil(r.xMin / niceStep(xRange / 6)) * niceStep(xRange / 6)
    val xStep = niceStep(xRange / 6)
    while (gx <= r.xMax) {
        val px = ((gx - r.xMin) / xRange * size.width).toFloat()
        drawLine(
            color = if (Math.abs(gx) < xStep * 0.01) AXIS else GRID,
            start = Offset(px, 0f), end = Offset(px, size.height),
            strokeWidth = if (Math.abs(gx) < xStep * 0.01) 2f else 1f
        )
        gx += xStep
    }

    val yStep = niceStep(yRange / 6)
    var gy = Math.ceil(r.yMin / yStep) * yStep
    while (gy <= r.yMax) {
        val py = ((1.0 - (gy - r.yMin) / yRange) * size.height).toFloat()
        drawLine(
            color = if (Math.abs(gy) < yStep * 0.01) AXIS else GRID,
            start = Offset(0f, py), end = Offset(size.width, py),
            strokeWidth = if (Math.abs(gy) < yStep * 0.01) 2f else 1f
        )
        gy += yStep
    }
}

private fun DrawScope.drawCurve(r: Renderable, onFeature: Boolean) {
    if (r !is LineChart || r.samples.isEmpty()) return
    val xRange = r.xMax - r.xMin
    val yRange = r.yMax - r.yMin
    val path = Path()
    var first = true
    for (pt in r.samples) {
        val px = ((pt.x - r.xMin) / xRange * size.width).toFloat()
        val normY = (pt.y - r.yMin) / yRange
        if (normY < -0.15 || normY > 1.15) { first = true; continue }
        val py = ((1.0 - normY) * size.height).toFloat()
        if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
    }
    drawPath(
        path,
        color = if (onFeature) CURVE_ACTIVE else CURVE_IDLE,
        style = Stroke(width = if (onFeature) 5f else 3f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawFinger(normPos: Offset, cue: Cue?) {
    val cx = normPos.x * size.width
    val cy = normPos.y * size.height
    drawLine(FINGER_DOT.copy(alpha = 0.25f), Offset(cx, 0f), Offset(cx, size.height), 1f)
    if (cue?.onFeature == true) {
        drawCircle(CURVE_ACTIVE.copy(alpha = 0.2f), 36f, Offset(cx, cy))
    }
    drawCircle(FINGER_DOT, 14f, Offset(cx, cy))
}

private fun DrawScope.drawMolecule(
    r: MoleculeGraph,
    fingerNorm: Offset?,
    tm: androidx.compose.ui.text.TextMeasurer
) {
    // Bonds drawn first (underneath atoms).
    for (bond in r.bonds) {
        if (bond.fromIndex >= r.atoms.size || bond.toIndex >= r.atoms.size) continue
        val a = r.atoms[bond.fromIndex]; val b = r.atoms[bond.toIndex]
        val ax = a.normX.toFloat() * size.width; val ay = a.normY.toFloat() * size.height
        val bx = b.normX.toFloat() * size.width; val by = b.normY.toFloat() * size.height
        drawLine(CURVE_IDLE, Offset(ax, ay), Offset(bx, by), strokeWidth = 4f, cap = StrokeCap.Round)
        if (bond.order >= 2) {
            val dx = (by - ay); val dy = -(bx - ax)
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val offX = dx / len * 6f; val offY = dy / len * 6f
            drawLine(CURVE_IDLE, Offset(ax + offX, ay + offY), Offset(bx + offX, by + offY), 4f)
        }
    }
    // Atoms with element labels.
    for (atom in r.atoms) {
        val px = atom.normX.toFloat() * size.width
        val py = atom.normY.toFloat() * size.height
        val nearFinger = fingerNorm != null &&
            hypot((px / size.width - fingerNorm.x), (py / size.height - fingerNorm.y)) < 0.08f
        val bg = if (nearFinger) CURVE_ACTIVE.copy(alpha = 0.3f) else Color(0xFF2A2B2E)
        drawCircle(bg, radius = 22f, center = Offset(px, py))
        drawCircle(if (nearFinger) CURVE_ACTIVE else AXIS, radius = 22f, center = Offset(px, py),
            style = Stroke(2f))
        val measured = tm.measure(
            atom.element,
            TextStyle(color = if (nearFinger) CURVE_ACTIVE else Color.White, fontSize = 12.sp)
        )
        drawText(measured, topLeft = Offset(px - measured.size.width / 2f, py - measured.size.height / 2f))
    }
}

private fun niceStep(rough: Double): Double {
    if (rough <= 0) return 1.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(rough)))
    return when {
        rough / mag < 2 -> mag
        rough / mag < 5 -> 2 * mag
        else -> 5 * mag
    }
}
