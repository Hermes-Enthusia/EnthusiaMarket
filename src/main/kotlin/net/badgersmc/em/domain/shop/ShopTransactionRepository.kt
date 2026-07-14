package net.badgersmc.em.domain.shop

import java.util.UUID

/** Average price stats for a sell item over a time window. */
data class PriceStats(val avgPrice: Double, val sampleCount: Int)

interface ShopTransactionRepository {
    fun record(tx: ShopTransaction): ShopTransaction
    /** Newest-first, paged. */
    fun findByOwner(owner: UUID, limit: Int, offset: Int): List<ShopTransaction>
    fun countUnnotified(owner: UUID): Int
    fun markNotified(owner: UUID)
    /** Delete rows older than [beforeMs]; returns rows removed. */
    fun prune(beforeMs: Long): Int
    /** Average sell price for [item] between [fromMs] (inclusive) and [toMs] (exclusive). */
    fun avgPriceInWindow(item: String, fromMs: Long, toMs: Long): PriceStats?
}
