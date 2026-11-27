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

	/** 유튜브 동시 송출을 켰는지. 방송을 시작할 때 정하고 바꾸지 않는다 (ADR-0007) */
	@Column(nullable = false)
	private boolean simulcast;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private SimulcastStatus simulcastStatus;

	/** BroadcastSync와 Owner의 요청이 겹칠 때, 지워진 Broadcast를 되살리거나 종료를 덮어쓰지 않게 한다 */
	@Version
	private long version;

	/** 운영자가 내렸으면 그 시각. 숨긴 Broadcast는 누구에게도 보이지 않는다 */
	private Instant hiddenAt;

	protected Broadcast() {
	}

	static Broadcast pending(UUID id, User owner, String title, String streamKeyHash, boolean simulcast,
			Instant now) {
		Broadcast broadcast = new Broadcast();
		broadcast.simulcast = simulcast;
		broadcast.simulcastStatus = simulcast ? SimulcastStatus.CONNECTING : null;
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

	/** 중계 스크립트가 알린 유튜브 상태를 남긴다. 동시 송출을 켜지 않았거나 끝난 방송은 바꾸지 않는다. 바뀌었으면 true */
	boolean reportSimulcast(SimulcastStatus reported) {
		if (!simulcast || status == BroadcastStatus.ENDED || simulcastStatus == reported) {
			return false;
		}
		simulcastStatus = reported;
		return true;
	}

	/** 중계할 수 있는 방송인지. 영상이 들어오자마자 중계가 시작되므로 아직 준비 상태일 수 있다 */
	boolean wantsSimulcast() {
		return simulcast && status != BroadcastStatus.ENDED;
	}

	/** 운영자가 내린다. 진행 중이면 끝낸다 */
	void hide(Instant now) {
		end(now);
		if (hiddenAt == null) {
			hiddenAt = now;
		}
	}

	public boolean isHidden() {
		return hiddenAt != null;
	}

	/** 운영자가 내린 시각. 내리지 않았으면 null */
	public Instant getHiddenAt() {
		return hiddenAt;
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

	public boolean isSimulcast() {
		return simulcast;
	}

	public SimulcastStatus getSimulcastStatus() {
		return simulcastStatus;
	}
}
