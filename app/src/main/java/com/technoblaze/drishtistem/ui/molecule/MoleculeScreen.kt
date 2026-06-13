package com.technoblaze.drishtistem.ui.molecule

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technoblaze.drishtistem.core.Engines
import com.technoblaze.drishtistem.model.Bond
import com.technoblaze.drishtistem.model.Element
import com.technoblaze.drishtistem.model.MoleculeConcept
import kotlin.math.hypot

/** CPK-inspired display colors per element. */
private val ElementColors = mapOf(
    Element.HYDROGEN to Color(0xFFE8EAED),
    Element.CARBON to Color(0xFF9AA0A6),
    Element.OXYGEN to Color(0xFFEF5350),
    Element.SODIUM to Color(0xFFAB7FD6),
    Element.CHLORINE to Color(0xFF66BB6A),
    Element.NITROGEN to Color(0xFF5C7CFA),
    Element.SULFUR to Color(0xFFFFD43B),
    Element.PHOSPHORUS to Color(0xFFFF922B),
    Element.FLUORINE to Color(0xFF63E6BE),
    Element.GENERIC to Color(0xFFB0BEC5)
)

private fun colorFor(element: Element): Color =
    ElementColors[element] ?: Color(0xFFB0BEC5)

/** What the finger is currently on: nothing, an atom, or a bond. */
private sealed interface TouchRegion {
    data object None : TouchRegion
    data class OnAtom(val index: Int) : TouchRegion
    data class OnBond(val index: Int) : TouchRegion
}

/**
 * Tactile molecule canvas: each atom is a large target with its element's
 * signature vibration and pitch; sliding along a bond gives a light buzz.
 * Transitions between regions trigger the feedback, so resting still is quiet.
 */
@Composable
fun MoleculeScreen(concept: MoleculeConcept, engines: Engines, onBack: () -> Unit) {
    var region by remember { mutableStateOf<TouchRegion>(TouchRegion.None) }
    var fingerScreen by remember { mutableStateOf<Offset?>(null) }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFE8C49A))
            }
            Text(concept.title, color = Color(0xFFE8C49A), fontSize = 18.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Molecule exploration area. ${concept.instructions}" }
                .pointerInput(concept.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            engines.speech.announce(concept.structureSummary, interrupt = true)
                        }
                    )
                }
                .pointerInput(concept.id) {
                    detectDragGestures(
                        onDragStart = { pos -> fingerScreen = pos },
                        onDrag = { change, _ ->
                            change.consume()
                            fingerScreen = change.position
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val newRegion = hitTest(concept, change.position, w, h)
                            if (newRegion != region) {
                                region = newRegion
                                onRegionEntered(newRegion, concept, engines)
                            }
                            if (newRegion is TouchRegion.None) {
                                engines.tone.mute()
                            }
                        },
                        onDragEnd = {
                            fingerScreen = null
                            region = TouchRegion.None
                            engines.tone.mute()
                            engines.haptics.stop()
                        },
                        onDragCancel = {
                            fingerScreen = null
                            region = TouchRegion.None
                            engines.tone.mute()
                            engines.haptics.stop()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawMolecule(concept, region)
                fingerScreen?.let { drawCircle(Color(0x40FFFFFF), radius = 44f, center = it) }
            }
        }
    }
}

private fun atomScreenPos(concept: MoleculeConcept, index: Int, w: Float, h: Float): Offset {
    val atom = concept.atoms[index]
    return Offset(atom.x * w, atom.y * h)
}

private fun atomRadius(element: Element): Float = when (element) {
    Element.HYDROGEN, Element.FLUORINE -> 70f
    Element.CARBON, Element.SODIUM, Element.NITROGEN -> 95f
    Element.OXYGEN -> 100f
    Element.CHLORINE, Element.SULFUR, Element.PHOSPHORUS -> 110f
    Element.GENERIC -> 90f
}

private fun hitTest(concept: MoleculeConcept, pos: Offset, w: Float, h: Float): TouchRegion {
    concept.atoms.forEachIndexed { i, atom ->
        val center = atomScreenPos(concept, i, w, h)
        if (hypot(pos.x - center.x, pos.y - center.y) <= atomRadius(atom.element) + 30f) {
            return TouchRegion.OnAtom(i)
        }
    }
    concept.bonds.forEachIndexed { i, bond ->
        val a = atomScreenPos(concept, bond.fromIndex, w, h)
        val b = atomScreenPos(concept, bond.toIndex, w, h)
        if (distanceToSegment(pos, a, b) < 55f) return TouchRegion.OnBond(i)
    }
    return TouchRegion.None
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lenSq = abx * abx + aby * aby
    if (lenSq == 0f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * abx), p.y - (a.y + t * aby))
}

private fun onRegionEntered(region: TouchRegion, concept: MoleculeConcept, engines: Engines) {
    when (region) {
        is TouchRegion.OnAtom -> {
            val atom = concept.atoms[region.index]
            val e = atom.element
            engines.haptics.pattern(e.vibrationTimings, e.vibrationAmplitudes)
            engines.tone.setFrequency(e.toneHz)
            engines.tone.setPan(atom.x)
            engines.tone.play()
            engines.speech.announce("${e.elementName}. ${atom.role}.")
        }
        is TouchRegion.OnBond -> {
            val bond = concept.bonds[region.index]
            val from = concept.atoms[bond.fromIndex].element
            val to = concept.atoms[bond.toIndex].element
            // Light continuous buzz, pitch between the two atoms' tones.
            engines.haptics.feel(0.3f)
            engines.tone.setFrequency((from.toneHz + to.toneHz) / 2f)
            engines.tone.play()
            engines.speech.announce(
                "${bond.spokenKind} between ${from.elementName} and ${to.elementName}"
            )
        }
        TouchRegion.None -> {
            engines.haptics.stop()
        }
    }
}

private fun DrawScope.drawMolecule(concept: MoleculeConcept, region: TouchRegion) {
    val w = size.width
    val h = size.height

    concept.bonds.forEachIndexed { i, bond ->
        val a = atomScreenPos(concept, bond.fromIndex, w, h)
        val b = atomScreenPos(concept, bond.toIndex, w, h)
        val active = region is TouchRegion.OnBond && region.index == i
        val color = if (active) Color.White else Color(0xFF8A8F98)
        when {
            bond.kind == Bond.Kind.IONIC ->
                drawLine(
                    color, a, b, strokeWidth = 8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 18f))
                )
            bond.order == 2 -> {
                // Two parallel lines offset perpendicular to the bond axis.
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val ox = -dy / len * 10f
                val oy = dx / len * 10f
                drawLine(color, Offset(a.x + ox, a.y + oy), Offset(b.x + ox, b.y + oy), strokeWidth = 7f)
                drawLine(color, Offset(a.x - ox, a.y - oy), Offset(b.x - ox, b.y - oy), strokeWidth = 7f)
            }
            else -> drawLine(color, a, b, strokeWidth = 8f)
        }
    }

    concept.atoms.forEachIndexed { i, atom ->
        val center = atomScreenPos(concept, i, w, h)
        val r = atomRadius(atom.element)
        val active = region is TouchRegion.OnAtom && region.index == i
        drawCircle(colorFor(atom.element), radius = r, center = center)
        if (active) {
            drawCircle(Color.White, radius = r + 10f, center = center, style = Stroke(width = 6f))
        }
        drawContext.canvas.nativeCanvas.drawText(
            atom.element.symbol,
            center.x,
            center.y + 18f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 52f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
        )
    }
}
