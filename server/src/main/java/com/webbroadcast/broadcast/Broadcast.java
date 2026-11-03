package com.webbroadcast.broadcast;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "broadcast")
public class Broadcast {

	public static final int TITLE_MAX_LENGTH = 100;

	/** 준비 상태에서 이 시간 안에 영상이 오지 않으면 종료한다. */
	static final Duration PENDING_TIMEOUT = Duration.ofSeconds(30);

	@Id
	private UUID id;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BroadcastStatus status;

	@Column(nullable = false, length = 64)
	private String streamKeyHash;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant startedAt;

	private Instant endedAt;

	protected Broadcast() {
	}

	static Broadcast pending(UUID id, String title, String streamKeyHash, Instant now) {
		Broadcast broadcast = new Broadcast();
		broadcast.id = id;
		broadcast.title = title;
		broadcast.status = BroadcastStatus.PENDING;
		broadcast.streamKeyHash = streamKeyHash;
		broadcast.createdAt = now;
		return broadcast;
	}

	boolean acceptsStreamKey(String key) {
		return StreamKey.matches(key, streamKeyHash);
	}

	/**
	 * 미디어 서버에 영상이 들어오고 있는지(mediaReady)에 맞춰 상태를 옮긴다.
	 * 상태가 바뀌었으면 true.
	 */
	boolean syncWithMedia(boolean mediaReady, Instant now) {
		switch (status) {
			case PENDING -> {
				if (mediaReady) {
					status = BroadcastStatus.LIVE;
					startedAt = now;
					return true;
				}
				if (now.isAfter(createdAt.plus(PENDING_TIMEOUT))) {
					end(now);
					return true;
				}
				return false;
			}
			case LIVE -> {
				// 끊긴 송출은 이어가지 않는다
				if (!mediaReady) {
					end(now);
					return true;
				}
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	void end(Instant now) {
		if (status != BroadcastStatus.ENDED) {
			status = BroadcastStatus.ENDED;
			endedAt = now;
		}
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public BroadcastStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}
}
