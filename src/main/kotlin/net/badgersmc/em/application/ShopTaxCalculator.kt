package net.badgersmc.em.application

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Pure tax computation for shop trades.
 *
 * @param amount      the raw trade amount
 * @param taxPct      tax rate as decimal (e.g. 0.02 = 2%)
 * @param rounding    rounding mode: "up" (ceiling), "down" (floor), "nearest"
 * @return tax amount to deduct from seller proceeds
 */
object ShopTaxCalculator {

    fun computeTax(amount: Long, taxPct: Double, rounding: String): Long {
        if (taxPct <= 0.0 || amount <= 0) return 0L
        val raw = amount * taxPct
        return when (rounding.lowercase()) {
            "up" -> ceil(raw).toLong()
            "down" -> floor(raw).toLong()
            else -> raw.roundToLong() // "nearest" or any other value
        }
    }

    /**
     * Returns the tax-inclusive price (amount + tax).
     */
    fun inclusivePrice(amount: Long, taxPct: Double, rounding: String): Long {
        return amount + computeTax(amount, taxPct, rounding)
    }

    /**
     * Returns what the seller receives after tax deduction.
     */
    fun sellerProceeds(amount: Long, taxPct: Double, rounding: String): Long {
        return amount - computeTax(amount, taxPct, rounding)
    }
}
