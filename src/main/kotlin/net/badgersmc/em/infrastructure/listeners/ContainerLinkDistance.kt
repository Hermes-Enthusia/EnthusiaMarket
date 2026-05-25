package net.badgersmc.em.infrastructure.listeners

import kotlin.math.sqrt

/**
 * Calculates the Euclidean distance between two block positions.
 */
fun blockDistance(
    x1: Int, y1: Int, z1: Int,
    x2: Int, y2: Int, z2: Int
): Double {
    val dx = (x1 - x2).toDouble()
    val dy = (y1 - y2).toDouble()
    val dz = (z1 - z2).toDouble()
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * Checks whether two block positions are within the given max distance.
 */
fun isWithinLinkDistance(
    signX: Int, signY: Int, signZ: Int,
    containerX: Int, containerY: Int, containerZ: Int,
    maxDistance: Int
): Boolean {
    return blockDistance(signX, signY, signZ, containerX, containerY, containerZ) <= maxDistance
}
