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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Turns the structured JSON that [GemmaVision] coaxes out of Gemma 3n into the
 * app's existing [MoleculeConcept] / [GraphConcept] types, so the standard
 * MoleculeScreen and GraphExplorerScreen render scanned content unchanged.
 *
 * Gemma supplies elements and connectivity but not reliable 2D coordinates, so
 * [layout] computes positions: the most-connected atom sits near the centre and
 * its neighbours fan out around it — good enough for small molecules.
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

        val bonds = ArrayList<Bond>()
        if (obj.has("bonds")) {
            val bondsJson = obj.getJSONArray("bonds")
            for (i in 0 until bondsJson.length()) {
                val b = bondsJson.getJSONObject(i)
                val from = b.optInt("from", -1)
                val to = b.optInt("to", -1)
                if (from in elements.indices && to in elements.indices && from != to) {
                    val order = b.optInt("order", 1).coerceIn(1, 3)
                    bonds.add(Bond(from, to, order = order))
                }
            }
        }

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

    /**
     * Place atoms in normalised 0..1 coordinates.
     *
     * If the bond graph contains a ring of ≥ 4 atoms (e.g. benzene), those atoms
     * are arranged as a regular polygon and substituents radiate outward from their
     * ring atom. For acyclic molecules the previous centre-fan heuristic is used:
     * the highest-degree atom sits near the centre and its neighbours fan out
     * (downward arc for ≤3, full circle for 4+). Remaining atoms are tucked beside
     * an already-placed neighbour.
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
        val ring = findLargestRing(n, adj)

        if (ring != null && ring.size >= 4) {
            // Arrange ring atoms as a regular polygon centred at (0.5, 0.5).
            val ringRadius = if (ring.size <= 6) 0.27f else 0.34f
            val ringSet = ring.toHashSet()
            ring.forEachIndexed { i, atom ->
                val angle = 2.0 * PI * i / ring.size - PI / 2
                pos[atom] = (0.5f + ringRadius * cos(angle)).toFloat().coerceIn(0.08f, 0.92f) to
                            (0.5f + ringRadius * sin(angle)).toFloat().coerceIn(0.08f, 0.92f)
            }
            // Substituents: point radially outward from their ring neighbour.
            val substituteRadius = 0.18f
            for (i in 0 until n) {
                if (pos[i] != null) continue
                val ringNb = adj[i].firstOrNull { it in ringSet } ?: continue
                val (rx, ry) = pos[ringNb]!!
                val dx = rx - 0.5f
                val dy = ry - 0.5f
                val len = hypot(dx, dy).coerceAtLeast(0.01f)
                pos[i] = (rx + dx / len * substituteRadius).coerceIn(0.05f, 0.95f) to
                         (ry + dy / len * substituteRadius).coerceIn(0.05f, 0.95f)
            }
        }

        // Fallback / acyclic: centre-fan for any atoms not yet placed.
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

    /** DFS cycle search — returns atom indices of the largest simple ring, or null. */
    private fun findLargestRing(n: Int, adj: Array<MutableList<Int>>): List<Int>? {
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
                    if (best == null || cycle.size > best!!.size) best = cycle
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
