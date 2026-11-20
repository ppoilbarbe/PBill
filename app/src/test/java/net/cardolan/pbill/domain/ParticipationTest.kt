package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ParticipationTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun day(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun at(dateTime: String) = LocalDateTime.parse(dateTime).atZone(paris).toInstant().toEpochMilli()

    @Test
    fun joinDayUsesTheLocalDate() {
        // 00:30 in Paris is still the previous day in UTC
        assertEquals(day("2026-07-10"), Participation.joinDay(at("2026-07-10T00:30"), paris))
        assertEquals(day("2026-07-10"), Participation.joinDay(at("2026-07-10T23:50"), paris))
    }

    @Test
    fun memberSharesExpensesFromTheDayTheyJoined() {
        val joined = at("2026-07-10T15:00")
        assertFalse(Participation.sharesByDefault(100, joined, day("2026-07-09"), paris))
        assertTrue(Participation.sharesByDefault(100, joined, day("2026-07-10"), paris))
        assertTrue(Participation.sharesByDefault(100, joined, day("2026-07-12"), paris))
    }

    @Test
    fun zeroWeightNeverSharesByDefault() {
        assertFalse(Participation.sharesByDefault(0, at("2026-07-10T15:00"), day("2026-07-12"), paris))
    }
}
