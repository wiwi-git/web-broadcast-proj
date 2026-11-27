package com.webbroadcast.report;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.comment.CommentService;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;
import com.webbroadcast.video.VideoService;

/**
 * 로그인한 User가 남의 Broadcast·Video·Comment를 신고한다. 대상마다 한 번만 받는다.
 * 운영자는 처리 전 신고를 보고 대상을 내리거나 기각한다 (ModerationService).
 */
@Service
public class ReportService {

	private final ReportRepository repository;
	private final BroadcastService broadcastService;
	private final VideoService videoService;
	private final CommentService commentService;
	private final UserService userService;
	private final Clock clock;

	ReportService(ReportRepository repository, BroadcastService broadcastService, VideoService videoService,
			CommentService commentService, UserService userService, Clock clock) {
		this.repository = repository;
		this.broadcastService = broadcastService;
		this.videoService = videoService;
		this.commentService = commentService;
		this.userService = userService;
		this.clock = clock;
	}

	public Report file(UUID reporterId, ReportTargetType targetType, UUID targetId, ReportReason reason,
			String rawDetail) {
		if (targetType == null || targetId == null) {
			throw new InvalidReportException("신고할 대상을 찾을 수 없습니다.");
		}
		if (reason == null) {
			throw new InvalidReportException("신고 사유를 골라 주세요.");
		}
		String detail = validDetail(rawDetail);
		if (ownerOf(targetType, targetId).is(reporterId)) {
			throw new InvalidReportException("내가 만든 방송이나 동영상, 내가 쓴 댓글은 신고할 수 없습니다.");
		}
		if (repository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
			throw new AlreadyReportedException();
		}
		try {
			return repository.saveAndFlush(Report.filed(userService.get(reporterId), targetType, targetId, reason,
					detail, clock.instant()));
		}
		catch (DataIntegrityViolationException e) {
			// 같은 신고를 동시에 두 번 보낸 경우
			throw new AlreadyReportedException();
		}
	}

	/** 처리 전인 신고 전부, 오래된 순 */
	public List<Report> listOpen() {
		return repository.findByStatusOrderByCreatedAtAsc(ReportStatus.OPEN);
	}

	/** 대상들을 내리게 만든(ACTIONED) 신고, 대상 ID별로 */
	public Map<UUID, List<Report>> actionedFor(ReportTargetType targetType, Collection<UUID> targetIds) {
		if (targetIds.isEmpty()) {
			return Map.of();
		}
		return repository.findByTargetTypeAndTargetIdInAndStatus(targetType, targetIds, ReportStatus.ACTIONED)
			.stream()
			.collect(Collectors.groupingBy(Report::getTargetId));
	}

	/**
	 * 회원 탈퇴. 그 User가 한 신고를 지운다. 그 User의 Content에 걸린 다른 사람의 신고는
	 * 신고한 사람의 기록이므로 남긴다(대상은 지워졌다고 보인다).
	 */
	public void forget(UUID userId) {
		repository.deleteByReporterId(userId);
		repository.forgetResolver(userId);
	}

	/** 대상 하나에 걸린 처리 전 신고를 모두 닫는다 */
	public void resolveOpen(ReportTargetType targetType, UUID targetId, ReportStatus result, UUID adminId) {
		User admin = userService.get(adminId);
		Instant now = clock.instant();
		List<Report> open = repository.findByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.OPEN);
		open.forEach(report -> report.resolve(result, admin, now));
		repository.saveAll(open);
	}

	/** 대상이 없으면 각 서비스의 NotFound 예외가 난다 */
	private User ownerOf(ReportTargetType targetType, UUID targetId) {
		return switch (targetType) {
			case BROADCAST -> broadcastService.get(targetId).getOwner();
			case VIDEO -> videoService.get(targetId).getOwner();
			case COMMENT -> commentService.get(targetId).getAuthor();
		};
	}

	private static String validDetail(String rawDetail) {
		String detail = rawDetail == null ? "" : rawDetail.strip();
		if (detail.length() > Report.DETAIL_MAX_LENGTH) {
			throw new InvalidReportException("신고 내용은 " + Report.DETAIL_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return detail.isEmpty() ? null : detail;
	}
}
