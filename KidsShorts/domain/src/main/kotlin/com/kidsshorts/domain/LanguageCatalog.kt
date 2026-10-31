package com.kidsshorts.domain

data class LanguageOption(val code: String, val englishName: String, val nativeName: String)

object LanguageCatalog {
    /** Languages pre-listed for the parent (India-focused, as specified). */
    val india: List<LanguageOption> = listOf(
        LanguageOption("en", "English", "English"),
        LanguageOption("hi", "Hindi", "हिन्दी"),
        LanguageOption("bn", "Bengali", "বাংলা"),
        LanguageOption("or", "Odia", "ଓଡ଼ିଆ"),
        LanguageOption("ta", "Tamil", "தமிழ்"),
        LanguageOption("te", "Telugu", "తెలుగు"),
        LanguageOption("kn", "Kannada", "ಕನ್ನಡ"),
        LanguageOption("ml", "Malayalam", "മലയാളം"),
        LanguageOption("mr", "Marathi", "मराठी"),
        LanguageOption("gu", "Gujarati", "ગુજરાતી"),
        LanguageOption("pa", "Punjabi", "ਪੰਜਾਬੀ"),
        LanguageOption("ur", "Urdu", "اردو"),
        LanguageOption("as", "Assamese", "অসমীয়া"),
        LanguageOption("sa", "Sanskrit", "संस्कृतम्"),
    )

    /** Additional languages a parent may add. Only ISO 639-1 codes the evaluator can match exactly. */
    val additional: List<LanguageOption> = listOf(
        LanguageOption("ne", "Nepali", "नेपाली"),
        LanguageOption("si", "Sinhala", "සිංහල"),
        LanguageOption("es", "Spanish", "Español"),
        LanguageOption("fr", "French", "Français"),
        LanguageOption("de", "German", "Deutsch"),
        LanguageOption("pt", "Portuguese", "Português"),
        LanguageOption("ru", "Russian", "Русский"),
        LanguageOption("ar", "Arabic", "العربية"),
        LanguageOption("id", "Indonesian", "Bahasa Indonesia"),
        LanguageOption("ja", "Japanese", "日本語"),
        LanguageOption("ko", "Korean", "한국어"),
        LanguageOption("zh", "Chinese", "中文"),
    )

    val all: List<LanguageOption> = india + additional

    fun find(code: String?): LanguageOption? {
        val n = LanguageNormalizer.normalize(code) ?: return null
        return all.firstOrNull { it.code == n }
    }

    /** "Hindi (hi)" style label; unknown codes are shown verbatim, never guessed. */
    fun label(code: String?): String {
        val n = LanguageNormalizer.normalize(code) ?: return "Unknown"
        val opt = all.firstOrNull { it.code == n }
        return if (opt != null) "${opt.englishName} (${opt.code})" else n
    }
}
