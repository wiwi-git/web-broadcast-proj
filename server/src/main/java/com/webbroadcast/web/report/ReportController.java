package com.webbroadcast.web.report;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.report.ReportReason;
import com.webbroadcast.report.ReportService;
import com.webbroadcast.report.ReportTargetType;
import com.webbroadcast.user.CurrentUser;

/** 로그인한 User만 신고한다 (SecurityConfig의 나머지 요청 규칙). */
@RestController
class ReportController {

	record ReportRequest(ReportTargetType targetType, UUID targetId, ReportReason reason, String detail) {
	}

	record ReportResponse(UUID id) {
	}

	private final ReportService reportService;

	ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@PostMapping("/api/reports")
	@ResponseStatus(HttpStatus.CREATED)
	ReportResponse file(@AuthenticationPrincipal CurrentUser user, @RequestBody ReportRequest request) {
		return new ReportResponse(reportService
			.file(user.id(), request.targetType(), request.targetId(), request.reason(), request.detail())
			.getId());
	}
}
