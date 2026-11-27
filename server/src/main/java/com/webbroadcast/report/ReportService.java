package com.webbroadcast.report;

import java.time.Clock;
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
 * 신고를 처리하는 운영자 기능은 아직 없다.
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
