package com.kidsshorts.domain

object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /** Returns a parent-facing error message, or null if the PIN is acceptable. */
    fun validate(pin: String): String? {
        if (pin.length !in MIN_LENGTH..MAX_LENGTH || !pin.all { it in '0'..'9' }) {
            return "Use $MIN_LENGTH to $MAX_LENGTH digits."
        }
        if (pin.toSet().size == 1) return "Choose a PIN that isn't all the same digit."
        val diffs = pin.zipWithNext { a, b -> b - a }
        if (diffs.all { it == 1 } || diffs.all { it == -1 }) return "Choose a PIN that isn't a simple sequence."
        return null
    }
}

/** YouTube video IDs are 11 chars of [A-Za-z0-9_-]. Validated before any ID reaches JavaScript. */
object VideoIds {
    private val pattern = Regex("^[A-Za-z0-9_-]{11}$")
    fun isValid(id: String?): Boolean = id != null && pattern.matches(id)
}
