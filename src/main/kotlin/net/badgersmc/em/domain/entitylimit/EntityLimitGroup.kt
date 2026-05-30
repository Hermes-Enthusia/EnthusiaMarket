package net.badgersmc.em.domain.entitylimit

import org.bukkit.entity.EntityType

/**
 * Describes how many entities of each type (and in total) are allowed
 * within a market region. Configured per region-kind in
 * [net.badgersmc.em.config.EnthusiaMarketConfig.entitylimits].
 *
 * `-1` is the unlimited sentinel: a cap of `-1` for any dimension means
 * no restriction applies to that dimension.
 *
 * @property perType  Per-[EntityType] cap. Missing key → uncapped for that type.
 * @property total    Overall entity ceiling across all types. `-1` = unlimited.
 * @property extras   Per-[EntityType] *additive* allowance that stalls may carry
 *                    (set via [net.badgersmc.em.domain.stall.Stall.extraEntities]).
 *                    These are applied on top of the base cap during limit checks.
 */
data class EntityLimitGroup(
    val perType: Map<EntityType, Int>,
    val total: Int,
    val extras: Map<EntityType, Int>,
) {

    /**
     * Returns `true` when the entity placement would violate this group's caps.
     *
     * Both per-type and total caps are evaluated; either violation causes the
     * method to return `true`. Extra allowances ([extraOfType], [extraTotal])
     * are added to the respective base caps before comparison so that stalls
     * with elevated permissions can accommodate more entities.
     *
     * @param type           The [EntityType] being spawned / counted.
     * @param currentOfType  How many entities of [type] already exist in the region.
     * @param currentTotal   How many entities (all types) already exist in the region.
     * @param extraOfType    Stall-specific additive cap bonus for [type].
     * @param extraTotal     Stall-specific additive cap bonus for the total count.
     * @return `true` if the next entity would exceed any applicable cap.
     */
    fun isOverLimit(
        type: EntityType,
        currentOfType: Int,
        currentTotal: Int,
        extraOfType: Int = 0,
        extraTotal: Int = 0,
    ): Boolean {
        // Total cap check — -1 means unlimited.
        if (total >= 0) {
            val effectiveTotalCap = total + extraTotal
            if (currentTotal >= effectiveTotalCap) return true
        }

        // Per-type cap check — absent key treated as uncapped.
        val typeCap = perType[type]
        if (typeCap != null && typeCap >= 0) {
            val effectiveTypeCap = typeCap + extraOfType
            if (currentOfType >= effectiveTypeCap) return true
        }

        return false
    }

    companion object {
        /** Sentinel that imposes no entity restrictions whatsoever. */
        val UNLIMITED = EntityLimitGroup(emptyMap(), -1, emptyMap())
    }
}
