package com.webbroadcast.video;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.webbroadcast.user.UserService;

/**
 * 변환 워커 여러 대가 DB 대기열을 나눠 갖는 규칙을 실제 PostgreSQL에서 확인한다 (ADR-0004).
 * 워커는 꺼 두고 점유만 직접 다룬다.
 */
@SpringBootTest(properties = "video.worker.enabled=false")
@Testcontainers
class VideoJobsTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@TempDir
	static Path storage;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("media.storage-root", storage::toString);
		registry.add("mediamtx.api-url", () -> "http://127.0.0.1:1");
	}

	private static final Duration LEASE = Duration.ofMinutes(1);
	private static final AtomicInteger users = new AtomicInteger();

	@Autowired
	VideoJobs jobs;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	UserService userService;

	@BeforeEach
	void emptyQueue() {
		jdbc.sql("delete from video").update();
	}

	@Test
	void twoWorkersClaimDifferentVideosInUploadOrder() {
		UUID first = processingVideo("1 minute");
		UUID second = processingVideo("0 minutes");

		VideoJobs.Claim a = jobs.claimNext(LEASE).orElseThrow();
		VideoJobs.Claim b = jobs.claimNext(LEASE).orElseThrow();

		assertThat(a.videoId()).isEqualTo(first);
		assertThat(b.videoId()).isEqualTo(second);
		assertThat(jobs.claimNext(LEASE)).isEmpty();
	}

	@Test
	void anExpiredClaimIsTakenOverAndTheOldWorkerCannotFinish() {
		UUID id = processingVideo("0 minutes");
		VideoJobs.Claim dead = jobs.claimNext(LEASE).orElseThrow();
		jdbc.sql("update video set claim_expires_at = now() - interval '1 second' where id = :id").param("id", id).update();

		VideoJobs.Claim takeover = jobs.claimNext(LEASE).orElseThrow();

		assertThat(takeover.videoId()).isEqualTo(id);
		assertThat(jobs.extend(dead, LEASE)).isFalse();
		assertThat(jobs.finish(dead, VideoStatus.PUBLISHED)).isZero();
		assertThat(jobs.finish(takeover, VideoStatus.PUBLISHED)).isOne();
		assertThat(status(id)).isEqualTo("PUBLISHED");
	}

	@Test
	void aReleasedClaimIsAvailableAtOnce() {
		processingVideo("0 minutes");
		VideoJobs.Claim stopping = jobs.claimNext(LEASE).orElseThrow();

		jobs.release(stopping);

		assertThat(jobs.claimNext(LEASE)).map(VideoJobs.Claim::videoId).contains(stopping.videoId());
	}

	@Test
	void finishedVideosAreNotClaimed() {
		UUID id = processingVideo("0 minutes");
		jobs.finish(jobs.claimNext(LEASE).orElseThrow(), VideoStatus.FAILED);

		assertThat(jobs.claimNext(LEASE)).isEmpty();
		assertThat(status(id)).isEqualTo("FAILED");
	}

	private UUID processingVideo(String age) {
		int n = users.incrementAndGet();
		UUID owner = userService.signUp("jobs" + n, "password1", "작업" + n).getId();
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into video (id, owner_id, title, status, original_extension, size_bytes, created_at)
				values (:id, :owner, '영상', 'PROCESSING', 'mp4', 1, now() - cast(:age as interval))
				""")
			.param("id", id)
			.param("owner", owner)
			.param("age", age)
			.update();
		return id;
	}

	private String status(UUID id) {
		return jdbc.sql("select status from video where id = :id").param("id", id).query(String.class).single();
	}
}
