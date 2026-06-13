package com.technoblaze.drishtistem.data

import com.technoblaze.drishtistem.model.Concept

/**
 * Holds the most recently scanned concept (a molecule or a graph) in memory so
 * the upload screen can hand it to the right explorer through navigation. Scans
 * are transient by design — nothing is persisted, keeping chosen images private.
 */
object ScannedConceptStore {
    @Volatile
    var current: Concept? = null
}
