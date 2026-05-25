package net.badgersmc.em.interaction.bedrock

import net.badgersmc.em.application.ContainerTradeResult
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopTaxCalculator
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.shop.Shop
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.SimpleForm
import java.util.logging.Logger

/**
 * Bedrock Cumulus SimpleForm for shop purchases (REQ-013).
 * Displays the shop's real items, tax-inclusive price, stock count, and frozen status.
 * BUY/SELL buttons execute trades via [ContainerTradeService].
 */
class BedrockPurchaseForm(
    player: Player,
    private val shop: Shop,
    private val tradeService: ContainerTradeService,
    private val config: EnthusiaMarketConfig = EnthusiaMarketConfig(),
    logger: Logger
) : BedrockMenuBase(player, logger) {

    override fun buildForm(): SimpleForm {
        val ownerName = Bukkit.getOfflinePlayer(shop.owner).name ?: "Unknown"

        // Deserialize items for display
        val sellStack = ItemStackSerializer.deserialize(shop.sellItem)
            ?: ItemStack(Material.BARRIER)
        val costStack = ItemStackSerializer.deserialize(shop.costItem)
            ?: ItemStack(Material.BARRIER)

        val sellItemName = sellStack.itemMeta?.displayName
            ?: sellStack.type.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")
        val costItemName = costStack.itemMeta?.displayName
            ?: costStack.type.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")

        // Tax calculation
        val taxPct = if (config.shop.taxEnabled) config.shop.taxPct else 0.0
        val taxRounding = config.shop.taxRounding
        val taxAmount = ShopTaxCalculator.computeTax(shop.costAmount.toLong(), taxPct, taxRounding)
        val totalCost = shop.costAmount + taxAmount.toInt()

        // Stock from container
        val stockCount = getStockCount(shop, sellStack)

        val statusText = if (shop.frozen) "§cFrozen" else "§aActive"

        val priceLine = if (taxAmount > 0) {
            "Price: ${shop.costAmount} $costItemName (+ ${taxAmount.toInt()} tax = $totalCost)"
        } else {
            "Price: ${shop.costAmount} $costItemName"
        }

        return SimpleForm.builder()
            .title("Shop — $sellItemName")
            .content(
                "Selling: ${shop.sellAmount}x $sellItemName\n" +
                    "$priceLine\n" +
                    "Stock: $stockCount\n" +
                    "Owner: $ownerName\n" +
                    "Status: $statusText"
            )
            .button(if (shop.frozen) "§cFROZEN" else "§a§lBUY")
            .button(if (shop.frozen) "§cFROZEN" else "§e§lSELL")
            .button("§7Back")
            .validResultHandler { response ->
                if (shop.frozen) return@validResultHandler
                when (response.clickedButtonId()) {
                    0 -> {
                        when (val result = tradeService.executeBuy(shop, player.uniqueId)) {
                            is ContainerTradeResult.Success -> player.sendMessage("§a[Shop] ${result.message}")
                            is ContainerTradeResult.Failure -> player.sendMessage("§c[Shop] ${result.reason}")
                            is ContainerTradeResult.CompensationFailed -> {
                                player.sendMessage("§c[Shop] Trade failed: ${result.error}")
                                player.sendMessage("§7  Compensation: ${result.compensation}")
                            }
                        }
                    }
                    1 -> {
                        when (val result = tradeService.executeSell(shop, player.uniqueId)) {
                            is ContainerTradeResult.Success -> player.sendMessage("§a[Shop] ${result.message}")
                            is ContainerTradeResult.Failure -> player.sendMessage("§c[Shop] ${result.reason}")
                            is ContainerTradeResult.CompensationFailed -> {
                                player.sendMessage("§c[Shop] Trade failed: ${result.error}")
                                player.sendMessage("§7  Compensation: ${result.compensation}")
                            }
                        }
                    }
                    2 -> { /* back */ }
                }
            }
            .build()
    }

    private fun getStockCount(shop: Shop, sellStack: ItemStack): Int {
        return try {
            val world = Bukkit.getWorld(shop.containerWorld) ?: return 0
            val block = world.getBlockAt(shop.containerX, shop.containerY, shop.containerZ)
            val container = block.state as? org.bukkit.block.Container ?: return 0
            container.inventory.contents.filterNotNull()
                .filter { it.isSimilar(sellStack) }
                .sumOf { it.amount }
        } catch (_: Exception) {
            0
        }
    }
}
