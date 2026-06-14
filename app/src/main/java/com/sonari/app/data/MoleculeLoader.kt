package com.sonari.app.data

import com.sonari.app.model.Atom
import com.sonari.app.model.Bond
import com.sonari.app.model.MoleculeGraph
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MoleculeLoader {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // PubChem PUG REST base URL.
    private const val BASE = "https://pubchem.ncbi.nlm.nih.gov/rest/pug"

    // Three offline fallbacks always available without network.
    val FALLBACKS: Map<String, MoleculeGraph> = mapOf(
        "water" to water(),
        "caffeine" to caffeine(),
        "aspirin" to aspirin()
    )

    fun load(name: String): Result<MoleculeGraph> {
        // Check offline fallbacks first (case-insensitive).
        val key = name.trim().lowercase()
        FALLBACKS[key]?.let { return Result.success(it) }

        return try {
            val cid = fetchCid(name) ?: return Result.failure(
                IllegalArgumentException("Molecule \"$name\" not found on PubChem")
            )
            val graph = fetchRecord(cid)
            if (graph.atoms.isEmpty()) Result.failure(IllegalStateException("Empty atom list from PubChem"))
            else Result.success(graph)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchCid(name: String): Long? {
        val url = "$BASE/compound/name/${name.trim().replace(" ", "%20")}/cids/JSON"
        val body = get(url) ?: return null
        val json = JSONObject(body)
        return json.optJSONObject("IdentifierList")
            ?.optJSONArray("CID")
            ?.getLong(0)
    }

    private fun fetchRecord(cid: Long): MoleculeGraph {
        val url = "$BASE/compound/cid/$cid/record/JSON?record_type=2d"
        val body = get(url) ?: throw IllegalStateException("No record for CID $cid")
        return parseRecord(JSONObject(body))
    }

    private fun parseRecord(json: JSONObject): MoleculeGraph {
        // PC_Compounds is a JSON array in PubChem record format.
        val compound = try {
            json.getJSONArray("PC_Compounds").getJSONObject(0)
        } catch (e: Exception) {
            json.getJSONObject("PC_Compounds")
        }

        // Atom elements (element numbers, 1=H 6=C 7=N 8=O …).
        val atomSection = compound.getJSONObject("atoms")
        val atomicNums = atomSection.getJSONArray("element")
        val elements = (0 until atomicNums.length()).map { atomicNumToSymbol(atomicNums.getInt(it)) }

        // 2D coordinates.
        val coordsSection = compound
            .getJSONArray("coords")
            .getJSONObject(0)
            .getJSONArray("conformers")
            .getJSONObject(0)
        val xs = coordsSection.getJSONArray("x")
        val ys = coordsSection.getJSONArray("y")

        val rawXs = (0 until xs.length()).map { xs.getDouble(it) }
        val rawYs = (0 until ys.length()).map { ys.getDouble(it) }

        val atoms = buildAtoms(elements, rawXs, rawYs)

        // Bonds.
        val bonds = mutableListOf<Bond>()
        val bondSection = compound.optJSONObject("bonds")
        if (bondSection != null) {
            val aid1 = bondSection.getJSONArray("aid1")
            val aid2 = bondSection.getJSONArray("aid2")
            val order = bondSection.optJSONArray("order")
            for (i in 0 until aid1.length()) {
                bonds += Bond(
                    fromIndex = aid1.getInt(i) - 1,
                    toIndex = aid2.getInt(i) - 1,
                    order = order?.optInt(i, 1) ?: 1
                )
            }
        }

        return MoleculeGraph(atoms = atoms, bonds = bonds)
    }

    private fun buildAtoms(elements: List<String>, rawXs: List<Double>, rawYs: List<Double>): List<Atom> {
        if (rawXs.isEmpty()) return emptyList()
        val xMin = rawXs.min(); val xMax = rawXs.max()
        val yMin = rawYs.min(); val yMax = rawYs.max()
        val xRange = (xMax - xMin).let { if (it < 1e-9) 1.0 else it }
        val yRange = (yMax - yMin).let { if (it < 1e-9) 1.0 else it }
        val margin = 0.1
        return elements.indices.map { i ->
            Atom(
                element = elements[i],
                normX = (margin + (rawXs[i] - xMin) / xRange * (1.0 - 2 * margin)).coerceIn(0.0, 1.0),
                normY = (margin + (rawYs[i] - yMin) / yRange * (1.0 - 2 * margin)).coerceIn(0.0, 1.0)
            )
        }
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }

    private fun atomicNumToSymbol(num: Int): String = when (num) {
        1 -> "H"; 5 -> "B"; 6 -> "C"; 7 -> "N"; 8 -> "O"
        9 -> "F"; 15 -> "P"; 16 -> "S"; 17 -> "Cl"; 35 -> "Br"
        53 -> "I"; else -> num.toString()
    }

    // ─── Hardcoded offline fallbacks ─────────────────────────────────────────

    private fun water() = MoleculeGraph(
        atoms = listOf(
            Atom("O", 0.50, 0.42),
            Atom("H", 0.30, 0.65),
            Atom("H", 0.70, 0.65)
        ),
        bonds = listOf(Bond(0, 1), Bond(0, 2))
    )

    private fun caffeine() = MoleculeGraph(
        // Purine ring system (fused pyrimidine + imidazole)
        // 6-ring: C0-N1-C2-N3-C4-N5-C0 (clockwise)
        // 5-ring: N5-C6-N7-C8-C9-N1 (fused, extending above the 6-ring)
        // Substituents: 2 carbonyls (O10,O11), 3 methyls (C12,C13,C14)
        atoms = listOf(
            Atom("C", 0.50, 0.37), // 0: C0 — 6-ring, carbonyl (C=O11), bonds to N1 & N5
            Atom("N", 0.66, 0.46), // 1: N1 — 6-ring, methyl C13, bonds to C0, C2, C9
            Atom("C", 0.66, 0.64), // 2: C2 — 6-ring, methyl C12, bonds to N1, N3
            Atom("N", 0.50, 0.73), // 3: N3 — 6-ring, bonds to C2, C4
            Atom("C", 0.34, 0.64), // 4: C4 — 6-ring, carbonyl C=O10, bonds to N3, N5
            Atom("N", 0.34, 0.46), // 5: N5 — 6-ring/5-ring bridge, bonds to C4, C0, C6
            Atom("C", 0.34, 0.30), // 6: C6 — 5-ring imidazole, bonds to N5, N7
            Atom("N", 0.42, 0.20), // 7: N7 — 5-ring imidazole, methyl C14, bonds to C6, C8
            Atom("C", 0.55, 0.23), // 8: C8 — 5-ring imidazole, bonds to N7, C9
            Atom("C", 0.66, 0.33), // 9: C9 — 5-ring imidazole, bonds to C8, N1
            Atom("O", 0.34, 0.78), // 10: O10 — carbonyl double-bond to C4
            Atom("O", 0.50, 0.23), // 11: O11 — carbonyl double-bond to C0
            Atom("C", 0.82, 0.64), // 12: C12 — methyl on C2
            Atom("C", 0.78, 0.40), // 13: C13 — methyl on N1
            Atom("C", 0.42, 0.05)  // 14: C14 — methyl on N7
        ),
        bonds = listOf(
            Bond(0, 1), Bond(1, 2), Bond(2, 3), Bond(3, 4), Bond(4, 5),
            Bond(5, 0), Bond(5, 6), Bond(6, 7), Bond(7, 8), Bond(8, 9),
            Bond(9, 1), Bond(4, 10, 2), Bond(0, 11, 2),
            Bond(2, 12), Bond(1, 13), Bond(7, 14)
        )
    )

    private fun aspirin() = MoleculeGraph(
        // Benzene ring (C0-C1-C3-C5-C4-C2-C0) with acetyloxy at C0 and carboxyl at C3
        // Ring order: 0→1→3→5→4→2→0 (not sequential — follows actual bond connectivity)
        atoms = listOf(
            Atom("C", 0.50, 0.35), // 0: C0 — ring pos 1, acetyloxy C6 attached
            Atom("C", 0.67, 0.45), // 1: C1 — ring pos 2
            Atom("C", 0.33, 0.45), // 2: C2 — ring pos 6
            Atom("C", 0.67, 0.65), // 3: C3 — ring pos 3, carboxyl C11 attached
            Atom("C", 0.33, 0.65), // 4: C4 — ring pos 5
            Atom("C", 0.50, 0.75), // 5: C5 — ring pos 4
            Atom("C", 0.50, 0.19), // 6: C6 — acetyloxy benzylic carbon
            Atom("O", 0.38, 0.12), // 7: O7 — acetyloxy ether bridge
            Atom("C", 0.25, 0.19), // 8: C8 — acetyl carbonyl carbon
            Atom("O", 0.25, 0.32), // 9: O9 — acetyl carbonyl oxygen (double)
            Atom("O", 0.12, 0.15), // 10: O10 — acetyl oxygen/methyl
            Atom("C", 0.84, 0.65), // 11: C11 — carboxyl carbon
            Atom("O", 0.84, 0.78), // 12: O12 — carboxyl carbonyl oxygen (double)
            Atom("O", 0.98, 0.60)  // 13: O13 — carboxyl hydroxyl
        ),
        bonds = listOf(
            Bond(0, 1), Bond(0, 2), Bond(1, 3), Bond(2, 4), Bond(3, 5),
            Bond(4, 5), Bond(0, 6), Bond(6, 7), Bond(7, 8), Bond(8, 9, 2),
            Bond(8, 10), Bond(3, 11), Bond(11, 12, 2), Bond(11, 13)
        )
    )
}
