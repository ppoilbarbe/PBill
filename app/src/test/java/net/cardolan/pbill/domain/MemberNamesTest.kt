package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberNamesTest {

    @Test
    fun normalizeRemovesEveryKindOfBlankAround() {
        assertEquals("Alice", MemberNames.normalize("  Alice "))
        assertEquals("Alice", MemberNames.normalize("\t\n\u00A0\u202F\u2003Alice\u3000\r"))
        assertEquals("Alice", MemberNames.normalize("\u200B\uFEFFAlice\u2060"))
    }

    @Test
    fun normalizeKeepsInnerSpaces() {
        assertEquals("Bob & Carol", MemberNames.normalize(" Bob & Carol "))
    }

    @Test
    fun nameIsTakenWhenEqualOnceTrimmed() {
        val others = listOf("Alice", " Bob\u00A0")
        assertTrue(MemberNames.isTaken("Alice", others))
        assertTrue(MemberNames.isTaken("\u202FAlice  ", others))
        assertTrue(MemberNames.isTaken("Bob", others))
    }

    @Test
    fun differentNamesAreFree() {
        val others = listOf("Alice", "Bob")
        assertFalse(MemberNames.isTaken("Carol", others))
        assertFalse(MemberNames.isTaken("Ali ce", others))
        assertFalse(MemberNames.isTaken("Alice", emptyList()))
    }
}
