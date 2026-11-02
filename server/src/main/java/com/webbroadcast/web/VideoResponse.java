package com.webbroadcast.web;

import java.time.Instant;
import java.util.UUID;

import com.webbroadcast.media.VideoTranscoder;
import com.webbroadcast.video.Video;
import com.webbroadcast.video.VideoStatus;

/**
 * playbackUrl과 thumbnailUrl은 공개 상태일 때만 채워진다.
 */
public record VideoResponse(
		UUID id,
		String title,
		String description,
		VideoStatus status,
		Double durationSeconds,
		Instant createdAt,
		String playbackUrl,
		String thumbnailUrl) {

	static VideoResponse from(Video video) {
		boolean published = video.getStatus() == VideoStatus.PUBLISHED;
		String mediaBase = MediaWebConfig.MEDIA_PATH + video.getId() + "/";
		return new VideoResponse(
				video.getId(),
				video.getTitle(),
				video.getDescription(),
				video.getStatus(),
				video.getDurationSeconds(),
				video.getCreatedAt(),
				published ? mediaBase + VideoTranscoder.MASTER_PLAYLIST : null,
				published ? mediaBase + VideoTranscoder.THUMBNAIL : null);
	}
}
