package com.sonari.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
import com.sonari.app.model.atomDisplayRadius
import kotlin.math.hypot

// ─── Colors ──────────────────────────────────────────────────────────────────

private val BG = Color(0xFF1A1B1E)
private val GRID = Color(0xFF2C2D31)
private val AXIS = Color(0xFF4A4B50)
private val CURVE_IDLE = Color(0xFFCCCCCC)
private val CURVE_ACTIVE = Color(0xFF4FC3F7)
private val BOND_COLOR = Color(0xFFB8BCC4)
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
        LaunchedEffect(renderable) { announcer.molecule(renderable) }
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            OverviewCanvas(
                renderable, 0f,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(renderable) {
                        detectTapGestures { announcer.molecule(renderable) }
                    }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Tap the molecule to hear its description · Switch to Explore mode to trace atoms and bonds",
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
    val px = cl() + normX * cw()
    val t = ct(); val b = size.height - cb()
    drawLine(PLAYHEAD, Offset(px, t), Offset(px, b), strokeWidth = 3f)
    drawCircle(PLAYHEAD, radius = 8f, center = Offset(px, t + 10f))
}

private fun DrawScope.drawLandmarkMarkers(renderable: Renderable) {
    for (lm in renderable.landmarks) {
        val px = (cl() + lm.normX * cw()).toFloat()
        val py = (ct() + (1.0 - lm.normY) * ch()).toFloat()
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
    var lastTouchedAtom by remember { mutableStateOf<String?>(null) }
    var lastTouchedBond by remember { mutableStateOf<Int?>(null) }
    val cue: Cue? = remember(normPos, renderable) {
        normPos?.let { DefaultMappingEngine.cueAt(it.x.toDouble(), it.y.toDouble(), renderable) }
    }

    LaunchedEffect(cue, isTouching) {
        if (isTouching && cue?.onFeature == true) sonifier.setCue(cue.freqHz, cue.pan, true)
        else sonifier.setCue(440.0, 0.0, false)
    }

    if (renderable !is MoleculeGraph) {
        LaunchedEffect(cue?.landmark, cue?.onFeature) {
            val lm = cue?.landmark
            if (lm != null && lm != lastLandmark && cue?.onFeature == true) {
                haptics.landmark()
                haptics.steady()
                announcer.landmark(lm)
                kotlinx.coroutines.delay(1500)
                if (isTouching) haptics.feel()
            }
            lastLandmark = lm
        }
    }

    LaunchedEffect(isTouching, cue?.onFeature) {
        if (isTouching && cue?.onFeature == true) haptics.feel()
        else haptics.cancel()
    }

    if (renderable is MoleculeGraph) {
        LaunchedEffect(renderable) { announcer.molecule(renderable) }

        LaunchedEffect(cue?.touchedAtom, isTouching, cue?.onFeature) {
            val ta = if (isTouching && cue?.onFeature == true) cue?.touchedAtom else null
            if (ta != null && ta != lastTouchedAtom) {
                haptics.atomSignature(ta)
                announcer.speakAtom(ta)
                kotlinx.coroutines.delay(150)
                if (isTouching) haptics.feel()
            }
            lastTouchedAtom = ta
        }

        LaunchedEffect(cue?.touchedBond, cue?.touchedAtom, isTouching, cue?.onFeature) {
            val tb = if (isTouching && cue?.onFeature == true && cue?.touchedAtom == null) cue?.touchedBond else null
            if (tb != null && tb != lastTouchedBond) {
                haptics.bondSignature(tb)
                announcer.speakBond(tb)
                kotlinx.coroutines.delay(150)
                if (isTouching) haptics.feel()
            }
            lastTouchedBond = tb
        }
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
            onSingleTap = {
                if (renderable is MoleculeGraph) announcer.molecule(renderable)
            },
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
            text = if (renderable is MoleculeGraph)
                "Tap = describe molecule · Drag = trace · Two-finger tap = nearest atom"
            else
                "Drag · Two-finger tap = nearest landmark · Double-tap = coordinates",
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
    onSingleTap: () -> Unit = {},
    onTwoFingerTap: (normX: Float, normY: Float) -> Unit,
    onDoubleTap: (normX: Float, normY: Float) -> Unit
) {
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val pInsetL = with(density) { 32.dp.toPx() }
    val pInsetR = with(density) { 32.dp.toPx() }
    val pInsetT = with(density) { 24.dp.toPx() }
    val pInsetB = with(density) { 24.dp.toPx() }
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription =
                    "Interactive function canvas. Drag to hear the shape. " +
                    "Two-finger tap speaks coordinates. Double-tap speaks nearest landmark."
            }
            .pointerInput(renderable, pInsetL, pInsetR, pInsetT, pInsetB) {
                val contentW = (size.width - pInsetL - pInsetR).toFloat()
                val contentH = (size.height - pInsetT - pInsetB).toFloat()

                // lastTapMs/lastTapNorm persist across gestures (vars outside awaitEachGesture).
                var lastTapMs = 0L
                var lastTapNorm = Offset.Zero

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downMs = System.currentTimeMillis()
                    val downNorm = Offset(
                        ((down.position.x - pInsetL) / contentW).coerceIn(0f, 1f),
                        ((down.position.y - pInsetT) / contentH).coerceIn(0f, 1f)
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
                                    onSingleTap()
                                }
                            }
                            onTouchEnd()
                            break
                        }

                        primary.consume()
                        val norm = Offset(
                            ((primary.position.x - pInsetL) / contentW).coerceIn(0f, 1f),
                            ((primary.position.y - pInsetT) / contentH).coerceIn(0f, 1f)
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

// Content insets — graph elements render inside this padded area.
private fun DrawScope.cl(): Float = 32f * density
private fun DrawScope.cr(): Float = 32f * density
private fun DrawScope.ct(): Float = 24f * density
private fun DrawScope.cb(): Float = 24f * density
private fun DrawScope.cw(): Float = size.width - cl() - cr()
private fun DrawScope.ch(): Float = size.height - ct() - cb()

private fun DrawScope.drawBackground() = drawRect(BG)

private fun DrawScope.drawGrid(r: Renderable) {
    val xRange = r.xMax - r.xMin
    val yRange = r.yMax - r.yMin
    val left = cl(); val cw = cw(); val top = ct(); val ch = ch()
    val rightEdge = size.width - cr(); val bottomEdge = size.height - cb()

    var gx = Math.ceil(r.xMin / niceStep(xRange / 6)) * niceStep(xRange / 6)
    val xStep = niceStep(xRange / 6)
    while (gx <= r.xMax) {
        val px = (left + (gx - r.xMin) / xRange * cw).toFloat()
        drawLine(
            color = if (Math.abs(gx) < xStep * 0.01) AXIS else GRID,
            start = Offset(px, top), end = Offset(px, bottomEdge),
            strokeWidth = if (Math.abs(gx) < xStep * 0.01) 2f else 1f
        )
        gx += xStep
    }

    val yStep = niceStep(yRange / 6)
    var gy = Math.ceil(r.yMin / yStep) * yStep
    while (gy <= r.yMax) {
        val py = (top + (1.0 - (gy - r.yMin) / yRange) * ch).toFloat()
        drawLine(
            color = if (Math.abs(gy) < yStep * 0.01) AXIS else GRID,
            start = Offset(left, py), end = Offset(rightEdge, py),
            strokeWidth = if (Math.abs(gy) < yStep * 0.01) 2f else 1f
        )
        gy += yStep
    }
}

private fun DrawScope.drawCurve(r: Renderable, onFeature: Boolean) {
    if (r !is LineChart || r.samples.isEmpty()) return
    val xRange = r.xMax - r.xMin
    val yRange = r.yMax - r.yMin
    val left = cl(); val cw = cw(); val top = ct(); val ch = ch()
    val path = Path()
    var first = true
    for (pt in r.samples) {
        val px = (left + (pt.x - r.xMin) / xRange * cw).toFloat()
        val normY = (pt.y - r.yMin) / yRange
        if (normY < -0.15 || normY > 1.15) { first = true; continue }
        val py = (top + (1.0 - normY) * ch).toFloat()
        if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
    }
    drawPath(
        path,
        color = if (onFeature) CURVE_ACTIVE else CURVE_IDLE,
        style = Stroke(width = if (onFeature) 5f else 3f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawFinger(normPos: Offset, cue: Cue?) {
    val left = cl(); val cw = cw(); val top = ct(); val ch = ch()
    val cx = left + normPos.x * cw
    val cy = top + normPos.y * ch
    drawLine(FINGER_DOT.copy(alpha = 0.25f), Offset(cx, top), Offset(cx, top + ch), 1f)
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
    val baseRadius = 120f
    val left = cl(); val cw = cw(); val top = ct(); val ch = ch()

    for (bond in r.bonds) {
        if (bond.fromIndex >= r.atoms.size || bond.toIndex >= r.atoms.size) continue
        val a = r.atoms[bond.fromIndex]; val b = r.atoms[bond.toIndex]
        val ax = left + a.normX.toFloat() * cw; val ay = top + a.normY.toFloat() * ch
        val bx = left + b.normX.toFloat() * cw; val by = top + b.normY.toFloat() * ch
        val bondWidth = when (bond.order) { 1 -> 6f; 2 -> 8f; else -> 10f }
        when (bond.order) {
            1 -> {
                drawLine(BOND_COLOR, Offset(ax, ay), Offset(bx, by), strokeWidth = bondWidth, cap = StrokeCap.Round)
            }
            2 -> {
                val dx = (by - ay); val dy = -(bx - ax)
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val offX = dx / len * 10f; val offY = dy / len * 10f
                drawLine(BOND_COLOR, Offset(ax + offX, ay + offY), Offset(bx + offX, by + offY), bondWidth, cap = StrokeCap.Round)
                drawLine(BOND_COLOR, Offset(ax - offX, ay - offY), Offset(bx - offX, by - offY), bondWidth, cap = StrokeCap.Round)
            }
            else -> {
                val dx = (by - ay); val dy = -(bx - ax)
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val offX = dx / len * 12f; val offY = dy / len * 12f
                drawLine(BOND_COLOR, Offset(ax, ay), Offset(bx, by), bondWidth, cap = StrokeCap.Round)
                drawLine(BOND_COLOR, Offset(ax + offX, ay + offY), Offset(bx + offX, by + offY), bondWidth, cap = StrokeCap.Round)
                drawLine(BOND_COLOR, Offset(ax - offX, ay - offY), Offset(bx - offX, by - offY), bondWidth, cap = StrokeCap.Round)
            }
        }
    }

    for (atom in r.atoms) {
        val px = left + atom.normX.toFloat() * cw
        val py = top + atom.normY.toFloat() * ch
        val radius = baseRadius * atomDisplayRadius(atom.element)
        val nearFinger = fingerNorm != null &&
            hypot((atom.normX.toFloat() - fingerNorm.x), (atom.normY.toFloat() - fingerNorm.y)) < 0.23f
        val fill = elementColor(atom.element)
        if (nearFinger) drawCircle(CURVE_ACTIVE.copy(alpha = 0.3f), radius + 12f, Offset(px, py))
        drawCircle(fill, radius = radius, center = Offset(px, py))
        drawCircle(
            if (nearFinger) CURVE_ACTIVE else Color.White.copy(alpha = 0.85f),
            radius = radius, center = Offset(px, py), style = Stroke(3f)
        )
        val measured = tm.measure(
            atom.element,
            TextStyle(color = labelColorFor(fill), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
        drawText(measured, topLeft = Offset(px - measured.size.width / 2f, py - measured.size.height / 2f))
    }
}

private fun elementColor(element: String): Color = when (element.trim()) {
    "H" -> Color(0xFFEDEDED)
    "C" -> Color(0xFF4A4A4F)
    "N" -> Color(0xFF3B5BFF)
    "O" -> Color(0xFFFF4136)
    "S" -> Color(0xFFFFD400)
    "P" -> Color(0xFFFF8000)
    "F" -> Color(0xFF59E0A0)
    "Cl" -> Color(0xFF2ECC40)
    "Br" -> Color(0xFFB5462A)
    "Na" -> Color(0xFFB14CF0)
    "K" -> Color(0xFF8F40D4)
    else -> Color(0xFFE0609A)
}

private fun labelColorFor(fill: Color): Color =
    if (fill.luminance() > 0.5f) Color(0xFF111111) else Color.White

private fun niceStep(rough: Double): Double {
    if (rough <= 0) return 1.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(rough)))
    return when {
        rough / mag < 2 -> mag
        rough / mag < 5 -> 2 * mag
        else -> 5 * mag
    }
}
