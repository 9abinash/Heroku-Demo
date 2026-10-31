package com.kidsshorts.data.youtube

import com.kidsshorts.domain.ChannelInput
import com.kidsshorts.domain.RejectReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class YouTubeApiClientTest {

    private class FakeHttp(private val responder: (String) -> HttpResult) : HttpGet {
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): HttpResult {
            urls += url; this.headers += headers
            return responder(url)
        }
    }

    private fun client(key: String = "KEY123", http: HttpGet) =
        YouTubeApiClient({ key }, "com.kidsshorts", "AABBCC", http, "https://example.test/v3")

    private fun failure(block: suspend () -> Unit): YouTubeApiException? =
        try { runBlocking { block() }; null } catch (e: YouTubeApiException) { e }

    private val channelBody = """{"items":[{"id":"UCabcdefghijklmnopqrstuv","snippet":{"title":"T"},
        "contentDetails":{"relatedPlaylists":{"uploads":"UUabcdefghijklmnopqrstuv"}}}]}"""

    @Test fun resolveChannel_byHandle_sendsEncodedHandle_keyAndAndroidHeaders() {
        val http = FakeHttp { HttpResult(200, channelBody) }
        val c = runBlocking { client(http = http).resolveChannel(ChannelInput.Handle("@Kids Fun".replace(" ", "_"))) }
        assertEquals("UUabcdefghijklmnopqrstuv", c.uploadsPlaylistId)
        val url = http.urls.single()
        assertTrue(url, url.startsWith("https://example.test/v3/channels?"))
        assertTrue(url, "forHandle=%40Kids_Fun" in url)
        assertTrue(url, "key=KEY123" in url)
        assertEquals("com.kidsshorts", http.headers.single()["X-Android-Package"])
        assertEquals("AABBCC", http.headers.single()["X-Android-Cert"])
    }

    @Test fun resolveChannel_idMismatch_isRejected() {
        val http = FakeHttp { HttpResult(200, channelBody) }
        val e = failure { client(http = http).resolveChannel(ChannelInput.Id("UCzzzzzzzzzzzzzzzzzzzzzz")) }
        assertEquals(YouTubeApiException.Kind.BAD_RESPONSE, e?.kind)
    }

    @Test fun resolveChannel_noItems_isNotFound() {
        val e = failure { client(http = FakeHttp { HttpResult(200, """{"items":[]}""") }).resolveChannel(ChannelInput.Handle("@nobody")) }
        assertEquals(YouTubeApiException.Kind.NOT_FOUND, e?.kind)
    }

    @Test fun resolveChannel_rejectedInput_neverHitsTheNetwork() {
        val http = FakeHttp { error("must not be called") }
        try {
            runBlocking { client(http = http).resolveChannel(ChannelInput.Rejected(RejectReason.VIDEO_URL)) }
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
        assertTrue(http.urls.isEmpty())
    }

    @Test fun missingApiKey_failsBeforeAnyRequest() {
        val http = FakeHttp { error("must not be called") }
        val e = failure { client(key = "  ", http = http).getVideos(listOf("dQw4w9WgXcQ")) }
        assertEquals(YouTubeApiException.Kind.API_KEY_MISSING, e?.kind)
        assertTrue(http.urls.isEmpty())
    }

    @Test fun getVideos_chunksBy50_dedupes_andDropsInvalidIds() {
        val ids = (1..51).map { "video%06d__".format(it).take(11) } + listOf("video000001__", "bad id", "")
        val http = FakeHttp { HttpResult(200, """{"items":[]}""") }
        runBlocking { client(http = http).getVideos(ids) }
        assertEquals(2, http.urls.size)
        val firstIds = http.urls[0].substringAfter("&id=").substringBefore("&key").split("%2C")
        assertEquals(50, firstIds.size)
        assertTrue(http.urls.all { "bad+id" !in it && "part=snippet%2CcontentDetails%2Cstatus" in it })
    }

    @Test fun listUploadVideoIds_followsPagesUntilNoToken() {
        val http = FakeHttp { url ->
            if ("pageToken=P2" in url) HttpResult(200, """{"items":[{"contentDetails":{"videoId":"bbbbbbbbbbb"}}]}""")
            else HttpResult(200, """{"nextPageToken":"P2","items":[{"contentDetails":{"videoId":"aaaaaaaaaaa"}}]}""")
        }
        val ids = runBlocking { client(http = http).listUploadVideoIds("UUx") }
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), ids)
        assertEquals(2, http.urls.size)
    }

    @Test fun listUploadVideoIds_respectsMaxPages_toProtectQuota() {
        val http = FakeHttp { HttpResult(200, """{"nextPageToken":"MORE","items":[{"contentDetails":{"videoId":"aaaaaaaaaaa"}}]}""") }
        runBlocking { client(http = http).listUploadVideoIds("UUx", maxPages = 3) }
        assertEquals(3, http.urls.size)
    }

    @Test fun httpErrors_areMapped() {
        val quota = failure { client(http = FakeHttp { HttpResult(403, """{"error":{"message":"q","errors":[{"reason":"quotaExceeded"}]}}""") }).getVideos(listOf("dQw4w9WgXcQ")) }
        assertEquals(YouTubeApiException.Kind.QUOTA_EXCEEDED, quota?.kind)
    }

    @Test fun ioFailure_isMappedToNetworkError() {
        val e = failure { client(http = FakeHttp { throw IOException("offline") }).getVideos(listOf("dQw4w9WgXcQ")) }
        assertEquals(YouTubeApiException.Kind.NETWORK, e?.kind)
    }
}
