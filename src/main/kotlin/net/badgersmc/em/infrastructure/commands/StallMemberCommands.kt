package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.StallMemberService
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
import java.util.UUID

@Command(name = "em", description = "EnthusiaMarket stall member commands", aliases = ["enthusiamarket"])
@Component
class StallMemberCommands(
    private val stallMembers: StallMemberService,
    @Suppress("UnusedPrivateMember") private val stalls: StallRepository,
    private val lang: LangService,
) {
    @Subcommand("stall members add")
    @Permission("enthusiamarket.stall.members")
    fun membersAdd(
        @Context sender: Player,
        @Arg("stall") stall: String,
        @Arg("player") player: String,
    ) {
        val offline = org.bukkit.Bukkit.getOfflinePlayer(player)
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(lang.msg("stall.members.unknown_player", "player" to player))
            return
        }
        val targetUuid = offline.uniqueId
        renderMemberMutation(sender, stall, "added") {
            stallMembers.addMember(StallId(stall), sender.uniqueId, targetUuid) to targetUuid
        }
    }

    @Subcommand("stall members remove")
    @Permission("enthusiamarket.stall.members")
    fun membersRemove(
        @Context sender: Player,
        @Arg("stall") stall: String,
        @Arg("player") player: String,
    ) {
        val offline = org.bukkit.Bukkit.getOfflinePlayer(player)
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(lang.msg("stall.members.unknown_player", "player" to player))
            return
        }
        val targetUuid = offline.uniqueId
        renderMemberMutation(sender, stall, "removed") {
            stallMembers.removeMember(StallId(stall), sender.uniqueId, targetUuid) to targetUuid
        }
    }

    @Subcommand("stall members list")
    @Permission("enthusiamarket.stall.members")
    fun membersList(
        @Context sender: Player,
        @Arg("stall") stall: String,
    ) {
        when (val r = stallMembers.listMembers(StallId(stall), sender.uniqueId)) {
            is StallMemberService.Result.NotFound ->
                sender.sendMessage(lang.msg("stall.members.not_found", "stall" to stall))
            is StallMemberService.Result.NotAuthorised ->
                sender.sendMessage(lang.msg("stall.members.not_authorised", "stall" to stall))
            is StallMemberService.Result.Rejected ->
                sender.sendMessage(lang.msg("stall.members.rejected", "reason" to r.reason))
            is StallMemberService.Result.Success -> {
                val members = r.stall.members
                if (members.isEmpty()) {
                    sender.sendMessage(lang.msg("stall.members.list_empty", "stall" to stall))
                } else {
                    sender.sendMessage(
                        lang.msg(
                            "stall.members.list_header",
                            "stall" to stall,
                            "count" to members.size,
                        )
                    )
                    for (uuid in members) {
                        val name = org.bukkit.Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                        sender.sendMessage(lang.msg("stall.members.list_entry", "player" to name))
                    }
                }
            }
        }
    }

    private inline fun renderMemberMutation(
        sender: Player,
        stall: String,
        successKey: String,
        op: () -> Pair<StallMemberService.Result, UUID>,
    ) {
        val (result, targetUuid) = op()
        val targetName = org.bukkit.Bukkit.getOfflinePlayer(targetUuid).name ?: targetUuid.toString()
        val msg = when (result) {
            is StallMemberService.Result.Success ->
                lang.msg("stall.members.$successKey", "player" to targetName, "stall" to stall)
            is StallMemberService.Result.NotFound ->
                lang.msg("stall.members.not_found", "stall" to stall)
            is StallMemberService.Result.NotAuthorised ->
                lang.msg("stall.members.not_authorised", "stall" to stall)
            is StallMemberService.Result.Rejected ->
                lang.msg("stall.members.rejected", "reason" to result.reason)
        }
        sender.sendMessage(msg)
    }
}
