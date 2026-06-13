package com.sonari.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitPointerEvent
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonari.app.a11y.Announcer
import com.sonari.app.audio.Sonifier
import com.sonari.app.data.EquationLoader
import com.sonari.app.engine.DefaultMappingEngine
import com.sonari.app.haptic.Haptics
import com.sonari.app.model.DataPoint
import com.sonari.app.model.Landmark
import com.sonari.app.model.LineChart
import com.sonari.app.model.Renderable
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ─── Colors ──────────────────────────────────────────────────────────────────

private val BG_T = Color(0xFF1A1B1E)
private val ACCENT_T = Color(0xFF4FC3F7)
private val CARD_T = Color(0xFF2C2D31)
private val GRID_T = Color(0xFF2A2B2E)
private val CURVE_T = Color(0xFFCCCCCC)
private val CURVE_ON_T = Color(0xFF4FC3F7)
private val FINGER_T = Color(0xFFFFEB3B)

// ─── Step definitions ─────────────────────────────────────────────────────────

private enum class Practice { NONE, DRAG, TWO_FINGER_TAP, DOUBLE_TAP }

private data class Step(
    val title: String,
    val body: String,
    val hint: String,
    val practice: Practice = Practice.NONE
)

private val STEPS = listOf(
    Step(
        title = "Welcome to Sonari",
        body = "Sonari turns maths graphs and molecules into sound and vibration, " +
               "so you can explore STEM with your ears and fingertips, eyes completely closed.",
        hint = "Swipe right to go to the next step, or tap Next."
    ),
    Step(
        title = "Pitch equals height",
        body = "A high pitch means the value is high on the graph. " +
               "A low pitch means low. " +
               "The range is 200 hertz at the bottom to 1000 hertz at the top. " +
               "Think of a piano: high notes at the top, low notes at the bottom.",
        hint = "This is the one rule. Everything else follows from it."
    ),
    Step(
        title = "Overview mode",
        body = "Tap the Play Overview button and simply listen. " +
               "The app sweeps left to right over about 5 seconds. " +
               "Pitch rises and falls with the curve. " +
               "A double vibration marks a special point such as a peak, a valley, or a zero crossing.",
        hint = "You do not need to touch the screen at all. Just listen."
    ),
    Step(
        title = "Explore mode — drag your finger",
        body = "Drag one finger slowly across the surface below. " +
               "You will hear a continuous tone that follows the curve. " +
               "When your finger lands on the curve, the phone buzzes. " +
               "That buzz is your contact confirmation: you are on the line.",
        hint = "Try it now. Drag your finger across the practice area.",
        practice = Practice.DRAG
    ),
    Step(
        title = "Anchors — you are never lost",
        body = "The four screen edges are your fixed reference points. " +
               "Left edge is x minimum. Right edge is x maximum. " +
               "Top edge is y maximum. Bottom edge is y minimum. " +
               "Press any edge with a finger to orient yourself before exploring.",
        hint = "Press the left edge of the practice area to find x minimum."
    ),
    Step(
        title = "Gesture: two-finger tap",
        body = "Place two fingers on the screen and lift them quickly. " +
               "The app speaks the nearest landmark: an intercept, a peak, or a valley. " +
               "Use this to identify key points without knowing their exact position.",
        hint = "Try it below. Tap with two fingers and listen for the landmark name.",
        practice = Practice.TWO_FINGER_TAP
    ),
    Step(
        title = "Gesture: double-tap",
        body = "Tap the screen twice quickly in the same spot. " +
               "The app will speak your exact coordinates: x and y values in real units. " +
               "Use this whenever you want to know exactly where you are.",
        hint = "Try it below. Double-tap and listen for the spoken coordinates.",
        practice = Practice.DOUBLE_TAP
    ),
    Step(
        title = "Molecules",
        body = "In the Molecule tab on the home screen, type a name such as caffeine or aspirin. " +
               "Atoms appear as circles with element labels. " +
               "Drag your finger to trace bonds between them. " +
               "Each atom has a pitch based on its vertical position in the diagram.",
        hint = "Three molecules always work without internet: water, caffeine, and aspirin."
    ),
    Step(
        title = "You are ready",
        body = "Start with Overview on y equals x squared, the parabola. " +
               "Listen for the low pitch at the bottom rising on both sides. " +
               "Then switch to Explore, find the vertex with your finger, and feel the buzz. " +
               "Two-finger tap to confirm the coordinates.",
        hint = "Tap Try it now to load the parabola."
    )
)

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun TutorialScreen(
    announcer: Announcer,
    sonifier: Sonifier,
    haptics: Haptics,
    onFinish: (Renderable) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    val current = STEPS[step]
    val isLast = step == STEPS.lastIndex

    // Auto-announce step content whenever step changes.
    LaunchedEffect(step) {
        delay(300) // brief pause so UI settles before speaking
        announcer.announce(
            "Step ${step + 1} of ${STEPS.size}. ${current.title}. ${current.body}. ${current.hint}"
        )
    }

    // Silence audio when leaving this screen.
    LaunchedEffect(Unit) { sonifier.setCue(440.0, 0.0, false) }

    // Swipe right = next, swipe left = back — detected on the whole screen.
    var swipeStartX by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG_T)
            .pointerInput(step) {
                detectDragGestures(
                    onDragStart = { swipeStartX = it.x },
                    onDrag = { _, _ -> },
                    onDragEnd = { /* handled below — see accumulated delta */ }
                )
            }
            .pointerInput(step) {
                // Track total horizontal swipe distance; navigate on release.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val p = event.changes.firstOrNull { it.pressed }
                        if (p == null) {
                            if (totalX < -80f && step < STEPS.lastIndex) step++
                            else if (totalX > 80f && step > 0) step--
                            break
                        }
                        totalX += p.positionChange().x
                        // Don't consume — let child canvas handle its own drag.
                    }
                }
            }
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tutorial", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text(
                "${step + 1} / ${STEPS.size}",
                color = Color(0xFF666666),
                fontSize = 13.sp
            )
        }

        // ── Step title + body ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (current.practice != Practice.NONE) 0.38f else 0.6f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                current.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Step ${step+1}: ${current.title}"
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(current.body, color = Color(0xFFCCCCCC), fontSize = 15.sp, lineHeight = 23.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(current.hint, color = ACCENT_T, fontSize = 13.sp)
        }

        // ── Practice canvas (Explore / gesture steps) ──
        if (current.practice != Practice.NONE) {
            PracticeCanvas(
                practice = current.practice,
                sonifier = sonifier,
                haptics = haptics,
                announcer = announcer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            Spacer(modifier = Modifier.weight(0.38f))
        }

        // ── Navigation ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isLast) {
                Button(
                    onClick = {
                        val chart = EquationLoader.load("x^2", -3.0, 3.0).getOrNull() ?: return@Button
                        onFinish(chart)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT_T),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "Try it now — load parabola and explore" }
                ) { Text("Try it now", color = Color.Black, fontSize = 17.sp) }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Re-read button — always visible.
                Button(
                    onClick = {
                        announcer.announce("${current.title}. ${current.body}. ${current.hint}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CARD_T),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { contentDescription = "Repeat this step" }
                ) { Text("Repeat", color = Color(0xFFAAAAAA), fontSize = 14.sp) }

                if (step > 0) {
                    Button(
                        onClick = { step-- },
                        colors = ButtonDefaults.buttonColors(containerColor = CARD_T),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Go to previous step" }
                    ) { Text("Back", color = Color.White, fontSize = 14.sp) }
                }

                if (!isLast) {
                    Button(
                        onClick = { step++ },
                        colors = ButtonDefaults.buttonColors(containerColor = ACCENT_T),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Go to next step" }
                    ) { Text("Next", color = Color.Black, fontSize = 14.sp) }
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Exit tutorial" }
            ) { Text("Exit tutorial", color = Color(0xFF555555), fontSize = 12.sp) }
        }
    }
}

// ─── Interactive practice canvas ─────────────────────────────────────────────

private val PRACTICE_RENDERABLE: LineChart = run {
    val samples = (-200..200).map { i ->
        val x = i / 100.0 * PI
        DataPoint(x, sin(x))
    }
    LineChart(
        xMin = -PI, xMax = PI, yMin = -1.0, yMax = 1.0,
        samples = samples,
        landmarks = listOf(
            Landmark(0.5, 0.5, Landmark.Type.INTERCEPT, "centre, x equals 0, y equals 0"),
            Landmark(0.25, 1.0, Landmark.Type.EXTREMUM, "peak at x equals negative pi over 2"),
            Landmark(0.75, 0.0, Landmark.Type.EXTREMUM, "valley at x equals pi over 2")
        )
    )
}

@Composable
private fun PracticeCanvas(
    practice: Practice,
    sonifier: Sonifier,
    haptics: Haptics,
    announcer: Announcer,
    modifier: Modifier
) {
    var normPos by remember { mutableStateOf<Offset?>(null) }
    var isTouching by remember { mutableStateOf(false) }
    var lastLandmark by remember { mutableStateOf<Landmark?>(null) }
    var lastContactMs by remember { mutableLongStateOf(0L) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var lastTapNorm by remember { mutableStateOf(Offset.Zero) }

    val r: Renderable = PRACTICE_RENDERABLE

    val cue = remember(normPos) {
        normPos?.let { DefaultMappingEngine.cueAt(it.x.toDouble(), it.y.toDouble(), r) }
    }

    LaunchedEffect(cue, isTouching) {
        if (practice == Practice.DRAG && isTouching && cue != null) {
            sonifier.setCue(cue.freqHz, 0.0, true)
        } else {
            sonifier.setCue(440.0, 0.0, false)
        }
    }

    LaunchedEffect(cue?.landmark) {
        val lm = cue?.landmark
        if (lm != null && lm != lastLandmark) { haptics.landmark(); announcer.landmark(lm) }
        lastLandmark = lm
    }

    LaunchedEffect(isTouching) {
        while (isTouching) {
            delay(300)
            val now = System.currentTimeMillis()
            if (cue?.onFeature == true && now - lastContactMs >= 280) {
                haptics.contact(); lastContactMs = now
            }
        }
        haptics.cancel()
    }

    Box(
        modifier = modifier
            .background(CARD_T, RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = when (practice) {
                        Practice.DRAG -> "Practice area. Drag one finger to hear the curve."
                        Practice.TWO_FINGER_TAP -> "Practice area. Tap with two fingers to hear coordinates."
                        Practice.DOUBLE_TAP -> "Practice area. Double-tap to hear nearest landmark."
                        else -> "Practice area."
                    }
                }
                .pointerInput(practice) {
                    var lastDownMs = 0L
                    var lastDownNorm = Offset.Zero

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downMs = System.currentTimeMillis()
                        val downNorm = Offset(
                            (down.position.x / size.width).coerceIn(0f, 1f),
                            (down.position.y / size.height).coerceIn(0f, 1f)
                        )
                        val isDouble = downMs - lastDownMs < 300 &&
                            (downNorm - lastDownNorm).getDistance() < 0.2f

                        down.consume()
                        isTouching = true
                        normPos = downNorm

                        var twoFingers = false
                        var dragged = false

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) twoFingers = true

                            val p = event.changes.firstOrNull { it.pressed }
                            if (p == null) {
                                val upMs = System.currentTimeMillis()
                                val isTap = upMs - downMs < 250 && !dragged

                                when {
                                    twoFingers && isTap && practice == Practice.TWO_FINGER_TAP -> {
                                        val nearest = r.landmarks.minByOrNull { lm ->
                                            Math.hypot(downNorm.x - lm.normX, downNorm.y - lm.normY)
                                        }
                                        if (nearest != null) {
                                            announcer.landmark(nearest)
                                            haptics.landmark()
                                        }
                                    }
                                    !twoFingers && isTap && isDouble && practice == Practice.DOUBLE_TAP -> {
                                        val x = r.xMin + downNorm.x * (r.xMax - r.xMin)
                                        val y = r.yMin + downNorm.y * (r.yMax - r.yMin)
                                        announcer.announce("x %.2f, y %.2f".format(x, y))
                                        haptics.landmark()
                                    }
                                    !twoFingers && isTap -> {
                                        lastDownMs = upMs
                                        lastDownNorm = downNorm
                                    }
                                }
                                isTouching = false
                                normPos = null
                                break
                            }
                            p.consume()
                            val n = Offset(
                                (p.position.x / size.width).coerceIn(0f, 1f),
                                (p.position.y / size.height).coerceIn(0f, 1f)
                            )
                            if ((n - downNorm).getDistance() > 0.03f) dragged = true
                            normPos = n
                        }
                    }
                }
        ) {
            // Background.
            drawRect(CARD_T)

            // Grid (just y=0 axis).
            val midY = size.height / 2f
            drawLine(Color(0xFF3A3B40), Offset(0f, midY), Offset(size.width, midY), 1.5f)
            val midX = size.width / 2f
            drawLine(Color(0xFF3A3B40), Offset(midX, 0f), Offset(midX, size.height), 1.5f)

            // Curve.
            if (PRACTICE_RENDERABLE.samples.isNotEmpty()) {
                val path = Path()
                var first = true
                for (pt in PRACTICE_RENDERABLE.samples) {
                    val nx = ((pt.x - PRACTICE_RENDERABLE.xMin) /
                        (PRACTICE_RENDERABLE.xMax - PRACTICE_RENDERABLE.xMin)).toFloat()
                    val ny = ((pt.y - PRACTICE_RENDERABLE.yMin) /
                        (PRACTICE_RENDERABLE.yMax - PRACTICE_RENDERABLE.yMin)).toFloat()
                    val px = nx * size.width
                    val py = (1f - ny) * size.height
                    if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
                }
                drawPath(
                    path,
                    color = if (cue?.onFeature == true) CURVE_ON_T else CURVE_T,
                    style = Stroke(4f, cap = StrokeCap.Round)
                )
            }

            // Finger indicator.
            normPos?.let { np ->
                val cx = np.x * size.width; val cy = np.y * size.height
                if (cue?.onFeature == true) drawCircle(CURVE_ON_T.copy(0.2f), 28f, Offset(cx, cy))
                drawCircle(FINGER_T, 12f, Offset(cx, cy))
            }
        }

        // Instruction label.
        if (!isTouching) {
            Text(
                text = when (practice) {
                    Practice.DRAG -> "↔ drag to explore"
                    Practice.TWO_FINGER_TAP -> "✌ two-finger tap"
                    Practice.DOUBLE_TAP -> "↕ double-tap"
                    else -> ""
                },
                color = Color(0xFF555555),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}
