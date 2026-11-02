package com.webbroadcast.media;

/**
 * ffprobe로 읽어낸 영상 파일 정보. durationSeconds는 컨테이너가 길이를 기록하지 않으면 null.
 */
public record MediaInfo(int width, int height, Double durationSeconds, boolean hasAudio) {

	/** 세로 영상도 1080p/720p 기준에 맞추기 위해 짧은 변을 해상도로 본다. */
	public int shortSide() {
		return Math.min(width, height);
	}
}
