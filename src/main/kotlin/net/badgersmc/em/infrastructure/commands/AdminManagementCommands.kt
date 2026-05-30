package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.ImportStallsService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.config.ConfigManager
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.command.CommandSender
import java.util.UUID

@Command(name = "em", description = "EnthusiaMarket admin management commands", aliases = ["enthusiamarket"])
@Component
class AdminManagementCommands(
    private val service: ImportStallsService,
    private val stalls: StallRepository,
    private val regionMembers: RegionMemberSync,
    private val config: EnthusiaMarketConfig,
    private val configManager: ConfigManager,
    private val lang: LangService,
) {
    @Subcommand("import")
    @Permission("enthusiamarket.admin.import")
    fun import(@Context sender: CommandSender) {
        val r = service.import(config.market.world, config.market.regionPrefix)
        sender.sendMessage(
            lang.msg(
                "admin.import.result",
                "created" to r.created,
                "skipped" to r.skipped,
                KEY_WORLD to config.market.world,
                KEY_REGION_PREFIX to config.market.regionPrefix
            )
        )
    }

    @Suppress("TooGenericExceptionCaught")
    @Subcommand("reload")
    @Permission("enthusiamarket.admin.reload")
    fun reload(@Context sender: CommandSender) {
        try {
            configManager.reload(EnthusiaMarketConfig::class)
            lang.reload()
            sender.sendMessage(
                lang.msg(
                    "admin.reload.success",
                    KEY_WORLD to config.market.world,
                    KEY_REGION_PREFIX to config.market.regionPrefix
                )
            )
        } catch (e: Exception) {
            sender.sendMessage(lang.msg("admin.reload.failure", "reason" to (e.message ?: "unknown error")))
        }
    }

    @Subcommand("list")
    @Permission("enthusiamarket.admin.list")
    fun list(@Context sender: CommandSender) {
        for (s in stalls.all()) {
            sender.sendMessage(
                lang.msg(
                    "admin.list.line",
                    "id" to s.id,
                    "state" to s.state,
                    "world" to s.world,
                    "region" to s.regionId
                )
            )
        }
    }

    // ----- Stall info (TDD-230) -----

    @Subcommand("stall info")
    @Permission("enthusiamarket.stall.info")
    fun stallInfo(
        @Context sender: CommandSender,
        @Arg("stall") stall: String,
    ) {
        val found = stalls.findById(StallId(stall))
        if (found == null) {
            sender.sendMessage(lang.msg("stall.info.not_found", "stall" to stall))
            return
        }
        val ownerDisplay = when (found.owner.type) {
            net.badgersmc.em.domain.stall.OwnerType.NONE -> "<gray>none"
            net.badgersmc.em.domain.stall.OwnerType.SOLO ->
                runCatching {
                    org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(found.owner.id)).name
                        ?: found.owner.id
                }.getOrElse { found.owner.id }
            net.badgersmc.em.domain.stall.OwnerType.GUILD -> "Guild:${found.owner.id}"
        }
        val membersDisplay = found.members.size.toString()
        val maxMembersDisplay = if (found.maxMembers < 0) "∞" else found.maxMembers.toString()
        val rentDisplay = found.rentTerms.toString()
        val nextRentDisplay = found.nextRentAt?.toString() ?: "N/A"
        val availableDisplay = (found.state == net.badgersmc.em.domain.stall.StallState.UNOWNED).toString()
        sender.sendMessage(
            lang.msg(
                "stall.info.card",
                "id" to found.id,
                "state" to found.state,
                "owner" to ownerDisplay,
                "members" to membersDisplay,
                "maxMembers" to maxMembersDisplay,
                "rent" to rentDisplay,
                "nextRent" to nextRentDisplay,
                "available" to availableDisplay,
            )
        )
    }

    // ----- WG resync (operator backfill) -----

    @Subcommand("rg resync")
    @Permission("enthusiamarket.admin")
    fun rgResync(@Context sender: CommandSender) {
        var fixed = 0
        var skipped = 0
        var errors = 0
        for (stall in stalls.all()) {
            when (stall.state) {
                StallState.OWNED, StallState.GRACE -> when (stall.owner.type) {
                    OwnerType.SOLO -> try {
                        val uuid = UUID.fromString(stall.owner.id)
                        // Rebuild full ACL: clear first, set owner, then replay members.
                        regionMembers.clearOwnersAndMembers(stall.world, stall.regionId)
                        regionMembers.setOwner(stall.world, stall.regionId, uuid)
                        for (memberId in stall.members) {
                            regionMembers.addMember(stall.world, stall.regionId, memberId)
                        }
                        fixed++
                    } catch (_: Exception) {
                        errors++
                    }
                    OwnerType.GUILD -> skipped++  // no auto WG bridge for guilds yet
                    OwnerType.NONE -> Unit
                }
                StallState.UNOWNED -> try {
                    regionMembers.clearOwnersAndMembers(stall.world, stall.regionId)
                    fixed++
                } catch (_: Exception) {
                    errors++
                }
                // Active auctions left alone — the winning bid will
                // run setOwner via settleWithWinner at expiry.
                StallState.AUCTIONING,
                StallState.RE_AUCTIONING,
                StallState.EMERGENCY_AUCTIONING -> skipped++
            }
        }
        sender.sendMessage(lang.msg(
            "admin.rg_resync.result",
            "fixed" to fixed, "skipped" to skipped, "errors" to errors,
        ))
    }

    private companion object {
        const val KEY_WORLD = "world"
        const val KEY_REGION_PREFIX = "region_prefix"
    }
}
