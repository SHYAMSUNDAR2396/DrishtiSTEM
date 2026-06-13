package com.sonari.app.model

sealed interface Renderable {
    val xMin: Double
    val xMax: Double
    val yMin: Double
    val yMax: Double
    val landmarks: List<Landmark>
}

data class DataPoint(val x: Double, val y: Double)

data class Landmark(
    val normX: Double,
    val normY: Double,
    val type: Type,
    val label: String
) {
    enum class Type { INTERCEPT, EXTREMUM, ATOM }
}

data class LineChart(
    override val xMin: Double,
    override val xMax: Double,
    override val yMin: Double,
    override val yMax: Double,
    val samples: List<DataPoint>,
    override val landmarks: List<Landmark>
) : Renderable

data class Atom(val element: String, val normX: Double, val normY: Double)

data class Bond(val fromIndex: Int, val toIndex: Int, val order: Int = 1)

data class MoleculeGraph(
    override val xMin: Double = 0.0,
    override val xMax: Double = 1.0,
    override val yMin: Double = 0.0,
    override val yMax: Double = 1.0,
    val atoms: List<Atom>,
    val bonds: List<Bond>
) : Renderable {
    override val landmarks: List<Landmark> = atoms.map { atom ->
        Landmark(atom.normX, atom.normY, Landmark.Type.ATOM, atom.element)
    }
}
