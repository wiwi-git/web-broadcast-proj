package com.webbroadcast.broadcast;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class BroadcastService {

	/** 시작 직후 Broadcaster에게만 돌려주는 값. streamKey는 다시 조회할 수 없다. */
	public record Started(Broadcast broadcast, String streamKey) {
	}

	private final BroadcastRepository repository;
	private final Clock clock;

	BroadcastService(BroadcastRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public Started start(String rawTitle) {
		String title = rawTitle == null ? "" : rawTitle.strip();
		if (title.isEmpty()) {
			throw new InvalidBroadcastException("방송 제목을 입력해 주세요.");
		}
		if (title.length() > Broadcast.TITLE_MAX_LENGTH) {
			throw new InvalidBroadcastException("방송 제목은 " + Broadcast.TITLE_MAX_LENGTH + "자 이하여야 합니다.");
		}
		String key = StreamKey.generate();
		Broadcast broadcast = repository.save(
				Broadcast.pending(UUID.randomUUID(), title, StreamKey.hash(key), clock.instant()));
		return new Started(broadcast, key);
	}

	public Broadcast get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new BroadcastNotFoundException(id));
	}

	/** 방송 중인 Broadcast, 최근에 시작한 순. */
	public List<Broadcast> listLive() {
		return repository.findByStatusOrderByStartedAtDesc(BroadcastStatus.LIVE);
	}

	/** 최근 종료된 Broadcast 20개. */
	public List<Broadcast> listRecentlyEnded() {
		return repository.findTop20ByStatusOrderByEndedAtDesc(BroadcastStatus.ENDED);
	}

	/** Broadcaster가 직접 끝낸다. 송출 연결은 다음 동기화 때 끊긴다. */
	public void end(UUID id, String streamKey) {
		Broadcast broadcast = get(id);
		if (!broadcast.acceptsStreamKey(streamKey)) {
			throw new StreamKeyMismatchException();
		}
		broadcast.end(clock.instant());
		repository.save(broadcast);
	}

	/** 준비 상태의 Broadcast에 올바른 Stream Key로만 송출할 수 있다. 종료된 Broadcast는 다시 시작되지 않는다. */
	public boolean canPublish(String mediaPath, String streamKey) {
		return MediaPath.parse(mediaPath)
			.flatMap(repository::findById)
			.filter(b -> b.getStatus() == BroadcastStatus.PENDING)
			.filter(b -> b.acceptsStreamKey(streamKey))
			.isPresent();
	}

	public boolean canWatch(String mediaPath) {
		return MediaPath.parse(mediaPath)
			.flatMap(repository::findById)
			.filter(b -> b.getStatus() != BroadcastStatus.ENDED)
			.isPresent();
	}
}
