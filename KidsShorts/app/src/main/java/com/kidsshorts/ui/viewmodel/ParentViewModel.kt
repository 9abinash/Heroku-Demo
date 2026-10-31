package com.kidsshorts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidsshorts.data.db.AuditEntity
import com.kidsshorts.data.repo.FeedSnapshot
import com.kidsshorts.data.repo.LookupResult
import com.kidsshorts.data.repo.SyncOutcome
import com.kidsshorts.data.repo.needsReview
import com.kidsshorts.data.youtube.ChannelInfo
import com.kidsshorts.di.AppContainer
import com.kidsshorts.domain.ApprovedChannel
import com.kidsshorts.domain.EligibilityResult
import com.kidsshorts.domain.QuietHours
import com.kidsshorts.domain.ScreenTimeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelRow(
    val channel: ApprovedChannel,
    val eligibleCount: Int,
    val totalCount: Int,
    val needsReviewCount: Int,
)

sealed interface LookupUi {
    data object Idle : LookupUi
    data object Loading : LookupUi
    data class Found(val info: ChannelInfo, val alreadyApproved: Boolean) : LookupUi
    data class Error(val message: String) : LookupUi
}

class ParentViewModel(private val c: AppContainer) : ViewModel() {

    val snapshot: StateFlow<FeedSnapshot?> =
        c.feedRepository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channelRows: StateFlow<List<ChannelRow>> = c.feedRepository.snapshot.map { snap ->
        snap.channels.map { ch ->
            val mine = snap.evaluations.filter { it.video.channelId == ch.channelId }
            ChannelRow(
                channel = ch,
                eligibleCount = mine.count { it.result is EligibilityResult.Eligible },
                totalCount = mine.size,
                needsReviewCount = mine.count { it.needsReview() },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val screenTime: StateFlow<ScreenTimeSettings> =
        c.settingsRepository.screenTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeSettings.DEFAULT)

    val usedTodaySeconds: StateFlow<Int> =
        c.screenTimeRepository.usedTodaySeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val audit: StateFlow<List<AuditEntity>> =
        c.auditRepository.recent(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lookup = MutableStateFlow<LookupUi>(LookupUi.Idle)
    val lookup: StateFlow<LookupUi> = _lookup.asStateFlow()

    fun hasApiKey(): Boolean = c.apiKeyStore.get().isNotBlank()

    // ---- channels
    fun lookupChannel(raw: String) {
        _lookup.value = LookupUi.Loading
        viewModelScope.launch {
            _lookup.value = when (val r = c.channelRepository.lookup(raw)) {
                is LookupResult.Found -> LookupUi.Found(r.info, r.alreadyApproved)
                is LookupResult.Rejected -> LookupUi.Error(r.message)
                is LookupResult.Failed -> LookupUi.Error(r.message)
            }
        }
    }

    fun clearLookup() { _lookup.value = LookupUi.Idle }

    fun approveChannel(info: ChannelInfo, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val outcome = c.channelRepository.approve(info)
            _busy.value = false
            _message.value = when (outcome) {
                is SyncOutcome.Done -> "${info.title} approved. Found ${outcome.videosFound} videos."
                is SyncOutcome.Failed -> "${info.title} approved, but videos couldn't be loaded: ${outcome.message}"
            }
            _lookup.value = LookupUi.Idle
            onDone()
        }
    }

    fun refreshChannel(channelId: String) {
        viewModelScope.launch {
            _busy.value = true
            val outcome = c.channelRepository.sync(channelId)
            _busy.value = false
            _message.value = when (outcome) {
                is SyncOutcome.Done -> "Updated: ${outcome.videosFound} videos checked."
                is SyncOutcome.Failed -> outcome.message
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _busy.value = true
            var failure: String? = null
            for (row in channelRows.value.filter { it.channel.isEnabled }) {
                val o = c.channelRepository.sync(row.channel.channelId)
                if (o is SyncOutcome.Failed) { failure = o.message; if (o.quotaExceeded) break }
            }
            _busy.value = false
            _message.value = failure ?: "All channels updated."
        }
    }

    fun setChannelEnabled(channelId: String, enabled: Boolean) {
        viewModelScope.launch { c.channelRepository.setEnabled(channelId, enabled) }
    }

    fun removeChannel(channelId: String) {
        viewModelScope.launch { c.channelRepository.remove(channelId) }
    }

    // ---- languages
    fun setAllowedLanguages(codes: Set<String>) { viewModelScope.launch { c.settingsRepository.setAllowedLanguages(codes) } }
    fun setPreferredLanguage(code: String?) { viewModelScope.launch { c.settingsRepository.setPreferredLanguage(code) } }
    fun setBlockUnknown(block: Boolean) { viewModelScope.launch { c.settingsRepository.setBlockUnknownLanguage(block) } }

    // ---- videos
    fun approveVideo(videoId: String, title: String, languageCode: String?) {
        viewModelScope.launch { c.reviewRepository.approve(videoId, languageCode, title) }
    }
    fun blockVideo(videoId: String, title: String) { viewModelScope.launch { c.reviewRepository.block(videoId, title) } }
    fun undoDecision(videoId: String, title: String) { viewModelScope.launch { c.reviewRepository.clearDecision(videoId, title) } }

    // ---- screen time
    fun setDailyLimit(minutes: Int) { viewModelScope.launch { c.settingsRepository.setDailyLimitMinutes(minutes) } }
    fun setQuietHours(q: QuietHours) { viewModelScope.launch { c.settingsRepository.setQuietHours(q) } }
    fun grantExtraTime(minutes: Int, quietBypassMinutes: Int = 0) {
        viewModelScope.launch {
            c.screenTimeRepository.grantExtraTime(minutes, quietBypassMinutes)
            _message.value = "Override recorded in the activity log."
        }
    }

    // ---- data & key
    fun saveApiKey(key: String) {
        c.apiKeyStore.set(key)
        viewModelScope.launch {
            c.auditRepository.log(com.kidsshorts.data.repo.AuditType.API_KEY_CHANGED, if (key.isBlank()) "API key removed" else "API key updated")
        }
        _message.value = if (key.isBlank()) "API key removed." else "API key saved."
    }

    fun resetAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            c.resetAllData()
            onDone()
        }
    }
}
