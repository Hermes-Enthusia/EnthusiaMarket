package net.badgersmc.em.application

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopTaxCalculatorTest {

    @Test fun `zero tax pct returns zero tax`() {
        assertEquals(0L, ShopTaxCalculator.computeTax(100L, 0.0, "nearest"))
    }

    @Test fun `negative tax pct returns zero tax`() {
        assertEquals(0L, ShopTaxCalculator.computeTax(100L, -0.05, "nearest"))
    }

    @Test fun `zero amount returns zero tax`() {
        assertEquals(0L, ShopTaxCalculator.computeTax(0L, 0.02, "nearest"))
    }

    @Test fun `nearest rounding rounds to nearest`() {
        // 100 * 0.02 = 2.0 -> 2
        assertEquals(2L, ShopTaxCalculator.computeTax(100L, 0.02, "nearest"))
        // 150 * 0.02 = 3.0 -> 3
        assertEquals(3L, ShopTaxCalculator.computeTax(150L, 0.02, "nearest"))
    }

    @Test fun `up rounding always rounds up`() {
        // 101 * 0.02 = 2.02 -> 3
        assertEquals(3L, ShopTaxCalculator.computeTax(101L, 0.02, "up"))
        // 100 * 0.02 = 2.0 -> 2 (exact, no rounding needed)
        assertEquals(2L, ShopTaxCalculator.computeTax(100L, 0.02, "up"))
    }

    @Test fun `down rounding always rounds down`() {
        // 101 * 0.02 = 2.02 -> 2
        assertEquals(2L, ShopTaxCalculator.computeTax(101L, 0.02, "down"))
        // 150 * 0.02 = 3.0 -> 3
        assertEquals(3L, ShopTaxCalculator.computeTax(150L, 0.02, "down"))
    }

    @Test fun `inclusive price adds tax`() {
        assertEquals(102L, ShopTaxCalculator.inclusivePrice(100L, 0.02, "nearest"))
    }

    @Test fun `seller proceeds deducts tax`() {
        assertEquals(98L, ShopTaxCalculator.sellerProceeds(100L, 0.02, "nearest"))
    }

    @Test fun `5 percent tax on 64 gold`() {
        // 64 * 0.05 = 3.2
        assertEquals(3L, ShopTaxCalculator.computeTax(64L, 0.05, "down"))
        assertEquals(4L, ShopTaxCalculator.computeTax(64L, 0.05, "up"))
        assertEquals(3L, ShopTaxCalculator.computeTax(64L, 0.05, "nearest"))
    }

    @Test fun `case insensitive rounding mode`() {
        assertEquals(3L, ShopTaxCalculator.computeTax(101L, 0.02, "UP"))
        assertEquals(2L, ShopTaxCalculator.computeTax(101L, 0.02, "Down"))
    }
}
