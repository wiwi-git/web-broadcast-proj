package com.webbroadcast.comment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 목록은 모두 최신순(작성 시각, 같으면 ID 역순)이다. */
interface CommentRepository extends JpaRepository<Comment, UUID> {

	@EntityGraph(attributePaths = "author")
	@Query("select c from Comment c where c.videoId = :videoId order by c.createdAt desc, c.id desc")
	List<Comment> findLatestOnVideo(UUID videoId, Limit limit);

	/** cursor로 주어진 Comment보다 오래된 것들 */
	@EntityGraph(attributePaths = "author")
	@Query("""
			select c from Comment c
			where c.videoId = :videoId
			  and (c.createdAt < :createdAt or (c.createdAt = :createdAt and c.id < :id))
			order by c.createdAt desc, c.id desc""")
	List<Comment> findOnVideoOlderThan(UUID videoId, Instant createdAt, UUID id, Limit limit);

	long countByVideoId(UUID videoId);

	@EntityGraph(attributePaths = "author")
	@Query("select c from Comment c where c.broadcastId = :broadcastId order by c.createdAt desc, c.id desc")
	List<Comment> findLatestOnBroadcast(UUID broadcastId, Limit limit);
}
