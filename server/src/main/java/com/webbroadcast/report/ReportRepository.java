package com.webbroadcast.report;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ReportRepository extends JpaRepository<Report, UUID> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(UUID reporterId, ReportTargetType targetType, UUID targetId);

	/** 오래된 순 */
	@EntityGraph(attributePaths = "reporter")
	List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);

	@EntityGraph(attributePaths = "reporter")
	List<Report> findByTargetTypeAndTargetIdInAndStatus(ReportTargetType targetType, Collection<UUID> targetIds,
			ReportStatus status);

	List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, UUID targetId, ReportStatus status);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Report r where r.reporter.id = :reporterId")
	int deleteByReporterId(UUID reporterId);

	/** 처리한 운영자가 사라져도 처리 결과는 남긴다 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Report r set r.resolvedBy = null where r.resolvedBy.id = :adminId")
	int forgetResolver(UUID adminId);
}
