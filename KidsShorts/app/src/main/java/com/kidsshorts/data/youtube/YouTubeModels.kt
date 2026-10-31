package com.kidsshorts.data.youtube

data class ChannelInfo(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploadsPlaylistId: String?,
)

data class PlaylistPage(val videoIds: List<String>, val nextPageToken: String?)

class YouTubeApiException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { API_KEY_MISSING, KEY_REJECTED, QUOTA_EXCEEDED, NOT_FOUND, NETWORK, HTTP, BAD_RESPONSE }
}
