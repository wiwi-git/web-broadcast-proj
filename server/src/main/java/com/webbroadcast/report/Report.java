package com.webbroadcast.report;

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

import com.webbroadcast.user.User;

/**
 * Reporter가 남의 Broadcast·Video·Comment 하나를 신고한 기록. 대상이 지워져도 남는다.
 */
@Entity
@Table(name = "report")
public class Report {

	public static final int DETAIL_MAX_LENGTH = 200;

	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "reporter_id")
	private User reporter;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportTargetType targetType;

	@Column(nullable = false)
	private UUID targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportReason reason;

	/** 적지 않았으면 null */
	@Column(length = DETAIL_MAX_LENGTH)
	private String detail;

	@Column(nullable = false)
	private Instant createdAt;

	protected Report() {
	}

	static Report filed(User reporter, ReportTargetType targetType, UUID targetId, ReportReason reason, String detail,
			Instant now) {
		Report report = new Report();
		report.id = UUID.randomUUID();
		report.reporter = reporter;
		report.targetType = targetType;
		report.targetId = targetId;
		report.reason = reason;
		report.detail = detail;
		report.createdAt = now;
		return report;
	}

	public UUID getId() {
		return id;
	}
}
