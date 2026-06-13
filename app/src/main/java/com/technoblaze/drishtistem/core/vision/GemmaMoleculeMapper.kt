package com.technoblaze.drishtistem.core.vision

import com.technoblaze.drishtistem.model.Atom
import com.technoblaze.drishtistem.model.Bond
import com.technoblaze.drishtistem.model.Curve
import com.technoblaze.drishtistem.model.Element
import com.technoblaze.drishtistem.model.GraphConcept
import com.technoblaze.drishtistem.model.MoleculeConcept
import com.technoblaze.drishtistem.model.Subject
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Turns the structured JSON that [GemmaVision] coaxes out of Gemma 3n into the
 * app's existing [MoleculeConcept] / [GraphConcept] types, so the standard
 * MoleculeScreen and GraphExplorerScreen render scanned content unchanged.
 *
 * Gemma supplies elements and connectivity but not reliable 2D coordinates, so
 * [layout] computes positions: ring atoms are placed as a regular polygon; all
 * other atoms are placed BFS-outward from the ring. Acyclic molecules use the
 * centre-fan fallback. Bond output from the model is deduplicated and capped at
 * each element's chemical valence before layout to eliminate spurious cross-bonds.
 */
object GemmaMoleculeMapper {

    private const val X_MAX = 10f
    private const val Y_MAX = 10f

    /** Extract the first JSON object from raw model text (tolerates ``` fences and prose). */
    fun extractJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    fun moleculeFromJson(obj: JSONObject): MoleculeConcept {
        val name = obj.optString("name").ifBlank { "Scanned molecule" }
        val atomsJson = obj.getJSONArray("atoms")
        require(atomsJson.length() > 0) { "no atoms" }

        val symbols = ArrayList<String>(atomsJson.length())
        val elements = ArrayList<Element>(atomsJson.length())
        for (i in 0 until atomsJson.length()) {
            val sym = atomsJson.getJSONObject(i).optString("element").trim()
            symbols.add(sym)
            elements.add(Element.fromSymbol(sym))
        }

        val rawBonds = ArrayList<Bond>()
        if (obj.has("bonds")) {
            val bondsJson = obj.getJSONArray("bonds")
            for (i in 0 until bondsJson.length()) {
                val b = bondsJson.getJSONObject(i)
                val from = b.optInt("from", -1)
                val to = b.optInt("to", -1)
                if (from in elements.indices && to in elements.indices && from != to) {
                    val order = b.optInt("order", 1).coerceIn(1, 3)
                    rawBonds.add(Bond(from, to, order = order))
                }
            }
        }

        // Remove duplicate bonds and cap at max chemical valence before layout.
        val bonds = cleanBonds(elements, rawBonds)

        val positions = layout(elements.size, bonds)
        val atoms = elements.indices.map { i ->
            val e = elements[i]
            val role = if (e == Element.GENERIC) {
                "${symbols[i].ifBlank { "unknown" }} atom"
            } else {
                "${e.elementName.lowercase()} atom"
            }
            Atom(e, positions[i].first, positions[i].second, role)
        }

        val counts = elements.filter { it != Element.GENERIC }
            .groupingBy { it.elementName }.eachCount()
        val countText = counts.entries.joinToString(", ") { (el, n) ->
            "$n ${el.lowercase()}${if (n == 1) "" else " atoms"}"
        }.ifBlank { "${elements.size} atoms" }

        val displayName = name.replaceFirstChar { it.uppercase() }
        return MoleculeConcept(
            id = "scanned",
            subject = Subject.CHEMISTRY,
            title = "Scanned: $displayName",
            spokenIntro = "Scanned molecule. $displayName. $countText, with ${bonds.size} bonds.",
            formulaSpoken = displayName,
            atoms = atoms,
            bonds = bonds,
            structureSummary = "$displayName. It has $countText, joined by ${bonds.size} bonds. " +
                "Move your finger to each atom to feel its element, and slide along the bonds between them."
        )
    }

    fun graphFromJson(obj: JSONObject): GraphConcept {
        val arr = obj.getJSONArray("points")
        require(arr.length() >= 2) { "too few points" }
        val samples = FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        return GraphConcept(
            id = "scanned",
            subject = Subject.MATHS,
            title = "Scanned graph",
            spokenIntro = "Scanned graph.",
            curves = listOf(Curve("scanned curve") { x -> sampleAt(samples, x) }),
            xMin = 0f, xMax = X_MAX,
            yMin = 0f, yMax = Y_MAX,
            xAxisLabel = "position",
            yAxisLabel = "relative height"
        )
    }

    private fun sampleAt(samples: FloatArray, x: Float): Float {
        val t = (x / X_MAX).coerceIn(0f, 1f)
        val pos = t * (samples.size - 1)
        val i = pos.toInt()
        if (i >= samples.size - 1) return samples.last()
        val frac = pos - i
        return samples[i] * (1 - frac) + samples[i + 1] * frac
    }

    /** Maximum covalent bonds each element can form. */
    private fun maxValence(e: Element): Int = when (e) {
        Element.HYDROGEN, Element.FLUORINE, Element.CHLORINE, Element.SODIUM -> 1
        Element.OXYGEN, Element.SULFUR -> 2
        Element.NITROGEN -> 3
        Element.CARBON -> 4
        Element.PHOSPHORUS -> 5
        Element.GENERIC -> 4
    }

    /**
     * Deduplicate bonds (a→b == b→a) then drop any bond that would exceed either
     * atom's max valence. Bonds between heavier atoms are kept first so that
     * the carbon skeleton and polar bonds survive; excess C–H bonds are shed last.
     */
    private fun cleanBonds(elements: List<Element>, raw: List<Bond>): List<Bond> {
        val seen = HashSet<Long>()
        val unique = raw.filter { b ->
            val key = minOf(b.fromIndex, b.toIndex).toLong() * 10_000 +
                      maxOf(b.fromIndex, b.toIndex)
            seen.add(key)
        }
        val degree = IntArray(elements.size)
        return unique
            .sortedByDescending { b ->
                elements[b.fromIndex].atomicNumber + elements[b.toIndex].atomicNumber
            }
            .filter { b ->
                val ok = degree[b.fromIndex] < maxValence(elements[b.fromIndex]) &&
                         degree[b.toIndex]   < maxValence(elements[b.toIndex])
                if (ok) { degree[b.fromIndex]++; degree[b.toIndex]++ }
                ok
            }
    }

    /**
     * Place atoms in normalised 0..1 coordinates.
     *
     * Strategy for ring molecules (detected by [findSmallestRing]):
     *   1. Place the ring atoms as a regular polygon centred at (0.5, 0.5).
     *   2. BFS outward from every ring atom: each unplaced neighbour is positioned
     *      in the direction already established by its parent, spreading multiple
     *      substituents ±30° around that direction. This correctly handles chains
     *      like –COOH without them bunching up on one side.
     *
     * Acyclic molecules fall through to the centre-fan heuristic.
     */
    fun layout(n: Int, bonds: List<Bond>): List<Pair<Float, Float>> {
        if (n == 1) return listOf(0.5f to 0.5f)

        val degree = IntArray(n)
        val adj = Array(n) { mutableListOf<Int>() }
        for (b in bonds) {
            degree[b.fromIndex]++; degree[b.toIndex]++
            adj[b.fromIndex].add(b.toIndex); adj[b.toIndex].add(b.fromIndex)
        }

        if (bonds.isEmpty()) {
            return (0 until n).map { i ->
                val x = if (n == 1) 0.5f else 0.15f + 0.7f * i / (n - 1)
                x to 0.5f
            }
        }

        val pos = arrayOfNulls<Pair<Float, Float>>(n)
        val ring = findSmallestRing(n, adj)

        if (ring != null && ring.size >= 4) {
            val ringRadius = if (ring.size <= 6) 0.27f else 0.34f
            val ringSet = ring.toHashSet()
            ring.forEachIndexed { i, atom ->
                val angle = 2.0 * PI * i / ring.size - PI / 2
                pos[atom] = (0.5f + ringRadius * cos(angle)).toFloat().coerceIn(0.08f, 0.92f) to
                            (0.5f + ringRadius * sin(angle)).toFloat().coerceIn(0.08f, 0.92f)
            }

            // BFS outward from ring: propagate direction so chains (e.g. –COOH) extend
            // in a straight line rather than bunching.
            val outAngle = DoubleArray(n)
            for (ringAtom in ring) {
                val (rx, ry) = pos[ringAtom]!!
                outAngle[ringAtom] = atan2((ry - 0.5).toDouble(), (rx - 0.5).toDouble())
            }
            val bfsVisited = BooleanArray(n) { pos[it] != null }
            val bfsQueue = ArrayDeque<Int>().also { q -> ring.forEach(q::add) }

            while (bfsQueue.isNotEmpty()) {
                val cur = bfsQueue.removeFirst()
                val (cx, cy) = pos[cur]!!
                val unplaced = adj[cur].filter { !bfsVisited[it] }
                val count = unplaced.size
                unplaced.forEachIndexed { idx, nb ->
                    bfsVisited[nb] = true
                    val spread = if (count == 1) 0.0
                                 else Math.toRadians(-30.0 + 60.0 * idx / (count - 1))
                    val angle = outAngle[cur] + spread
                    outAngle[nb] = angle
                    pos[nb] = (cx + 0.18f * cos(angle)).toFloat().coerceIn(0.05f, 0.95f) to
                              (cy + 0.18f * sin(angle)).toFloat().coerceIn(0.05f, 0.95f)
                    bfsQueue.add(nb)
                }
            }
        }

        // Fallback / acyclic: centre-fan for atoms not yet placed.
        val center = (0 until n).filter { pos[it] == null }.maxByOrNull { degree[it] }
        if (center != null) {
            pos[center] = 0.5f to 0.42f
            val neighbors = adj[center].distinct().filter { pos[it] == null }
            val radius = 0.30f
            val count = neighbors.size
            neighbors.forEachIndexed { idx, atom ->
                val angle = if (count <= 3) {
                    val span = Math.toRadians(120.0)
                    val startA = Math.toRadians(210.0)
                    startA + if (count == 1) span / 2 else span * idx / (count - 1)
                } else {
                    2.0 * PI * idx / count - PI / 2
                }
                pos[atom] = (0.5f + radius * cos(angle).toFloat()).coerceIn(0.1f, 0.9f) to
                            (0.42f + radius * sin(angle).toFloat()).coerceIn(0.1f, 0.9f)
            }
        }

        // Any still-unplaced atoms: offset from a placed neighbour or a bottom row.
        var leftover = 0
        for (i in 0 until n) {
            if (pos[i] != null) continue
            val anchor = adj[i].firstOrNull { pos[it] != null }
            pos[i] = if (anchor != null) {
                val (ax, ay) = pos[anchor]!!
                ((ax + 0.16f).coerceIn(0.1f, 0.9f)) to ((ay + 0.16f).coerceIn(0.1f, 0.9f))
            } else {
                val x = 0.2f + 0.15f * leftover++
                x.coerceIn(0.1f, 0.9f) to 0.85f
            }
        }
        return pos.map { it!! }
    }

    /**
     * DFS cycle search — returns the atom indices of the SMALLEST simple ring
     * (≥ 4 atoms), or null if the graph is acyclic. Preferring the smallest ring
     * finds the true aromatic ring (e.g. benzene's 6-cycle) rather than a spurious
     * larger cycle that can arise from LLM-generated cross-bonds.
     */
    private fun findSmallestRing(n: Int, adj: Array<MutableList<Int>>): List<Int>? {
        var best: List<Int>? = null
        val visited = BooleanArray(n)
        val path = ArrayDeque<Int>()
        val inPath = BooleanArray(n)

        fun dfs(node: Int, parent: Int) {
            visited[node] = true
            path.addLast(node)
            inPath[node] = true
            for (nb in adj[node]) {
                if (nb == parent) continue
                if (inPath[nb]) {
                    val idx = path.indexOf(nb)
                    val cycle = path.subList(idx, path.size).toList()
                    if (cycle.size >= 4 && (best == null || cycle.size < best!!.size)) best = cycle
                } else if (!visited[nb]) {
                    dfs(nb, node)
                }
            }
            path.removeLast()
            inPath[node] = false
        }

        for (s in 0 until n) if (!visited[s]) dfs(s, -1)
        return best
    }
}
