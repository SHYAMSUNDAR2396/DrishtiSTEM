package com.sonari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonari.app.data.EquationLoader
import com.sonari.app.model.LineChart
import com.sonari.app.model.Renderable

private val BG = Color(0xFF1A1B1E)
private val ACCENT = Color(0xFF4FC3F7)
private val CARD_BG = Color(0xFF2C2D31)

private data class TutorialStep(
    val title: String,
    val body: String,
    val hint: String = ""
)

private val STEPS = listOf(
    TutorialStep(
        title = "Welcome to Sonari",
        body = "Sonari turns math graphs and molecules into sound and vibration — " +
               "so you can explore STEM with your ears and fingertips, eyes closed.",
        hint = "Swipe right or tap Next to continue."
    ),
    TutorialStep(
        title = "Pitch = height",
        body = "A high pitch means the value is high on the graph. " +
               "A low pitch means low. The pitch range is 200 Hz (bottom) to 1 000 Hz (top). " +
               "Think of a piano: high notes are at the top, low notes at the bottom.",
        hint = "This is the core rule. Everything else builds on it."
    ),
    TutorialStep(
        title = "Overview mode",
        body = "Tap Play Overview and listen. The app sweeps left to right over about 5 seconds. " +
               "Pitch rises and falls with the curve. " +
               "A double vibration marks a special point: a peak, a valley, or a zero crossing.",
        hint = "You do not need to touch the screen at all. Just listen."
    ),
    TutorialStep(
        title = "Explore mode — drag your finger",
        body = "Switch to Explore mode. Drag one finger slowly across the screen. " +
               "You will hear a continuous tone that tracks the curve under your finger. " +
               "When your finger is on the curve, the phone buzzes softly — that is your contact confirmation.",
        hint = "The buzz is how you know you are on the line."
    ),
    TutorialStep(
        title = "Anchors: you are never lost",
        body = "The four screen edges are the four axis bounds — a fixed physical reference you can feel. " +
               "Left edge = x minimum. Right edge = x maximum. " +
               "Top edge = y maximum. Bottom edge = y minimum.",
        hint = "Press a finger to any edge and orient yourself."
    ),
    TutorialStep(
        title = "Gestures",
        body = "Two-finger tap anywhere → the app speaks your exact coordinates.\n\n" +
               "Double-tap → speaks the nearest landmark (intercept, peak, or valley).\n\n" +
               "These work in Explore mode.",
        hint = "Practise these two gestures until they feel natural."
    ),
    TutorialStep(
        title = "Molecules",
        body = "In the Molecule tab, type a name like caffeine or aspirin. " +
               "Atoms are drawn as labelled circles. Drag your finger to trace bonds between them. " +
               "Each atom has a distinct pitch based on its position in the diagram.",
        hint = "Three molecules always work offline: water, caffeine, and aspirin."
    ),
    TutorialStep(
        title = "You are ready",
        body = "Start with Overview on y = x² (a parabola). " +
               "Listen for the low pitch at the bottom, rising on both sides. " +
               "Then Explore — find the vertex with your finger and feel the buzz. " +
               "Two-finger tap to confirm the coordinates.",
        hint = "Tap Try it now to load the parabola and begin."
    )
)

@Composable
fun TutorialScreen(
    onFinish: (Renderable) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    val current = STEPS[step]
    val isLast = step == STEPS.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
            // Progress indicator.
            Text(
                "${step + 1} / ${STEPS.size}",
                color = Color(0xFF666666),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = current.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Step ${step+1}: ${current.title}" }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(current.body, color = Color(0xFFCCCCCC), fontSize = 16.sp, lineHeight = 24.sp)

            if (current.hint.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(current.hint, color = Color(0xFF4FC3F7), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column {
            if (isLast) {
                Button(
                    onClick = {
                        val chart = EquationLoader
                            .load("x^2", -3.0, 3.0)
                            .getOrNull() ?: return@Button
                        onFinish(chart)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "Try it now with parabola" }
                ) { Text("Try it now", color = Color.Black, fontSize = 17.sp) }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (step > 0) {
                    Button(
                        onClick = { step-- },
                        colors = ButtonDefaults.buttonColors(containerColor = CARD_BG),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Previous step" }
                    ) { Text("Back", color = Color.White) }
                }

                if (!isLast) {
                    Button(
                        onClick = { step++ },
                        colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Next step" }
                    ) { Text("Next", color = Color.Black) }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Skip tutorial" }
            ) { Text("Skip tutorial", color = Color(0xFF666666), fontSize = 13.sp) }
        }
    }
}
