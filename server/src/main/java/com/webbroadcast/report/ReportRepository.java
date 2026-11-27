package com.webbroadcast.report;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReportRepository extends JpaRepository<Report, UUID> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(UUID reporterId, ReportTargetType targetType, UUID targetId);

	/** 오래된 순 */
	@EntityGraph(attributePaths = "reporter")
	List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);

	@EntityGraph(attributePaths = "reporter")
	List<Report> findByTargetTypeAndTargetIdInAndStatus(ReportTargetType targetType, Collection<UUID> targetIds,
			ReportStatus status);

	List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, UUID targetId, ReportStatus status);
}
