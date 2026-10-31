package com.kidsshorts.data.youtube

import com.kidsshorts.domain.ChannelInput
import com.kidsshorts.domain.VideoIds
import com.kidsshorts.domain.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HttpResult(val code: Int, val body: String)

fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): HttpResult
}

class UrlConnectionHttpGet : HttpGet {
    override fun get(url: String, headers: Map<String, String>): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = false
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(code, body)
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Official YouTube Data API v3 only (no scraping, no private endpoints).
 * Quota (default 10,000 units/day): channels.list = 1, playlistItems.list = 1, videos.list = 1
 * per call (50 ids). Syncing one channel with the default 4 pages costs about 4 + 4 = 8 units.
 * We never call search.list (100 units) because uploads playlists give the same data far cheaper.
 */
class YouTubeApiClient(
    private val apiKeyProvider: () -> String,
    private val packageName: String,
    private val certSha1Hex: String?,
    private val http: HttpGet = UrlConnectionHttpGet(),
    private val baseUrl: String = "https://www.googleapis.com/youtube/v3",
) {
    suspend fun resolveChannel(input: ChannelInput): ChannelInfo {
        val selector = when (input) {
            is ChannelInput.Id -> "id" to input.channelId
            is ChannelInput.Handle -> "forHandle" to input.handle
            is ChannelInput.LegacyUsername -> "forUsername" to input.username
            is ChannelInput.Rejected -> throw IllegalArgumentException(input.reason.message)
        }
        val body = request("channels", "part" to "snippet,contentDetails", selector)
        val channel = YouTubeJson.parseChannels(body).firstOrNull()
            ?: throw YouTubeApiException(YouTubeApiException.Kind.NOT_FOUND, "No YouTube channel was found for that link.")
        if (input is ChannelInput.Id && channel.channelId != input.channelId) {
            throw YouTubeApiException(YouTubeApiException.Kind.BAD_RESPONSE, "YouTube returned a different channel than requested.")
        }
        return channel
    }

    /** Newest-first video IDs from the channel's uploads playlist, up to [maxPages] * 50. */
    suspend fun listUploadVideoIds(uploadsPlaylistId: String, maxPages: Int = DEFAULT_PAGES): List<String> {
        val ids = LinkedHashSet<String>()
        var pageToken: String? = null
        repeat(maxPages) {
            val params = mutableListOf("part" to "contentDetails", "playlistId" to uploadsPlaylistId, "maxResults" to "50")
            pageToken?.let { params += "pageToken" to it }
            val page = YouTubeJson.parsePlaylistPage(request("playlistItems", *params.toTypedArray()))
            ids += page.videoIds
            pageToken = page.nextPageToken
            if (pageToken == null) return ids.toList()
        }
        return ids.toList()
    }

    /** Videos that are deleted/private are simply absent from the result; callers must handle that. */
    suspend fun getVideos(videoIds: List<String>): List<VideoItem> =
        videoIds.filter(VideoIds::isValid).distinct().chunked(50).flatMap { chunk ->
            YouTubeJson.parseVideos(
                request("videos", "part" to "snippet,contentDetails,status", "id" to chunk.joinToString(",")),
            )
        }

    private suspend fun request(endpoint: String, vararg params: Pair<String, String>): String {
        val key = apiKeyProvider()
        if (key.isBlank()) throw YouTubeApiException(YouTubeApiException.Kind.API_KEY_MISSING, "No YouTube API key is configured.")
        val query = (params.toList() + ("key" to key)).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val headers = buildMap {
            put("Accept", "application/json")
            // Required for keys restricted to this Android app; harmless for unrestricted keys.
            put("X-Android-Package", packageName)
            certSha1Hex?.let { put("X-Android-Cert", it) }
        }
        val result = try {
            withContext(Dispatchers.IO) { http.get("$baseUrl/$endpoint?$query", headers) }
        } catch (e: IOException) {
            throw YouTubeApiException(YouTubeApiException.Kind.NETWORK, "Couldn't reach YouTube. Check the internet connection.", e)
        }
        if (result.code !in 200..299) throw YouTubeJson.parseError(result.code, result.body)
        return result.body
    }

    companion object {
        const val DEFAULT_PAGES = 4
    }
}
