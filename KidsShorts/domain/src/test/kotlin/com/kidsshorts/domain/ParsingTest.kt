package com.kidsshorts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelInputParserTest {
    private val id = "UCX6OQ3DkcsbYNE6H8uQQuVA"

    @Test fun rawChannelId_isAccepted() = assertEquals(ChannelInput.Id(id), ChannelInputParser.parse(id))

    @Test fun channelUrls_areAccepted() {
        assertEquals(ChannelInput.Id(id), ChannelInputParser.parse("https://www.youtube.com/channel/$id"))
        assertEquals(ChannelInput.Id(id), ChannelInputParser.parse("youtube.com/channel/$id/shorts?x=1"))
        assertEquals(ChannelInput.Id(id), ChannelInputParser.parse("  https://m.youtube.com/channel/$id  "))
    }

    @Test fun handles_areAccepted() {
        assertEquals(ChannelInput.Handle("@MrBeast"), ChannelInputParser.parse("@MrBeast"))
        assertEquals(ChannelInput.Handle("@MrBeast"), ChannelInputParser.parse("https://www.youtube.com/@MrBeast/shorts"))
    }

    @Test fun legacyUserUrl_isAccepted() =
        assertEquals(ChannelInput.LegacyUsername("GoogleDevelopers"), ChannelInputParser.parse("https://www.youtube.com/user/GoogleDevelopers"))

    @Test fun videoUrls_areNeverChannelApprovals() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/abcdEFGHijk",
            "https://www.youtube.com/live/abcdEFGHijk",
            "https://www.youtube.com/embed/abcdEFGHijk",
            "youtube.com/watch?v=abc&list=PL123",
        ).forEach {
            assertEquals(it, ChannelInput.Rejected(RejectReason.VIDEO_URL), ChannelInputParser.parse(it))
        }
    }

    @Test fun playlistCustomUrlAndNonYoutube_areRejected() {
        assertEquals(ChannelInput.Rejected(RejectReason.PLAYLIST_URL), ChannelInputParser.parse("https://www.youtube.com/playlist?list=PL1"))
        assertEquals(ChannelInput.Rejected(RejectReason.CUSTOM_URL), ChannelInputParser.parse("https://www.youtube.com/c/SomeName"))
        assertEquals(ChannelInput.Rejected(RejectReason.NOT_YOUTUBE), ChannelInputParser.parse("https://evil.example.com/channel/$id"))
        assertEquals(ChannelInput.Rejected(RejectReason.NOT_YOUTUBE), ChannelInputParser.parse("https://youtube.com.evil.example/channel/$id"))
        assertEquals(ChannelInput.Rejected(RejectReason.EMPTY), ChannelInputParser.parse("   "))
        assertTrue(ChannelInputParser.parse("UCshort") is ChannelInput.Rejected)
        assertTrue(ChannelInputParser.parse("@a") is ChannelInput.Rejected)
    }
}

class Iso8601DurationTest {
    @Test fun parsesCommonShapes() {
        assertEquals(45, Iso8601Duration.parseSeconds("PT45S"))
        assertEquals(65, Iso8601Duration.parseSeconds("PT1M5S"))
        assertEquals(3_600, Iso8601Duration.parseSeconds("PT1H"))
        assertEquals(3_723, Iso8601Duration.parseSeconds("PT1H2M3S"))
        assertEquals(86_400 + 30, Iso8601Duration.parseSeconds("P1DT30S"))
        assertEquals(0, Iso8601Duration.parseSeconds("P0D"))
    }

    @Test fun rejectsMalformed() {
        listOf(null, "", "P", "PT", "45", "PT-5S", "1:05", "PTxS").forEach { assertNull("input=$it", Iso8601Duration.parseSeconds(it)) }
    }
}
