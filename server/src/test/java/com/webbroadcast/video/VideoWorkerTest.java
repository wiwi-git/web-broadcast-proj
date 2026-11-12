package com.webbroadcast.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.webbroadcast.media.VideoTranscoder;
import com.webbroadcast.storage.VideoStorage;
import com.webbroadcast.user.UserService;

/**
 * 업로드는 처리 중인 Video를 남기기만 하고, 워커가 DB 대기열에서 가져가 실제 ffmpeg로 공개까지 만든다.
 */
@SpringBootTest
@Testcontainers
class VideoWorkerTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@TempDir
	static Path storage;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("media.storage-root", storage::toString);
		registry.add("mediamtx.api-url", () -> "http://127.0.0.1:1");
		registry.add("video.worker.poll-interval", () -> "200ms");
	}

	@Autowired
	VideoService videoService;

	@Autowired
	VideoRepository repository;

	@Autowired
	VideoStorage videoStorage;

	@Autowired
	UserService userService;

	@Test
	void theWorkerPublishesAnUploadedVideo() throws Exception {
		Path sample = storage.resolve("sample.mp4");
		Process ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i",
				"testsrc=size=320x240:rate=15", "-t", "1", "-pix_fmt", "yuv420p", sample.toString())
			.inheritIO().start();
		assertThat(ffmpeg.waitFor()).isZero();
		var owner = userService.signUp("worker1", "password1", "워커");

		Video video = videoService.upload(owner.getId(),
				new MockMultipartFile("file", "sample.mp4", "video/mp4", Files.readAllBytes(sample)), "영상", null);
		assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);

		Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
		while (repository.findById(video.getId()).orElseThrow().getStatus() == VideoStatus.PROCESSING
				&& Instant.now().isBefore(deadline)) {
			Thread.sleep(200);
		}
		assertThat(repository.findById(video.getId()).orElseThrow().getStatus()).isEqualTo(VideoStatus.PUBLISHED);
		assertThat(videoStorage.publicRoot().resolve(video.getId().toString()).resolve(VideoTranscoder.MASTER_PLAYLIST))
			.exists();
	}
}
