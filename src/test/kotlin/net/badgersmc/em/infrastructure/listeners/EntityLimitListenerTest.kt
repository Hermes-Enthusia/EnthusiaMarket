package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.application.LimitResolutionService
import net.badgersmc.em.domain.entitylimit.EntityLimitGroup
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.entity.Hanging
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [EntityLimitListener] (TDD-221).
 *
 * WorldGuard calls are avoided by overriding [EntityLimitListener.findStallAt]
 * and [EntityLimitListener.countEntities] — the same testability pattern used
 * throughout the listener layer.
 *
 * Scenarios:
 *  - spawn below per-type cap → event not cancelled
 *  - spawn at/above per-type cap → event cancelled
 *  - spawn below total cap → event not cancelled
 *  - spawn at/above total cap → event cancelled
 *  - NATURAL spawn reason → skipped (no enforcement)
 *  - outside stall → no enforcement
 *  - HangingPlaceEvent below cap → allowed
 *  - HangingPlaceEvent at/above cap → cancelled + player notified
 */
class EntityLimitListenerTest {

    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private val worldName = "world"

    /** A simple owned stall with no extra-entity bonuses. */
    private fun sampleStall(
        extraEntities: Map<String, Int> = emptyMap(),
        extraTotal: Int = 0,
    ): Stall = Stall(
        id = StallId("stall_01"),
        regionId = "stall_01",
        world = worldName,
        state = StallState.OWNED,
        owner = OwnerRef.solo(UUID.randomUUID()),
        ownerSince = null,
        winningBid = 1000L,
        rentTerms = RentTerms.formula(0.01),
        extraEntities = extraEntities,
        extraTotal = extraTotal,
    )

    /**
     * Build a listener whose [EntityLimitListener.findStallAt] returns [stall]
     * and whose [EntityLimitListener.countEntities] returns [countOfType] and
     * [countTotal]. The [LimitResolutionService] mock is pre-configured to
     * return [limitGroup] for any regionKind.
     */
    private fun listenerWithCounts(
        stall: Stall?,
        limitGroup: EntityLimitGroup,
        countOfType: Int,
        countTotal: Int,
        stallRepo: StallRepository = mockk(relaxed = true),
        lang: LangService = mockk(relaxed = true),
    ): EntityLimitListener {
        val limitService = mockk<LimitResolutionService>(relaxed = true)
        every { limitService.entityLimitFor(any()) } returns limitGroup

        return object : EntityLimitListener(stallRepo, limitService, lang) {
            override fun findStallAt(location: Location): Stall? = stall
            override fun countEntities(stall: Stall, location: Location, spawnType: EntityType): Pair<Int, Int> =
                Pair(countOfType, countTotal)
        }
    }

    /** Create a mocked [LivingEntity] at a fake location. */
    private fun mockEntity(type: EntityType = EntityType.VILLAGER): LivingEntity {
        val loc = mockk<Location>(relaxed = true)
        every { loc.world } returns null
        val entity = mockk<LivingEntity>(relaxed = true)
        every { entity.type } returns type
        every { entity.location } returns loc
        return entity
    }

    /** Create a mocked [Hanging] entity (item frame / painting). */
    private fun mockHanging(type: EntityType = EntityType.ITEM_FRAME): Hanging {
        val loc = mockk<Location>(relaxed = true)
        every { loc.world } returns null
        val entity = mockk<Hanging>(relaxed = true)
        every { entity.type } returns type
        every { entity.location } returns loc
        return entity
    }

    // -------------------------------------------------------------------------
    // CreatureSpawnEvent — per-type cap
    // -------------------------------------------------------------------------

    @Test
    fun `creature spawn below per-type cap is allowed`() {
        // Cap = 3, currently 2 → spawn is fine.
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 3),
            total = -1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 2,
            countTotal = 2,
        )

        val entity = mockEntity(EntityType.VILLAGER)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "Spawn below cap should not be cancelled")
    }

    @Test
    fun `creature spawn at per-type cap is cancelled`() {
        // Cap = 3, currently 3 → blocked.
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 3),
            total = -1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 3,
            countTotal = 3,
        )

        val entity = mockEntity(EntityType.VILLAGER)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertTrue(event.isCancelled, "Spawn at cap should be cancelled")
    }

    @Test
    fun `creature spawn above per-type cap is cancelled`() {
        // Cap = 2, currently 5 → blocked.
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 2),
            total = -1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 5,
            countTotal = 5,
        )

        val entity = mockEntity(EntityType.VILLAGER)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.CUSTOM)
        listener.onCreatureSpawn(event)

        assertTrue(event.isCancelled, "Spawn above cap should be cancelled")
    }

    // -------------------------------------------------------------------------
    // CreatureSpawnEvent — total cap
    // -------------------------------------------------------------------------

    @Test
    fun `creature spawn below total cap is allowed`() {
        // Total cap = 10, currently 9.
        val group = EntityLimitGroup(
            perType = emptyMap(),
            total = 10,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 0,
            countTotal = 9,
        )

        val entity = mockEntity(EntityType.ZOMBIE)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "Spawn below total cap should not be cancelled")
    }

    @Test
    fun `creature spawn at total cap is cancelled`() {
        // Total cap = 5, currently 5.
        val group = EntityLimitGroup(
            perType = emptyMap(),
            total = 5,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 0,
            countTotal = 5,
        )

        val entity = mockEntity(EntityType.ZOMBIE)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertTrue(event.isCancelled, "Spawn at total cap should be cancelled")
    }

    // -------------------------------------------------------------------------
    // CreatureSpawnEvent — natural / chunk-gen reasons are skipped
    // -------------------------------------------------------------------------

    @Test
    fun `NATURAL spawn reason is skipped even when over cap`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.ZOMBIE to 1),
            total = 1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 99,
            countTotal = 99,
        )

        val entity = mockEntity(EntityType.ZOMBIE)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.NATURAL)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "NATURAL spawns must never be cancelled by entity limit")
    }

    @Test
    fun `CHUNK_GEN spawn reason is skipped even when over cap`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.ZOMBIE to 1),
            total = 1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 99,
            countTotal = 99,
        )

        val entity = mockEntity(EntityType.ZOMBIE)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.CHUNK_GEN)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "CHUNK_GEN spawns must never be cancelled by entity limit")
    }

    // -------------------------------------------------------------------------
    // CreatureSpawnEvent — outside stall
    // -------------------------------------------------------------------------

    @Test
    fun `creature spawn outside any stall is not cancelled`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 1),
            total = 1,
            extras = emptyMap(),
        )
        // stall = null → not inside a stall
        val listener = listenerWithCounts(
            stall = null,
            limitGroup = group,
            countOfType = 99,
            countTotal = 99,
        )

        val entity = mockEntity(EntityType.VILLAGER)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "Spawn outside a stall should not be cancelled")
    }

    // -------------------------------------------------------------------------
    // CreatureSpawnEvent — extraEntities per-stall bonus
    // -------------------------------------------------------------------------

    @Test
    fun `per-stall extraEntities bonus raises effective cap`() {
        // Base cap = 3; stall gives +2 extra → effective 5. Currently at 4 → allowed.
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 3),
            total = -1,
            extras = emptyMap(),
        )
        val stallWithBonus = sampleStall(extraEntities = mapOf("VILLAGER" to 2))
        val listener = listenerWithCounts(
            stall = stallWithBonus,
            limitGroup = group,
            countOfType = 4,
            countTotal = 4,
        )

        val entity = mockEntity(EntityType.VILLAGER)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "Spawn within extra-bonus effective cap should not be cancelled")
    }

    @Test
    fun `per-stall extraEntities bonus does not help different entity type`() {
        // Base cap VILLAGER = 2; stall bonus for VILLAGER = 5. But we're spawning ZOMBIE with cap=1.
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.VILLAGER to 2, EntityType.ZOMBIE to 1),
            total = -1,
            extras = emptyMap(),
        )
        val stallWithBonus = sampleStall(extraEntities = mapOf("VILLAGER" to 5))
        val listener = listenerWithCounts(
            stall = stallWithBonus,
            limitGroup = group,
            countOfType = 1, // already at ZOMBIE cap
            countTotal = 1,
        )

        val entity = mockEntity(EntityType.ZOMBIE)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertTrue(event.isCancelled, "ZOMBIE at cap should be cancelled even when VILLAGER has a bonus")
    }

    // -------------------------------------------------------------------------
    // HangingPlaceEvent — item frame / painting
    // -------------------------------------------------------------------------

    @Test
    fun `hanging place below per-type cap is allowed`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.ITEM_FRAME to 5),
            total = -1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 4,
            countTotal = 4,
        )

        val player = mockk<Player>(relaxed = true)
        val hanging = mockHanging(EntityType.ITEM_FRAME)
        val event = HangingPlaceEvent(
            hanging, player,
            mockk(relaxed = true), BlockFace.NORTH, EquipmentSlot.HAND,
        )
        listener.onHangingPlace(event)

        assertFalse(event.isCancelled, "Hanging place below cap should not be cancelled")
    }

    @Test
    fun `hanging place at per-type cap is cancelled and player notified`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.ITEM_FRAME to 5),
            total = -1,
            extras = emptyMap(),
        )
        val lang = mockk<LangService>(relaxed = true)
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 5,
            countTotal = 5,
            lang = lang,
        )

        val player = mockk<Player>(relaxed = true)
        val hanging = mockHanging(EntityType.ITEM_FRAME)
        val event = HangingPlaceEvent(
            hanging, player,
            mockk(relaxed = true), BlockFace.NORTH, EquipmentSlot.HAND,
        )
        listener.onHangingPlace(event)

        assertTrue(event.isCancelled, "Hanging place at cap should be cancelled")
        verify { player.sendMessage(any<net.kyori.adventure.text.Component>()) }
    }

    @Test
    fun `hanging place above per-type cap is cancelled`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.ITEM_FRAME to 3),
            total = -1,
            extras = emptyMap(),
        )
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = group,
            countOfType = 7,
            countTotal = 7,
        )

        val player = mockk<Player>(relaxed = true)
        val hanging = mockHanging(EntityType.ITEM_FRAME)
        val event = HangingPlaceEvent(
            hanging, player,
            mockk(relaxed = true), BlockFace.NORTH, EquipmentSlot.HAND,
        )
        listener.onHangingPlace(event)

        assertTrue(event.isCancelled, "Hanging place above cap should be cancelled")
    }

    @Test
    fun `hanging place outside stall is not cancelled`() {
        val group = EntityLimitGroup(
            perType = mapOf(EntityType.ITEM_FRAME to 1),
            total = 1,
            extras = emptyMap(),
        )
        // stall = null
        val listener = listenerWithCounts(
            stall = null,
            limitGroup = group,
            countOfType = 99,
            countTotal = 99,
        )

        val player = mockk<Player>(relaxed = true)
        val hanging = mockHanging(EntityType.ITEM_FRAME)
        val event = HangingPlaceEvent(
            hanging, player,
            mockk(relaxed = true), BlockFace.NORTH, EquipmentSlot.HAND,
        )
        listener.onHangingPlace(event)

        assertFalse(event.isCancelled, "Hanging place outside a stall should not be cancelled")
    }

    @Test
    fun `UNLIMITED limit group never cancels spawn`() {
        val listener = listenerWithCounts(
            stall = sampleStall(),
            limitGroup = EntityLimitGroup.UNLIMITED,
            countOfType = 9999,
            countTotal = 9999,
        )

        val entity = mockEntity(EntityType.VILLAGER)
        val event = CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.COMMAND)
        listener.onCreatureSpawn(event)

        assertFalse(event.isCancelled, "UNLIMITED group must never cancel spawns")
    }
}
