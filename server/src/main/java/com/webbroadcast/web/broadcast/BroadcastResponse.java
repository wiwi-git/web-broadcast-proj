package com.webbroadcast.web.broadcast;

import java.time.Instant;
import java.util.UUID;

import com.webbroadcast.broadcast.Broadcast;
import com.webbroadcast.broadcast.BroadcastStatus;
import com.webbroadcast.broadcast.MediaPath;
import com.webbroadcast.web.UserSummary;

/**
 * playbackUrl(LL-HLS)과 webrtcUrl(WHEP)은 방송 중일 때만 채워진다.
 * 시청은 LL-HLS로 하고, 송출 소리(Opus)를 HLS로 재생하지 못하는 기기(iOS)만 WHEP로 본다 (ADR-0006).
 */
public record BroadcastResponse(
		UUID id,
		String title,
		BroadcastStatus status,
		UserSummary owner,
		Instant createdAt,
		Instant startedAt,
		Instant endedAt,
		String playbackUrl,
		String webrtcUrl) {

	/** 미디어 서버의 LL-HLS. 웹 서버(개발 중에는 Vite)가 이 경로를 미디어 서버로 넘긴다. */
	static final String HLS_PATH = "/hls/";

	/** 미디어 서버의 WebRTC 시청(WHEP). 송출(WHIP)과 같은 주소를 쓴다. */
	static final String WEBRTC_PATH = "/whip/";

	static BroadcastResponse from(Broadcast broadcast) {
		boolean live = broadcast.getStatus() == BroadcastStatus.LIVE;
		String mediaPath = MediaPath.of(broadcast.getId());
		return new BroadcastResponse(
				broadcast.getId(),
				broadcast.getTitle(),
				broadcast.getStatus(),
				UserSummary.from(broadcast.getOwner()),
				broadcast.getCreatedAt(),
				broadcast.getStartedAt(),
				broadcast.getEndedAt(),
				live ? HLS_PATH + mediaPath + "/index.m3u8" : null,
				live ? WEBRTC_PATH + mediaPath + "/whep" : null);
	}
}
