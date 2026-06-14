package com.sonari.app.a11y

import android.content.Context
import android.speech.tts.TextToSpeech
import com.sonari.app.model.Landmark
import com.sonari.app.model.MoleculeGraph
import com.sonari.app.model.Renderable
import java.util.Locale

class Announcer(context: Context) {

    private var ready = false
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
        }
    }

    init {
        tts.language = Locale.US
    }

    fun announce(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sonari-${text.hashCode()}")
    }

    fun coordinates(normX: Double, normY: Double, r: Renderable) {
        val x = r.xMin + normX * (r.xMax - r.xMin)
        val y = r.yMin + normY * (r.yMax - r.yMin)
        announce("x %.2f, y %.2f".format(x, y))
    }

    fun landmark(l: Landmark) = announce(l.label)

    fun molecule(r: MoleculeGraph) = announce(describeMolecule(r))

    fun release() = tts.shutdown()
}

private fun describeMolecule(r: MoleculeGraph): String {
    val composition = r.atoms.groupingBy { it.element }.eachCount().entries
        .sortedByDescending { it.value }
        .joinToString(", ") { (element, n) -> "$n ${elementName(element)}" }
    val singles = r.bonds.count { it.order <= 1 }
    val doubles = r.bonds.count { it.order == 2 }
    val triples = r.bonds.count { it.order >= 3 }
    val bonds = listOfNotNull(
        singles.takeIf { it > 0 }?.let { "$it single" },
        doubles.takeIf { it > 0 }?.let { "$it double" },
        triples.takeIf { it > 0 }?.let { "$it triple" }
    ).joinToString(", ")
    return buildString {
        append("Molecule with ${r.atoms.size} atoms")
        if (r.bonds.isNotEmpty()) append(" and ${r.bonds.size} bonds")
        append(". Composition: $composition.")
        if (bonds.isNotEmpty()) append(" Bonds: $bonds.")
    }
}

private fun elementName(symbol: String): String = when (symbol) {
    "H" -> "hydrogen"; "C" -> "carbon"; "N" -> "nitrogen"; "O" -> "oxygen"
    "F" -> "fluorine"; "P" -> "phosphorus"; "S" -> "sulfur"; "Cl" -> "chlorine"
    "Br" -> "bromine"; "I" -> "iodine"; "B" -> "boron"
    "Na" -> "sodium"; "K" -> "potassium"
    else -> symbol
}
