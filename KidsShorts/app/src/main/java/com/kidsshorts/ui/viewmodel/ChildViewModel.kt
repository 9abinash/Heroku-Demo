package com.kidsshorts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidsshorts.data.repo.ChildVideo
import com.kidsshorts.di.AppContainer
import com.kidsshorts.domain.LanguageCatalog
import com.kidsshorts.domain.LanguageStatus
import com.kidsshorts.domain.PlaybackGate
import com.kidsshorts.domain.VideoItem
import com.kidsshorts.player.PlayerError
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ChildUiState {
    data object Loading : ChildUiState
    /** No eligible videos. NEVER padded with anything else. */
    data object Empty : ChildUiState
    data object LimitReached : ChildUiState
    data class QuietHours(val endsAtMinuteOfDay: Int) : ChildUiState
    data class Playing(
        val video: VideoItem,
        val channelTitle: String,
        val languageLabel: String,
        /** false until the child has interacted: the first video is only cued (limits pre-interaction data). */
        val autoplay: Boolean,
    ) : ChildUiState
}

enum class PlayerProblem { PLAYER_CONFIGURATION, VIDEO_FAILED }

class ChildViewModel(private val c: AppContainer) : ViewModel() {

    private val feed: StateFlow<List<ChildVideo>?> =
        c.feedRepository.childFeed.map<List<ChildVideo>, List<ChildVideo>?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentId = MutableStateFlow<String?>(null)
    private val interacted = MutableStateFlow(false)
    private val foreground = MutableStateFlow(false)

    private val _problem = MutableStateFlow<PlayerProblem?>(null)
    val problem: StateFlow<PlayerProblem?> = _problem.asStateFlow()

    val state: StateFlow<ChildUiState> = combine(c.screenTimeRepository.gate, feed, currentId, interacted) { gate, list, cur, did ->
        when (gate) {
            PlaybackGate.LimitReached -> ChildUiState.LimitReached
            is PlaybackGate.QuietHoursActive -> ChildUiState.QuietHours(gate.endsAtMinuteOfDay)
            is PlaybackGate.Open -> when {
                list == null -> ChildUiState.Loading
                list.isEmpty() -> ChildUiState.Empty
                else -> {
                    // If the remembered video is no longer eligible it simply isn't in `list`.
                    val chosen = list.firstOrNull { it.eligible.video.videoId == cur } ?: list.first()
                    ChildUiState.Playing(
                        video = chosen.eligible.video,
                        channelTitle = chosen.channelTitle,
                        languageLabel = label(chosen),
                        autoplay = did,
                    )
                }
            }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, ChildUiState.Loading)

    init {
        // Count viewing time ONLY while the feed is on screen AND a video is actually showing.
        viewModelScope.launch {
            combine(foreground, state.map { it is ChildUiState.Playing }.distinctUntilChanged()) { fg, playing -> fg && playing }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    val tracker = c.screenTimeRepository.newTracker()
                    tracker.start()
                    try {
                        while (true) {
                            delay(5_000)
                            tracker.tick()
                        }
                    } finally {
                        withContext(NonCancellable) { tracker.stop() }
                    }
                }
        }
    }

    fun onForeground(isForeground: Boolean) { foreground.value = isForeground }

    fun next() = step(+1)
    fun previous() = step(-1)

    private fun step(delta: Int) {
        val list = feed.value.orEmpty()
        val shown = (state.value as? ChildUiState.Playing)?.video?.videoId
        if (list.isEmpty() || shown == null) return
        val index = list.indexOfFirst { it.eligible.video.videoId == shown }.coerceAtLeast(0)
        val target = list[(index + delta + list.size) % list.size]
        interacted.value = true // moving on is explicit playback intent
        _problem.value = null
        currentId.value = target.eligible.video.videoId
    }

    /** Child pressed play / pause / mute: counts as interaction so later videos may autoplay. */
    fun onChildInteracted() { interacted.value = true }

    fun onPlayerError(code: Int) {
        val shown = (state.value as? ChildUiState.Playing)?.video?.videoId ?: return
        when {
            code == PlayerError.CLIENT_IDENTITY -> _problem.value = PlayerProblem.PLAYER_CONFIGURATION
            PlayerError.isVideoUnavailable(code) -> viewModelScope.launch {
                // The feed flow re-emits without this video and the state moves on by itself.
                c.feedRepository.markPlayerUnavailable(shown)
            }
            else -> _problem.value = PlayerProblem.VIDEO_FAILED
        }
    }

    fun onRendererGone() { _problem.value = PlayerProblem.VIDEO_FAILED }

    private fun label(v: ChildVideo): String = when (v.eligible.language.status) {
        LanguageStatus.UNKNOWN, LanguageStatus.CONFLICTING -> "Language unknown"
        else -> LanguageCatalog.label(v.eligible.language.languageCode)
    }
}
