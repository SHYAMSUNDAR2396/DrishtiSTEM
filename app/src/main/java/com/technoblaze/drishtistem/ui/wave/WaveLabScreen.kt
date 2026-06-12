package com.technoblaze.drishtistem.ui.wave

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.technoblaze.drishtistem.model.WaveConcept
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Adjustable sine wave: frequency (cycles on screen) and amplitude are changed
 * with large accessible buttons. Tracing the wave sonifies its shape; "Play
 * wave" renders the chosen frequency as a real tone plus a matching vibration
 * rhythm, so frequency and amplitude become physical sensations.
 */
@Composable
fun WaveLabScreen(concept: WaveConcept, engines: Engines, onBack: () -> Unit) {
    var freq by remember { mutableIntStateOf(2) } // cycles across the screen, 1..8
    var amp by remember { mutableIntStateOf(3) } // 1..5 -> 0.2..1.0
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(concept.id) {
        engines.speech.announce(
            "${concept.title}. ${concept.spokenIntro} ${concept.instructions} " +
                "Frequency $freq, amplitude $amp of 5.",
            interrupt = true
        )
    }

    val ampF = amp / 5f

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFE8C49A))
            }
            Text("Wave Lab", color = Color(0xFFE8C49A), fontSize = 18.sp)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { contentDescription = "Wave display. Drag a finger to feel the wave shape." }
                .pointerInput(freq, amp) {
                    detectDragGestures(
                        onDragStart = { engines.tone.play() },
                        onDrag = { change, _ ->
                            change.consume()
                            val x01 = (change.position.x / size.width).coerceIn(0f, 1f)
                            val phase = 2.0 * PI * freq * x01
                            val y = ampF * sin(phase).toFloat()
                            engines.tone.setPitchFromValue(y, -1f, 1f)
                            engines.tone.setPan(x01)
                            val slope = abs(cos(phase)).toFloat() * ampF
                            engines.haptics.feel(0.1f + 0.9f * slope)
                        },
                        onDragEnd = { engines.tone.mute(); engines.haptics.stop() },
                        onDragCancel = { engines.tone.mute(); engines.haptics.stop() }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2
                drawLine(Color(0xFF6B6F76), Offset(0f, midY), Offset(size.width, midY), strokeWidth = 3f)
                val path = Path()
                val steps = 400
                for (s in 0..steps) {
                    val x01 = s / steps.toFloat()
                    val y = ampF * sin(2.0 * PI * freq * x01).toFloat()
                    val sx = x01 * size.width
                    val sy = midY - y * (size.height / 2 - 40f)
                    if (s == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                drawPath(path, Color(0xFF4FC3F7), style = Stroke(width = 7f))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "Frequency: $freq    Amplitude: $amp / 5",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabButton("Freq −", "Lower frequency", Modifier.weight(1f)) {
                    if (freq > 1) freq--
                    engines.speech.announce("Frequency $freq")
                    engines.haptics.tick()
                }
                LabButton("Freq +", "Raise frequency", Modifier.weight(1f)) {
                    if (freq < 8) freq++
                    engines.speech.announce("Frequency $freq")
                    engines.haptics.tick()
                }
                LabButton("Amp −", "Lower amplitude", Modifier.weight(1f)) {
                    if (amp > 1) amp--
                    engines.speech.announce("Amplitude $amp of 5")
                    engines.haptics.tick()
                }
                LabButton("Amp +", "Raise amplitude", Modifier.weight(1f)) {
                    if (amp < 5) amp++
                    engines.speech.announce("Amplitude $amp of 5")
                    engines.haptics.tick()
                }
            }
            Button(
                onClick = {
                    if (playing) return@Button
                    playing = true
                    engines.speech.announce("Playing wave. Frequency $freq, amplitude $amp.")
                    scope.launch {
                        delay(1600) // let the announcement finish
                        // Audible pitch scales with visual frequency: 1..8 -> 110..880 Hz.
                        engines.tone.setFrequency(110f * freq)
                        engines.tone.setPan(0.5f)
                        engines.tone.play()
                        // Vibration rhythm matches the wave: freq pulses per second.
                        val period = 1000L / freq
                        val on = (period / 2).coerceAtLeast(20L)
                        val strength = (ampF * 255).toInt().coerceAtLeast(60)
                        repeat(2 * freq) {
                            engines.haptics.pattern(longArrayOf(0, on), intArrayOf(0, strength))
                            delay(period)
                        }
                        engines.tone.mute()
                        engines.haptics.stop()
                        playing = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB5651D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(64.dp)
                    .semantics { contentDescription = "Play wave as sound and vibration" }
            ) {
                Text(if (playing) "Playing…" else "▶  Play wave", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun LabButton(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3136)),
        modifier = modifier
            .height(64.dp)
            .semantics { contentDescription = description }
    ) {
        Text(label, fontSize = 15.sp, color = Color(0xFFE8C49A))
    }
}
