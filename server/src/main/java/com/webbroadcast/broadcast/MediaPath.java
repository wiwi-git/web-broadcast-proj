package com.webbroadcast.broadcast;

import java.util.Optional;
import java.util.UUID;

/**
 * 미디어 서버에서 Broadcast 하나가 쓰는 경로. "live/{broadcastId}" 형태이며 Viewer에게 공개된다.
 * Stream Key는 경로에 넣지 않는다.
 */
public final class MediaPath {

	private static final String PREFIX = "live/";

	private MediaPath() {
	}

	public static String of(UUID broadcastId) {
		return PREFIX + broadcastId;
	}

	public static Optional<UUID> parse(String path) {
		if (path == null || !path.startsWith(PREFIX)) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(path.substring(PREFIX.length())));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
