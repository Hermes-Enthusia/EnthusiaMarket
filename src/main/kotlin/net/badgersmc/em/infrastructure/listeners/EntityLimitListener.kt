package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.application.LimitResolutionService
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.util.BoundingBox

/**
 * Enforces per-stall entity and hanging-entity limits (TDD-221).
 *
 * Two entry points:
 * - [onCreatureSpawn]: blocks mob / entity spawns (e.g. spawn eggs, plugins)
 *   that would exceed the region-kind cap defined in config + any per-stall
 *   [Stall.extraEntities] / [Stall.extraTotal] bonuses.
 * - [onHangingPlace]: same logic for item frames and paintings.
 *
 * Natural spawns (NATURAL / CHUNK_GEN) are intentionally skipped so the
 * listener never interferes with ordinary mob spawning outside stall areas.
 */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class EntityLimitListener(
    private val stallRepository: StallRepository,
    private val limitService: LimitResolutionService,
    private val lang: LangService,
) : Listener {

    // -------------------------------------------------------------------------
    // CreatureSpawnEvent
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        // Skip wild / chunk-generation spawns — these are not player/plugin-induced.
        val reason = event.spawnReason
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL ||
            reason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN
        ) return

        val entity = event.entity
        val location = entity.location

        val stall = findStallAt(location) ?: return

        // Resolve the EntityLimitGroup for the region kind ("default" until
        // regionKind is added to Stall — see TDD-221 notes).
        val limitGroup = limitService.entityLimitFor("default")

        // Count current inhabitants of the stall bounding box.
        val (currentOfType, currentTotal) = countEntities(stall, location, entity.type)

        val extraOfType = stall.extraEntities[entity.type.name] ?: 0
        val extraTotal = stall.extraTotal

        if (limitGroup.isOverLimit(entity.type, currentOfType, currentTotal, extraOfType, extraTotal)) {
            event.isCancelled = true
            // Notify a player-induced spawn (spawn egg, dispenser with player as cause, etc.)
            notifySpawner(event, stall)
        }
    }

    // -------------------------------------------------------------------------
    // HangingPlaceEvent (item frames, paintings)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        val player = event.player ?: return
        val hanging = event.entity
        val location = hanging.location

        val stall = findStallAt(location) ?: return

        val limitGroup = limitService.entityLimitFor("default")

        val (currentOfType, currentTotal) = countEntities(stall, location, hanging.type)

        val extraOfType = stall.extraEntities[hanging.type.name] ?: 0
        val extraTotal = stall.extraTotal

        if (limitGroup.isOverLimit(hanging.type, currentOfType, currentTotal, extraOfType, extraTotal)) {
            event.isCancelled = true
            player.sendMessage(lang.msg("entity_limit.spawn_blocked"))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Locate the stall enclosing [location] by querying WorldGuard's region
     * container for the applicable regions and matching against the stall
     * repository. Same pattern as [SignPlaceListener.findStallAt].
     */
    open fun findStallAt(location: Location): Stall? {
        val world = location.world ?: return null
        val wgWorld = BukkitAdapter.adapt(world)
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(wgWorld) ?: return null

        val regions = regionManager.getApplicableRegions(
            BukkitAdapter.asBlockVector(location)
        )

        for (region in regions) {
            val stall = stallRepository.findByRegion(world.name, region.id)
            if (stall != null) return stall
        }
        return null
    }

    /**
     * Count (a) how many entities of [spawnType] and (b) how many entities in
     * total currently occupy the WorldGuard region bounding box for [stall].
     *
     * The bounding box is derived from the stall's WorldGuard [ProtectedRegion]
     * min/max block vectors via [BukkitAdapter], then passed to
     * [World.getNearbyEntities] which accepts a [BoundingBox].
     *
     * Returns a [Pair] of (countOfType, countTotal).
     */
    open fun countEntities(stall: Stall, location: Location, spawnType: EntityType): Pair<Int, Int> {
        val world = location.world ?: return Pair(0, 0)
        val wgWorld = BukkitAdapter.adapt(world)
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(wgWorld) ?: return Pair(0, 0)
        val region = regionManager.getRegion(stall.regionId) ?: return Pair(0, 0)

        val min = region.minimumPoint
        val max = region.maximumPoint

        // Convert WG BlockVector3 → Bukkit Location bounds → BoundingBox.
        val box = BoundingBox(
            min.getX().toDouble(), min.getY().toDouble(), min.getZ().toDouble(),
            max.getX().toDouble() + 1.0, max.getY().toDouble() + 1.0, max.getZ().toDouble() + 1.0,
        )

        val nearby = world.getNearbyEntities(box)
        val countOfType = nearby.count { it.type == spawnType }
        val countTotal = nearby.size
        return Pair(countOfType, countTotal)
    }

    /**
     * If the spawn was player-induced (e.g. spawn egg), send the entity-limit
     * message to that player. Currently detects the spawner from
     * [CreatureSpawnEvent.spawnReason] heuristics.
     */
    private fun notifySpawner(event: CreatureSpawnEvent, stall: Stall) {
        // Only known player-induced reasons worth notifying about.
        val playerInducedReasons = setOf(
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
            CreatureSpawnEvent.SpawnReason.DISPENSE_EGG,
        )
        if (event.spawnReason !in playerInducedReasons) return

        // There is no direct "spawning player" reference on CreatureSpawnEvent
        // for spawn eggs — the entity's spawner is not exposed cleanly before
        // it's placed. We fall back to finding a nearby player who is looking
        // roughly at the spawn location; this is a best-effort heuristic.
        // A future refactor could hook PlayerInteractEvent to track the egg user.
        val spawnLoc = event.entity.location
        val world = spawnLoc.world ?: return
        world.getNearbyPlayers(spawnLoc, 5.0).firstOrNull()?.let { nearby ->
            nearby.sendMessage(lang.msg("entity_limit.spawn_blocked"))
        }
    }
}
