package com.webbroadcast.video;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * DB의 변환 대기열에서 Video를 하나씩 점유해 변환한다 (ADR-0004).
 * 워커 프로세스를 여러 개 띄우면 각자 다른 Video를 가져가므로, 워커 수를 늘리는 것만으로 변환 처리량이 는다.
 * 변환하는 동안 점유 기한을 계속 늘리고, 늘리지 못하면(점유를 잃으면) 변환을 멈춘다.
 */
@Component
@ConditionalOnBooleanProperty(name = "video.worker.enabled", matchIfMissing = true)
class VideoWorker {

	private static final Logger log = LoggerFactory.getLogger(VideoWorker.class);

	private final VideoJobs jobs;
	private final VideoProcessor processor;
	private final VideoWorkerProperties properties;
	private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().name("video-worker-heartbeat").daemon().factory());
	private volatile boolean running = true;
	private Thread thread;

	VideoWorker(VideoJobs jobs, VideoProcessor processor, VideoWorkerProperties properties) {
		this.jobs = jobs;
		this.processor = processor;
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	void start() {
		thread = Thread.ofPlatform().name("video-worker").start(this::loop);
		log.info("변환 워커 시작 (점유 기한 {}초)", properties.lease().toSeconds());
	}

	private void loop() {
		while (running) {
			try {
				Optional<VideoJobs.Claim> claim = jobs.claimNext(properties.lease());
				if (claim.isPresent()) {
					work(claim.get());
				}
				else {
					Thread.sleep(properties.pollInterval());
				}
			}
			catch (InterruptedException e) {
				// 끝나가는 점유 연장이 늦게 보낸 인터럽트일 수 있다. 종료 중일 때만 멈춘다
				if (!running) {
					return;
				}
			}
			catch (RuntimeException e) {
				// DB에 잠시 닿지 않는 경우 등. 워커는 멈추지 않고 다음 주기에 다시 시도한다
				log.warn("변환 대기열 조회 실패: {}", e.getMessage());
				sleepQuietly();
			}
		}
	}

	private void work(VideoJobs.Claim claim) {
		Thread worker = Thread.currentThread();
		long every = Math.max(1, properties.lease().toMillis() / 3);
		ScheduledFuture<?> renewal = heartbeat.scheduleAtFixedRate(() -> {
			try {
				if (!jobs.extend(claim, properties.lease())) {
					// 지워졌거나 다른 워커가 가져갔다. ffmpeg를 멈춘다 (ProcessRunner)
					worker.interrupt();
				}
			}
			catch (RuntimeException e) {
				log.warn("Video {} 점유 연장 실패: {}", claim.videoId(), e.getMessage());
			}
		}, every, every, TimeUnit.MILLISECONDS);
		log.info("Video {} 변환 시작", claim.videoId());
		try {
			processor.process(claim);
		}
		finally {
			renewal.cancel(false);
			boolean stopping = !running;
			// 점유 상실로 받은 인터럽트는 여기서 지우고 다음 Video로 넘어간다
			Thread.interrupted();
			if (stopping) {
				jobs.release(claim);
			}
		}
	}

	private void sleepQuietly() {
		try {
			Thread.sleep(properties.pollInterval());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** 변환 중이던 Video는 점유를 풀어서 다른 워커가 기한을 기다리지 않고 바로 이어받게 한다. */
	@PreDestroy
	void stop() throws InterruptedException {
		running = false;
		heartbeat.shutdownNow();
		if (thread != null) {
			thread.interrupt();
			thread.join(TimeUnit.SECONDS.toMillis(10));
		}
	}
}
