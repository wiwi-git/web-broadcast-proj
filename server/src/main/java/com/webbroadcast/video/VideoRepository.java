package com.webbroadcast.video;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface VideoRepository extends JpaRepository<Video, UUID> {

	@EntityGraph(attributePaths = "owner")
	Page<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status, Pageable pageable);

	/**
	 * 변환이 끝난 Video의 상태를 바꾼다. 변환 도중 Video가 삭제되었으면 0을 돌려준다.
	 * 엔티티를 저장하지 않고 갱신하는 이유는, 삭제된 Video를 다시 만들어 내지 않기 위해서다.
	 */
	@Transactional
	@Modifying
	@Query("update Video v set v.status = :status where v.id = :id and v.status = com.webbroadcast.video.VideoStatus.PROCESSING")
	int finishProcessing(UUID id, VideoStatus status);

	List<Video> findByStatus(VideoStatus status);
}
