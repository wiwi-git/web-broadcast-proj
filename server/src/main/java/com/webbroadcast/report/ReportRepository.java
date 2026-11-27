package com.webbroadcast.report;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReportRepository extends JpaRepository<Report, UUID> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(UUID reporterId, ReportTargetType targetType, UUID targetId);
}
