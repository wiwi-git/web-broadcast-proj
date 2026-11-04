package com.webbroadcast.broadcast;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.webbroadcast.user.User;

@Entity
@Table(name = "broadcast")
public class Broadcast {

	public static final int TITLE_MAX_LENGTH = 100;

	/** 준비 상태에서 이 시간 안에 영상이 오지 않으면 종료한다. */
	static final Duration PENDING_TIMEOUT = Duration.ofSeconds(30);

	@Id
	private UUID id;

	/** Broadcaster */
	@ManyToOne(optional = false)
	@JoinColumn(name = "owner_id")
	private User owner;

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

	/** BroadcastSync와 Owner의 요청이 겹칠 때, 지워진 Broadcast를 되살리거나 종료를 덮어쓰지 않게 한다 */
	@Version
	private long version;

	protected Broadcast() {
	}

	static Broadcast pending(UUID id, User owner, String title, String streamKeyHash, Instant now) {
		Broadcast broadcast = new Broadcast();
		broadcast.id = id;
		broadcast.owner = owner;
		broadcast.title = title;
		broadcast.status = BroadcastStatus.PENDING;
		broadcast.streamKeyHash = streamKeyHash;
		broadcast.createdAt = now;
		return broadcast;
	}

	boolean acceptsStreamKey(String key) {
		return StreamKey.matches(key, streamKeyHash);
	}

	/** 이전 Stream Key는 더 이상 받지 않는다. 이미 연결된 송출은 끊지 않는다. */
	void replaceStreamKey(String newStreamKeyHash) {
		this.streamKeyHash = newStreamKeyHash;
	}

	boolean isOwnedBy(UUID userId) {
		return owner.is(userId);
	}

	/** 제목은 방송 중이든 종료 후든 바꿀 수 있다. */
	void rename(String title) {
		this.title = title;
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

	public User getOwner() {
		return owner;
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
