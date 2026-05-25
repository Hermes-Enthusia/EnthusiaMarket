package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * TDD-84: Item-based payment tests for ContainerTradeService.
 *
 * These tests cover the new item-based payment flow where:
 * - BUY: player gives sell items to container, receives payment items (costItem deserialized) from container
 * - SELL: player gives payment items to container, receives sell items from container
 * - Tax is deducted from seller proceeds when enabled
 *
 * These tests should FAIL (red) until the item-based payment implementation is added.
 */
class ContainerTradeServiceItemPaymentTest {

    private val playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")

    /** Sample shop with item-based costItem for item-payment tests. */
    private fun itemShop(
        stallId: String = "stall_01",
        frozen: Boolean = false,
        sellAmount: Int = 1,
        costAmount: Int = 1,
        sellItemBase64: String = "sellItemBase64",
        costItemBase64: String = "costItemBase64"
    ): Shop = Shop(
        stallId = stallId,
        owner = ownerUuid,
        signWorld = "world", signX = 100, signY = 64, signZ = 200,
        containerWorld = "world", containerX = 0, containerY = 0, containerZ = 0,
        sellItem = sellItemBase64, sellAmount = sellAmount,
        costItem = costItemBase64, costAmount = costAmount,
        frozen = frozen
    )

    /** Sample owned stall. */
    private fun sampleStall(owner: UUID = ownerUuid): Stall = Stall(
        id = StallId("stall_01"),
        regionId = "stall_01",
        world = "world",
        state = StallState.OWNED,
        owner = OwnerRef.solo(owner),
        ownerSince = java.time.Instant.now(),
        winningBid = 1000L,
        rentTerms = RentTerms.formula(0.01)
    )

    /**
     * Build a [ContainerTradeService] with overridden [getContainer] and [deserializeStack]
     * so tests don't need a real Bukkit runtime.
     *
     * @param sellStack the ItemStack to return when deserializing sellItem
     * @param costStack the ItemStack to return when deserializing costItem
     */
    private fun buildItemService(
        stallRepo: StallRepository = mockk(relaxed = true),
        economy: EconomyProvider = mockk(relaxed = true),
        guildProvider: GuildProvider? = null,
        config: EnthusiaMarketConfig = mockk(relaxed = true),
        sellStack: ItemStack = mockk(relaxed = true),
        costStack: ItemStack = mockk(relaxed = true),
        mockContainer: Container = mockk(relaxed = true)
    ): ContainerTradeService {
        return object : ContainerTradeService(stallRepo, economy, guildProvider, mockk<Logger>(relaxed = true), config) {
            override fun deserializeStack(base64: String): ItemStack? {
                return when (base64) {
                    "sellItemBase64" -> sellStack
                    "costItemBase64" -> costStack
                    else -> null
                }
            }
            override fun getContainer(shop: Shop): Container? = mockContainer
        }
    }

    // ===== 1. BUY: payment items move from container to player =====

    @Test
    fun `buy trade moves payment items from container to player`() {
        val shop = itemShop(sellAmount = 5, costAmount = 3)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        // Owner can afford (has enough balance for economy check path)
        every { economy.balance(ownerUuid) } returns 1000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns false
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "nearest"

        // Sell stack: what player gives to container
        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 5
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 5
        }

        // Cost stack: what player receives from container (payment items)
        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 3
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 3
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        every { playerInv.containsAtLeast(any<ItemStack>(), any()) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)

        val containerInv = mockk<Inventory>(relaxed = true)
        // Container accepts sell items
        every { containerInv.addItem(any()) } returns hashMapOf()
        // Container has enough cost (payment) items
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(3)) } returns true

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeBuy(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Success, "Expected Success but got $result")

        // Verify: sell items moved from player to container
        verify { playerInv.removeItem(any()) }
        verify { containerInv.addItem(any()) }

        // Verify: payment items (costStack) moved from container to player
        verify { containerInv.removeItem(any()) }
        verify { playerInv.addItem(any()) }
    }

    // ===== 2. BUY: fails when container lacks payment items =====

    @Test
    fun `buy trade fails when container lacks payment items`() {
        val shop = itemShop(sellAmount = 5, costAmount = 10)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(ownerUuid) } returns 1000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns false
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "nearest"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 5
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 5
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 10
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 10
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        every { playerInv.containsAtLeast(any<ItemStack>(), any()) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player

        val containerInv = mockk<Inventory>(relaxed = true)
        // Container does NOT have enough cost (payment) items
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(10)) } returns false

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeBuy(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Failure, "Expected Failure but got $result")
        assertTrue(
            (result as ContainerTradeResult.Failure).reason.contains("can't afford", ignoreCase = true),
            "Expected 'can't afford' but got: ${result.reason}"
        )
    }

    // ===== 3. SELL: payment items move from player to container =====

    @Test
    fun `sell trade moves payment items from player to container`() {
        val shop = itemShop(sellAmount = 2, costAmount = 5)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(playerUuid) } returns 1000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns false
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "nearest"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 2
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 2
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 5
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 5
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        // Player has enough payment (cost) items
        every { playerInv.containsAtLeast(any<ItemStack>(), eq(5)) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)

        val containerInv = mockk<Inventory>(relaxed = true)
        // Container has sell items in stock
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(2)) } returns true
        // Player can receive sell items
        every { playerInv.addItem(any()) } returns hashMapOf()

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeSell(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Success, "Expected Success but got $result")

        // Verify: sell items moved from container to player
        verify { containerInv.removeItem(any()) }
        verify { playerInv.addItem(any()) }

        // Verify: payment items (costStack) moved from player to container
        verify { playerInv.removeItem(any()) }
        verify { containerInv.addItem(any()) }
    }

    // ===== 4. SELL: fails when player lacks payment items =====

    @Test
    fun `sell trade fails when player lacks payment items`() {
        val shop = itemShop(sellAmount = 2, costAmount = 50)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(playerUuid) } returns 1000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns false
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "nearest"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 2
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 2
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 50
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 50
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        // Player does NOT have enough payment (cost) items
        every { playerInv.containsAtLeast(any<ItemStack>(), eq(50)) } returns false

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player

        val containerInv = mockk<Inventory>(relaxed = true)
        // Container has sell items in stock
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(2)) } returns true

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeSell(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Failure, "Expected Failure but got $result")
        assertTrue(
            (result as ContainerTradeResult.Failure).reason.contains("don't have", ignoreCase = true),
            "Expected 'don't have' but got: ${result.reason}"
        )
    }

    // ===== 5. Tax deducted from seller proceeds when enabled =====

    @Test
    fun `tax is deducted from seller proceeds when enabled`() {
        // Use a shop where costAmount=100, taxPct=0.02 => tax=2
        val shop = itemShop(sellAmount = 1, costAmount = 100)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(ownerUuid) } returns 10000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns true
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "nearest"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 1
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 1
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 100
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 100
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        every { playerInv.containsAtLeast(any<ItemStack>(), any()) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)

        val containerInv = mockk<Inventory>(relaxed = true)
        every { containerInv.addItem(any()) } returns hashMapOf()
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(100)) } returns true

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeBuy(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Success, "Expected Success but got $result")

        // Verify: tax was computed and deducted
        // With taxEnabled=true, taxPct=0.02, costAmount=100:
        // tax = round(100 * 0.02) = 2
        // The implementation should call ShopTaxCalculator.computeTax or equivalent
        // and deduct from the payment. We verify the economy deposit reflects tax deduction.
        // Owner should receive costAmount - tax = 98 (not 100)
        verify { economy.deposit(playerUuid, 98L) }
    }

    // ===== 6. Tax is zero when disabled =====

    @Test
    fun `tax is zero when disabled`() {
        val shop = itemShop(sellAmount = 1, costAmount = 100)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(ownerUuid) } returns 10000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns false
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "nearest"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 1
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 1
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 100
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 100
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        every { playerInv.containsAtLeast(any<ItemStack>(), any()) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)

        val containerInv = mockk<Inventory>(relaxed = true)
        every { containerInv.addItem(any()) } returns hashMapOf()
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(100)) } returns true

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeBuy(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Success, "Expected Success but got $result")

        // With tax disabled, no tax should be deducted
        // Player should receive full costAmount = 100
        verify { economy.deposit(playerUuid, 100L) }
    }

    // ===== 7. Tax rounding up (ceiling) =====

    @Test
    fun `tax rounding up works correctly`() {
        // costAmount=33, taxPct=0.02 => raw=0.66, ceil=1
        val shop = itemShop(sellAmount = 1, costAmount = 33)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(ownerUuid) } returns 10000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns true
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "up"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 1
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 1
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 33
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 33
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        every { playerInv.containsAtLeast(any<ItemStack>(), any()) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)

        val containerInv = mockk<Inventory>(relaxed = true)
        every { containerInv.addItem(any()) } returns hashMapOf()
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(33)) } returns true

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeBuy(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Success, "Expected Success but got $result")

        // tax = ceil(33 * 0.02) = ceil(0.66) = 1
        // Player receives 33 - 1 = 32
        verify { economy.deposit(playerUuid, 32L) }
    }

    // ===== 8. Tax rounding down (floor) =====

    @Test
    fun `tax rounding down works correctly`() {
        // costAmount=33, taxPct=0.02 => raw=0.66, floor=0
        val shop = itemShop(sellAmount = 1, costAmount = 33)

        val stallRepo = mockk<StallRepository>(relaxed = true)
        every { stallRepo.findById(StallId("stall_01")) } returns sampleStall()

        val economy = mockk<EconomyProvider>(relaxed = true)
        every { economy.balance(ownerUuid) } returns 10000L

        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns true
        every { config.shop.taxPct } returns 0.02
        every { config.shop.taxRounding } returns "down"

        val sellStack = mockk<ItemStack>(relaxed = true)
        every { sellStack.amount } returns 1
        every { sellStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 1
        }

        val costStack = mockk<ItemStack>(relaxed = true)
        every { costStack.amount } returns 33
        every { costStack.clone() } returns mockk<ItemStack>(relaxed = true) {
            every { amount } returns 33
        }

        val playerInv = mockk<PlayerInventory>(relaxed = true)
        every { playerInv.containsAtLeast(any<ItemStack>(), any()) } returns true

        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns playerInv

        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayer(playerUuid) } returns player
        every { Bukkit.getPluginManager() } returns mockk(relaxed = true)

        val containerInv = mockk<Inventory>(relaxed = true)
        every { containerInv.addItem(any()) } returns hashMapOf()
        every { containerInv.containsAtLeast(any<ItemStack>(), eq(33)) } returns true

        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv

        val service = buildItemService(
            stallRepo = stallRepo,
            economy = economy,
            config = config,
            sellStack = sellStack,
            costStack = costStack,
            mockContainer = container
        )

        val result = service.executeBuy(shop, playerUuid)

        assertTrue(result is ContainerTradeResult.Success, "Expected Success but got $result")

        // tax = floor(33 * 0.02) = floor(0.66) = 0
        // Player receives 33 - 0 = 33
        verify { economy.deposit(playerUuid, 33L) }
    }
}
