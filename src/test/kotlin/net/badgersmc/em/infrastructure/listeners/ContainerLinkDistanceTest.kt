package net.badgersmc.em.infrastructure.listeners

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD-81: Unit tests for container link distance validation.
 */
class ContainerLinkDistanceTest {

    @Test
    fun `distance of 1 block is within default max of 3`() {
        assertTrue(isWithinLinkDistance(100, 64, 200, 101, 64, 200, 3))
    }

    @Test
    fun `distance of 5 blocks exceeds default max of 3`() {
        assertFalse(isWithinLinkDistance(100, 64, 200, 105, 64, 200, 3))
    }

    @Test
    fun `diagonal distance within 3 blocks`() {
        // sqrt(4+1+1) = sqrt(6) ≈ 2.45
        assertTrue(isWithinLinkDistance(100, 64, 200, 102, 65, 201, 3))
    }

    @Test
    fun `vertical distance within 3 blocks`() {
        assertTrue(isWithinLinkDistance(100, 64, 200, 100, 67, 200, 3))
    }

    @Test
    fun `exact max distance is accepted`() {
        assertTrue(isWithinLinkDistance(100, 64, 200, 103, 64, 200, 3))
    }

    @Test
    fun `just beyond max distance is rejected`() {
        assertFalse(isWithinLinkDistance(100, 64, 200, 104, 64, 200, 3))
    }

    @Test
    fun `zero distance is accepted`() {
        assertTrue(isWithinLinkDistance(100, 64, 200, 100, 64, 200, 3))
    }

    @Test
    fun `block distance calculation is correct`() {
        assertTrue(blockDistance(0, 0, 0, 3, 4, 0) == 5.0)
    }

    @Test
    fun `custom max distance of 1 works`() {
        assertTrue(isWithinLinkDistance(100, 64, 200, 101, 64, 200, 1))
        assertFalse(isWithinLinkDistance(100, 64, 200, 102, 64, 200, 1))
    }
}
