package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.SellOfferService
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.entity.Player

@Command(name = "em", description = "EnthusiaMarket stall offer commands", aliases = ["enthusiamarket"])
@Component
class StallOfferCommands(
    private val sellOffers: SellOfferService,
    @Suppress("UnusedPrivateMember") private val stalls: StallRepository,
    private val lang: LangService,
) {
    // ----- Sell offers (REQ-260..264) -----

    @Subcommand("stall offer")
    @Permission("enthusiamarket.stall.offer")
    fun stallOffer(
        @Context sender: Player,
        @Arg("stall") stall: String,
        @Arg("price") price: Long,
    ) {
        val msg = when (val r = sellOffers.create(StallId(stall), sender.uniqueId, price)) {
            is SellOfferService.Result.Created ->
                lang.msg("offer.created", "stall" to stall, "price" to r.offer.price)
            is SellOfferService.Result.NotFound ->
                lang.msg("offer.stall_not_found", "stall" to stall)
            is SellOfferService.Result.NotAuthorised ->
                lang.msg("offer.not_authorised", "stall" to stall)
            is SellOfferService.Result.AuctionOpen ->
                lang.msg("offer.auction_open", "stall" to stall)
            is SellOfferService.Result.OfferOpen ->
                lang.msg("offer.rejected", "reason" to "offer already exists")
            is SellOfferService.Result.Rejected ->
                lang.msg("offer.rejected", "reason" to r.reason)
            is SellOfferService.Result.Cancelled,
            is SellOfferService.Result.Purchased ->
                lang.msg("offer.rejected", "reason" to "unexpected")
        }
        sender.sendMessage(msg)
    }

    @Subcommand("stall offer cancel")
    @Permission("enthusiamarket.stall.offer")
    fun stallOfferCancel(
        @Context sender: Player,
        @Arg("stall") stall: String,
    ) {
        val msg = when (val r = sellOffers.cancel(StallId(stall), sender.uniqueId)) {
            is SellOfferService.Result.Cancelled ->
                lang.msg("offer.cancelled", "stall" to stall)
            is SellOfferService.Result.NotFound ->
                lang.msg("offer.not_found", "stall" to stall)
            is SellOfferService.Result.NotAuthorised ->
                lang.msg("offer.not_authorised", "stall" to stall)
            is SellOfferService.Result.AuctionOpen,
            is SellOfferService.Result.OfferOpen,
            is SellOfferService.Result.Created,
            is SellOfferService.Result.Purchased,
            is SellOfferService.Result.Rejected ->
                lang.msg("offer.rejected", "reason" to "unexpected")
        }
        sender.sendMessage(msg)
    }

    @Subcommand("stall buy")
    @Permission("enthusiamarket.stall.buy")
    fun stallBuy(
        @Context sender: Player,
        @Arg("stall") stall: String,
    ) {
        val msg = when (val r = sellOffers.purchase(StallId(stall), sender.uniqueId)) {
            is SellOfferService.Result.Purchased -> {
                val total = r.offer.price + r.tax
                lang.msg(
                    "offer.purchased",
                    "stall" to stall,
                    "price" to r.offer.price,
                    "tax" to r.tax,
                    "total" to total,
                )
            }
            is SellOfferService.Result.NotFound ->
                lang.msg("offer.not_found", "stall" to stall)
            is SellOfferService.Result.NotAuthorised ->
                lang.msg("offer.not_authorised", "stall" to stall)
            is SellOfferService.Result.Rejected ->
                lang.msg("offer.rejected", "reason" to r.reason)
            is SellOfferService.Result.AuctionOpen,
            is SellOfferService.Result.OfferOpen,
            is SellOfferService.Result.Created,
            is SellOfferService.Result.Cancelled ->
                lang.msg("offer.rejected", "reason" to "unexpected")
        }
        sender.sendMessage(msg)
    }
}
