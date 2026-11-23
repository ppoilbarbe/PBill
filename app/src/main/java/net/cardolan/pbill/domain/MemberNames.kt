package net.cardolan.pbill.domain

/** Member names: trimmed, and unique within a tab. */
object MemberNames {

    /**
     * Blank characters removed around a name: every Unicode whitespace (spaces, no-break and
     * narrow no-break spaces, tabs, line breaks…) plus the invisible zero-width characters.
     */
    private fun isBlank(c: Char): Boolean = c.isWhitespace() || c in "\u200B\u200C\u200D\u2060\uFEFF\u180E"

    fun normalize(name: String): String = name.trim(::isBlank)

    /** Whether [name], once normalized, is already used by one of [otherNames] (compared normalized too). */
    fun isTaken(name: String, otherNames: Collection<String>): Boolean {
        val normalized = normalize(name)
        return otherNames.any { normalize(it) == normalized }
    }
}
