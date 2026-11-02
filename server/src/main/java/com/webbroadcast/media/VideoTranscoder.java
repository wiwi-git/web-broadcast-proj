package com.webbroadcast.media;

import java.nio.file.Path;

/**
 * Original을 시청 가능한 형태(HLS Rendition + 썸네일)로 변환한다.
 * 지금은 API 서버 안에서 돌지만, 별도 워커로 옮길 때 이 경계를 그대로 쓴다 (ADR-0001).
 */
public interface VideoTranscoder {

	/** 변환된 파일 이름. outputDir 바로 아래에 생긴다. */
	String MASTER_PLAYLIST = "master.m3u8";
	String THUMBNAIL = "thumbnail.jpg";

	void transcode(Path original, Path outputDir) throws TranscodeException;
}
