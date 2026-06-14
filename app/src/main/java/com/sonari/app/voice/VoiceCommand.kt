package com.sonari.app.voice

/**
 * Parsed voice command from recognized speech text.
 */
sealed class VoiceCommand {
    /** "show water", "show me methane" → LoadMolecule("water") */
    data class LoadMolecule(val name: String) : VoiceCommand()

    /** "plot x^2", "graph sin(x)" → LoadEquation("x^2") */
    data class LoadEquation(val expr: String) : VoiceCommand()

    /** "back", "go back" */
    data object NavigateBack : VoiceCommand()

    /** "home", "go home" */
    data object NavigateHome : VoiceCommand()

    /** "tutorial", "help" */
    data object OpenTutorial : VoiceCommand()

    /** "settings", "preferences" */
    data object OpenSettings : VoiceCommand()
}

/**
 * Parses recognized speech [text] into a [VoiceCommand].
 * Returns null when the input doesn't match any known pattern.
 */
fun parseVoiceCommand(text: String): VoiceCommand? {
    val lower = text.lowercase().trim()

    // ── Molecule: "show [me] [the] [molecule] {name}" ──────────────────────
    val showMol = Regex("""^show\s+(?:me\s+)?(?:the\s+)?(?:molecule\s+)?(.+)$""").find(lower)
    if (showMol != null) return VoiceCommand.LoadMolecule(showMol.groupValues[1].trim())

    // ── Equation: "plot {expr}" or "graph {expr}" ──────────────────────────
    val plotEq = Regex("""^(?:plot|graph)\s+(.+)$""").find(lower)
    if (plotEq != null) return VoiceCommand.LoadEquation(plotEq.groupValues[1].trim())

    // ── Navigation ─────────────────────────────────────────────────────────
    return when (lower) {
        in setOf("back", "go back", "return")                         -> VoiceCommand.NavigateBack
        in setOf("home", "go home", "menu", "main menu", "home screen") -> VoiceCommand.NavigateHome
        in setOf("tutorial", "help", "guide", "how to use")            -> VoiceCommand.OpenTutorial
        in setOf("settings", "preferences", "options", "setting")      -> VoiceCommand.OpenSettings
        else                                                           -> null
    }
}
