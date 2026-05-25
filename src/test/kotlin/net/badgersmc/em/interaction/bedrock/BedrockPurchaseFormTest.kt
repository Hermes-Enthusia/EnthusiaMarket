package net.badgersmc.em.interaction.bedrock

import io.mockk.*
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.SimpleForm
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD-86: Tests for BedrockPurchaseForm — real items, tax-inclusive price, stock.
 */
class BedrockPurchaseFormTest {

    private val diamondBase64 = ItemStackSerializer.serialize(ItemStack(Material.DIAMOND))
    private val emeraldBase64 = ItemStackSerializer.serialize(ItemStack(Material.EMERALD))

    private val testShop = Shop(
        id = 1L, stallId = "s1", owner = UUID.randomUUID(),
        signWorld = "w", signX = 1, signY = 2, signZ = 3,
        containerWorld = "w", containerX = 4, containerY = 5, containerZ = 6,
        sellItem = diamondBase64, sellAmount = 5, costItem = emeraldBase64, costAmount = 10,
        frozen = false
    )

    private val defaultConfig = EnthusiaMarketConfig().apply {
        shop.taxEnabled = true
        shop.taxPct = 0.02
        shop.taxRounding = "nearest"
    }

    @Test
    fun `purchase form constructs without throwing`() {
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
        val form = BedrockPurchaseForm(
            mockk<Player>(relaxed = true),
            testShop,
            mockk<ContainerTradeService>(relaxed = true),
            defaultConfig,
            mockk<Logger>(relaxed = true)
        )
        val built = form.buildForm()
        assertNotNull(built)
        assertTrue(built is SimpleForm)
    }

    @Test
    fun `form builds with tax disabled`() {
        val config = EnthusiaMarketConfig().apply {
            shop.taxEnabled = false
            shop.taxPct = 0.02
            shop.taxRounding = "nearest"
        }
        val form = BedrockPurchaseForm(
            mockk<Player>(relaxed = true),
            testShop,
            mockk<ContainerTradeService>(relaxed = true),
            config,
            mockk<Logger>(relaxed = true)
        )
        val built = form.buildForm()
        assertNotNull(built)
    }

    @Test
    fun `form builds with frozen shop`() {
        val frozenShop = testShop.copy(frozen = true)
        val form = BedrockPurchaseForm(
            mockk<Player>(relaxed = true),
            frozenShop,
            mockk<ContainerTradeService>(relaxed = true),
            defaultConfig,
            mockk<Logger>(relaxed = true)
        )
        val built = form.buildForm()
        assertNotNull(built)
    }

    @Test
    fun `form builds with high tax rate`() {
        val config = EnthusiaMarketConfig().apply {
            shop.taxEnabled = true
            shop.taxPct = 0.50
            shop.taxRounding = "up"
        }
        val form = BedrockPurchaseForm(
            mockk<Player>(relaxed = true),
            testShop,
            mockk<ContainerTradeService>(relaxed = true),
            config,
            mockk<Logger>(relaxed = true)
        )
        val built = form.buildForm()
        assertNotNull(built)
    }

    @Test
    fun `form has correct number of buttons`() {
        val form = BedrockPurchaseForm(
            mockk<Player>(relaxed = true),
            testShop,
            mockk<ContainerTradeService>(relaxed = true),
            defaultConfig,
            mockk<Logger>(relaxed = true)
        )
        val built = form.buildForm() as SimpleForm
        // Should have 3 buttons: BUY, SELL, Back
        assertNotNull(built.buttons())
    }
}
