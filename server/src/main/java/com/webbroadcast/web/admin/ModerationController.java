package com.webbroadcast.web.admin;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.moderation.ModerationService;
import com.webbroadcast.report.Report;
import com.webbroadcast.report.ReportReason;
import com.webbroadcast.report.ReportTargetType;
import com.webbroadcast.user.CurrentUser;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;
import com.webbroadcast.web.video.VideoResponse;

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

	/** 운영자에게만 아이디를 보여 준다 (운영자 지정 SQL에 쓴다) */
	record UserResponse(UUID id, String username, String nickname, boolean admin, boolean suspended,
			Instant suspendedAt, Instant createdAt) {
		static UserResponse from(User user) {
			return new UserResponse(user.getId(), user.getUsername(), user.getNickname(), user.isAdmin(),
					user.isSuspended(), user.getSuspendedAt(), user.getCreatedAt());
		}
	}

	record UserPageResponse(List<UserResponse> items, int page, int totalPages, long totalItems) {
	}

	/**
	 * 운영자가 내린 것 하나. summary는 제목이나 댓글 본문, parent는 화면에서 열 Content(댓글이면 달린 곳)다.
	 * reportCount·reasons는 이것을 내리게 만든 신고다. playbackUrl·thumbnailUrl은 공개 상태까지 변환된 동영상만 있다.
	 */
	record HiddenItemResponse(ReportTargetType type, UUID id, String summary, ReportTargetType parentType,
			UUID parentId, OwnerResponse owner, Instant createdAt, Instant hiddenAt, String status, int reportCount,
			Map<ReportReason, Long> reasons, List<ReportItem> reports, String playbackUrl, String thumbnailUrl) {
	}

	record HiddenPageResponse(List<HiddenItemResponse> items, int page, int totalPages, long totalItems) {
	}

	private final ModerationService moderationService;
	private final UserService userService;

	ModerationController(ModerationService moderationService, UserService userService) {
		this.moderationService = moderationService;
		this.userService = userService;
	}

	/** q는 아이디나 닉네임 일부, suspended로 정지 여부를 거른다. 최근 가입한 순 20명씩 */
	@GetMapping(PATH + "/users")
	UserPageResponse users(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean suspended,
			@RequestParam(defaultValue = "0") int page) {
		Page<User> found = userService.search(q, suspended, page);
		return new UserPageResponse(found.getContent().stream().map(UserResponse::from).toList(), found.getNumber(),
				found.getTotalPages(), found.getTotalElements());
	}

	/** 운영자가 내린 Broadcast·Video·Comment, 최근에 내린 순 20개씩. 파일은 남아 있어 동영상은 재생할 수 있다 */
	@GetMapping(PATH + "/hidden")
	HiddenPageResponse hidden(@RequestParam ReportTargetType type, @RequestParam(defaultValue = "0") int page) {
		Page<ModerationService.HiddenContent> found = moderationService.hidden(type, page);
		return new HiddenPageResponse(found.getContent().stream().map(h -> {
			VideoResponse video = h.video() == null ? null : VideoResponse.from(h.video());
			return new HiddenItemResponse(h.type(), h.id(), h.summary(), h.parentType(), h.parentId(),
					OwnerResponse.from(h.owner()), h.createdAt(), h.hiddenAt(), h.status(), h.reports().size(),
					countReasons(h.reports()), h.reports().stream().map(ReportItem::from).toList(),
					video == null ? null : video.playbackUrl(), video == null ? null : video.thumbnailUrl());
		}).toList(), found.getNumber(), found.getTotalPages(), found.getTotalElements());
	}

	private static Map<ReportReason, Long> countReasons(List<Report> reports) {
		Map<ReportReason, Long> reasons = new EnumMap<>(ReportReason.class);
		reports.forEach(r -> reasons.merge(r.getReason(), 1L, Long::sum));
		return reasons;
	}

	@GetMapping(PATH + "/reports")
	List<ReportGroupResponse> openReports() {
		return moderationService.openGroups().stream().map(group -> {
			Map<ReportReason, Long> reasons = countReasons(group.reports());
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
