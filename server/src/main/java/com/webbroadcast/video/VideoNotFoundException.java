package com.webbroadcast.video;

import java.util.UUID;

public class VideoNotFoundException extends RuntimeException {

	public VideoNotFoundException(UUID id) {
		super("Video를 찾을 수 없습니다: " + id);
	}
}
