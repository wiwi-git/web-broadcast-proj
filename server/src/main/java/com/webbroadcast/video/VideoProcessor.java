package com.webbroadcast.video;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.webbroadcast.media.VideoTranscoder;
import com.webbroadcast.storage.VideoStorage;

/**
 * 점유한 Video 하나를 변환해 공개 또는 실패 상태로 만든다.
 */
@Component
class VideoProcessor {

	private static final Logger log = LoggerFactory.getLogger(VideoProcessor.class);

	private final VideoRepository repository;
	private final VideoJobs jobs;
	private final VideoStorage storage;
	private final VideoTranscoder transcoder;

	VideoProcessor(VideoRepository repository, VideoJobs jobs, VideoStorage storage, VideoTranscoder transcoder) {
		this.repository = repository;
		this.jobs = jobs;
		this.storage = storage;
		this.transcoder = transcoder;
	}

	/**
	 * 스레드가 인터럽트되면(워커 종료, 점유 상실) 상태를 바꾸지 않고 멈춘다.
	 * 점유가 풀리면 다른 워커가 처음부터 다시 변환한다.
	 */
	void process(VideoJobs.Claim claim) {
		Video video = repository.findById(claim.videoId()).orElse(null);
		if (video == null || video.getStatus() != VideoStatus.PROCESSING) {
			return;
		}
		long startedAt = System.nanoTime();
		try {
			Path workDir = storage.prepareWorkDir(video.getId());
			transcoder.transcode(storage.originalPath(video.getId(), video.getOriginalExtension()), workDir);
			if (Thread.currentThread().isInterrupted()) {
				storage.discardWorkDir(video.getId());
				return;
			}
			storage.publishWorkDir(video.getId());
			finish(claim, video, VideoStatus.PUBLISHED);
			log.info("Video {} 공개 ({}초 소요)", video.getId(), (System.nanoTime() - startedAt) / 1_000_000_000);
		}
		catch (Exception e) {
			storage.discardWorkDir(video.getId());
			if (Thread.currentThread().isInterrupted()) {
				log.info("Video {} 변환 중단 (다른 워커가 이어받음)", video.getId());
				return;
			}
			finish(claim, video, VideoStatus.FAILED);
			log.error("Video {} 변환 실패", video.getId(), e);
		}
	}

	private void finish(VideoJobs.Claim claim, Video video, VideoStatus status) {
		if (jobs.finish(claim, status) == 1) {
			return;
		}
		if (repository.existsById(video.getId())) {
			// 점유 기한이 지나 다른 워커가 가져갔다. 결과는 그 워커가 기록한다
			log.warn("Video {}의 점유를 잃어 결과를 기록하지 않음", video.getId());
			return;
		}
		// 변환하는 동안 Owner가 삭제했다
		storage.deleteAll(video.getId(), video.getOriginalExtension());
		log.info("Video {} 변환 중 삭제되어 결과물을 버림", video.getId());
	}
}
