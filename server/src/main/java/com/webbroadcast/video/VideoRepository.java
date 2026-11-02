package com.webbroadcast.video;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, UUID> {

	Page<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status, Pageable pageable);

	List<Video> findByStatus(VideoStatus status);
}
