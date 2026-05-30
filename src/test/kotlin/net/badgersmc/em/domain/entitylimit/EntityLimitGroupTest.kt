package net.badgersmc.em.domain.entitylimit

import org.bukkit.entity.EntityType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [EntityLimitGroup.isOverLimit] — TDD-220.
 *
 * Scenarios verified:
 *   - At cap returns true; under cap returns false.
 *   - A cap of -1 (unlimited) never triggers a limit violation.
 *   - Per-stall extra allowances add to the effective cap.
 *   - Missing [EntityType] key is treated as uncapped for that type.
 *   - [EntityLimitGroup.UNLIMITED] never returns true.
 */
class EntityLimitGroupTest {

    // -----------------------------------------------------------------------
    // Total-cap tests
    // -----------------------------------------------------------------------

    @Test fun `isOverLimit returns false when currentTotal is under total cap`() {
        val group = EntityLimitGroup(emptyMap(), total = 10, emptyMap())
        assertFalse(group.isOverLimit(EntityType.VILLAGER, currentOfType = 0, currentTotal = 9))
    }

    @Test fun `isOverLimit returns true when currentTotal equals total cap`() {
        val group = EntityLimitGroup(emptyMap(), total = 10, emptyMap())
        assertTrue(group.isOverLimit(EntityType.VILLAGER, currentOfType = 0, currentTotal = 10))
    }

    @Test fun `isOverLimit returns true when currentTotal exceeds total cap`() {
        val group = EntityLimitGroup(emptyMap(), total = 5, emptyMap())
        assertTrue(group.isOverLimit(EntityType.ZOMBIE, currentOfType = 0, currentTotal = 7))
    }

    // -----------------------------------------------------------------------
    // Per-type-cap tests
    // -----------------------------------------------------------------------

    @Test fun `isOverLimit returns false when currentOfType is under perType cap`() {
        val group = EntityLimitGroup(mapOf(EntityType.VILLAGER to 3), total = -1, emptyMap())
        assertFalse(group.isOverLimit(EntityType.VILLAGER, currentOfType = 2, currentTotal = 2))
    }

    @Test fun `isOverLimit returns true when currentOfType equals perType cap`() {
        val group = EntityLimitGroup(mapOf(EntityType.VILLAGER to 3), total = -1, emptyMap())
        assertTrue(group.isOverLimit(EntityType.VILLAGER, currentOfType = 3, currentTotal = 3))
    }

    @Test fun `isOverLimit returns false for entity type with no perType entry`() {
        // ZOMBIE has no cap — should never be over-limit regardless of count.
        val group = EntityLimitGroup(mapOf(EntityType.VILLAGER to 2), total = -1, emptyMap())
        assertFalse(group.isOverLimit(EntityType.ZOMBIE, currentOfType = 999, currentTotal = 0))
    }

    // -----------------------------------------------------------------------
    // Unlimited (-1) sentinel
    // -----------------------------------------------------------------------

    @Test fun `isOverLimit always returns false when total is -1 and no perType caps`() {
        val group = EntityLimitGroup(emptyMap(), total = -1, emptyMap())
        assertFalse(group.isOverLimit(EntityType.VILLAGER, currentOfType = 9999, currentTotal = 9999))
    }

    @Test fun `isOverLimit returns false when perType cap is -1 for that type`() {
        val group = EntityLimitGroup(
            mapOf(EntityType.VILLAGER to -1),
            total = 100,
            emptyMap()
        )
        assertFalse(group.isOverLimit(EntityType.VILLAGER, currentOfType = 9999, currentTotal = 50))
    }

    @Test fun `UNLIMITED companion always returns false`() {
        assertFalse(
            EntityLimitGroup.UNLIMITED.isOverLimit(
                EntityType.ZOMBIE, currentOfType = 9999, currentTotal = 9999
            )
        )
    }

    // -----------------------------------------------------------------------
    // Extras add to the effective cap
    // -----------------------------------------------------------------------

    @Test fun `extraOfType raises the per-type effective cap`() {
        // Base cap = 3; stall extra = 2 → effective cap = 5.
        val group = EntityLimitGroup(mapOf(EntityType.VILLAGER to 3), total = -1, emptyMap())
        // currentOfType = 4 would normally be over the 3-cap...
        assertTrue(group.isOverLimit(EntityType.VILLAGER, currentOfType = 4, currentTotal = 4))
        // ...but with extraOfType = 2 it should be fine (4 < 5).
        assertFalse(
            group.isOverLimit(
                EntityType.VILLAGER, currentOfType = 4, currentTotal = 4, extraOfType = 2
            )
        )
    }

    @Test fun `extraTotal raises the total effective cap`() {
        // Base total cap = 5; stall extraTotal = 3 → effective total cap = 8.
        val group = EntityLimitGroup(emptyMap(), total = 5, emptyMap())
        // At 7 without bonus → over limit.
        assertTrue(group.isOverLimit(EntityType.ZOMBIE, currentOfType = 0, currentTotal = 7))
        // At 7 with extraTotal 3 → effective cap 8, not over.
        assertFalse(
            group.isOverLimit(
                EntityType.ZOMBIE, currentOfType = 0, currentTotal = 7, extraTotal = 3
            )
        )
    }

    @Test fun `extras from companion extras field are ignored by isOverLimit itself`() {
        // The 'extras' field on the data class is for stall lookup; isOverLimit
        // takes explicit extra* parameters — the field does NOT auto-apply.
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 2),
            total = -1,
            extras = mapOf(EntityType.VILLAGER to 99), // large field-level extra
        )
        // Without passing extraOfType the field has no effect: 2 hits the cap.
        assertTrue(group.isOverLimit(EntityType.VILLAGER, currentOfType = 2, currentTotal = 2))
    }

    // -----------------------------------------------------------------------
    // Companion object
    // -----------------------------------------------------------------------

    @Test fun `UNLIMITED is a stable singleton with expected shape`() {
        assertSame(EntityLimitGroup.UNLIMITED, EntityLimitGroup.UNLIMITED)
        assertTrue(EntityLimitGroup.UNLIMITED.perType.isEmpty())
        assertTrue(EntityLimitGroup.UNLIMITED.extras.isEmpty())
        // total == -1
        assertFalse(
            EntityLimitGroup.UNLIMITED.isOverLimit(
                EntityType.VILLAGER, currentOfType = 0, currentTotal = 0
            )
        )
    }
}
