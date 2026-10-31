package com.kidsshorts.domain

sealed interface ChannelInput {
    data class Id(val channelId: String) : ChannelInput
    data class Handle(val handle: String) : ChannelInput
    data class LegacyUsername(val username: String) : ChannelInput
    data class Rejected(val reason: RejectReason) : ChannelInput
}

enum class RejectReason(val message: String) {
    EMPTY("Paste a YouTube channel link or channel ID."),
    VIDEO_URL("That is a video link. Channels can only be approved from a channel link, @handle or channel ID."),
    PLAYLIST_URL("That is a playlist link. Please paste a channel link, @handle or channel ID."),
    CUSTOM_URL("Custom /c/ links can't be resolved by the official API. Use the channel's @handle or its channel ID (starts with UC)."),
    NOT_YOUTUBE("That doesn't look like a YouTube channel link."),
    UNRECOGNISED("Couldn't recognise a channel in that text."),
}

/** Never accepts a video URL as a channel approval. */
object ChannelInputParser {
    private val channelId = Regex("^UC[0-9A-Za-z_-]{22}$")
    private val handleBody = Regex("^[A-Za-z0-9._-]{3,30}$")
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com")

    fun parse(raw: String): ChannelInput {
        val text = raw.trim()
        if (text.isEmpty()) return ChannelInput.Rejected(RejectReason.EMPTY)
        if (channelId.matches(text)) return ChannelInput.Id(text)
        if (text.startsWith("@")) {
            val body = text.removePrefix("@")
            return if (handleBody.matches(body)) ChannelInput.Handle("@$body") else ChannelInput.Rejected(RejectReason.UNRECOGNISED)
        }

        val withScheme = if (text.contains("://")) text else "https://$text"
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        if (host == "youtu.be") return ChannelInput.Rejected(RejectReason.VIDEO_URL)
        if (host !in youtubeHosts) return ChannelInput.Rejected(RejectReason.NOT_YOUTUBE)

        val afterHost = withScheme.substringAfter("://").substringAfter('/', "")
        val path = afterHost.substringBefore('?').substringBefore('#').trim('/')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ChannelInput.Rejected(RejectReason.UNRECOGNISED)

        val first = segments[0]
        return when {
            first == "watch" || first == "shorts" || first == "live" || first == "embed" || first == "v" ->
                ChannelInput.Rejected(RejectReason.VIDEO_URL)
            first == "playlist" ->
                ChannelInput.Rejected(RejectReason.PLAYLIST_URL)
            first == "channel" && segments.size >= 2 && channelId.matches(segments[1]) -> ChannelInput.Id(segments[1])
            first.startsWith("@") && handleBody.matches(first.removePrefix("@")) -> ChannelInput.Handle(first)
            first == "user" && segments.size >= 2 && segments[1].isNotBlank() -> ChannelInput.LegacyUsername(segments[1])
            first == "c" -> ChannelInput.Rejected(RejectReason.CUSTOM_URL)
            else -> ChannelInput.Rejected(RejectReason.UNRECOGNISED)
        }
    }
}
