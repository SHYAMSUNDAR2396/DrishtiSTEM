package com.technoblaze.drishtistem.data

import com.technoblaze.drishtistem.model.Atom
import com.technoblaze.drishtistem.model.Bond
import com.technoblaze.drishtistem.model.Concept
import com.technoblaze.drishtistem.model.Curve
import com.technoblaze.drishtistem.model.Element
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.Landmark
import com.technoblaze.drishtistem.model.MoleculeConcept
import com.technoblaze.drishtistem.model.Subject
import com.technoblaze.drishtistem.model.WaveConcept
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Built-in offline concept library for the MVP demo. */
object ConceptRepository {

    val concepts: List<Concept> = listOf(
        // ----- Mathematics -----
        GraphConcept(
            id = "maths_line",
            subject = Subject.MATHS,
            title = "Straight line: y = x",
            spokenIntro = "Straight line, y equals x. A line rising steadily from the bottom left to the top right.",
            curves = listOf(Curve("y equals x") { it }),
            xMin = -5f, xMax = 5f, yMin = -5f, yMax = 5f
        ),
        GraphConcept(
            id = "maths_parabola",
            subject = Subject.MATHS,
            title = "Parabola: y = x²",
            spokenIntro = "Parabola, y equals x squared. A U shaped curve with its lowest point at the origin.",
            curves = listOf(Curve("y equals x squared") { it * it }),
            xMin = -3f, xMax = 3f, yMin = -1f, yMax = 9f
        ),
        GraphConcept(
            id = "maths_sine",
            subject = Subject.MATHS,
            title = "Sine wave: y = sin(x)",
            spokenIntro = "Sine wave, y equals sine of x. A smooth wave going up and down between one and minus one.",
            curves = listOf(Curve("sine of x") { sin(it) }),
            xMin = -6.5f, xMax = 6.5f, yMin = -1.5f, yMax = 1.5f
        ),
        GraphConcept(
            id = "maths_intersection",
            subject = Subject.MATHS,
            title = "Two lines crossing",
            spokenIntro = "Two straight lines. y equals 2 x plus 1, and y equals minus x plus 4. They cross at one point. Find it.",
            curves = listOf(
                Curve("first line, 2 x plus 1") { 2 * it + 1 },
                Curve("second line, minus x plus 4") { -it + 4 }
            ),
            xMin = -2f, xMax = 5f, yMin = -3f, yMax = 9f,
            guidanceTarget = Landmark(
                1f, 3f,
                "Intersection found. The lines cross at x equals 1, y equals 3.",
                Landmark.Kind.INTERSECTION
            )
        ),

        GraphConcept(
            id = "maths_cosine",
            subject = Subject.MATHS,
            title = "Cosine wave: y = cos(x)",
            spokenIntro = "Cosine wave, y equals cosine of x. Like sine, it oscillates between one and minus one, but starts at its peak at x equals zero.",
            curves = listOf(Curve("cosine of x") { cos(it) }),
            xMin = -6.5f, xMax = 6.5f, yMin = -1.5f, yMax = 1.5f,
            guidanceTarget = Landmark(
                0f, 1f,
                "Peak found. Cosine reaches its maximum of 1 at x equals 0.",
                Landmark.Kind.PEAK
            )
        ),
        GraphConcept(
            id = "maths_absolute",
            subject = Subject.MATHS,
            title = "Absolute value: y = |x|",
            spokenIntro = "Absolute value of x. A V shape: the left branch rises to the left, the right branch rises to the right. Both sides meet at the origin. The value is always zero or positive.",
            curves = listOf(Curve("absolute value of x") { abs(it) }),
            xMin = -5f, xMax = 5f, yMin = -0.5f, yMax = 5f,
            guidanceTarget = Landmark(
                0f, 0f,
                "Corner found. The vertex of the V is at the origin, x equals zero, y equals zero.",
                Landmark.Kind.TROUGH
            )
        ),

        // ----- Physics -----
        GraphConcept(
            id = "physics_uniform",
            subject = Subject.PHYSICS,
            title = "Uniform motion (distance–time)",
            spokenIntro = "Distance time graph of a bus moving at constant speed, 2 meters per second. " +
                "Constant speed feels like a straight, even slope.",
            curves = listOf(Curve("distance of the bus") { 2 * it }),
            xMin = 0f, xMax = 10f, yMin = 0f, yMax = 20f,
            xAxisLabel = "time in seconds", yAxisLabel = "distance in meters"
        ),
        GraphConcept(
            id = "physics_acceleration",
            subject = Subject.PHYSICS,
            title = "Acceleration (distance–time)",
            spokenIntro = "Distance time graph of a car accelerating from rest at 1 meter per second squared. " +
                "Notice the slope getting steeper: the vibration grows stronger as the car speeds up.",
            curves = listOf(Curve("distance of the car") { 0.5f * it * it }),
            xMin = 0f, xMax = 8f, yMin = 0f, yMax = 32f,
            xAxisLabel = "time in seconds", yAxisLabel = "distance in meters"
        ),
        GraphConcept(
            id = "physics_velocity",
            subject = Subject.PHYSICS,
            title = "Velocity–time: speeding up, then steady",
            spokenIntro = "Velocity time graph. A cyclist speeds up for 4 seconds, then holds a steady 8 meters per second. " +
                "Feel the rising part vibrate, then go calm when speed is constant.",
            curves = listOf(Curve("velocity of the cyclist") { if (it < 4f) 2 * it else 8f }),
            xMin = 0f, xMax = 10f, yMin = 0f, yMax = 12f,
            xAxisLabel = "time in seconds", yAxisLabel = "velocity in meters per second"
        ),
        WaveConcept(
            id = "physics_wavelab",
            subject = Subject.PHYSICS,
            title = "Wave Lab",
            spokenIntro = "Wave lab. Change frequency and amplitude, then hear and feel the wave you built."
        ),

        // ----- Chemistry -----
        MoleculeConcept(
            id = "chem_water",
            subject = Subject.CHEMISTRY,
            title = "Water (H₂O)",
            spokenIntro = "Water molecule, H 2 O. One oxygen atom bonded to two hydrogen atoms in a bent shape.",
            formulaSpoken = "H 2 O",
            atoms = listOf(
                Atom(Element.OXYGEN, 0.50f, 0.38f, "central oxygen atom"),
                Atom(Element.HYDROGEN, 0.25f, 0.62f, "left hydrogen atom"),
                Atom(Element.HYDROGEN, 0.75f, 0.62f, "right hydrogen atom")
            ),
            bonds = listOf(Bond(0, 1), Bond(0, 2)),
            structureSummary = "Water: one oxygen atom bonded to two hydrogen atoms. " +
                "The bonds form a bent shape with an angle of about 104.5 degrees."
        ),
        MoleculeConcept(
            id = "chem_co2",
            subject = Subject.CHEMISTRY,
            title = "Carbon dioxide (CO₂)",
            spokenIntro = "Carbon dioxide, C O 2. A straight line molecule: carbon in the middle, oxygen on both sides.",
            formulaSpoken = "C O 2",
            atoms = listOf(
                Atom(Element.CARBON, 0.50f, 0.50f, "central carbon atom"),
                Atom(Element.OXYGEN, 0.18f, 0.50f, "left oxygen atom"),
                Atom(Element.OXYGEN, 0.82f, 0.50f, "right oxygen atom")
            ),
            bonds = listOf(Bond(0, 1, order = 2), Bond(0, 2, order = 2)),
            structureSummary = "Carbon dioxide: one carbon atom double bonded to two oxygen atoms, " +
                "all in a straight line. A linear molecule."
        ),
        MoleculeConcept(
            id = "chem_methane",
            subject = Subject.CHEMISTRY,
            title = "Methane (CH₄)",
            spokenIntro = "Methane, C H 4. One carbon atom surrounded by four hydrogen atoms.",
            formulaSpoken = "C H 4",
            atoms = listOf(
                Atom(Element.CARBON, 0.50f, 0.50f, "central carbon atom"),
                Atom(Element.HYDROGEN, 0.50f, 0.18f, "top hydrogen atom"),
                Atom(Element.HYDROGEN, 0.20f, 0.68f, "lower left hydrogen atom"),
                Atom(Element.HYDROGEN, 0.80f, 0.68f, "lower right hydrogen atom"),
                Atom(Element.HYDROGEN, 0.50f, 0.85f, "bottom hydrogen atom")
            ),
            bonds = listOf(Bond(0, 1), Bond(0, 2), Bond(0, 3), Bond(0, 4)),
            structureSummary = "Methane: one carbon atom bonded to four hydrogen atoms. " +
                "In three dimensions the hydrogens spread out evenly in a tetrahedron, " +
                "with bond angles of 109.5 degrees."
        ),
        MoleculeConcept(
            id = "chem_nacl",
            subject = Subject.CHEMISTRY,
            title = "Salt (NaCl)",
            spokenIntro = "Sodium chloride, common salt. A sodium ion and a chloride ion held together by an ionic bond.",
            formulaSpoken = "N a C l",
            atoms = listOf(
                Atom(Element.SODIUM, 0.30f, 0.50f, "sodium ion, positively charged"),
                Atom(Element.CHLORINE, 0.70f, 0.50f, "chloride ion, negatively charged")
            ),
            bonds = listOf(Bond(0, 1, kind = Bond.Kind.IONIC)),
            structureSummary = "Sodium chloride: sodium gives one electron to chlorine, " +
                "making a positive sodium ion and a negative chloride ion. " +
                "Opposite charges attract: that attraction is the ionic bond."
        ),
        MoleculeConcept(
            id = "chem_ammonia",
            subject = Subject.CHEMISTRY,
            title = "Ammonia (NH₃)",
            spokenIntro = "Ammonia, N H 3. One nitrogen atom bonded to three hydrogen atoms arranged in a triangular pyramid shape.",
            formulaSpoken = "N H 3",
            atoms = listOf(
                Atom(Element.NITROGEN, 0.50f, 0.38f, "central nitrogen atom"),
                Atom(Element.HYDROGEN, 0.22f, 0.65f, "lower left hydrogen atom"),
                Atom(Element.HYDROGEN, 0.78f, 0.65f, "lower right hydrogen atom"),
                Atom(Element.HYDROGEN, 0.50f, 0.80f, "bottom hydrogen atom")
            ),
            bonds = listOf(Bond(0, 1), Bond(0, 2), Bond(0, 3)),
            structureSummary = "Ammonia: one nitrogen atom single bonded to three hydrogen atoms. " +
                "The molecule has a trigonal pyramidal shape with bond angles of about 107 degrees. " +
                "Nitrogen has a lone pair of electrons above the three bonds."
        ),
        MoleculeConcept(
            id = "chem_hcl",
            subject = Subject.CHEMISTRY,
            title = "Hydrogen chloride (HCl)",
            spokenIntro = "Hydrogen chloride, H C l. The simplest acid: one hydrogen atom and one chlorine atom sharing a covalent bond.",
            formulaSpoken = "H C l",
            atoms = listOf(
                Atom(Element.HYDROGEN, 0.30f, 0.50f, "hydrogen atom"),
                Atom(Element.CHLORINE, 0.70f, 0.50f, "chlorine atom")
            ),
            bonds = listOf(Bond(0, 1)),
            structureSummary = "Hydrogen chloride: one hydrogen atom bonded to one chlorine atom. " +
                "It is a polar covalent bond: chlorine is much more electronegative and pulls the shared electrons closer, " +
                "giving chlorine a partial negative charge and hydrogen a partial positive charge."
        )
    )

    fun bySubject(subject: Subject): List<Concept> = concepts.filter { it.subject == subject }

    fun byId(id: String): Concept? = concepts.find { it.id == id }
}
