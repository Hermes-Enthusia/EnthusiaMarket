package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.events.ShopCreatedEvent
import net.badgersmc.em.interaction.Menu
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Step-by-step shop creation GUI (REQ-028, TDD-83).
 *
 * Step 1: Select sell item from container inventory
 * Step 2: Select payment item type from material list
 * Step 3: Set sell amount and cost amount, then confirm
 */
class CreateShopMenu(
    private val player: Player,
    private val container: Container,
    private val stallId: String,
    private val signLocation: Location,
    private val shopRepository: ShopRepository
) : Menu {

    companion object {
        private val PAYMENT_MATERIALS = listOf(
            Material.DIAMOND,
            Material.EMERALD,
            Material.GOLD_INGOT,
            Material.NETHERITE_INGOT,
            Material.IRON_INGOT,
            Material.LAPIS_LAZULI,
            Material.REDSTONE,
            Material.COAL,
            Material.COPPER_INGOT,
            Material.AMETHYST_SHARD,
            Material.QUARTZ,
            Material.SLIME_BALL
        )

        private const val MIN_AMOUNT = 1
        private const val MAX_AMOUNT = 64
    }

    private var step = 1
    private var selectedSellItem: ItemStack? = null
    private var selectedPaymentMaterial: Material = Material.DIAMOND
    private var sellAmount = 1
    private var costAmount = 1

    override fun open(player: Player) {
        showStep1()
    }

    /**
     * Step 1: Select the item to sell from the container inventory.
     */
    private fun showStep1() {
        step = 1
        val gui = ChestGui(6, "§8Step 1: Select Item to Sell")
        val pane = StaticPane(9, 6)

        val containerItems = container.snapshotInventory.contents.filterNotNull()
        if (containerItems.isEmpty()) {
            player.sendMessage("§cContainer is empty — add items to the chest first")
            return
        }

        // Display container items (up to 54 slots)
        containerItems.take(54).forEachIndexed { index, item ->
            val row = index / 9
            val col = index % 9
            pane.addItem(GuiItem(item.clone()) { event ->
                event.isCancelled = true
                selectedSellItem = item.clone()
                showStep2()
            }, col, row)
        }

        // Cancel button
        val cancelStack = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§cCancel")
            }
        }
        pane.addItem(GuiItem(cancelStack) { event ->
            event.isCancelled = true
            player.closeInventory()
        }, 4, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Step 2: Select the payment item type.
     */
    private fun showStep2() {
        step = 2
        val gui = ChestGui(6, "§8Step 2: Select Payment Item")
        val pane = StaticPane(9, 6)

        PAYMENT_MATERIALS.take(54).forEachIndexed { index, material ->
            val row = index / 9
            val col = index % 9
            val itemStack = ItemStack(material)
            val meta = itemStack.itemMeta
            meta?.setDisplayName("§e${material.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }}")
            itemStack.itemMeta = meta

            pane.addItem(GuiItem(itemStack) { event ->
                event.isCancelled = true
                selectedPaymentMaterial = material
                showStep3()
            }, col, row)
        }

        // Back button
        val backStack = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§7← Back") }
        }
        pane.addItem(GuiItem(backStack) { event ->
            event.isCancelled = true
            showStep1()
        }, 0, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Step 3: Set sell amount and cost amount.
     */
    private fun showStep3() {
        step = 3
        val gui = ChestGui(3, "§8Step 3: Set Amounts")
        val pane = StaticPane(9, 3)

        val sellItem = selectedSellItem ?: return

        // Display preview
        val previewStack = ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§6Preview")
                lore = listOf(
                    "§7Selling: §f${sellAmount}x ${sellItem.type.name.lowercase()}",
                    "§7Cost: §f${costAmount}x ${selectedPaymentMaterial.name.lowercase()}",
                    "",
                    "§eClick amounts to adjust, then confirm"
                )
            }
        }
        pane.addItem(GuiItem(previewStack) { event -> event.isCancelled = true }, 4, 0)

        // Sell amount controls
        pane.addItem(createAmountItem("${sellAmount}x Sell Item", sellItem.type, 0, 1) { d ->
            sellAmount = (sellAmount + d).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
            showStep3()
        }, 2, 1)

        // Cost amount controls
        pane.addItem(createAmountItem("${costAmount}x Payment", selectedPaymentMaterial, 0, 1) { d ->
            costAmount = (costAmount + d).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
            showStep3()
        }, 6, 1)

        // Confirm button
        val confirmStack = ItemStack(Material.LIME_DYE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§a§lConfirm & Create Shop")
                lore = listOf(
                    "§7Sell: §f${sellAmount}x ${sellItem.type.name.lowercase()}",
                    "§7Cost: §f${costAmount}x ${selectedPaymentMaterial.name.lowercase()}",
                    "",
                    "§eClick to create your shop!"
                )
            }
        }
        pane.addItem(GuiItem(confirmStack) { event ->
            event.isCancelled = true
            createShop()
        }, 4, 2)

        // Back button
        val backStack = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§7← Back") }
        }
        pane.addItem(GuiItem(backStack) { event ->
            event.isCancelled = true
            showStep2()
        }, 0, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Creates an amount adjustment GUI item with +/- buttons.
     */
    private fun createAmountItem(
        label: String,
        material: Material,
        currentAmount: Int,
        delta: Int,
        onClick: (Int) -> Unit
    ): GuiItem {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta?.setDisplayName("§f$label")
        stack.itemMeta = meta
        return GuiItem(stack) { event ->
            event.isCancelled = true
            onClick(delta)
        }
    }

    /**
     * Creates the shop in the database and fires the event.
     */
    private fun createShop() {
        val sellItem = selectedSellItem ?: run {
            player.sendMessage("§cNo sell item selected")
            return
        }

        val sellBase64 = ItemStackSerializer.serialize(sellItem.clone().apply { amount = sellAmount })
        val costStack = ItemStack(selectedPaymentMaterial)
        val costBase64 = ItemStackSerializer.serialize(costStack.apply { amount = costAmount })

        val worldName = signLocation.world?.name ?: "world"

        val shop = Shop(
            stallId = stallId,
            owner = player.uniqueId,
            signWorld = worldName,
            signX = signLocation.blockX,
            signY = signLocation.blockY,
            signZ = signLocation.blockZ,
            containerWorld = worldName,
            containerX = container.location.blockX,
            containerY = container.location.blockY,
            containerZ = container.location.blockZ,
            sellItem = sellBase64,
            sellAmount = sellAmount,
            costItem = costBase64,
            costAmount = costAmount,
            creatorId = player.uniqueId
        )

        shopRepository.upsert(shop)
        player.sendMessage("§a[Shop] Shop created! Selling §f${sellAmount}x ${sellItem.type.name.lowercase()} §afor §f${costAmount}x ${selectedPaymentMaterial.name.lowercase()}")
        Bukkit.getPluginManager().callEvent(ShopCreatedEvent(player.uniqueId))
        player.closeInventory()
    }
}
