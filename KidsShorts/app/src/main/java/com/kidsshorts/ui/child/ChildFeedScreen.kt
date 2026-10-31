package com.kidsshorts.ui.child

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsshorts.player.PlayerEvents
import com.kidsshorts.player.PlayerState
import com.kidsshorts.player.PlayerWebView
import com.kidsshorts.ui.formatMinuteOfDay
import com.kidsshorts.ui.theme.ChildColors
import com.kidsshorts.ui.viewmodel.ChildUiState
import com.kidsshorts.ui.viewmodel.ChildViewModel
import com.kidsshorts.ui.viewmodel.PlayerProblem

const val EMPTY_FEED_MESSAGE = "No videos available in your approved channels and languages. Ask a parent to add more."

@Composable
fun ChildFeedScreen(vm: ChildViewModel, applicationId: String, onParentRequested: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE) vm.onForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onForeground(false)
        }
    }

    // Clear exit path: Back leads to the PIN-protected parent screen, never a dead end.
    BackHandler(onBack = onParentRequested)

    Box(Modifier.fillMaxSize().background(ChildColors.background).systemBarsPadding()) {
        when (val s = state) {
            ChildUiState.Loading -> MessagePanel("Loading…", null, onParentRequested)
            ChildUiState.Empty -> MessagePanel(EMPTY_FEED_MESSAGE, null, onParentRequested)
            ChildUiState.LimitReached -> MessagePanel(
                "That's all for today. Time for a break!",
                "Daily viewing limit reached. A parent can review it in the Parent area.",
                onParentRequested,
            )
            is ChildUiState.QuietHours -> MessagePanel(
                "Videos are resting right now.",
                "Quiet hours are on until ${formatMinuteOfDay(s.endsAtMinuteOfDay)}.",
                onParentRequested,
            )
            is ChildUiState.Playing -> PlayingPanel(s, vm, problem, applicationId, onParentRequested)
        }
    }
}

@Composable
private fun MessagePanel(title: String, subtitle: String?, onParentRequested: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = ChildColors.onBackground, fontSize = 24.sp, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle, color = ChildColors.muted, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        OutlinedButton(onClick = onParentRequested, modifier = Modifier.padding(top = 32.dp)) {
            Text("Parent area", color = ChildColors.onBackground)
        }
    }
}

@Composable
private fun PlayingPanel(
    s: ChildUiState.Playing,
    vm: ChildViewModel,
    problem: PlayerProblem?,
    applicationId: String,
    onParentRequested: () -> Unit,
) {
    var player by remember { mutableStateOf<PlayerWebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }

    val shown = rememberUpdatedState(s)
    val vmRef = rememberUpdatedState(vm)

    val events = remember {
        object : PlayerEvents {
            override fun onReady() { ready = true }

            override fun onStateChange(state: Int) {
                isPlaying = state == PlayerState.PLAYING
                if (state == PlayerState.ENDED) {
                    // No endless chains: re-cue THIS video so YouTube's end-screen suggestions never appear.
                    player?.setVideo(shown.value.video.videoId, false)
                }
            }

            override fun onError(code: Int) { vmRef.value.onPlayerError(code) }

            override fun onForeignVideo(playedVideoId: String) {
                // The embedded player moved to a video we didn't request. Put the vetted one back, paused.
                isPlaying = false
                player?.setVideo(shown.value.video.videoId, false)
            }

            override fun onAutoplayBlocked() { isPlaying = false }
            override fun onRendererGone() { vmRef.value.onRendererGone() }
        }
    }

    // Pause when the app leaves the foreground; never resume by itself.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.let { it.pause(); it.onPause() }
                Lifecycle.Event.ON_RESUME -> player?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load whenever the vetted video changes. `autoplay` is read, but deliberately not a key.
    LaunchedEffect(ready, s.video.videoId) {
        if (ready) player?.setVideo(s.video.videoId, s.autoplay)
    }

    Column(Modifier.fillMaxSize()) {
        // Info strip ABOVE the player (nothing is ever drawn over YouTube's controls or branding).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.channelTitle, color = ChildColors.onBackground, fontSize = 14.sp, maxLines = 1)
                Text(s.languageLabel, color = ChildColors.muted, fontSize = 12.sp)
            }
            TextButton(onClick = onParentRequested) { Text("Parent", color = ChildColors.muted) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerWebView(ctx).apply {
                        onSwipeUp = { vmRef.value.next() }
                        onSwipeDown = { vmRef.value.previous() }
                        start(applicationId, events)
                        player = this
                    }
                },
                onRelease = { it.release() },
            )
        }

        if (problem != null) {
            Text(
                when (problem) {
                    PlayerProblem.PLAYER_CONFIGURATION -> "The video player couldn't start. Ask a parent to check the app setup."
                    PlayerProblem.VIDEO_FAILED -> "This video couldn't be played. Try the next one."
                },
                color = ChildColors.onBackground, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }

        // Large, simple controls BELOW the player.
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigButton(if (isPlaying) "Pause" else "Play", Modifier.weight(1f)) {
                vm.onChildInteracted()
                if (isPlaying) player?.pause() else player?.play()
            }
            BigButton(if (muted) "Sound on" else "Sound off", Modifier.weight(1f)) {
                muted = !muted
                player?.setMuted(muted)
            }
            BigButton("Next", Modifier.weight(1f)) { vm.next() }
        }
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ChildColors.button, contentColor = ChildColors.onBackground),
    ) {
        Text(text, fontSize = 18.sp, style = MaterialTheme.typography.titleMedium)
    }
}
