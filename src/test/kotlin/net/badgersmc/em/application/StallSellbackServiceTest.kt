package net.badgersmc.em.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.RegionMemberSync
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StallSellbackServiceTest {

    private val actorUuid   = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val guildId     = "guild_alpha"
    private val stallId     = StallId("stall_01")

    // ---------- helpers ----------

    /** Builds an [EnthusiaMarketConfig] with rent.collectionInterval = "PT24H". */
    private fun makeConfig(): EnthusiaMarketConfig {
        val cfg = EnthusiaMarketConfig()
        cfg.rent.collectionInterval = "PT24H"
        return cfg
    }

    /** Minimal OWNED stall owned by a solo player (actor). */
    private fun soloOwnedStall(nextRentAt: Instant? = null): Stall = Stall(
        id           = stallId,
        regionId     = "stall_01",
        world        = "world",
        state        = StallState.OWNED,
        owner        = OwnerRef.solo(actorUuid),
        ownerSince   = Instant.now().minusSeconds(3600),
        winningBid   = 1000L,
        rentTerms    = RentTerms.flat(10L),
        nextRentAt   = nextRentAt,
    )

    /** Minimal OWNED stall owned by a guild. */
    private fun guildOwnedStall(): Stall = Stall(
        id           = stallId,
        regionId     = "stall_01",
        world        = "world",
        state        = StallState.OWNED,
        owner        = OwnerRef.guild(guildId),
        ownerSince   = Instant.now().minusSeconds(3600),
        winningBid   = 1000L,
        rentTerms    = RentTerms.flat(10L),
    )

    /** Builds the service wiring all deps as mocks. */
    private fun buildService(
        stall: Stall,
        depositOk: Boolean = true,
    ): Triple<StallSellbackService, StallRepository, EconomyProvider> {
        val stallRepo     = mockk<StallRepository>(relaxUnitFun = true)
        val shopRepo      = mockk<ShopRepository>(relaxUnitFun = true)
        val economy       = mockk<EconomyProvider>()
        val guildProvider = mockk<GuildProvider>(relaxed = true)
        val regionMembers = mockk<RegionMemberSync>(relaxUnitFun = true)
        val cfg           = makeConfig()

        every { stallRepo.findById(stallId) } returns stall
        every { shopRepo.findByStall(stallId.value) } returns emptyList()
        every { economy.deposit(any(), any()) } returns depositOk

        // Solo owner: canManage always true when actor matches owner
        // (no guildProvider calls needed for SOLO)

        val svc = StallSellbackService(
            stalls        = stallRepo,
            shops         = shopRepo,
            economy       = economy,
            guildProvider = guildProvider,
            config        = cfg,
            regionMembers = regionMembers,
        )
        return Triple(svc, stallRepo, economy)
    }

    // ===== 1. quote() NotAuthorised for GUILD stall =====

    @Test
    fun `quote returns NotAuthorised when stall is guild owned`() {
        val stall = guildOwnedStall()
        val (svc) = buildService(stall)

        val result = svc.quote(stallId, actorUuid)

        assertIs<StallSellbackService.QuoteResult.NotAuthorised>(result)
    }

    // ===== 2. execute() NotAuthorised for GUILD stall =====

    @Test
    fun `execute returns NotAuthorised when stall is guild owned`() {
        val stall = guildOwnedStall()
        val (svc) = buildService(stall)

        val result = svc.execute(stallId, actorUuid)

        assertIs<StallSellbackService.ExecuteResult.NotAuthorised>(result)
    }

    // ===== 3. computeRefund ceiling: 47h remaining, 24h interval → 1 refundable =====

    @Test
    fun `execute refunds 1 period for 47h remaining with 24h interval`() {
        // ceil(47/24) = 2 total periods; 2 - 1 current = 1 refundable
        // RentTerms.flat(10) → dailyRent = 10, so refund = 1 * 10 = 10
        val nextRentAt = Instant.now().plusSeconds(47L * 3600)
        val stall      = soloOwnedStall(nextRentAt = nextRentAt)
        val (svc, stallRepo, economy) = buildService(stall, depositOk = true)

        val result = svc.execute(stallId, actorUuid)

        val sold = assertIs<StallSellbackService.ExecuteResult.Sold>(result)
        assertEquals(10L, sold.refund,  "Expected 1 refundable period × 10 flat rent = 10")
        // Economy deposit must have been called with the computed refund
        verify { economy.deposit(actorUuid, 10L) }
        // Stall must have been cleared to UNOWNED
        verify { stallRepo.save(match { it.state == StallState.UNOWNED && it.owner.type == OwnerType.NONE }) }
    }

    // ===== 4. computeRefund returns 0,0 when nextRentAt is null =====

    @Test
    fun `execute issues zero refund when nextRentAt is null`() {
        val stall = soloOwnedStall(nextRentAt = null)
        val (svc, _, economy) = buildService(stall, depositOk = true)

        val result = svc.execute(stallId, actorUuid)

        val sold = assertIs<StallSellbackService.ExecuteResult.Sold>(result)
        assertEquals(0L, sold.refund, "No refund expected when nextRentAt is null")
        // deposit must NOT be called when refund == 0
        verify(exactly = 0) { economy.deposit(any(), any()) }
    }

    // ===== 5. execute() rolls back stall when economy.deposit returns false =====

    @Test
    fun `execute rolls back stall state when economy deposit fails`() {
        val nextRentAt = Instant.now().plusSeconds(47L * 3600)
        val stall      = soloOwnedStall(nextRentAt = nextRentAt)
        val (svc, stallRepo) = buildService(stall, depositOk = false)

        val result = svc.execute(stallId, actorUuid)

        assertIs<StallSellbackService.ExecuteResult.Rejected>(result)

        // First save: clears to UNOWNED
        verify { stallRepo.save(match { it.state == StallState.UNOWNED }) }
        // Second save: restores original OWNED state (rollback)
        verify { stallRepo.save(match { it.state == StallState.OWNED && it.owner == stall.owner }) }
    }
}
