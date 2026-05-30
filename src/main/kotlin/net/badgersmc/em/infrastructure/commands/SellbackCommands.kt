package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.StallSellbackService
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.entity.Player
import java.util.UUID

@Command(name = "em", description = "EnthusiaMarket sellback commands", aliases = ["enthusiamarket"])
@Component
class SellbackCommands(
    private val sellback: StallSellbackService,
    private val lang: LangService,
) {
    /** Pending `/em sellback` confirmations keyed on (player, stall). */
    private val pendingSellbacks =
        java.util.concurrent.ConcurrentHashMap<Pair<UUID, String>, java.time.Instant>()
    private val sellbackConfirmWindow: java.time.Duration = java.time.Duration.ofSeconds(30)

    // ----- Sellback (voluntary relinquish + refund) -----

    @Subcommand("sellback")
    @Permission("enthusiamarket.stall.sellback")
    fun sellback(
        @Context sender: Player,
        @Arg("stall") stall: String,
    ) {
        prunePendingSellbacks()
        when (val r = sellback.quote(StallId(stall), sender.uniqueId)) {
            is StallSellbackService.QuoteResult.NotFound ->
                sender.sendMessage(lang.msg("sellback.stall_not_found", "stall" to stall))
            is StallSellbackService.QuoteResult.NotOwned ->
                sender.sendMessage(lang.msg("sellback.not_owned", "stall" to stall))
            is StallSellbackService.QuoteResult.NotAuthorised ->
                sender.sendMessage(lang.msg("sellback.not_authorised", "stall" to stall))
            is StallSellbackService.QuoteResult.Ok -> {
                pendingSellbacks[sender.uniqueId to stall] = java.time.Instant.now()
                sender.sendMessage(lang.msg(
                    "sellback.warn.header",
                    "stall" to stall,
                    "refund" to r.quote.refund,
                    "periods" to r.quote.refundedPeriods,
                    "shops" to r.quote.shopCount,
                    "seconds" to sellbackConfirmWindow.seconds,
                ))
                sender.sendMessage(lang.msg("sellback.warn.wipe", "shops" to r.quote.shopCount))
                sender.sendMessage(lang.msg("sellback.warn.belongings"))
                sender.sendMessage(lang.msg("sellback.warn.schematic"))
                sender.sendMessage(lang.msg("sellback.warn.confirm", "stall" to stall))
            }
        }
    }

    @Subcommand("sellback confirm")
    @Permission("enthusiamarket.stall.sellback")
    fun sellbackConfirm(
        @Context sender: Player,
        @Arg("stall") stall: String,
    ) {
        val key = sender.uniqueId to stall
        val stagedAt = pendingSellbacks[key]
        if (stagedAt == null ||
            java.time.Duration.between(stagedAt, java.time.Instant.now()) > sellbackConfirmWindow
        ) {
            pendingSellbacks.remove(key)
            sender.sendMessage(lang.msg("sellback.no_pending", "stall" to stall))
            return
        }
        pendingSellbacks.remove(key)

        val msg = when (val r = sellback.execute(StallId(stall), sender.uniqueId)) {
            is StallSellbackService.ExecuteResult.Sold -> lang.msg(
                "sellback.success",
                "stall" to stall,
                "refund" to r.refund,
                "shops" to r.shopsWiped,
            )
            is StallSellbackService.ExecuteResult.NotFound ->
                lang.msg("sellback.stall_not_found", "stall" to stall)
            is StallSellbackService.ExecuteResult.NotOwned ->
                lang.msg("sellback.not_owned", "stall" to stall)
            is StallSellbackService.ExecuteResult.NotAuthorised ->
                lang.msg("sellback.not_authorised", "stall" to stall)
            is StallSellbackService.ExecuteResult.Rejected ->
                lang.msg("sellback.rejected", "reason" to r.reason)
        }
        sender.sendMessage(msg)
    }

    /**
     * Drop expired entries from [pendingSellbacks] so the map doesn't
     * leak across stagings that never confirm. Called inline on every
     * sellback subcommand — keeps the cost O(n) on small n with no
     * background scheduler.
     */
    private fun prunePendingSellbacks() {
        val now = java.time.Instant.now()
        pendingSellbacks.entries.removeIf {
            java.time.Duration.between(it.value, now) > sellbackConfirmWindow
        }
    }
}
