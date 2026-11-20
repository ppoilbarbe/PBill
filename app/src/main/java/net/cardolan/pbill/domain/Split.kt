package net.cardolan.pbill.domain

import kotlin.math.abs

object Split {
    /**
     * Splits [amount] proportionally to [weights] so that the parts always add
     * up exactly to [amount]: each part is rounded down, then the leftover
     * minor units go to the largest remainders (ties: lowest index first).
     */
    fun proportional(amount: Long, weights: List<Int>): List<Long> {
        require(weights.isNotEmpty()) { "weights must not be empty" }
        require(weights.all { it >= 0 }) { "weights must be non-negative" }
        val total = weights.sumOf { it.toLong() }
        require(total > 0) { "total weight must be positive" }

        val sign = if (amount < 0) -1 else 1
        val absolute = abs(amount)
        val parts = LongArray(weights.size)
        val remainders = LongArray(weights.size)
        weights.forEachIndexed { i, w ->
            val product = absolute * w
            parts[i] = product / total
            remainders[i] = product % total
        }
        var leftover = absolute - parts.sum()
        val order = weights.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
        for (i in order) {
            if (leftover == 0L) break
            parts[i] += 1
            leftover -= 1
        }
        return parts.map { it * sign }
    }

    /** Same as [proportional] keyed by member id, in a stable (sorted id) order. */
    fun byWeights(amount: Long, weights: Map<String, Int>): Map<String, Long> {
        val ids = weights.keys.sorted()
        val parts = proportional(amount, ids.map { weights.getValue(it) })
        return ids.zip(parts).toMap()
    }
}
