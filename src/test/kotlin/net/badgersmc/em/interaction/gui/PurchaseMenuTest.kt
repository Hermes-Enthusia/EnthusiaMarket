package net.badgersmc.em.interaction.gui

import io.mockk.*
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * TDD-85: Tests for PurchaseMenu — real item icons, tax display, stock count.
 *
 * NOTE: These tests verify construction only. Full GUI content verification
 * requires integration testing with a live server since IFramework creates
 * actual inventory views.
 */
class PurchaseMenuTest {

    private val testShop = Shop(
        id = 1L, stallId = "s1", owner = UUID.randomUUID(),
        signWorld = "w", signX = 1, signY = 2, signZ = 3,
        containerWorld = "w", containerX = 4, containerY = 5, containerZ = 6,
        sellItem = "c3RvcnlfZGlhbW9uZA==", sellAmount = 5,
        costItem = "c3RvcnlfZW1lcmFsZA==", costAmount = 10,
        frozen = false
    )

    private val defaultConfig = EnthusiaMarketConfig().apply {
        shop.taxEnabled = true
        shop.taxPct = 0.02
        shop.taxRounding = "nearest"
    }

    @Test
    fun `purchase menu constructs without throwing`() {
        val menu = PurchaseMenu(testShop, mockk<ContainerTradeService>(relaxed = true), defaultConfig)
        assertNotNull(menu)
    }

    @Test
    fun `purchase menu with tax disabled config constructs`() {
        val config = EnthusiaMarketConfig().apply {
            shop.taxEnabled = false
            shop.taxPct = 0.02
            shop.taxRounding = "nearest"
        }
        val menu = PurchaseMenu(testShop, mockk<ContainerTradeService>(relaxed = true), config)
        assertNotNull(menu)
    }

    @Test
    fun `purchase menu with frozen shop constructs`() {
        val frozenShop = testShop.copy(frozen = true)
        val menu = PurchaseMenu(frozenShop, mockk<ContainerTradeService>(relaxed = true), defaultConfig)
        assertNotNull(menu)
    }

    @Test
    fun `purchase menu with high tax config constructs`() {
        val config = EnthusiaMarketConfig().apply {
            shop.taxEnabled = true
            shop.taxPct = 0.50
            shop.taxRounding = "up"
        }
        val menu = PurchaseMenu(testShop, mockk<ContainerTradeService>(relaxed = true), config)
        assertNotNull(menu)
    }
}
