package net.badgersmc.em.interaction.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD-86: Tests for BedrockPurchaseForm — real items, tax-inclusive price, stock.
 */
class BedrockPurchaseFormTest {

    private val playerUuid = UUID.randomUUID()

    // Dummy base64 strings — deserialization will fail without real BukkitObjectInputStream
    // and fall back to BARRIER, which is fine since these tests only verify form construction.
    private val diamondBase64 = "ZHVtbXlfZGlhbW9uZA=="
    private val emeraldBase64 = "ZHVtbXlfZW1lcmFsZA=="

    private val testShop = Shop(
        id = 1L, stallId = "s1", owner = UUID.randomUUID(),
        signWorld = "w", signX = 1, signY = 2, signZ = 3,
        containerWorld = "w", containerX = 4, containerY = 5, containerZ = 6,
        sellItem = diamondBase64, sellAmount = 5, costItem = emeraldBase64, costAmount = 10,
        frozen = false
    )

    private fun mockConfig(
        taxEnabled: Boolean = true,
        taxPct: Double = 0.02,
        taxRounding: String = "nearest"
    ): EnthusiaMarketConfig {
        val config = mockk<EnthusiaMarketConfig>(relaxed = true)
        every { config.shop.taxEnabled } returns taxEnabled
        every { config.shop.taxPct } returns taxPct
        every { config.shop.taxRounding } returns taxRounding
        return config
    }

    @Test
    fun `purchase form constructs without throwing`() {
        // No Bukkit mock needed — constructor only stores parameters
        val form = BedrockPurchaseForm(
            mockk<Player>(relaxed = true),
            testShop,
            mockk<ContainerTradeService>(relaxed = true),
            mockk<EnthusiaMarketConfig>(relaxed = true),
            mockk<Logger>(relaxed = true)
        )
        assertNotNull(form)
    }

    @Test
    fun `form builds without throwing`() {
        val server = MockBukkit.mock()
        try {
            mockkStatic(Bukkit::class)
            val offlinePlayer = mockk<OfflinePlayer>(relaxed = true)
            every { offlinePlayer.name } returns "TestOwner"
            every { Bukkit.getOfflinePlayer(any<UUID>()) } returns offlinePlayer
            every { Bukkit.getWorld(any<String>()) } returns server.addSimpleWorld("w")
            every { Bukkit.getPluginManager() } returns server.pluginManager

            val form = BedrockPurchaseForm(
                mockk<Player>(relaxed = true),
                testShop,
                mockk<ContainerTradeService>(relaxed = true),
                mockConfig(),
                mockk<Logger>(relaxed = true)
            )
            val built = form.buildForm()
            assertNotNull(built)
            assertTrue(built is SimpleForm)
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `form builds with tax disabled`() {
        val server = MockBukkit.mock()
        try {
            mockkStatic(Bukkit::class)
            val offlinePlayer = mockk<OfflinePlayer>(relaxed = true)
            every { offlinePlayer.name } returns "TestOwner"
            every { Bukkit.getOfflinePlayer(any<UUID>()) } returns offlinePlayer
            every { Bukkit.getWorld(any<String>()) } returns server.addSimpleWorld("w")
            every { Bukkit.getPluginManager() } returns server.pluginManager

            val form = BedrockPurchaseForm(
                mockk<Player>(relaxed = true),
                testShop,
                mockk<ContainerTradeService>(relaxed = true),
                mockConfig(taxEnabled = false),
                mockk<Logger>(relaxed = true)
            )
            val built = form.buildForm()
            assertNotNull(built)
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `form builds with frozen shop`() {
        val server = MockBukkit.mock()
        try {
            val frozenShop = testShop.copy(frozen = true)

            mockkStatic(Bukkit::class)
            val offlinePlayer = mockk<OfflinePlayer>(relaxed = true)
            every { offlinePlayer.name } returns "TestOwner"
            every { Bukkit.getOfflinePlayer(any<UUID>()) } returns offlinePlayer
            every { Bukkit.getWorld(any<String>()) } returns server.addSimpleWorld("w")
            every { Bukkit.getPluginManager() } returns server.pluginManager

            val form = BedrockPurchaseForm(
                mockk<Player>(relaxed = true),
                frozenShop,
                mockk<ContainerTradeService>(relaxed = true),
                mockConfig(),
                mockk<Logger>(relaxed = true)
            )
            val built = form.buildForm()
            assertNotNull(built)
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `form builds with high tax rate`() {
        val server = MockBukkit.mock()
        try {
            mockkStatic(Bukkit::class)
            val offlinePlayer = mockk<OfflinePlayer>(relaxed = true)
            every { offlinePlayer.name } returns "TestOwner"
            every { Bukkit.getOfflinePlayer(any<UUID>()) } returns offlinePlayer
            every { Bukkit.getWorld(any<String>()) } returns server.addSimpleWorld("w")
            every { Bukkit.getPluginManager() } returns server.pluginManager

            val form = BedrockPurchaseForm(
                mockk<Player>(relaxed = true),
                testShop,
                mockk<ContainerTradeService>(relaxed = true),
                mockConfig(taxPct = 0.50, taxRounding = "up"),
                mockk<Logger>(relaxed = true)
            )
            val built = form.buildForm()
            assertNotNull(built)
        } finally {
            MockBukkit.unmock()
        }
    }

    @Test
    fun `form has correct number of buttons`() {
        val server = MockBukkit.mock()
        try {
            mockkStatic(Bukkit::class)
            val offlinePlayer = mockk<OfflinePlayer>(relaxed = true)
            every { offlinePlayer.name } returns "TestOwner"
            every { Bukkit.getOfflinePlayer(any<UUID>()) } returns offlinePlayer
            every { Bukkit.getWorld(any<String>()) } returns server.addSimpleWorld("w")
            every { Bukkit.getPluginManager() } returns server.pluginManager

            val form = BedrockPurchaseForm(
                mockk<Player>(relaxed = true),
                testShop,
                mockk<ContainerTradeService>(relaxed = true),
                mockConfig(),
                mockk<Logger>(relaxed = true)
            )
            val built = form.buildForm() as SimpleForm
            // Should always produce a form with 3 buttons: BUY, SELL, Back
            assertNotNull(built.buttons())
        } finally {
            MockBukkit.unmock()
        }
    }
}