package com.webbroadcast.web.broadcast;

import java.time.Instant;
import java.util.UUID;

import com.webbroadcast.broadcast.Broadcast;
import com.webbroadcast.broadcast.BroadcastStatus;
import com.webbroadcast.broadcast.MediaPath;
import com.webbroadcast.web.UserSummary;

/**
 * playbackUrl은 방송 중일 때만 채워진다.
 */
public record BroadcastResponse(
		UUID id,
		String title,
		BroadcastStatus status,
		UserSummary owner,
		Instant createdAt,
		Instant startedAt,
		Instant endedAt,
		String playbackUrl) {

	/** 미디어 서버의 LL-HLS. 웹 서버(개발 중에는 Vite)가 이 경로를 미디어 서버로 넘긴다. */
	static final String HLS_PATH = "/hls/";

	static BroadcastResponse from(Broadcast broadcast) {
		boolean live = broadcast.getStatus() == BroadcastStatus.LIVE;
		return new BroadcastResponse(
				broadcast.getId(),
				broadcast.getTitle(),
				broadcast.getStatus(),
				UserSummary.from(broadcast.getOwner()),
				broadcast.getCreatedAt(),
				broadcast.getStartedAt(),
				broadcast.getEndedAt(),
				live ? HLS_PATH + MediaPath.of(broadcast.getId()) + "/index.m3u8" : null);
	}
}
