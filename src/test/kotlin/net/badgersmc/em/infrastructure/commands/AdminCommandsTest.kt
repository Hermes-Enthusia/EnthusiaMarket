package net.badgersmc.em.infrastructure.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.confirmVerified
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.ImportStallsService
import net.badgersmc.em.application.StallMemberService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AdminCommandsTest {

    private val sender = mockk<CommandSender>(relaxed = true)
    private val config = EnthusiaMarketConfig().apply {
        market.world = "world"
        market.regionPrefix = "stall_"
    }

    @Test fun `import delegates to service and reports counts`() {
        val service = mockk<ImportStallsService>()
        val repo = mockk<StallRepository>()
        every { service.import("world", "stall_") } returns ImportStallsService.Result(3, 1)

        val cmd = AdminCommands(service, repo, config, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        cmd.import(sender)

        verify { service.import("world", "stall_") }
        verify { sender.sendMessage(any<Component>()) }
    }

    @Test fun `list prints one line per stall`() {
        val service = mockk<ImportStallsService>()
        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(
            Stall(StallId("s1"), "s1", "world", StallState.UNOWNED, OwnerRef.unowned(),
                  null, 0L, RentTerms.formula(1.0))
        )

        val cmd = AdminCommands(service, repo, config, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        cmd.list(sender)

        verify { sender.sendMessage(any<Component>()) }
    }

    // --- REQ-202 — member command routing through StallMemberService ---

    /**
     * Member commands resolve player names via Bukkit.getOfflinePlayer
     * (intentional — no need to invent a port for a one-line lookup).
     * MockBukkit isn't on the classpath for this suite, so stub the
     * static call once per test class.
     */
    private val stubPlayer = mockk<OfflinePlayer>(relaxed = true).also {
        every { it.uniqueId } returns UUID.randomUUID()
        every { it.name } returns "Alice"
        every { it.hasPlayedBefore() } returns true
    }

    @BeforeTest fun mockBukkit() {
        mockkStatic(Bukkit::class)
        every { Bukkit.getOfflinePlayer(any<String>()) } returns stubPlayer
        every { Bukkit.getOfflinePlayer(any<UUID>()) } returns stubPlayer
    }

    @AfterTest fun unmockBukkit() = unmockkStatic(Bukkit::class)

    @Test fun `members add delegates to service with sender uuid as actor`() {
        val player = mockk<Player>(relaxed = true)
        val actorUuid = UUID.randomUUID()
        every { player.uniqueId } returns actorUuid

        val members = mockk<StallMemberService>(relaxed = true)
        every { members.addMember(any(), any(), any()) } returns
            StallMemberService.Result.Success(
                Stall(StallId("s1"), "s1", "world", StallState.UNOWNED, OwnerRef.unowned(),
                      null, 0L, RentTerms.formula(1.0))
            )

        val cmd = AdminCommands(
            mockk(relaxed = true), mockk(relaxed = true), config,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            members,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        cmd.membersAdd(player, "s1", "Alice")

        verify { members.addMember(StallId("s1"), actorUuid, any<UUID>()) }
    }

    @Test fun `members add surfaces NotAuthorised back to sender`() {
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()

        val members = mockk<StallMemberService>()
        every { members.addMember(any(), any(), any()) } returns
            StallMemberService.Result.NotAuthorised

        val cmd = AdminCommands(
            mockk(relaxed = true), mockk(relaxed = true), config,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            members,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        cmd.membersAdd(player, "s1", "Alice")

        // i18n migration in flight (handoff #22 — owned by Hermes) means
        // sendMessage takes Component, not String. We just verify a
        // message was sent — content assertion is a lang-key test that
        // belongs in the lang suite, not here.
        verify { player.sendMessage(any<Component>()) }
    }

    @Test fun `members list delegates to service with sender uuid`() {
        val player = mockk<Player>(relaxed = true)
        val actorUuid = UUID.randomUUID()
        every { player.uniqueId } returns actorUuid

        val members = mockk<StallMemberService>()
        every { members.listMembers(StallId("s1"), actorUuid) } returns
            StallMemberService.Result.Success(
                Stall(StallId("s1"), "s1", "world", StallState.UNOWNED, OwnerRef.unowned(),
                      null, 0L, RentTerms.formula(1.0))
            )

        val cmd = AdminCommands(
            mockk(relaxed = true), mockk(relaxed = true), config,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            members,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        cmd.membersList(player, "s1")

        verify { members.listMembers(StallId("s1"), actorUuid) }
    }

    // =========================================================================
    // rg resync — REQ: rebuild WG ACL for every stall
    // =========================================================================

    /**
     * Helper: build an AdminCommands with explicit [repo] and [regionMembers],
     * relaxing every other dependency.
     */
    private fun buildResyncCmd(
        repo: StallRepository,
        regionMembers: RegionMemberSync,
    ) = AdminCommands(
        mockk(relaxed = true), // ImportStallsService
        repo,
        config,
        mockk(relaxed = true), // AuctionLifecycleService
        mockk(relaxed = true), // ConfigManager
        mockk(relaxed = true), // AuctionRepository
        mockk(relaxed = true), // JavaPlugin
        mockk(relaxed = true), // LangService
        mockk(relaxed = true), // NexusScheduler
        mockk(relaxed = true), // StallMemberService
        mockk(relaxed = true), // SellOfferService
        mockk(relaxed = true), // StallSellbackService
        regionMembers,
    )

    @Test fun `rg resync SOLO-owned stall calls clearOwnersAndMembers then setOwner then addMember for each member`() {
        val ownerUuid = UUID.randomUUID()
        val memberUuid = UUID.randomUUID()
        val stall = Stall(
            id = StallId("s1"), regionId = "s1", world = "world",
            state = StallState.OWNED,
            owner = OwnerRef.solo(ownerUuid),
            ownerSince = null, winningBid = 100L,
            rentTerms = RentTerms.flat(10L),
            members = setOf(memberUuid),
        )

        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(stall)

        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)

        val cmd = buildResyncCmd(repo, regionMembers)
        cmd.rgResync(sender)

        verify(exactly = 1) { regionMembers.clearOwnersAndMembers("world", "s1") }
        verify(exactly = 1) { regionMembers.setOwner("world", "s1", ownerUuid) }
        verify(exactly = 1) { regionMembers.addMember("world", "s1", memberUuid) }
        confirmVerified(regionMembers)
    }

    @Test fun `rg resync SOLO-owned stall with no members does not call addMember`() {
        val ownerUuid = UUID.randomUUID()
        val stall = Stall(
            id = StallId("s2"), regionId = "s2", world = "world",
            state = StallState.OWNED,
            owner = OwnerRef.solo(ownerUuid),
            ownerSince = null, winningBid = 200L,
            rentTerms = RentTerms.flat(20L),
            members = emptySet(),
        )

        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(stall)

        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)

        val cmd = buildResyncCmd(repo, regionMembers)
        cmd.rgResync(sender)

        verify(exactly = 1) { regionMembers.clearOwnersAndMembers("world", "s2") }
        verify(exactly = 1) { regionMembers.setOwner("world", "s2", ownerUuid) }
        verify(exactly = 0) { regionMembers.addMember(any(), any(), any()) }
    }

    @Test fun `rg resync GUILD-owned stall is skipped — no regionMembers calls`() {
        val stall = Stall(
            id = StallId("s3"), regionId = "s3", world = "world",
            state = StallState.OWNED,
            owner = OwnerRef.guild("guild-123"),
            ownerSince = null, winningBid = 300L,
            rentTerms = RentTerms.flat(30L),
        )

        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(stall)

        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)

        val cmd = buildResyncCmd(repo, regionMembers)
        cmd.rgResync(sender)

        verify(exactly = 0) { regionMembers.clearOwnersAndMembers(any(), any()) }
        verify(exactly = 0) { regionMembers.setOwner(any(), any(), any()) }
        verify(exactly = 0) { regionMembers.addMember(any(), any(), any()) }
        confirmVerified(regionMembers)
    }

    @Test fun `rg resync UNOWNED stall calls clearOwnersAndMembers only`() {
        val stall = Stall(
            id = StallId("s4"), regionId = "s4", world = "world",
            state = StallState.UNOWNED,
            owner = OwnerRef.unowned(),
            ownerSince = null, winningBid = 0L,
            rentTerms = RentTerms.flat(0L),
        )

        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(stall)

        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)

        val cmd = buildResyncCmd(repo, regionMembers)
        cmd.rgResync(sender)

        verify(exactly = 1) { regionMembers.clearOwnersAndMembers("world", "s4") }
        verify(exactly = 0) { regionMembers.setOwner(any(), any(), any()) }
        verify(exactly = 0) { regionMembers.addMember(any(), any(), any()) }
        confirmVerified(regionMembers)
    }

    @Test fun `rg resync AUCTIONING stall is skipped — no regionMembers calls`() {
        val ownerUuid = UUID.randomUUID()
        val stall = Stall(
            id = StallId("s5"), regionId = "s5", world = "world",
            state = StallState.AUCTIONING,
            owner = OwnerRef.solo(ownerUuid),
            ownerSince = null, winningBid = 0L,
            rentTerms = RentTerms.flat(0L),
        )

        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(stall)

        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)

        val cmd = buildResyncCmd(repo, regionMembers)
        cmd.rgResync(sender)

        verify(exactly = 0) { regionMembers.clearOwnersAndMembers(any(), any()) }
        verify(exactly = 0) { regionMembers.setOwner(any(), any(), any()) }
        verify(exactly = 0) { regionMembers.addMember(any(), any(), any()) }
        confirmVerified(regionMembers)
    }

    @Test fun `rg resync mixed stall list processes each stall correctly`() {
        val soloOwner = UUID.randomUUID()
        val soloMember = UUID.randomUUID()
        val soloStall = Stall(
            id = StallId("solo"), regionId = "solo", world = "world",
            state = StallState.OWNED,
            owner = OwnerRef.solo(soloOwner),
            ownerSince = null, winningBid = 100L,
            rentTerms = RentTerms.flat(10L),
            members = setOf(soloMember),
        )
        val guildStall = Stall(
            id = StallId("guild"), regionId = "guild", world = "world",
            state = StallState.OWNED,
            owner = OwnerRef.guild("g1"),
            ownerSince = null, winningBid = 200L,
            rentTerms = RentTerms.flat(20L),
        )
        val unownedStall = Stall(
            id = StallId("unowned"), regionId = "unowned", world = "world",
            state = StallState.UNOWNED,
            owner = OwnerRef.unowned(),
            ownerSince = null, winningBid = 0L,
            rentTerms = RentTerms.flat(0L),
        )

        val repo = mockk<StallRepository>()
        every { repo.all() } returns listOf(soloStall, guildStall, unownedStall)

        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)

        val cmd = buildResyncCmd(repo, regionMembers)
        cmd.rgResync(sender)

        // SOLO stall: full rebuild
        verify(exactly = 1) { regionMembers.clearOwnersAndMembers("world", "solo") }
        verify(exactly = 1) { regionMembers.setOwner("world", "solo", soloOwner) }
        verify(exactly = 1) { regionMembers.addMember("world", "solo", soloMember) }

        // GUILD stall: nothing
        verify(exactly = 0) { regionMembers.clearOwnersAndMembers("world", "guild") }
        verify(exactly = 0) { regionMembers.setOwner("world", "guild", any()) }

        // UNOWNED stall: clear only
        verify(exactly = 1) { regionMembers.clearOwnersAndMembers("world", "unowned") }
    }
}