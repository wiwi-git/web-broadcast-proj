package com.webbroadcast.broadcast;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.webbroadcast.simulcast.SimulcastService;
import com.webbroadcast.user.NotOwnerException;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;

/**
 * Broadcast의 시작·수정·종료·삭제는 Owner만 할 수 있다. 영상 송출만은 Stream Key로 허가한다 (ADR-0003).
 */
@Service
public class BroadcastService {

	/** Owner에게만 돌려주는 값. streamKey는 저장하지 않으므로 다시 필요하면 새로 발급한다. */
	public record Started(Broadcast broadcast, String streamKey) {
	}

	private static final Set<BroadcastStatus> ACTIVE = EnumSet.of(BroadcastStatus.PENDING, BroadcastStatus.LIVE);

	private final BroadcastRepository repository;
	private final UserService userService;
	private final SimulcastService simulcastService;
	private final Clock clock;

	BroadcastService(BroadcastRepository repository, UserService userService, SimulcastService simulcastService,
			Clock clock) {
		this.repository = repository;
		this.userService = userService;
		this.simulcastService = simulcastService;
		this.clock = clock;
	}

	/** simulcast면 Owner의 유튜브 채널로도 내보낸다 (ADR-0007). 고른 값은 다음 방송의 기본값이 된다 */
	public Started start(UUID ownerId, String rawTitle, boolean simulcast) {
		String title = validTitle(rawTitle);
		if (simulcast) {
			simulcastService.requireAvailable();
			if (!simulcastService.hasYouTubeKey(ownerId)) {
				throw new InvalidBroadcastException("유튜브에도 송출하려면 유튜브 키를 먼저 등록해 주세요.");
			}
		}
		repository.findFirstByOwnerIdAndStatusIn(ownerId, ACTIVE).ifPresent(active -> {
			throw new BroadcastInProgressException(active.getId());
		});
		User owner = userService.get(ownerId);
		String key = StreamKey.generate();
		try {
			Broadcast broadcast = repository.saveAndFlush(
					Broadcast.pending(UUID.randomUUID(), owner, title, StreamKey.hash(key), simulcast, clock.instant()));
			simulcastService.rememberChoice(ownerId, simulcast);
			return new Started(broadcast, key);
		}
		catch (DataIntegrityViolationException e) {
			// 같은 User가 동시에 두 번 시작한 경우. DB의 유일 인덱스가 막는다
			throw new BroadcastInProgressException(
					repository.findFirstByOwnerIdAndStatusIn(ownerId, ACTIVE).map(Broadcast::getId).orElse(null));
		}
	}

	/** 운영자가 내린 Broadcast는 Owner에게도 없는 것으로 보인다 */
	public Broadcast get(UUID id) {
		return repository.findById(id).filter(b -> !b.isHidden()).orElseThrow(() -> new BroadcastNotFoundException(id));
	}

	/** 운영자 화면용. 운영자가 내린 것들 */
	public Page<Broadcast> listHidden(Pageable pageable) {
		return repository.findByHiddenAtIsNotNull(pageable);
	}

	/** 운영자 화면용. 숨긴 Broadcast도 찾는다 */
	public Optional<Broadcast> findIncludingHidden(UUID id) {
		return repository.findById(id);
	}

	/** 운영자가 내린다. 진행 중이면 끝내고, 송출 연결은 다음 동기화 때 끊긴다 */
	public void hide(UUID id) {
		Broadcast broadcast = repository.findById(id).orElseThrow(() -> new BroadcastNotFoundException(id));
		broadcast.hide(clock.instant());
		repository.save(broadcast);
	}

	/** 정지된 User의 진행 중인 Broadcast를 끝낸다 */
	public void endActiveOf(UUID ownerId) {
		repository.findFirstByOwnerIdAndStatusIn(ownerId, ACTIVE).ifPresent(broadcast -> {
			broadcast.end(clock.instant());
			repository.save(broadcast);
		});
	}

	/** 방송 중인 Broadcast, 최근에 시작한 순. */
	public List<Broadcast> listLive() {
		return repository.findByStatusAndHiddenAtIsNullOrderByStartedAtDesc(BroadcastStatus.LIVE);
	}

	/** 최근 종료된 Broadcast 20개. */
	public List<Broadcast> listRecentlyEnded() {
		return repository.findTop20ByStatusAndHiddenAtIsNullOrderByEndedAtDesc(BroadcastStatus.ENDED);
	}

	public Broadcast rename(UUID id, UUID userId, String rawTitle) {
		Broadcast broadcast = ownedBy(id, userId);
		broadcast.rename(validTitle(rawTitle));
		return repository.save(broadcast);
	}

	/** Owner가 직접 끝낸다. 송출 연결은 다음 동기화 때 끊긴다. */
	public void end(UUID id, UUID userId) {
		Broadcast broadcast = ownedBy(id, userId);
		broadcast.end(clock.instant());
		repository.save(broadcast);
	}

	/** 진행 중이어도 지울 수 있다. 남은 송출은 다음 동기화 때 끊기고, Comment도 함께 지워진다. */
	public void delete(UUID id, UUID userId) {
		repository.delete(ownedBy(id, userId));
	}

	/** 송출을 기다리는 Broadcast의 Stream Key를 새로 발급한다. 이전 Stream Key는 무효가 된다. */
	public String reissueStreamKey(UUID id, UUID userId) {
		Broadcast broadcast = ownedBy(id, userId);
		if (broadcast.getStatus() != BroadcastStatus.PENDING) {
			throw new InvalidBroadcastException("송출을 기다리는 방송만 방송 키를 다시 받을 수 있습니다.");
		}
		String key = StreamKey.generate();
		broadcast.replaceStreamKey(StreamKey.hash(key));
		repository.save(broadcast);
		return key;
	}

	/** 준비 상태의 Broadcast에 올바른 Stream Key로만 송출할 수 있다. 종료된 Broadcast는 다시 시작되지 않는다. */
	public boolean canPublish(String mediaPath, String streamKey) {
		return MediaPath.parse(mediaPath)
			.flatMap(repository::findById)
			.filter(b -> b.getStatus() == BroadcastStatus.PENDING && !b.isHidden())
			.filter(b -> b.acceptsStreamKey(streamKey))
			.isPresent();
	}

	public boolean canWatch(String mediaPath) {
		return MediaPath.parse(mediaPath)
			.flatMap(repository::findById)
			.filter(b -> b.getStatus() != BroadcastStatus.ENDED && !b.isHidden())
			.isPresent();
	}

	/** MediaMTX 중계 스크립트가 보낼 유튜브 주소. 동시 송출을 켠 진행 중인 방송이고 Owner의 키가 있을 때만 있다 */
	public Optional<String> simulcastTarget(String mediaPath) {
		return MediaPath.parse(mediaPath)
			.flatMap(repository::findById)
			.filter(Broadcast::wantsSimulcast)
			.flatMap(b -> simulcastService.youtubeUrl(b.getOwner().getId()));
	}

	/** 중계 스크립트가 알린 유튜브 상태를 남긴다. 그 사이 종료·삭제된 방송이면 무시한다 */
	public void reportSimulcast(String mediaPath, SimulcastStatus status) {
		MediaPath.parse(mediaPath).flatMap(repository::findById).ifPresent(broadcast -> {
			if (broadcast.reportSimulcast(status)) {
				try {
					repository.save(broadcast);
				}
				catch (OptimisticLockingFailureException e) {
					// 같은 때 상태 동기화나 Owner의 요청이 저장했다. 다음 보고나 화면 갱신에 영향이 없다
				}
			}
		});
	}

	private Broadcast ownedBy(UUID id, UUID userId) {
		Broadcast broadcast = get(id);
		if (!broadcast.isOwnedBy(userId)) {
			throw new NotOwnerException("본인의 방송만 수정·종료·삭제할 수 있습니다.");
		}
		return broadcast;
	}

	private static String validTitle(String rawTitle) {
		String title = rawTitle == null ? "" : rawTitle.strip();
		if (title.isEmpty()) {
			throw new InvalidBroadcastException("방송 제목을 입력해 주세요.");
		}
		if (title.length() > Broadcast.TITLE_MAX_LENGTH) {
			throw new InvalidBroadcastException("방송 제목은 " + Broadcast.TITLE_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return title;
	}
}
