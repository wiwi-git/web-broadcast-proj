package com.webbroadcast.video;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, UUID> {

	/** 운영자 화면용. 정렬은 pageable로 준다 */
	@EntityGraph(attributePaths = "owner")
	Page<Video> findByHiddenAtIsNotNull(Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	Page<Video> findByStatusAndHiddenAtIsNullOrderByCreatedAtDesc(VideoStatus status, Pageable pageable);

	List<Video> findByOwnerId(UUID ownerId);

	/** Owner가 올린 Original 크기의 합계(바이트). 변환 중·실패한 Video도 디스크를 쓰므로 모두 센다. */
	@Query("select coalesce(sum(v.sizeBytes), 0) from Video v where v.owner.id = :ownerId")
	long totalSizeOfOwner(@Param("ownerId") UUID ownerId);
}
