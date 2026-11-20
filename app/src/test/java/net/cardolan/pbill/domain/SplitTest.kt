package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTest {

    @Test
    fun equalWeightsSplitEvenly() {
        assertEquals(listOf(500L, 500L), Split.proportional(1000, listOf(100, 100)))
    }

    @Test
    fun coupleWeighsTwiceAsMuch() {
        // Couple (2) + single (1) share 90.00
        assertEquals(listOf(6000L, 3000L), Split.proportional(9000, listOf(200, 100)))
    }

    @Test
    fun fractionalWeights() {
        // Couple with child (2.5) + couple (2) + single (1) share 110.00
        assertEquals(listOf(5000L, 4000L, 2000L), Split.proportional(11000, listOf(250, 200, 100)))
    }

    @Test
    fun leftoverCentsKeepTheTotalExact() {
        val parts = Split.proportional(1000, listOf(100, 100, 100))
        assertEquals(listOf(334L, 333L, 333L), parts)
        assertEquals(1000L, parts.sum())
    }

    @Test
    fun leftoverGoesToLargestRemainder() {
        // 1.00 split 1 / 2: exact parts 33.33 and 66.66, the second remainder is larger
        assertEquals(listOf(33L, 67L), Split.proportional(100, listOf(100, 200)))
    }

    @Test
    fun negativeAmountsAreMirrored() {
        assertEquals(listOf(-334L, -333L, -333L), Split.proportional(-1000, listOf(100, 100, 100)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroTotalWeightIsRejected() {
        Split.proportional(100, listOf(0, 0))
    }
}
