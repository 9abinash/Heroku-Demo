package com.kidsshorts.domain

/**
 * Conservative, transparent language verification.
 *
 * Signals available with an API key: snippet.defaultAudioLanguage and snippet.defaultLanguage.
 *  - defaultAudioLanguage is the only signal about *spoken audio*, so it is the only one that can
 *    verify a language by itself.
 *  - defaultLanguage describes the title/description language. It is never used to establish a
 *    language (that would be inferring from text), only to detect a CONFLICT with the audio signal.
 *  - Title/description text is never analysed. Captions need OAuth and are not used.
 *  - A parent's explicit approval + language label on that exact video outranks all metadata.
 */
object LanguageResolver {

    fun resolve(video: VideoItem, decision: VideoDecision?): LanguageResolution {
        parentConfirmed(decision)?.let { return LanguageResolution(LanguageStatus.PARENT_CONFIRMED, it) }

        val audio = LanguageNormalizer.normalize(video.audioLanguage)
        val meta = LanguageNormalizer.normalize(video.metadataLanguage)

        return when {
            audio != null && meta != null && audio != meta ->
                LanguageResolution(LanguageStatus.CONFLICTING, null)
            audio != null ->
                LanguageResolution(LanguageStatus.VERIFIED_BY_METADATA, audio)
            else ->
                // Missing audio signal. A bare defaultLanguage is NOT evidence of spoken language.
                LanguageResolution(LanguageStatus.UNKNOWN, null)
        }
    }

    /** Only an APPROVED decision with a valid language label counts as confirmation. */
    private fun parentConfirmed(decision: VideoDecision?): String? {
        if (decision == null || decision.decision != ParentDecision.APPROVED) return null
        return LanguageNormalizer.normalize(decision.confirmedLanguageCode)
    }
}
