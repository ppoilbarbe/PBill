package net.cardolan.pbill.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ParticipationTest {

    private fun day(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun memberSharesExpensesFromTheirStartDate() {
        val start = day("2026-07-10")
        assertFalse(Participation.sharesByDefault(100, start, null, day("2026-07-09")))
        assertTrue(Participation.sharesByDefault(100, start, null, day("2026-07-10")))
        assertTrue(Participation.sharesByDefault(100, start, null, day("2027-01-01")))
    }

    @Test
    fun memberStopsSharingAfterTheirEndDate() {
        val start = day("2026-07-10")
        val end = day("2026-07-15")
        assertTrue(Participation.sharesByDefault(100, start, end, day("2026-07-15")))
        assertFalse(Participation.sharesByDefault(100, start, end, day("2026-07-16")))
        // A single-day participation
        assertTrue(Participation.sharesByDefault(100, start, start, start))
    }

    @Test
    fun zeroWeightNeverSharesByDefault() {
        assertFalse(Participation.sharesByDefault(0, day("2026-07-10"), null, day("2026-07-12")))
    }

    @Test
    fun periodValidity() {
        assertTrue(Participation.isValidPeriod(day("2026-07-10"), null))
        assertTrue(Participation.isValidPeriod(day("2026-07-10"), day("2026-07-10")))
        assertFalse(Participation.isValidPeriod(day("2026-07-10"), day("2026-07-09")))
    }
}
