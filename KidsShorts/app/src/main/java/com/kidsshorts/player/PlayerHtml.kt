package com.kidsshorts.player

/**
 * HTML page hosting the OFFICIAL YouTube IFrame player (privacy-enhanced youtube-nocookie host).
 * Nothing is drawn over the player: YouTube's own controls and branding stay visible and untouched.
 *
 * Referer / client identity: YouTube rejects embeds that send no Referer (IFrame error 153). Loading
 * this page with loadDataWithBaseURL(baseUrl(applicationId), ...) makes the WebView send that base URL.
 *
 * Foreign-video guard: the embedded player can show same-channel suggestions that a child could tap.
 * Those videos were NOT vetted by the eligibility function. The page therefore compares the video the
 * player is actually playing with the one the app asked for and reports/stops any mismatch.
 */
object PlayerHtml {
    const val BRIDGE_NAME = "Native"

    fun baseUrl(applicationId: String) = "https://$applicationId"

    fun build(): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
  html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
  #player, iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
</style>
</head>
<body>
<div id="player"></div>
<script>
  var player = null, ready = false, expected = null, pending = null, pendingAutoplay = false, graceUntil = 0;

  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%', height: '100%',
      host: 'https://www.youtube-nocookie.com',
      playerVars: { playsinline: 1, rel: 0, fs: 0, iv_load_policy: 3, controls: 1, enablejsapi: 1 },
      events: {
        onReady: function () {
          ready = true;
          $BRIDGE_NAME.onReady();
          if (pending) { var id = pending; pending = null; kidsSetVideo(id, pendingAutoplay); }
        },
        onStateChange: function (e) { if (guard()) $BRIDGE_NAME.onState(e.data); },
        onError: function (e) { $BRIDGE_NAME.onError(e.data); },
        onAutoplayBlocked: function () { $BRIDGE_NAME.onAutoplayBlocked(); }
      }
    });
  }

  function kidsSetVideo(id, autoplay) {
    expected = id;
    graceUntil = Date.now() + 2500;
    if (!ready) { pending = id; pendingAutoplay = autoplay; return; }
    if (autoplay) { player.loadVideoById(id); } else { player.cueVideoById(id); }
  }

  function currentId() {
    try { var d = player.getVideoData(); return (d && d.video_id) ? d.video_id : null; } catch (e) { return null; }
  }

  function guard() {
    if (!ready || !expected || Date.now() < graceUntil) return true;
    var c = currentId();
    if (c && c !== expected) {
      try { player.stopVideo(); } catch (e) {}
      $BRIDGE_NAME.onForeignVideo(c);
      return false;
    }
    return true;
  }
  setInterval(guard, 750);

  function kidsPlay()  { if (ready) player.playVideo(); }
  function kidsPause() { if (ready) player.pauseVideo(); }
  function kidsStop()  { expected = null; pending = null; if (ready) { try { player.stopVideo(); } catch (e) {} } }
  function kidsMute(m) { if (ready) { if (m) player.mute(); else player.unMute(); } }

  var tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  document.head.appendChild(tag);
</script>
</body>
</html>
""".trimIndent()
}
