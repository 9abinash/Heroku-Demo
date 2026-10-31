package com.kidsshorts.domain

/** Parses YouTube contentDetails.duration (e.g. PT1M5S, PT45S, P0D). Returns null if malformed. */
object Iso8601Duration {
    private val pattern = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

    fun parseSeconds(raw: String?): Int? {
        val text = raw?.trim() ?: return null
        if (text == "P" || text.endsWith("T")) return null
        val m = pattern.matchEntire(text) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        val total = g(1) * 86_400 + g(2) * 3_600 + g(3) * 60 + g(4)
        return if (total > Int.MAX_VALUE) null else total.toInt()
    }
}
