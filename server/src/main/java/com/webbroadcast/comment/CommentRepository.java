package com.webbroadcast.comment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 목록은 모두 최신순(작성 시각, 같으면 ID 역순)이고, 운영자가 숨긴 Comment는 빠진다. */
interface CommentRepository extends JpaRepository<Comment, UUID> {

	@EntityGraph(attributePaths = "author")
	@Query("select c from Comment c where c.videoId = :videoId and c.hiddenAt is null order by c.createdAt desc, c.id desc")
	List<Comment> findLatestOnVideo(UUID videoId, Limit limit);

	/** cursor로 주어진 Comment보다 오래된 것들 */
	@EntityGraph(attributePaths = "author")
	@Query("""
			select c from Comment c
			where c.videoId = :videoId and c.hiddenAt is null
			  and (c.createdAt < :createdAt or (c.createdAt = :createdAt and c.id < :id))
			order by c.createdAt desc, c.id desc""")
	List<Comment> findOnVideoOlderThan(UUID videoId, Instant createdAt, UUID id, Limit limit);

	long countByVideoIdAndHiddenAtIsNull(UUID videoId);

	/** 운영자가 숨긴 Video의 Comment ID들 */
	@Query("select c.id from Comment c where c.videoId = :videoId and c.hiddenAt is not null")
	List<UUID> findHiddenIdsOnVideo(UUID videoId);

	@EntityGraph(attributePaths = "author")
	@Query("select c from Comment c where c.broadcastId = :broadcastId and c.hiddenAt is null order by c.createdAt desc, c.id desc")
	List<Comment> findLatestOnBroadcast(UUID broadcastId, Limit limit);
}
