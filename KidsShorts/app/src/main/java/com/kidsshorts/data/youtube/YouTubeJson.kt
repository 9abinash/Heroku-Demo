package com.kidsshorts.data.youtube

import com.kidsshorts.domain.Iso8601Duration
import com.kidsshorts.domain.VideoIds
import com.kidsshorts.domain.VideoItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/** Pure parsing of YouTube Data API v3 responses. No Android or network dependencies beyond org.json. */
object YouTubeJson {

    fun parseChannels(body: String): List<ChannelInfo> {
        val items = root(body).optJSONArray("items") ?: return emptyList()
        return items.objects().mapNotNull { item ->
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val snippet = item.optJSONObject("snippet")
            ChannelInfo(
                channelId = id,
                title = snippet?.optString("title").orEmpty().ifBlank { id },
                thumbnailUrl = snippet?.optJSONObject("thumbnails")?.bestThumbnail(),
                uploadsPlaylistId = item.optJSONObject("contentDetails")
                    ?.optJSONObject("relatedPlaylists")?.optString("uploads")?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun parsePlaylistPage(body: String): PlaylistPage {
        val r = root(body)
        val ids = r.optJSONArray("items")?.objects().orEmpty().mapNotNull {
            it.optJSONObject("contentDetails")?.optString("videoId")?.takeIf(VideoIds::isValid)
        }
        return PlaylistPage(ids, r.optString("nextPageToken").takeIf { it.isNotBlank() })
    }

    /**
     * Maps videos.list(part=snippet,contentDetails,status) to domain items.
     * Anything missing is mapped to the *restrictive* value (not available / not embeddable / status
     * unknown) so incomplete API data can never make a video playable.
     */
    fun parseVideos(body: String): List<VideoItem> {
        val items = root(body).optJSONArray("items") ?: return emptyList()
        return items.objects().mapNotNull { item ->
            val id = item.optString("id").takeIf(VideoIds::isValid) ?: return@mapNotNull null
            val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
            val channelId = snippet.optString("channelId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val content = item.optJSONObject("contentDetails")
            val status = item.optJSONObject("status")

            val privacyPublic = status?.optString("privacyStatus") == "public"
            val processed = status?.optString("uploadStatus") == "processed"
            val notLive = snippet.optString("liveBroadcastContent", "none").let { it == "none" || it.isBlank() }

            VideoItem(
                videoId = id,
                channelId = channelId,
                title = snippet.optString("title"),
                thumbnailUrl = snippet.optJSONObject("thumbnails")?.bestThumbnail(),
                durationSeconds = Iso8601Duration.parseSeconds(content?.optString("duration")) ?: 0,
                isEmbeddable = status?.optBoolean("embeddable", false) ?: false,
                isPubliclyAvailable = privacyPublic && processed && notLive,
                madeForKids = if (status != null && status.has("madeForKids")) status.optBoolean("madeForKids") else null,
                metadataLanguage = snippet.optString("defaultLanguage").takeIf { it.isNotBlank() },
                audioLanguage = snippet.optString("defaultAudioLanguage").takeIf { it.isNotBlank() },
                playerReportedUnavailable = false,
                publishedAtEpochMs = runCatching { Instant.parse(snippet.optString("publishedAt")).toEpochMilli() }.getOrDefault(0L),
            )
        }
    }

    fun parseError(httpCode: Int, body: String): YouTubeApiException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message").orEmpty().ifBlank { "HTTP $httpCode" }
        val reasons = buildList {
            error?.optJSONArray("errors")?.objects()?.forEach { add(it.optString("reason")) }
            error?.optJSONArray("details")?.objects()?.forEach { add(it.optString("reason")) }
        }
        val kind = when {
            reasons.any { it in QUOTA_REASONS } -> YouTubeApiException.Kind.QUOTA_EXCEEDED
            reasons.any { it in KEY_REASONS } || message.contains("API key", ignoreCase = true) ->
                YouTubeApiException.Kind.KEY_REJECTED
            httpCode == 404 || reasons.any { it.endsWith("NotFound") } -> YouTubeApiException.Kind.NOT_FOUND
            httpCode == 401 || httpCode == 403 -> YouTubeApiException.Kind.KEY_REJECTED
            else -> YouTubeApiException.Kind.HTTP
        }
        return YouTubeApiException(kind, message)
    }

    private val QUOTA_REASONS = setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded")
    private val KEY_REASONS = setOf("keyInvalid", "API_KEY_INVALID", "accessNotConfigured", "ipRefererBlocked", "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED")

    private fun root(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw YouTubeApiException(YouTubeApiException.Kind.BAD_RESPONSE, "Unreadable response from YouTube", e)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.bestThumbnail(): String? =
        listOf("medium", "high", "standard", "default").firstNotNullOfOrNull { size ->
            optJSONObject(size)?.optString("url")?.takeIf { it.startsWith("https://") }
        }
}
