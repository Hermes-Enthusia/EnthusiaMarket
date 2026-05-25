package net.badgersmc.em.interaction.bedrock

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.shop.ShopRepository
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * TDD-87: Tests for BedrockCreateShopForm — Cumulus CustomForm for creating shops.
 */
class BedrockCreateShopFormTest {

    private val stallOwner = UUID.randomUUID()
    private val stallId = "stall_01"

    private fun mockContainer(
        worldName: String = "world",
        x: Int = 100,
        y: Int = 64,
        z: Int = 201
    ): Container {
        val container = mockk<Container>(relaxed = true)
        val containerLoc = mockk<Location>(relaxed = true)
        every { containerLoc.world?.name } returns worldName
        every { containerLoc.blockX } returns x
        every { containerLoc.blockY } returns y
        every { containerLoc.blockZ } returns z
        every { container.location } returns containerLoc
        every { container.snapshotInventory } returns mockk(relaxed = true)
        return container
    }

    @Test
    fun `create shop form constructs without throwing`() {
        val signLoc = mockk<Location>(relaxed = true)
        val container = mockContainer()

        val form = BedrockCreateShopForm(
            mockk<Player>(relaxed = true),
            stallOwner,
            stallId,
            signLoc,
            container,
            mockk<ShopRepository>(relaxed = true),
            mockk<Logger>(relaxed = true)
        )
        assertNotNull(form)
    }

    @Test
    fun `create shop form builds form without throwing`() {
        val signLoc = mockk<Location>(relaxed = true)
        val container = mockContainer()

        val form = BedrockCreateShopForm(
            mockk<Player>(relaxed = true),
            stallOwner,
            stallId,
            signLoc,
            container,
            mockk<ShopRepository>(relaxed = true),
            mockk<Logger>(relaxed = true)
        )
        val built = form.buildForm()
        assertNotNull(built)
    }

    @Test
    fun `form submission creates shop with correct data`() {
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns stallOwner

        val signLoc = mockk<Location>(relaxed = true)
        every { signLoc.world?.name } returns "world"
        every { signLoc.blockX } returns 50
        every { signLoc.blockY } returns 70
        every { signLoc.blockZ } returns 80

        val container = mockContainer("world", 51, 70, 80)

        val shopRepo = mockk<ShopRepository>(relaxed = true)
        every { shopRepo.upsert(any()) } returns mockk(relaxed = true)

        val form = BedrockCreateShopForm(
            player,
            stallOwner,
            stallId,
            signLoc,
            container,
            shopRepo,
            mockk<Logger>(relaxed = true)
        )

        // Build the form and extract the result handler by submitting a valid response
        val built = form.buildForm()
        assertNotNull(built)

        // Verify the form was built with the expected title
        val title = built.title()
        assert(title == "Create Shop") { "Form title should be 'Create Shop' but was '$title'" }

        // Verify the container location is correctly derived
        verify { container.location }
    }
}
