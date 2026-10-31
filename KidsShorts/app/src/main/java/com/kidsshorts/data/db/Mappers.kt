package com.kidsshorts.data.db

import com.kidsshorts.domain.ApprovedChannel
import com.kidsshorts.domain.ParentDecision
import com.kidsshorts.domain.VideoDecision
import com.kidsshorts.domain.VideoItem

fun ChannelEntity.toDomain() = ApprovedChannel(channelId, title, thumbnailUrl, uploadsPlaylistId, isEnabled)

fun VideoEntity.toDomain() = VideoItem(
    videoId = videoId, channelId = channelId, title = title, thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds, isEmbeddable = isEmbeddable, isPubliclyAvailable = isPubliclyAvailable,
    madeForKids = madeForKids, metadataLanguage = metadataLanguage, audioLanguage = audioLanguage,
    playerReportedUnavailable = playerReportedUnavailable, publishedAtEpochMs = publishedAt,
)

fun VideoItem.toEntity(fetchedAt: Long) = VideoEntity(
    videoId = videoId, channelId = channelId, title = title, thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds, isEmbeddable = isEmbeddable, isPubliclyAvailable = isPubliclyAvailable,
    madeForKids = madeForKids, metadataLanguage = metadataLanguage, audioLanguage = audioLanguage,
    playerReportedUnavailable = playerReportedUnavailable, publishedAt = publishedAtEpochMs, fetchedAt = fetchedAt,
)

/** Unknown/corrupt decision strings are dropped rather than guessed. */
fun VideoDecisionEntity.toDomainOrNull(): VideoDecision? {
    val d = runCatching { ParentDecision.valueOf(decision) }.getOrNull() ?: return null
    return VideoDecision(videoId, d, confirmedLanguageCode, updatedAt)
}
