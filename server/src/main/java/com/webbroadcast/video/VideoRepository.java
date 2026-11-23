package com.webbroadcast.video;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, UUID> {

	@EntityGraph(attributePaths = "owner")
	Page<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status, Pageable pageable);

	/** Owner가 올린 Original 크기의 합계(바이트). 변환 중·실패한 Video도 디스크를 쓰므로 모두 센다. */
	@Query("select coalesce(sum(v.sizeBytes), 0) from Video v where v.owner.id = :ownerId")
	long totalSizeOfOwner(@Param("ownerId") UUID ownerId);
}
