package net.badgersmc.em.application

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Service
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Base64
import java.util.UUID
import java.util.logging.Logger

sealed class ContainerTradeResult {
    data class Success(val message: String) : ContainerTradeResult()
    data class Failure(val reason: String) : ContainerTradeResult()
    data class CompensationFailed(val error: String, val compensation: String) : ContainerTradeResult()
}

private data class TradeContext(
    val ownerUuid: UUID,
    val player: Player,
    val containerInv: Inventory
)

/**
 * Executes buy/sell trades against container-linked shops.
 *
 * Handles item transfers between player inventory and container,
 * with economy integration for both personal and guild shops.
 */
@Service
open class ContainerTradeService(
    private val stallRepository: StallRepository,
    private val economy: EconomyProvider,
    private val guildProvider: GuildProvider?,
    private val logger: Logger,
    private val config: EnthusiaMarketConfig
) {
    fun executeBuy(shop: Shop, playerUuid: UUID): ContainerTradeResult {
        if (shop.frozen) return ContainerTradeResult.Failure("This shop is frozen")
        if (shop.sellAmount <= 0 || shop.costAmount <= 0) return ContainerTradeResult.Failure("Invalid trade amounts")
        val preconditions = buyPreconditions(shop, playerUuid)
        if (preconditions.result != null) return preconditions.result!!
        return executeBuyTransaction(shop, playerUuid, preconditions.ctx!!, preconditions.sellStack!!)
    }

    private data class BuyPreconditions(
        val ownerUuid: UUID? = null,
        val ctx: TradeContext? = null,
        val sellStack: ItemStack? = null,
        val result: ContainerTradeResult.Failure? = null
    )

    private fun buyPreconditions(shop: Shop, playerUuid: UUID): BuyPreconditions {
        val stall = stallRepository.findById(StallId(shop.stallId))
            ?: return BuyPreconditions(result = ContainerTradeResult.Failure("Stall not found"))
        val ownerUuid = resolveOwnerUuid(stall)
            ?: return BuyPreconditions(result = ContainerTradeResult.Failure("Invalid owner"))
        val player = getPlayer(playerUuid)
            ?: return BuyPreconditions(result = ContainerTradeResult.Failure("Player not online"))
        val sellStack = buildSellStack(shop)
            ?: return BuyPreconditions(result = ContainerTradeResult.Failure("Invalid item"))
        if (!player.inventory.containsAtLeast(sellStack, shop.sellAmount))
            return BuyPreconditions(result = ContainerTradeResult.Failure("You don't have the items to sell"))
        val container = getContainer(shop)
            ?: return BuyPreconditions(result = ContainerTradeResult.Failure("Container missing"))
        val containerInv = container.inventory
        val costStack = buildCostStack(shop)
            ?: return BuyPreconditions(result = ContainerTradeResult.Failure("Invalid cost item"))
        if (!containerInv.containsAtLeast(costStack, shop.costAmount))
            return BuyPreconditions(result = ContainerTradeResult.Failure("Shop can't afford this trade"))
        return BuyPreconditions(ownerUuid, TradeContext(ownerUuid, player, containerInv), sellStack)
    }

    private fun executeBuyTransaction(shop: Shop, playerUuid: UUID, ctx: TradeContext, sellStack: ItemStack): ContainerTradeResult {
        // Step 1: Remove sell items from player, add to container
        val removalResult = ctx.player.inventory.removeItem(sellStack.clone())
        if (removalResult.isNotEmpty()) return ContainerTradeResult.Failure("Not enough items in inventory")

        val remainder = ctx.containerInv.addItem(sellStack.clone())
        if (remainder.isNotEmpty()) {
            ctx.player.inventory.addItem(sellStack)
            return ContainerTradeResult.Failure("Container is full")
        }

        // Step 2: Move payment items (costStack) from container to player, with tax
        val costStack = buildCostStack(shop)
            ?: return ContainerTradeResult.Failure("Invalid cost item")

        val tax = if (config.shop.taxEnabled) {
            ShopTaxCalculator.computeTax(shop.costAmount.toLong(), config.shop.taxPct, config.shop.taxRounding)
        } else {
            0L
        }
        val paymentAmount = shop.costAmount - tax.toInt()

        // Remove full cost amount from container
        val costRemovalStack = costStack.clone()
        costRemovalStack.amount = shop.costAmount
        val costRemovalResult = ctx.containerInv.removeItem(costRemovalStack)
        if (costRemovalResult.isNotEmpty()) {
            // Rollback: return sell items to player
            rollbackContainerAndPlayer(ctx.containerInv, ctx.player, sellStack)
            return ContainerTradeResult.CompensationFailed(error = "Payment items missing", compensation = "Item returned")
        }

        // Give (costAmount - tax) items to player
        if (paymentAmount > 0) {
            val paymentStack = costStack.clone()
            paymentStack.amount = paymentAmount
            ctx.player.inventory.addItem(paymentStack)
        }

        // Deposit the payment to player via economy (reflects item transfer value)
        economy.deposit(playerUuid, paymentAmount.toLong())

        fireTransactionEvent(ctx.player, ctx.ownerUuid, sellStack, shop.sellAmount, shop.costAmount.toLong())
        return ContainerTradeResult.Success("Sold ${shop.sellAmount}x for ${shop.costAmount}")
    }

    fun executeSell(shop: Shop, playerUuid: UUID): ContainerTradeResult {
        if (shop.frozen) return ContainerTradeResult.Failure("This shop is frozen")
        if (shop.sellAmount <= 0 || shop.costAmount <= 0) return ContainerTradeResult.Failure("Invalid trade amounts")
        val preconditions = sellPreconditions(shop, playerUuid)
        if (preconditions.result != null) return preconditions.result!!
        return executeSellTransaction(shop, playerUuid, preconditions.ctx!!, preconditions.sellStack!!)
    }

    private data class SellPreconditions(
        val ctx: TradeContext? = null,
        val sellStack: ItemStack? = null,
        val result: ContainerTradeResult.Failure? = null
    )

    private fun sellPreconditions(shop: Shop, playerUuid: UUID): SellPreconditions {
        val stall = stallRepository.findById(StallId(shop.stallId))
            ?: return SellPreconditions(result = ContainerTradeResult.Failure("Stall not found"))
        val ownerUuid = resolveOwnerUuid(stall)
            ?: return SellPreconditions(result = ContainerTradeResult.Failure("Invalid owner"))
        val player = getPlayer(playerUuid)
            ?: return SellPreconditions(result = ContainerTradeResult.Failure("Player not online"))
        val sellStack = buildSellStack(shop)
            ?: return SellPreconditions(result = ContainerTradeResult.Failure("Invalid item"))
        val container = getContainer(shop)
            ?: return SellPreconditions(result = ContainerTradeResult.Failure("Container missing"))
        val containerInv = container.inventory
        if (!containerInv.containsAtLeast(sellStack, shop.sellAmount))
            return SellPreconditions(result = ContainerTradeResult.Failure("Out of stock"))
        // Check player has enough payment (cost) items
        val costStack = buildCostStack(shop)
            ?: return SellPreconditions(result = ContainerTradeResult.Failure("Invalid cost item"))
        if (!player.inventory.containsAtLeast(costStack, shop.costAmount))
            return SellPreconditions(result = ContainerTradeResult.Failure("You don't have enough payment items"))
        return SellPreconditions(TradeContext(ownerUuid, player, containerInv), sellStack)
    }

    private fun executeSellTransaction(
        shop: Shop, playerUuid: UUID, ctx: TradeContext, sellStack: ItemStack
    ): ContainerTradeResult {
        val costStack = buildCostStack(shop)
            ?: return ContainerTradeResult.Failure("Invalid cost item")

        // Step 1: Remove payment items (costStack) from player
        val costRemovalStack = costStack.clone()
        costRemovalStack.amount = shop.costAmount
        val removalResult = ctx.player.inventory.removeItem(costRemovalStack)
        if (removalResult.isNotEmpty()) {
            return ContainerTradeResult.Failure("You don't have enough payment items")
        }

        // Add payment items to container
        val remainder = ctx.containerInv.addItem(costRemovalStack.clone())
        if (remainder.isNotEmpty()) {
            ctx.player.inventory.addItem(costRemovalStack)
            return ContainerTradeResult.CompensationFailed(error = "Container is full", compensation = "Payment returned")
        }

        // Step 2: Remove sell items from container, give to player
        ctx.containerInv.removeItem(sellStack.clone())
        val sellRemainder = ctx.player.inventory.addItem(sellStack.clone())
        if (sellRemainder.isNotEmpty()) {
            // Rollback payment
            ctx.containerInv.removeItem(costRemovalStack)
            ctx.player.inventory.addItem(costRemovalStack)
            // Return sell items to container
            ctx.containerInv.addItem(sellStack)
            return ContainerTradeResult.CompensationFailed(error = "Inventory full", compensation = "Trade reversed")
        }

        // Compute tax and deposit seller proceeds
        val tax = if (config.shop.taxEnabled) {
            ShopTaxCalculator.computeTax(shop.costAmount.toLong(), config.shop.taxPct, config.shop.taxRounding)
        } else {
            0L
        }
        val sellerProceeds = shop.costAmount - tax.toInt()

        if (sellerProceeds > 0) {
            economy.deposit(ctx.ownerUuid, sellerProceeds.toLong())
        }

        fireTransactionEvent(ctx.player, ctx.ownerUuid, sellStack, shop.sellAmount, shop.costAmount.toLong())
        return ContainerTradeResult.Success("Bought ${shop.sellAmount}x for ${shop.costAmount}")
    }

    private fun rollbackContainerAndPlayer(containerInv: Inventory, player: Player, stack: ItemStack) {
        containerInv.removeItem(stack)
        player.inventory.addItem(stack)
    }

    private fun rollbackFullTransaction(
        guildId: UUID?, ownerUuid: UUID, playerUuid: UUID, cost: Long,
        containerInv: Inventory, sellStack: ItemStack
    ) {
        containerInv.addItem(sellStack)
        if (guildId != null) guildProvider?.bankWithdraw(guildId.toString(), cost) else economy.withdraw(ownerUuid, cost)
        economy.deposit(playerUuid, cost)
    }

    private fun buildSellStack(shop: Shop): ItemStack? {
        val base = deserializeStack(shop.sellItem) ?: return null
        base.amount = shop.sellAmount
        return base
    }

    private fun buildCostStack(shop: Shop): ItemStack? {
        val base = deserializeStack(shop.costItem) ?: return null
        base.amount = shop.costAmount
        return base
    }

    private fun fireTransactionEvent(
        player: org.bukkit.entity.Player,
        ownerUuid: UUID,
        item: org.bukkit.inventory.ItemStack,
        amount: Int,
        price: Long
    ) {
        // Hook for external transaction logging — intentionally a no-op in core.
    }

    private fun resolveOwnerUuid(stall: net.badgersmc.em.domain.stall.Stall): UUID? {
        return when (stall.owner.type) {
            OwnerType.SOLO -> try { UUID.fromString(stall.owner.id) } catch (_: IllegalArgumentException) { null }
            OwnerType.GUILD, OwnerType.NONE -> null
        }
    }

    open protected fun getContainer(shop: Shop): Container? {
        val world = Bukkit.getWorld(shop.containerWorld) ?: return null
        return world.getBlockAt(shop.containerX, shop.containerY, shop.containerZ).state as? Container
    }

    open protected fun getPlayer(uuid: UUID): Player? = Bukkit.getPlayer(uuid)

    open protected fun deserializeStack(base64: String): ItemStack? {
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            val stream = java.io.ByteArrayInputStream(bytes)
            org.bukkit.util.io.BukkitObjectInputStream(stream).readObject() as ItemStack
        } catch (_: Exception) {
            null
        }
    }
}
