package com.technoblaze.drishtistem.model

/**
 * Chemical elements with distinct sensory signatures: every element gets its
 * own vibration waveform and tone pitch so atoms are distinguishable by feel
 * alone. Heavier elements sound lower.
 */
enum class Element(
    val symbol: String,
    val elementName: String,
    val atomicNumber: Int,
    val toneHz: Float,
    val vibrationTimings: LongArray,
    val vibrationAmplitudes: IntArray
) {
    HYDROGEN(
        "H", "Hydrogen", 1,
        toneHz = 900f,
        // One short light tick.
        vibrationTimings = longArrayOf(0, 40),
        vibrationAmplitudes = intArrayOf(0, 150)
    ),
    CARBON(
        "C", "Carbon", 6,
        toneHz = 600f,
        // Double pulse.
        vibrationTimings = longArrayOf(0, 60, 50, 60),
        vibrationAmplitudes = intArrayOf(0, 220, 0, 220)
    ),
    OXYGEN(
        "O", "Oxygen", 8,
        toneHz = 450f,
        // One long strong buzz.
        vibrationTimings = longArrayOf(0, 260),
        vibrationAmplitudes = intArrayOf(0, 255)
    ),
    SODIUM(
        "Na", "Sodium", 11,
        toneHz = 350f,
        // Triple quick tick.
        vibrationTimings = longArrayOf(0, 35, 35, 35, 35, 35),
        vibrationAmplitudes = intArrayOf(0, 180, 0, 180, 0, 180)
    ),
    CHLORINE(
        "Cl", "Chlorine", 17,
        toneHz = 280f,
        // Long-short.
        vibrationTimings = longArrayOf(0, 180, 60, 50),
        vibrationAmplitudes = intArrayOf(0, 255, 0, 160)
    ),
    NITROGEN(
        "N", "Nitrogen", 7,
        toneHz = 520f,
        // Short-long.
        vibrationTimings = longArrayOf(0, 50, 50, 150),
        vibrationAmplitudes = intArrayOf(0, 170, 0, 240)
    ),
    SULFUR(
        "S", "Sulfur", 16,
        toneHz = 320f,
        // Three rising buzzes.
        vibrationTimings = longArrayOf(0, 70, 40, 70, 40, 120),
        vibrationAmplitudes = intArrayOf(0, 150, 0, 200, 0, 255)
    ),
    PHOSPHORUS(
        "P", "Phosphorus", 15,
        toneHz = 360f,
        // Long-long.
        vibrationTimings = longArrayOf(0, 140, 60, 140),
        vibrationAmplitudes = intArrayOf(0, 230, 0, 230)
    ),
    FLUORINE(
        "F", "Fluorine", 9,
        toneHz = 820f,
        // Two light ticks.
        vibrationTimings = longArrayOf(0, 35, 40, 35),
        vibrationAmplitudes = intArrayOf(0, 150, 0, 150)
    ),

    /** Fallback for any element the library doesn't model explicitly. */
    GENERIC(
        "?", "atom", 0,
        toneHz = 500f,
        vibrationTimings = longArrayOf(0, 90),
        vibrationAmplitudes = intArrayOf(0, 200)
    );

    companion object {
        /** Resolve a chemical symbol (case-insensitive) to a known element, or GENERIC. */
        fun fromSymbol(symbol: String): Element {
            val s = symbol.trim()
            return entries.firstOrNull { it != GENERIC && it.symbol.equals(s, ignoreCase = true) }
                ?: GENERIC
        }
    }
}

/** Position in molecule-local coordinates, 0..1 on both axes. */
data class Atom(
    val element: Element,
    val x: Float,
    val y: Float,
    /** Spoken role, e.g. "central oxygen atom". */
    val role: String
)

data class Bond(
    val fromIndex: Int,
    val toIndex: Int,
    val order: Int = 1,
    val kind: Kind = Kind.COVALENT
) {
    enum class Kind { COVALENT, IONIC }

    val spokenKind: String
        get() = when {
            kind == Kind.IONIC -> "ionic bond"
            order == 2 -> "double bond"
            order == 3 -> "triple bond"
            else -> "single bond"
        }
}

data class MoleculeConcept(
    override val id: String,
    override val subject: Subject,
    override val title: String,
    override val spokenIntro: String,
    val formulaSpoken: String,
    val atoms: List<Atom>,
    val bonds: List<Bond>,
    val structureSummary: String
) : Concept {
    override val instructions: String =
        "Move one finger around the screen to find atoms. " +
            "Each element has its own vibration: hydrogen ticks once, carbon pulses twice, oxygen buzzes long. " +
            "Slide between atoms to feel the bonds. " +
            "Double tap to hear the full structure."
}
