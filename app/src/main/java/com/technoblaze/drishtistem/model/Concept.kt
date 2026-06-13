package com.technoblaze.drishtistem.model

enum class Subject(val displayName: String, val spokenIntro: String) {
    MATHS(
        "Mathematics",
        "Mathematics. Explore function graphs through touch and sound."
    ),
    PHYSICS(
        "Physics",
        "Physics. Feel motion graphs and waves."
    ),
    CHEMISTRY(
        "Chemistry",
        "Chemistry. Touch molecules, atom by atom."
    )
}

/**
 * A unit of explorable content. [spokenIntro] is announced on screen entry;
 * [instructions] follow it so a blind user always knows the available gestures.
 */
sealed interface Concept {
    val id: String
    val subject: Subject
    val title: String
    val spokenIntro: String
    val instructions: String
}

/** A point of interest on a graph that triggers haptic + spoken callouts. */
data class Landmark(
    val x: Float,
    val y: Float,
    val announcement: String,
    val kind: Kind
) {
    enum class Kind { ROOT, PEAK, TROUGH, INTERSECTION }
}

data class Curve(
    val label: String,
    val f: (Float) -> Float
)

data class GraphConcept(
    override val id: String,
    override val subject: Subject,
    override val title: String,
    override val spokenIntro: String,
    val curves: List<Curve>,
    val xMin: Float,
    val xMax: Float,
    val yMin: Float,
    val yMax: Float,
    val xAxisLabel: String = "x",
    val yAxisLabel: String = "y",
    /** When set, double-tap toggles guidance toward this landmark. */
    val guidanceTarget: Landmark? = null
) : Concept {
    override val instructions: String =
        buildString {
            append("Drag one finger to find the curve. ")
            append("The phone vibrates only when your finger is on the line. ")
            append("When you are off the line, short pulses quicken as you get closer, guiding you onto it. ")
            append("Higher points on the line sound higher, and steeper parts vibrate more strongly. ")
            if (guidanceTarget != null) {
                append("Double tap to switch guidance mode on or off. ")
                append("In guidance mode, vibration grows stronger as you approach the intersection.")
            }
        }

    /** Numerically detected roots, peaks and troughs across all curves. */
    val landmarks: List<Landmark> by lazy { detectLandmarks() }

    private fun detectLandmarks(): List<Landmark> {
        val found = mutableListOf<Landmark>()
        guidanceTarget?.let { found.add(it) }
        val steps = 600
        val dx = (xMax - xMin) / steps
        for (curve in curves) {
            var prevY = curve.f(xMin)
            var prevSlope = 0f
            for (i in 1..steps) {
                val x = xMin + i * dx
                val y = curve.f(x)
                val slope = (y - prevY) / dx
                if (prevY != 0f && y != 0f && prevY * y < 0 && kotlin.math.abs(y) < (yMax - yMin)) {
                    found.add(
                        Landmark(x, 0f, "${curve.label} crosses zero near x = ${x.round1()}", Landmark.Kind.ROOT)
                    )
                }
                if (i > 1 && prevSlope > 0 && slope < 0) {
                    found.add(Landmark(x - dx, prevY, "Peak at x = ${(x - dx).round1()}, y = ${prevY.round1()}", Landmark.Kind.PEAK))
                }
                if (i > 1 && prevSlope < 0 && slope > 0) {
                    found.add(Landmark(x - dx, prevY, "Trough at x = ${(x - dx).round1()}, y = ${prevY.round1()}", Landmark.Kind.TROUGH))
                }
                prevY = y
                prevSlope = slope
            }
        }
        return found
    }
}

private fun Float.round1(): String {
    val r = kotlin.math.round(this * 10) / 10
    return if (r == kotlin.math.round(r)) r.toInt().toString() else r.toString()
}

data class WaveConcept(
    override val id: String,
    override val subject: Subject,
    override val title: String,
    override val spokenIntro: String
) : Concept {
    override val instructions: String =
        "Use the buttons at the bottom to raise or lower frequency and amplitude. " +
            "Drag a finger across the wave to feel its shape. " +
            "Press play wave to hear and feel the actual wave."
}
