package com.webbroadcast.video;

import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.webbroadcast.media.VideoTranscoder;
import com.webbroadcast.storage.VideoStorage;

/**
 * 처리 중인 Video 하나를 변환해 공개 또는 실패 상태로 만든다.
 */
@Component
class VideoProcessor {

	private static final Logger log = LoggerFactory.getLogger(VideoProcessor.class);

	private final VideoRepository repository;
	private final VideoStorage storage;
	private final VideoTranscoder transcoder;

	VideoProcessor(VideoRepository repository, VideoStorage storage, VideoTranscoder transcoder) {
		this.repository = repository;
		this.storage = storage;
		this.transcoder = transcoder;
	}

	void process(UUID videoId) {
		Video video = repository.findById(videoId).orElse(null);
		if (video == null || video.getStatus() != VideoStatus.PROCESSING) {
			return;
		}
		long startedAt = System.nanoTime();
		try {
			Path workDir = storage.prepareWorkDir(videoId);
			transcoder.transcode(storage.originalPath(videoId, video.getOriginalExtension()), workDir);
			storage.publishWorkDir(videoId);
			video.publish();
			log.info("Video {} 공개 ({}초 소요)", videoId, (System.nanoTime() - startedAt) / 1_000_000_000);
		}
		catch (Exception e) {
			storage.discardWorkDir(videoId);
			video.fail();
			log.error("Video {} 변환 실패", videoId, e);
		}
		repository.save(video);
	}
}
