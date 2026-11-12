package com.webbroadcast.video;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 처리 중인 Video를 변환 대기열로 쓴다 (ADR-0004). 별도 메시지 큐 없이 DB 행을 워커 하나가 점유한다.
 * 점유는 기한이 있어서, 워커가 연장하지 못하고 죽으면 기한이 지난 뒤 다른 워커가 가져간다.
 */
@Component
class VideoJobs {

	/** 워커 하나가 Video 하나를 점유한 상태. token이 맞아야 연장하거나 끝낼 수 있다. */
	record Claim(UUID videoId, UUID token) {
	}

	private final JdbcClient jdbc;

	VideoJobs(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 아무도 점유하지 않은(또는 점유 기한이 지난) 가장 오래된 처리 중 Video를 점유한다.
	 * SKIP LOCKED라서 여러 워커가 동시에 불러도 서로 다른 Video를 받는다.
	 */
	Optional<Claim> claimNext(Duration lease) {
		UUID token = UUID.randomUUID();
		return jdbc.sql("""
				update video set claim_token = :token, claim_expires_at = now() + make_interval(secs => :seconds)
				where id = (
				    select id from video
				    where status = 'PROCESSING' and (claim_expires_at is null or claim_expires_at < now())
				    order by created_at
				    limit 1
				    for update skip locked)
				returning id
				""")
			.param("token", token)
			.param("seconds", lease.toSeconds())
			.query(UUID.class)
			.optional()
			.map(id -> new Claim(id, token));
	}

	/** 점유 기한을 늘린다. 점유를 잃었거나 Video가 지워졌으면 false. */
	boolean extend(Claim claim, Duration lease) {
		return jdbc.sql("""
				update video set claim_expires_at = now() + make_interval(secs => :seconds)
				where id = :id and claim_token = :token and status = 'PROCESSING'
				""")
			.param("seconds", lease.toSeconds())
			.param("id", claim.videoId())
			.param("token", claim.token())
			.update() == 1;
	}

	/** 변환을 끝내지 못하고 내려놓는다. 다른 워커가 곧바로 가져갈 수 있다. */
	void release(Claim claim) {
		jdbc.sql("update video set claim_token = null, claim_expires_at = null where id = :id and claim_token = :token")
			.param("id", claim.videoId())
			.param("token", claim.token())
			.update();
	}

	/**
	 * 변환 결과를 기록한다. 점유를 잃었거나 그 사이 Video가 지워졌으면 0을 돌려준다.
	 * 엔티티를 저장하지 않고 갱신하는 이유는, 삭제된 Video를 다시 만들어 내지 않기 위해서다.
	 */
	int finish(Claim claim, VideoStatus status) {
		return jdbc.sql("""
				update video set status = :status, claim_token = null, claim_expires_at = null
				where id = :id and claim_token = :token and status = 'PROCESSING'
				""")
			.param("status", status.name())
			.param("id", claim.videoId())
			.param("token", claim.token())
			.update();
	}
}
