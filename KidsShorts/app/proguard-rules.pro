# The YouTube IFrame page calls these through addJavascriptInterface; R8 must not rename/remove them.
-keepclassmembers class com.kidsshorts.player.PlayerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
