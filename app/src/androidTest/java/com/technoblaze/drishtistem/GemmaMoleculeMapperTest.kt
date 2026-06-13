package com.technoblaze.drishtistem

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.technoblaze.drishtistem.core.vision.GemmaMoleculeMapper
import com.technoblaze.drishtistem.model.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Verifies the JSON → MoleculeConcept parsing and 2D layout without needing the
 * 3.4 GB Gemma model — feeds canned model output, as if Gemma had returned it.
 * (org.json is part of the Android runtime, so this runs as an instrumented test.)
 */
@RunWith(AndroidJUnit4::class)
class GemmaMoleculeMapperTest {

    @Test
    fun parsesWaterWithFencedJson() {
        val raw = """
            Here is the structure:
            ```json
            {"type":"molecule","name":"water","atoms":[{"element":"O"},{"element":"H"},{"element":"H"}],
             "bonds":[{"from":0,"to":1,"order":1},{"from":0,"to":2,"order":1}]}
            ```
        """.trimIndent()

        val json = GemmaMoleculeMapper.extractJson(raw)
        assertNotNull("JSON should be extracted from fenced prose", json)

        val molecule = GemmaMoleculeMapper.moleculeFromJson(json!!)
        assertEquals(3, molecule.atoms.size)
        assertEquals(2, molecule.bonds.size)
        assertEquals(Element.OXYGEN, molecule.atoms[0].element)
        assertEquals(Element.HYDROGEN, molecule.atoms[1].element)
        assertTrue(molecule.title.contains("Water", ignoreCase = true))

        // The most-connected atom (oxygen) should sit near the centre.
        val o = molecule.atoms[0]
        assertTrue("central atom x near 0.5", abs(o.x - 0.5f) < 0.05f)
        assertTrue("central atom y near 0.42", abs(o.y - 0.42f) < 0.05f)
    }

    @Test
    fun parsesCarbonDioxideBondOrders() {
        val raw = """{"type":"molecule","name":"carbon dioxide",
            "atoms":[{"element":"C"},{"element":"O"},{"element":"O"}],
            "bonds":[{"from":0,"to":1,"order":2},{"from":0,"to":2,"order":2}]}"""
        val molecule = GemmaMoleculeMapper.moleculeFromJson(GemmaMoleculeMapper.extractJson(raw)!!)
        assertEquals(3, molecule.atoms.size)
        assertEquals(Element.CARBON, molecule.atoms[0].element)
        assertTrue("double bonds preserved", molecule.bonds.all { it.order == 2 })
    }

    @Test
    fun unknownElementFallsBackToGeneric() {
        val raw = """{"type":"molecule","name":"mystery",
            "atoms":[{"element":"Xx"},{"element":"C"}],"bonds":[{"from":0,"to":1,"order":1}]}"""
        val molecule = GemmaMoleculeMapper.moleculeFromJson(GemmaMoleculeMapper.extractJson(raw)!!)
        assertEquals(Element.GENERIC, molecule.atoms[0].element)
        // The real symbol is preserved in the spoken role.
        assertTrue(molecule.atoms[0].role.contains("Xx"))
    }

    @Test
    fun parsesGraphPoints() {
        val raw = """{"type":"graph","points":[0,2,4,6,8,10]}"""
        val graph = GemmaMoleculeMapper.graphFromJson(GemmaMoleculeMapper.extractJson(raw)!!)
        val f = graph.curves.first().f
        // Rising samples: end clearly above start.
        assertTrue(f(10f) > f(0f) + 4f)
    }
}
