package com.webbroadcast.video;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 변환 작업을 서버 안의 단일 스레드에서 한 번에 하나씩 돌린다 (ADR-0001).
 */
@Component
class VideoProcessingQueue {

	private static final Logger log = LoggerFactory.getLogger(VideoProcessingQueue.class);

	private final VideoProcessor processor;
	private final VideoRepository repository;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "video-transcoder");
		thread.setDaemon(true);
		return thread;
	});

	VideoProcessingQueue(VideoProcessor processor, VideoRepository repository) {
		this.processor = processor;
		this.repository = repository;
	}

	void enqueue(UUID videoId) {
		executor.submit(() -> processor.process(videoId));
	}

	/** 서버가 변환 도중 꺼졌다면 처리 중으로 남은 Video를 다시 대기열에 넣는다. */
	@EventListener(ApplicationReadyEvent.class)
	void resumeUnfinished() {
		for (Video video : repository.findByStatus(VideoStatus.PROCESSING)) {
			log.info("미완료 Video {} 변환 재개", video.getId());
			enqueue(video.getId());
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}
}
