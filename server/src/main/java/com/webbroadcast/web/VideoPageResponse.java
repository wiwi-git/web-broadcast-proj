package com.webbroadcast.web;

import java.util.List;

import org.springframework.data.domain.Page;

import com.webbroadcast.video.Video;

public record VideoPageResponse(List<VideoResponse> items, int page, int totalPages, long totalItems) {

	static VideoPageResponse from(Page<Video> page) {
		return new VideoPageResponse(
				page.getContent().stream().map(VideoResponse::from).toList(),
				page.getNumber(),
				page.getTotalPages(),
				page.getTotalElements());
	}
}
