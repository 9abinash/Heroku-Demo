package com.kidsshorts.domain

/**
 * "Safe normalisation" of language tags. It only removes *notation* differences
 * (case, "_" vs "-", region/script subtags, a few legacy/ISO 639-2 synonyms for catalogue
 * languages). It never maps one language to a different, "similar" language.
 */
object LanguageNormalizer {
    private val undetermined = setOf("und", "zxx", "mul", "mis")
    private val primaryPattern = Regex("[a-z]{2,3}")
    private val subtagPattern = Regex("[a-z0-9]{1,8}")

    /** Deprecated ISO 639-1 codes that YouTube/legacy systems may still emit. */
    private val legacy = mapOf("iw" to "he", "in" to "id", "ji" to "yi", "jw" to "jv", "mo" to "ro")

    /** Exact ISO 639-2/3 synonyms for the languages in [LanguageCatalog]. */
    private val synonyms = mapOf(
        "eng" to "en", "hin" to "hi", "ben" to "bn", "ori" to "or", "ory" to "or",
        "tam" to "ta", "tel" to "te", "kan" to "kn", "mal" to "ml", "mar" to "mr",
        "guj" to "gu", "pan" to "pa", "urd" to "ur", "asm" to "as", "san" to "sa",
        "nep" to "ne", "sin" to "si", "spa" to "es", "fra" to "fr", "fre" to "fr",
        "deu" to "de", "ger" to "de", "ara" to "ar", "por" to "pt", "rus" to "ru",
        "jpn" to "ja", "kor" to "ko", "zho" to "zh", "chi" to "zh", "ind" to "id",
    )

    /** Returns the primary language subtag, or null if the input is missing/invalid/undetermined. */
    fun normalize(raw: String?): String? {
        val cleaned = raw?.trim()?.lowercase()?.replace('_', '-') ?: return null
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('-')
        val primary = parts.first()
        if (!primaryPattern.matches(primary)) return null
        if (parts.drop(1).any { !subtagPattern.matches(it) }) return null
        if (primary in undetermined) return null
        return legacy[primary] ?: synonyms[primary] ?: primary
    }
}
