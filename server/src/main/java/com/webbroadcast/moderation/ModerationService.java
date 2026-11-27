package com.webbroadcast.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.comment.Comment;
import com.webbroadcast.comment.CommentService;
import com.webbroadcast.report.Report;
import com.webbroadcast.report.ReportService;
import com.webbroadcast.report.ReportStatus;
import com.webbroadcast.report.ReportTargetType;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;
import com.webbroadcast.video.Video;
import com.webbroadcast.video.VideoService;

/**
 * 운영자(ADMIN)가 처리 전 신고를 대상별로 보고, 대상을 내리거나(숨김) 기각하고, User를 정지한다.
 * 권한 확인은 SecurityConfig가 /api/admin/**에 한다.
 */
@Service
public class ModerationService {

	/**
	 * 신고된 대상의 지금 모습. 대상이 지워졌으면 ReportGroup.target이 비어 있다.
	 *
	 * @param summary 방송·동영상 제목이나 댓글 본문
	 * @param parentType 화면에서 열 Content의 종류. Comment면 달린 Video나 Broadcast다
	 * @param owner Content의 Owner나 Comment의 Author
	 */
	public record Target(String summary, ReportTargetType parentType, UUID parentId, User owner, boolean hidden) {
	}

	/** 대상 하나에 걸린 처리 전 신고들, 오래된 순 */
	public record ReportGroup(ReportTargetType targetType, UUID targetId, Optional<Target> target,
			List<Report> reports) {
	}

	/**
	 * 운영자가 내린 Broadcast·Video·Comment 하나와, 그것을 내리게 만든 신고들.
	 * 신고 없이 내린 경우는 없지만(내리기는 신고 카드에서 한다), 신고 기록이 비어 있어도 그대로 보인다.
	 *
	 * @param video VIDEO일 때만. 운영자 화면이 재생 주소를 만든다
	 * @param status Broadcast·Video의 상태 이름. Comment면 null
	 */
	public record HiddenContent(ReportTargetType type, UUID id, String summary, ReportTargetType parentType,
			UUID parentId, User owner, Instant createdAt, Instant hiddenAt, String status, Video video,
			List<Report> reports) {
	}

	/** 운영자 화면의 내린 콘텐츠 한 쪽 */
	public static final int HIDDEN_PAGE_SIZE = 20;

	private final ReportService reportService;
	private final BroadcastService broadcastService;
	private final VideoService videoService;
	private final CommentService commentService;
	private final UserService userService;
	private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

	ModerationService(ReportService reportService, BroadcastService broadcastService, VideoService videoService,
			CommentService commentService, UserService userService,
			ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
		this.reportService = reportService;
		this.broadcastService = broadcastService;
		this.videoService = videoService;
		this.commentService = commentService;
		this.userService = userService;
		this.sessions = sessions;
	}

	/** 처리 전 신고를 대상별로 묶는다. 가장 먼저 신고된 대상이 앞에 온다 */
	@Transactional(readOnly = true)
	public List<ReportGroup> openGroups() {
		Map<String, List<Report>> byTarget = new LinkedHashMap<>();
		for (Report report : reportService.listOpen()) {
			byTarget.computeIfAbsent(report.getTargetType() + "/" + report.getTargetId(), k -> new ArrayList<>())
				.add(report);
		}
		return byTarget.values().stream().map(reports -> {
			Report first = reports.getFirst();
			return new ReportGroup(first.getTargetType(), first.getTargetId(),
					target(first.getTargetType(), first.getTargetId()), reports);
		}).toList();
	}

	/** 대상을 숨기고 걸린 신고를 모두 처리 완료로 닫는다. Broadcast는 진행 중이면 끝난다 */
	@Transactional
	public void hide(ReportTargetType targetType, UUID targetId, UUID adminId) {
		switch (targetType) {
			case BROADCAST -> broadcastService.hide(targetId);
			case VIDEO -> videoService.hide(targetId);
			case COMMENT -> commentService.hide(targetId);
		}
		reportService.resolveOpen(targetType, targetId, ReportStatus.ACTIONED, adminId);
	}

	/** 문제없다고 보고 걸린 신고를 닫는다. 대상은 그대로 둔다 */
	@Transactional
	public void dismiss(ReportTargetType targetType, UUID targetId, UUID adminId) {
		reportService.resolveOpen(targetType, targetId, ReportStatus.DISMISSED, adminId);
	}

	/**
	 * User를 정지한다. 로그인한 세션을 모두 끊고, 진행 중인 Broadcast를 끝낸다.
	 * 이미 쓴 Content·Comment는 그대로 두고, 신고된 것은 따로 내린다.
	 */
	@Transactional
	public void suspend(UUID userId) {
		User user = userService.find(userId).orElseThrow(() -> new InvalidModerationException("회원을 찾을 수 없습니다."));
		if (user.isAdmin()) {
			throw new InvalidModerationException("운영자 계정은 정지할 수 없습니다.");
		}
		userService.suspend(userId);
		broadcastService.endActiveOf(userId);
		FindByIndexNameSessionRepository<? extends Session> repository = sessions.getIfAvailable();
		if (repository != null) {
			repository.findByPrincipalName(user.getUsername()).keySet().forEach(repository::deleteById);
		}
	}

	@Transactional
	public void unsuspend(UUID userId) {
		if (userService.find(userId).isEmpty()) {
			throw new InvalidModerationException("회원을 찾을 수 없습니다.");
		}
		userService.unsuspend(userId);
	}

	/** 최근에 내린 순 */
	@Transactional(readOnly = true)
	public Page<HiddenContent> hidden(ReportTargetType type, int page) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), HIDDEN_PAGE_SIZE,
				Sort.by(Sort.Direction.DESC, "hiddenAt", "id"));
		Page<HiddenContent> found = switch (type) {
			case BROADCAST -> broadcastService.listHidden(pageable)
				.map(b -> new HiddenContent(type, b.getId(), b.getTitle(), ReportTargetType.BROADCAST, b.getId(),
						b.getOwner(), b.getCreatedAt(), b.getHiddenAt(), b.getStatus().name(), null, List.of()));
			case VIDEO -> videoService.listHidden(pageable)
				.map(v -> new HiddenContent(type, v.getId(), v.getTitle(), ReportTargetType.VIDEO, v.getId(),
						v.getOwner(), v.getCreatedAt(), v.getHiddenAt(), v.getStatus().name(), v, List.of()));
			case COMMENT -> commentService.listHidden(pageable).map(c -> {
				Target where = commentTarget(c);
				return new HiddenContent(type, c.getId(), c.getBody(), where.parentType(), where.parentId(),
						c.getAuthor(), c.getCreatedAt(), c.getHiddenAt(), null, null, List.of());
			});
		};
		Map<UUID, List<Report>> reports = reportService.actionedFor(type,
				found.getContent().stream().map(HiddenContent::id).toList());
		return found.map(h -> new HiddenContent(h.type(), h.id(), h.summary(), h.parentType(), h.parentId(),
				h.owner(), h.createdAt(), h.hiddenAt(), h.status(), h.video(), reports.getOrDefault(h.id(), List.of())));
	}

	private Optional<Target> target(ReportTargetType type, UUID id) {
		return switch (type) {
			case BROADCAST -> broadcastService.findIncludingHidden(id)
				.map(b -> new Target(b.getTitle(), ReportTargetType.BROADCAST, b.getId(), b.getOwner(), b.isHidden()));
			case VIDEO -> videoService.findIncludingHidden(id)
				.map(v -> new Target(v.getTitle(), ReportTargetType.VIDEO, v.getId(), v.getOwner(), v.isHidden()));
			case COMMENT -> commentService.findIncludingHidden(id).map(ModerationService::commentTarget);
		};
	}

	private static Target commentTarget(Comment c) {
		boolean onVideo = c.getVideoId() != null;
		return new Target(c.getBody(), onVideo ? ReportTargetType.VIDEO : ReportTargetType.BROADCAST,
				onVideo ? c.getVideoId() : c.getBroadcastId(), c.getAuthor(), c.isHidden());
	}
}
