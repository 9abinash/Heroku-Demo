package com.kidsshorts.player

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kidsshorts.domain.VideoIds
import kotlin.math.abs

/**
 * WebView hosting the official YouTube IFrame player.
 *  - Swipes are detected in dispatchTouchEvent() and then passed on untouched, so no transparent
 *    layer sits on top of YouTube's controls or branding.
 *  - Top-level navigation away from our page is blocked (no way into the wider YouTube site/app).
 */
class PlayerWebView(context: Context) : WebView(context) {

    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                val farEnough = abs(dy) > 96 * density
                val mostlyVertical = abs(dy) > 2 * abs(dx)
                val fastEnough = abs(velocityY) > 600 * density
                if (!(farEnough && mostlyVertical && fastEnough)) return false
                if (dy < 0) onSwipeUp?.invoke() else onSwipeDown?.invoke()
                return true
            }
        },
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun start(applicationId: String, events: PlayerEvents) {
        settings.apply {
            javaScriptEnabled = true            // required by the IFrame Player API
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(this@PlayerWebView, false)
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(0xFF000000.toInt())

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Sub-frame: only YouTube's own embed document may load. Main frame: nothing may navigate away.
                return if (request.isForMainFrame) true else !isYouTubeEmbedUrl(request.url)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                events.onRendererGone()
                return true
            }
        }
        addJavascriptInterface(PlayerBridge(events), PlayerHtml.BRIDGE_NAME)
        loadDataWithBaseURL(PlayerHtml.baseUrl(applicationId), PlayerHtml.build(), "text/html", "utf-8", null)
    }

    /** [autoplay]=false only cues the video (limits data sent to YouTube until the child presses play). */
    fun setVideo(videoId: String, autoplay: Boolean) {
        if (!VideoIds.isValid(videoId)) return // never interpolate unvalidated text into JavaScript
        evaluateJavascript("kidsSetVideo('$videoId', $autoplay);", null)
    }

    fun play() = evaluateJavascript("kidsPlay();", null)
    fun pause() = evaluateJavascript("kidsPause();", null)
    fun stopPlayback() = evaluateJavascript("kidsStop();", null)
    fun setMuted(muted: Boolean) = evaluateJavascript("kidsMute($muted);", null)

    fun release() {
        runCatching {
            removeJavascriptInterface(PlayerHtml.BRIDGE_NAME)
            stopLoading()
            loadUrl("about:blank")
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
    }

    private fun isYouTubeEmbedUrl(uri: Uri): Boolean =
        uri.scheme == "https" &&
            uri.host in setOf("www.youtube-nocookie.com", "www.youtube.com") &&
            uri.path?.startsWith("/embed/") == true
}
