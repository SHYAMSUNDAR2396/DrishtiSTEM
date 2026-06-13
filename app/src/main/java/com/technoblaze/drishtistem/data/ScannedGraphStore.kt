package com.technoblaze.drishtistem.data

import com.technoblaze.drishtistem.model.GraphConcept

/**
 * Holds the most recently scanned graph in memory so the camera screen can hand
 * it to the explorer through navigation. Scans are transient by design — nothing
 * is persisted to disk, keeping captured images private to the session.
 */
object ScannedGraphStore {
    @Volatile
    var current: GraphConcept? = null
}
