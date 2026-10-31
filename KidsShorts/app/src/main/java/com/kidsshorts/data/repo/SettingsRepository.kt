package com.kidsshorts.data.repo

import com.kidsshorts.data.db.SettingsDao
import com.kidsshorts.data.db.SettingsEntity
import com.kidsshorts.domain.LanguageNormalizer
import com.kidsshorts.domain.LanguagePolicy
import com.kidsshorts.domain.QuietHours
import com.kidsshorts.domain.ScreenTimeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsRepository(
    private val dao: SettingsDao,
    private val audit: AuditRepository,
) {
    private val mutex = Mutex()

    /** Strictest defaults: nothing allowed, unknown language blocked, 30 minutes/day. */
    private val defaults = SettingsEntity(
        allowedLanguages = "", preferredLanguage = null, blockUnknownLanguage = true,
        dailyLimitMinutes = ScreenTimeSettings.DEFAULT.dailyLimitMinutes,
        quietEnabled = false,
        quietStartMinute = QuietHours.DEFAULT.startMinuteOfDay,
        quietEndMinute = QuietHours.DEFAULT.endMinuteOfDay,
    )

    private val settings: Flow<SettingsEntity> = dao.observe().map { it ?: defaults }

    val languagePolicy: Flow<LanguagePolicy> = settings.map { it.toLanguagePolicy() }.distinctUntilChanged()

    val screenTime: Flow<ScreenTimeSettings> = settings.map {
        ScreenTimeSettings(it.dailyLimitMinutes, QuietHours(it.quietEnabled, it.quietStartMinute, it.quietEndMinute))
    }.distinctUntilChanged()

    private suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
        mutex.withLock { dao.upsert(transform(dao.get() ?: defaults)) }
    }

    suspend fun setAllowedLanguages(codes: Set<String>) {
        val normalized = codes.mapNotNull(LanguageNormalizer::normalize).toSortedSet()
        update { current ->
            current.copy(
                allowedLanguages = normalized.joinToString(","),
                // The preferred language can never outlive its place in the allowlist.
                preferredLanguage = current.preferredLanguage?.takeIf { it in normalized },
            )
        }
        audit.log(AuditType.LANGUAGES_CHANGED, "Allowed languages: ${normalized.joinToString(", ").ifEmpty { "none" }}")
    }

    suspend fun setPreferredLanguage(code: String?) {
        val normalized = LanguageNormalizer.normalize(code)
        update { current ->
            val allowed = current.toLanguagePolicy().allowedLanguageCodes
            current.copy(preferredLanguage = normalized?.takeIf { it in allowed })
        }
        audit.log(AuditType.LANGUAGE_POLICY_CHANGED, "Preferred language: ${normalized ?: "none"}")
    }

    suspend fun setBlockUnknownLanguage(block: Boolean) {
        update { it.copy(blockUnknownLanguage = block) }
        audit.log(AuditType.LANGUAGE_POLICY_CHANGED, "Block unknown language: ${if (block) "ON" else "OFF"}")
    }

    suspend fun setDailyLimitMinutes(minutes: Int) {
        val safe = minutes.coerceIn(1, 24 * 60)
        update { it.copy(dailyLimitMinutes = safe) }
        audit.log(AuditType.SCREEN_TIME_SETTINGS, "Daily limit: $safe min")
    }

    suspend fun setQuietHours(q: QuietHours) {
        update {
            it.copy(
                quietEnabled = q.enabled,
                quietStartMinute = q.startMinuteOfDay.coerceIn(0, 1439),
                quietEndMinute = q.endMinuteOfDay.coerceIn(0, 1439),
            )
        }
        audit.log(
            AuditType.SCREEN_TIME_SETTINGS,
            if (q.enabled) "Quiet hours ON ${fmt(q.startMinuteOfDay)}-${fmt(q.endMinuteOfDay)}" else "Quiet hours OFF",
        )
    }

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun SettingsEntity.toLanguagePolicy() = LanguagePolicy(
        allowedLanguageCodes = allowedLanguages.split(',').mapNotNull(LanguageNormalizer::normalize).toSet(),
        preferredLanguageCode = LanguageNormalizer.normalize(preferredLanguage),
        blockUnknownLanguage = blockUnknownLanguage,
    )
}
