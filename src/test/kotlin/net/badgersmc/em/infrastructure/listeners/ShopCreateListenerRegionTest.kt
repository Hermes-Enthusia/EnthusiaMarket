package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.em.config.EnthusiaMarketConfig
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Container
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.util.UUID
import kotlin.test.Test

/**
 * TDD-82: Region check tests for ShopCreateListener.
 * Verifies that the container must be inside the same stall region as the sign.
 */
class ShopCreateListenerRegionTest {

    private val testUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val worldName = "world"
    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    private val config = mockk<EnthusiaMarketConfig>(relaxed = true) {
        every { shop.containerLinkMaxDistance } returns 10
        every { shop.taxEnabled } returns true
        every { shop.taxPct } returns 0.02
        every { shop.taxRounding } returns "nearest"
    }

    /** Set up a sign block with a container at the attached face. */
    private fun wallSignBlock(
        signBlock: Block,
        containerBlock: Block,
        facing: BlockFace = BlockFace.NORTH
    ): Block {
        val wallSign: Sign = mockk(relaxed = true)
        every { signBlock.state } returns wallSign
        every { signBlock.type } returns Material.OAK_WALL_SIGN

        val wallData: WallSign = mockk(relaxed = true)
        every { wallData.facing } returns facing
        every { signBlock.blockData } returns wallData

        every { signBlock.getRelative(facing.oppositeFace) } returns containerBlock

        val signLoc = signBlock.location
        val contLoc: Location = mockk(relaxed = true)
        val offset = when (facing) {
            BlockFace.NORTH -> Triple(0, 0, 1)
            BlockFace.SOUTH -> Triple(0, 0, -1)
            BlockFace.EAST -> Triple(-1, 0, 0)
            BlockFace.WEST -> Triple(1, 0, 0)
            else -> Triple(0, 0, 1)
        }
        every { contLoc.world?.name } returns worldName
        every { contLoc.blockX } returns signLoc.blockX + offset.first
        every { contLoc.blockY } returns signLoc.blockY + offset.second
        every { contLoc.blockZ } returns signLoc.blockZ + offset.third
        every { containerBlock.location } returns contLoc

        return signBlock
    }

    /** Create a mock Location at a fixed position. */
    private fun location(x: Int = 100, y: Int = 64, z: Int = 200): Location {
        val loc: Location = mockk(relaxed = true)
        every { loc.world?.name } returns worldName
        every { loc.blockX } returns x
        every { loc.blockY } returns y
        every { loc.blockZ } returns z
        return loc
    }

    /** Create a mocked container block. */
    private fun containerBlock(): Block {
        val block: Block = mockk(relaxed = true)
        val container: Container = mockk(relaxed = true)
        every { block.state } returns container
        return block
    }

    private fun sampleStall(id: String, ownerUuid: UUID = testUuid): Stall = Stall(
        id = StallId(id),
        regionId = id,
        world = worldName,
        state = StallState.OWNED,
        owner = OwnerRef.solo(ownerUuid),
        ownerSince = null,
        winningBid = 1000L,
        rentTerms = RentTerms.formula(0.01)
    )

    /**
     * Create a listener that returns different stalls based on location coordinates.
     * Uses a map of (x,y,z) -> Stall to determine which stall a location falls in.
     */
    private fun listenerWithLocationBasedStalls(
        stallRepo: StallRepository = mockk(relaxed = true),
        shopRepo: ShopRepository = mockk(relaxed = true),
        locationStallMap: Map<Triple<Int, Int, Int>, Stall>,
        menuFactory: ((Player, Container, String, Location, ShopRepository) -> Menu)? = { _, _, _, _, _ ->
            mockk<Menu>(relaxed = true)
        }
    ): ShopCreateListener {
        val listener = ShopCreateListener(stallRepo, shopRepo, config, menuFactory = menuFactory)
        return object : ShopCreateListener(stallRepo, shopRepo, config, menuFactory = menuFactory) {
            override fun findStallAt(location: Location): Stall? {
                val key = Triple(location.blockX, location.blockY, location.blockZ)
                return locationStallMap[key]
            }
            override fun canManageStall(stall: Stall, player: Player): Boolean = true
        }
    }

    /** Helper: create a PlayerInteractEvent. */
    private fun interactEvent(
        player: Player,
        action: Action = Action.LEFT_CLICK_BLOCK,
        block: Block
    ): PlayerInteractEvent {
        val loc = block.location ?: location()
        every { block.location } returns loc
        return PlayerInteractEvent(player, action, null as ItemStack?, block, BlockFace.NORTH, EquipmentSlot.HAND)
    }

    @Test
    fun `container in different stall region is rejected`() {
        val player: Player = mockk(relaxed = true)
        every { player.uniqueId } returns testUuid
        every { player.isSneaking } returns true

        val signBlock: Block = mockk(relaxed = true)
        val signLoc = location(100, 64, 200)
        every { signBlock.location } returns signLoc
        val contBlock = containerBlock()
        wallSignBlock(signBlock, contBlock)

        val stallA = sampleStall("stall_A")
        val stallB = sampleStall("stall_B")

        // Sign at (100,64,200) -> stall_A, container at (100,64,201) -> stall_B
        val locationStallMap = mapOf(
            Triple(100, 64, 200) to stallA,
            Triple(100, 64, 201) to stallB
        )

        val shopRepo = mockk<ShopRepository>(relaxed = true)
        every { shopRepo.findBySign(worldName, 100, 64, 200) } returns null

        val listener = listenerWithLocationBasedStalls(
            shopRepo = shopRepo,
            locationStallMap = locationStallMap
        )

        val event = interactEvent(player, block = signBlock)
        listener.onSignInteract(event)

        // Container is in a different stall — event should NOT be denied (error message shown)
        assert(event.useInteractedBlock() != Event.Result.DENY) {
            "Event should not be denied when container is in a different stall region"
        }
        verify { player.sendMessage("§cContainer must be inside the same stall region") }
    }

    @Test
    fun `container outside any stall region is rejected`() {
        val player: Player = mockk(relaxed = true)
        every { player.uniqueId } returns testUuid
        every { player.isSneaking } returns true

        val signBlock: Block = mockk(relaxed = true)
        val signLoc = location(100, 64, 200)
        every { signBlock.location } returns signLoc
        val contBlock = containerBlock()
        wallSignBlock(signBlock, contBlock)

        val stallA = sampleStall("stall_A")

        // Sign at (100,64,200) -> stall_A, container at (100,64,201) -> no stall
        val locationStallMap = mapOf(
            Triple(100, 64, 200) to stallA
        )

        val shopRepo = mockk<ShopRepository>(relaxed = true)
        every { shopRepo.findBySign(worldName, 100, 64, 200) } returns null

        val listener = listenerWithLocationBasedStalls(
            shopRepo = shopRepo,
            locationStallMap = locationStallMap
        )

        val event = interactEvent(player, block = signBlock)
        listener.onSignInteract(event)

        assert(event.useInteractedBlock() != Event.Result.DENY) {
            "Event should not be denied when container is outside any stall region"
        }
        verify { player.sendMessage("§cContainer must be inside the same stall region") }
    }

    @Test
    fun `container in same stall region proceeds`() {
        val player: Player = mockk(relaxed = true)
        every { player.uniqueId } returns testUuid
        every { player.isSneaking } returns true

        val signBlock: Block = mockk(relaxed = true)
        val signLoc = location(100, 64, 200)
        every { signBlock.location } returns signLoc
        val contBlock = containerBlock()
        wallSignBlock(signBlock, contBlock)

        val stallA = sampleStall("stall_A")

        // Both sign at (100,64,200) and container at (100,64,201) -> stall_A
        val locationStallMap = mapOf(
            Triple(100, 64, 200) to stallA,
            Triple(100, 64, 201) to stallA
        )

        val shopRepo = mockk<ShopRepository>(relaxed = true)
        every { shopRepo.findBySign(worldName, 100, 64, 200) } returns null

        val listener = listenerWithLocationBasedStalls(
            shopRepo = shopRepo,
            locationStallMap = locationStallMap
        )

        val event = interactEvent(player, block = signBlock)
        listener.onSignInteract(event)

        // Container is in the same stall — event is denied (proceeds to menu)
        assert(event.useInteractedBlock() == Event.Result.DENY) {
            "Event should be denied when container is in the same stall region"
        }
    }
}
