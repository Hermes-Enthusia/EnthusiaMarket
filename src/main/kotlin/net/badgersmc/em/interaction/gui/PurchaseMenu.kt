package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.em.application.ContainerTradeResult
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopTaxCalculator
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.interaction.Menu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * IFramework ChestGui showing the shop's sell/cost items and trade buttons (REQ-013).
 *
 * Displays deserialized ItemStack icons from base64, tax information, and frozen status.
 * BUY executes an actual trade via [ContainerTradeService.executeBuy].
 */
class PurchaseMenu(
    private val shop: Shop,
    private val tradeService: ContainerTradeService,
    private val config: EnthusiaMarketConfig = EnthusiaMarketConfig()
) : Menu {

    override fun open(player: Player) {
        val sellStack = resolveItem(shop.sellItem, Material.DIAMOND)
        val costStack = resolveItem(shop.costItem, Material.EMERALD)

        val taxPct = config.shop.taxPct
        val taxRounding = config.shop.taxRounding
        val taxEnabled = config.shop.taxEnabled

        val taxAmount = if (taxEnabled) {
            ShopTaxCalculator.computeTax(shop.costAmount.toLong(), taxPct, taxRounding)
        } else {
            0L
        }
        val totalCost = shop.costAmount + taxAmount

        // Build sell item lore
        val sellLore = mutableListOf<String>()
        if (shop.frozen) {
            sellLore.add("§c§lFROZEN")
        }
        sellLore.add("§7Sell: §f${shop.sellAmount}x per trade")

        val sellMeta = sellStack.itemMeta ?: return
        sellMeta.setDisplayName("§e§lSELL: §f${shop.sellAmount}x Item")
        sellMeta.lore = sellLore
        sellStack.itemMeta = sellMeta

        // Arrow (center, slot 13)
        val arrow = ItemStack(Material.ARROW)
        val arrowMeta = arrow.itemMeta ?: return
        arrowMeta.setDisplayName("§7→")
        arrow.itemMeta = arrowMeta

        // Build cost item lore with tax info
        val costLore = mutableListOf<String>()
        costLore.add("§7Base cost: §f${shop.costAmount}x per trade")
        if (taxEnabled && taxAmount > 0) {
            val pctDisplay = (taxPct * 100).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            costLore.add("§7Tax: §6${taxAmount} items (${pctDisplay}%)")
        }
        if (taxAmount > 0) {
            costLore.add("§7Total: §6${totalCost}x per trade")
        }
        if (shop.frozen) {
            costLore.add("§c§lFROZEN — trading disabled")
        }

        val costMeta = costStack.itemMeta ?: return
        costMeta.setDisplayName("§6§lCOST: §f${shop.costAmount}x per trade")
        costMeta.lore = costLore
        costStack.itemMeta = costMeta

        // Create a 3-row GUI
        val gui = ChestGui(3, "§8Shop — ${shop.sellAmount}x Item")
        val pane = StaticPane(9, 3)

        // Buy button (bottom center, slot 22)
        val buyStack = if (shop.frozen) ItemStack(Material.RED_STAINED_GLASS_PANE) else ItemStack(Material.LIME_STAINED_GLASS_PANE)
        val buyMeta = buyStack.itemMeta ?: return
        buyMeta.setDisplayName(if (shop.frozen) "§c§lFROZEN" else "§a§lBUY")
        buyMeta.lore = if (shop.frozen) listOf("§7Trading is disabled") else listOf("§7Click to purchase")
        buyStack.itemMeta = buyMeta

        pane.addItem(GuiItem(sellStack), 2, 1) // slot 11
        pane.addItem(GuiItem(arrow), 4, 1)     // slot 13
        pane.addItem(GuiItem(costStack), 6, 1) // slot 15
        pane.addItem(GuiItem(buyStack, { event ->
            event.isCancelled = true
            when (val result = tradeService.executeBuy(shop, player.uniqueId)) {
                is ContainerTradeResult.Success -> player.sendMessage("§a[Shop] ${result.message}")
                is ContainerTradeResult.Failure -> player.sendMessage("§c[Shop] ${result.reason}")
                is ContainerTradeResult.CompensationFailed -> {
                    player.sendMessage("§c[Shop] Trade failed: ${result.error}")
                    player.sendMessage("§7  Compensation: ${result.compensation}")
                }
            }
        }), 4, 2) // slot 22

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Attempts to deserialize a base64-encoded ItemStack.
     * Falls back to [fallbackMaterial] if deserialization returns null.
     */
    private fun resolveItem(base64: String, fallbackMaterial: Material): ItemStack {
        val deserialized = ItemStackSerializer.deserialize(base64)
        if (deserialized != null) {
            return deserialized
        }
        return ItemStack(fallbackMaterial)
    }
}