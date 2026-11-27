package com.webbroadcast.web.admin;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.moderation.ModerationService;
import com.webbroadcast.report.Report;
import com.webbroadcast.report.ReportReason;
import com.webbroadcast.report.ReportTargetType;
import com.webbroadcast.user.CurrentUser;
import com.webbroadcast.user.User;

/** 운영자(ADMIN)만 쓴다 (SecurityConfig). */
@RestController
public class ModerationController {

	public static final String PATH = "/api/admin";

	record OwnerResponse(UUID id, String nickname, boolean suspended) {
		static OwnerResponse from(User user) {
			return new OwnerResponse(user.getId(), user.getNickname(), user.isSuspended());
		}
	}

	record ReportItem(String reporterNickname, ReportReason reason, String detail, Instant createdAt) {
		static ReportItem from(Report report) {
			return new ReportItem(report.getReporter().getNickname(), report.getReason(), report.getDetail(),
					report.getCreatedAt());
		}
	}

	/**
	 * 대상 하나에 걸린 처리 전 신고. 대상이 지워졌으면 summary·parent·owner가 null이다.
	 * parentType·parentId는 화면에서 열 Content다 (Comment면 달린 Video나 Broadcast).
	 */
	record ReportGroupResponse(ReportTargetType targetType, UUID targetId, String summary, ReportTargetType parentType,
			UUID parentId, OwnerResponse owner, boolean hidden, int reportCount, Map<ReportReason, Long> reasons,
			List<ReportItem> reports) {
	}

	private final ModerationService moderationService;

	ModerationController(ModerationService moderationService) {
		this.moderationService = moderationService;
	}

	@GetMapping(PATH + "/reports")
	List<ReportGroupResponse> openReports() {
		return moderationService.openGroups().stream().map(group -> {
			Map<ReportReason, Long> reasons = new EnumMap<>(ReportReason.class);
			group.reports().forEach(r -> reasons.merge(r.getReason(), 1L, Long::sum));
			var target = group.target();
			return new ReportGroupResponse(group.targetType(), group.targetId(),
					target.map(ModerationService.Target::summary).orElse(null),
					target.map(ModerationService.Target::parentType).orElse(null),
					target.map(ModerationService.Target::parentId).orElse(null),
					target.map(t -> OwnerResponse.from(t.owner())).orElse(null),
					target.map(ModerationService.Target::hidden).orElse(false), group.reports().size(), reasons,
					group.reports().stream().map(ReportItem::from).toList());
		}).toList();
	}

	@PostMapping(PATH + "/reports/{targetType}/{targetId}/hide")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void hide(@AuthenticationPrincipal CurrentUser admin, @PathVariable ReportTargetType targetType,
			@PathVariable UUID targetId) {
		moderationService.hide(targetType, targetId, admin.id());
	}

	@PostMapping(PATH + "/reports/{targetType}/{targetId}/dismiss")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void dismiss(@AuthenticationPrincipal CurrentUser admin, @PathVariable ReportTargetType targetType,
			@PathVariable UUID targetId) {
		moderationService.dismiss(targetType, targetId, admin.id());
	}

	@PostMapping(PATH + "/users/{userId}/suspend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void suspend(@PathVariable UUID userId) {
		moderationService.suspend(userId);
	}

	@PostMapping(PATH + "/users/{userId}/unsuspend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unsuspend(@PathVariable UUID userId) {
		moderationService.unsuspend(userId);
	}
}
