package com.kidsshorts.data.youtube

import com.kidsshorts.domain.ApprovedChannel
import com.kidsshorts.domain.EligibilityEvaluator
import com.kidsshorts.domain.EligibilityResult
import com.kidsshorts.domain.ExclusionReason
import com.kidsshorts.domain.LanguagePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeJsonTest {

    private fun video(
        id: String = "dQw4w9WgXcQ",
        extraSnippet: String = """"defaultAudioLanguage":"hi"""",
        content: String = """"duration":"PT45S"""",
        status: String? = """"uploadStatus":"processed","privacyStatus":"public","embeddable":true,"madeForKids":false""",
        live: String = "none",
    ) = """
    {"items":[{"id":"$id",
      "snippet":{"channelId":"UCabcdefghijklmnopqrstuv","title":"Mango song","publishedAt":"2025-03-01T10:15:30Z",
        "liveBroadcastContent":"$live",
        "thumbnails":{"default":{"url":"https://i.ytimg.com/vi/$id/default.jpg"},"medium":{"url":"https://i.ytimg.com/vi/$id/mqdefault.jpg"}},
        $extraSnippet},
      "contentDetails":{$content}
      ${if (status != null) ""","status":{$status}""" else ""}
    }]}"""

    private val channels = mapOf(
        "UCabcdefghijklmnopqrstuv" to ApprovedChannel("UCabcdefghijklmnopqrstuv", "c", null, null, true),
    )
    private val hindiOnly = LanguagePolicy(setOf("hi"), null, true)

    private fun eligible(json: String): Boolean = YouTubeJson.parseVideos(json).all {
        EligibilityEvaluator.evaluateVideo(it, channels, hindiOnly, emptyMap()) is EligibilityResult.Eligible
    } && YouTubeJson.parseVideos(json).isNotEmpty()

    @Test fun parseChannels_readsTitleThumbnailAndUploadsPlaylist() {
        val json = """{"items":[{"id":"UCabcdefghijklmnopqrstuv",
            "snippet":{"title":"Kids Rhymes","thumbnails":{"default":{"url":"https://yt3.ggpht.com/d"},"high":{"url":"https://yt3.ggpht.com/h"}}},
            "contentDetails":{"relatedPlaylists":{"uploads":"UUabcdefghijklmnopqrstuv"}}}]}"""
        val c = YouTubeJson.parseChannels(json).single()
        assertEquals("Kids Rhymes", c.title)
        assertEquals("https://yt3.ggpht.com/h", c.thumbnailUrl)
        assertEquals("UUabcdefghijklmnopqrstuv", c.uploadsPlaylistId)
    }

    @Test fun parseChannels_emptyOrMissingItems_isEmpty() {
        assertTrue(YouTubeJson.parseChannels("""{"items":[]}""").isEmpty())
        assertTrue(YouTubeJson.parseChannels("""{"pageInfo":{}}""").isEmpty())
    }

    @Test fun parsePlaylistPage_dropsMalformedVideoIds_andReadsToken() {
        val json = """{"nextPageToken":"CAUQAA","items":[
            {"contentDetails":{"videoId":"dQw4w9WgXcQ"}},
            {"contentDetails":{"videoId":"bad id; drop"}},
            {"contentDetails":{}}]}"""
        val p = YouTubeJson.parsePlaylistPage(json)
        assertEquals(listOf("dQw4w9WgXcQ"), p.videoIds)
        assertEquals("CAUQAA", p.nextPageToken)
        assertNull(YouTubeJson.parsePlaylistPage("""{"items":[]}""").nextPageToken)
    }

    @Test fun parseVideos_mapsAllSignals() {
        val v = YouTubeJson.parseVideos(
            video(extraSnippet = """"defaultAudioLanguage":"hi","defaultLanguage":"hi"""", content = """"duration":"PT1M5S""""),
        ).single()
        assertEquals("dQw4w9WgXcQ", v.videoId)
        assertEquals("UCabcdefghijklmnopqrstuv", v.channelId)
        assertEquals(65, v.durationSeconds)
        assertEquals("hi", v.audioLanguage); assertEquals("hi", v.metadataLanguage)
        assertEquals(false, v.madeForKids)
        assertTrue(v.isEmbeddable && v.isPubliclyAvailable)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", v.thumbnailUrl)
        assertEquals(1_740_824_130_000L, v.publishedAtEpochMs)
    }

    @Test fun healthyVideoFromAllowedChannelAndLanguage_isEligible() = assertTrue(eligible(video()))

    @Test fun missingStatusBlock_failsClosed() {
        val v = YouTubeJson.parseVideos(video(status = null)).single()
        assertFalse(v.isEmbeddable); assertFalse(v.isPubliclyAvailable); assertNull(v.madeForKids)
        assertFalse(eligible(video(status = null)))
    }

    @Test fun missingMadeForKids_isStatusNotVerified() {
        val json = video(status = """"uploadStatus":"processed","privacyStatus":"public","embeddable":true""")
        val v = YouTubeJson.parseVideos(json).single()
        assertNull(v.madeForKids)
        val r = EligibilityEvaluator.evaluateVideo(v, channels, hindiOnly, emptyMap()) as EligibilityResult.Excluded
        assertEquals(listOf(ExclusionReason.STATUS_NOT_VERIFIED), r.reasons)
    }

    @Test fun nonPublicOrUnprocessedOrLiveOrNotEmbeddable_areBlocked() {
        assertFalse(eligible(video(status = """"uploadStatus":"processed","privacyStatus":"unlisted","embeddable":true,"madeForKids":false""")))
        assertFalse(eligible(video(status = """"uploadStatus":"uploaded","privacyStatus":"public","embeddable":true,"madeForKids":false""")))
        assertFalse(eligible(video(status = """"uploadStatus":"processed","privacyStatus":"public","embeddable":false,"madeForKids":false""")))
        assertFalse(eligible(video(live = "live")))
        assertFalse(eligible(video(live = "upcoming", content = """"duration":"P0D"""")))
    }

    @Test fun missingOrBadDuration_isNotShortsFormat() {
        assertFalse(eligible(video(content = """"definition":"hd"""")))
        assertFalse(eligible(video(content = """"duration":"garbage"""")))
        assertFalse(eligible(video(content = """"duration":"PT5M"""")))
    }

    @Test fun missingAudioLanguage_isNeverEligibleUnderDefaults() {
        assertFalse(eligible(video(extraSnippet = """"defaultLanguage":"hi"""")))
        assertFalse(eligible(video(extraSnippet = """"title2":"x"""")))
    }

    @Test fun audioAndMetadataLanguageDisagree_isNotEligible() {
        assertFalse(eligible(video(extraSnippet = """"defaultAudioLanguage":"hi","defaultLanguage":"en"""")))
    }

    @Test fun parseVideos_skipsEntriesWithoutValidIdOrChannel() {
        val json = """{"items":[{"id":"nope","snippet":{"channelId":"UC1"}},{"id":"dQw4w9WgXcQ","snippet":{"title":"no channel"}}]}"""
        assertTrue(YouTubeJson.parseVideos(json).isEmpty())
    }

    @Test fun parseError_classifiesFailures() {
        fun kind(code: Int, body: String) = YouTubeJson.parseError(code, body).kind
        assertEquals(YouTubeApiException.Kind.QUOTA_EXCEEDED,
            kind(403, """{"error":{"code":403,"message":"quota","errors":[{"reason":"quotaExceeded"}]}}"""))
        assertEquals(YouTubeApiException.Kind.KEY_REJECTED,
            kind(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertEquals(YouTubeApiException.Kind.KEY_REJECTED,
            kind(403, """{"error":{"code":403,"message":"Requests from this app are blocked","errors":[{"reason":"forbidden"}],"details":[{"reason":"API_KEY_ANDROID_APP_BLOCKED"}]}}"""))
        assertEquals(YouTubeApiException.Kind.NOT_FOUND,
            kind(404, """{"error":{"code":404,"message":"The playlist identified with the request's playlistId parameter cannot be found.","errors":[{"reason":"playlistNotFound"}]}}"""))
        assertEquals(YouTubeApiException.Kind.HTTP, kind(500, "<html>oops</html>"))
    }

    @Test fun unreadableSuccessBody_isBadResponse() {
        try { YouTubeJson.parseVideos("<html>"); throw AssertionError("expected exception") }
        catch (e: YouTubeApiException) { assertEquals(YouTubeApiException.Kind.BAD_RESPONSE, e.kind) }
    }
}
