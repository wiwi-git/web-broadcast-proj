package com.webbroadcast.broadcast;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미디어 서버의 실제 송출 상황에 맞춰 Broadcast 상태를 주기적으로 맞춘다 (ADR-0002).
 * 이벤트를 놓쳐도 다음 주기에 저절로 맞춰진다.
 */
@Component
class BroadcastSync {

	private static final Logger log = LoggerFactory.getLogger(BroadcastSync.class);

	private final BroadcastRepository repository;
	private final MediaServer mediaServer;
	private final Clock clock;

	BroadcastSync(BroadcastRepository repository, MediaServer mediaServer, Clock clock) {
		this.repository = repository;
		this.mediaServer = mediaServer;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 2000)
	void sync() {
		Map<String, MediaServer.Publisher> active;
		try {
			active = mediaServer.activePaths();
		}
		catch (RuntimeException e) {
			// 미디어 서버에 닿지 않을 때 방송 중인 Broadcast를 섣불리 종료하지 않는다
			log.warn("미디어 서버 조회 실패: {}", e.getMessage());
			return;
		}
		Instant now = clock.instant();

		for (Broadcast broadcast : repository.findByStatusIn(EnumSet.of(BroadcastStatus.PENDING, BroadcastStatus.LIVE))) {
			BroadcastStatus before = broadcast.getStatus();
			if (broadcast.syncWithMedia(active.containsKey(MediaPath.of(broadcast.getId())), now)) {
				try {
					repository.save(broadcast);
					log.info("Broadcast {}: {} → {}", broadcast.getId(), before, broadcast.getStatus());
				}
				catch (OptimisticLockingFailureException e) {
					// 그 사이 Owner가 종료하거나 삭제했다. 다음 주기에 다시 맞춘다
					log.info("Broadcast {}가 동기화 도중 바뀌어 건너뜀", broadcast.getId());
				}
			}
		}

		// 종료된 Broadcast로 아직 송출이 들어오고 있으면 끊는다 (Broadcaster가 직접 종료한 경우 등)
		active.forEach((path, publisher) -> {
			Optional<UUID> id = MediaPath.parse(path);
			boolean ended = id.flatMap(repository::findById)
				.map(b -> b.getStatus() == BroadcastStatus.ENDED)
				.orElse(true);
			if (ended) {
				try {
					mediaServer.disconnect(publisher);
					log.info("종료된 경로 {}의 송출을 끊음", path);
				}
				catch (RuntimeException e) {
					log.warn("송출 끊기 실패: {}", path, e);
				}
			}
		});
	}
}
