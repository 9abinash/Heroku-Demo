package com.kidsshorts.player

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/** YouTube IFrame player state codes (documented values). */
object PlayerState {
    const val UNSTARTED = -1
    const val ENDED = 0
    const val PLAYING = 1
    const val PAUSED = 2
    const val BUFFERING = 3
    const val CUED = 5
}

/** IFrame onError codes that matter to us. */
object PlayerError {
    const val INVALID_PARAMETER = 2
    const val HTML5_ERROR = 5
    const val NOT_FOUND_OR_PRIVATE = 100
    const val EMBEDDING_DISABLED_101 = 101
    const val EMBEDDING_DISABLED_150 = 150
    const val EMBEDDING_DISABLED_152 = 152
    /** Missing Referer / client identity: an app configuration problem, not a problem with the video. */
    const val CLIENT_IDENTITY = 153

    /** Errors that mean "this specific video cannot be played here": mark it unavailable and move on. */
    fun isVideoUnavailable(code: Int) = code in setOf(NOT_FOUND_OR_PRIVATE, EMBEDDING_DISABLED_101, EMBEDDING_DISABLED_150, EMBEDDING_DISABLED_152)
}

interface PlayerEvents {
    fun onReady()
    fun onStateChange(state: Int)
    fun onError(code: Int)
    /** The player is playing a video the app did not request (e.g. a tapped suggestion). */
    fun onForeignVideo(playedVideoId: String)
    fun onAutoplayBlocked()
    fun onRendererGone()
}

/** Receives JS callbacks (on a WebView background thread) and re-posts them to the main thread. */
class PlayerBridge(private val events: PlayerEvents) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface fun onReady() { main.post { events.onReady() } }
    @JavascriptInterface fun onState(state: Int) { main.post { events.onStateChange(state) } }
    @JavascriptInterface fun onError(code: Int) { main.post { events.onError(code) } }
    @JavascriptInterface fun onForeignVideo(id: String?) { main.post { events.onForeignVideo(id.orEmpty()) } }
    @JavascriptInterface fun onAutoplayBlocked() { main.post { events.onAutoplayBlocked() } }
}
