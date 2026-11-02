package com.webbroadcast.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.webbroadcast.video.Video;
import com.webbroadcast.video.VideoService;

@RestController
@RequestMapping("/api/videos")
class VideoController {

	private final VideoService videoService;

	VideoController(VideoService videoService) {
		this.videoService = videoService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<VideoResponse> upload(
			@RequestParam(value = "file", required = false) MultipartFile file,
			@RequestParam(value = "title", required = false) String title,
			@RequestParam(value = "description", required = false) String description) {
		Video video = videoService.upload(file, title, description);
		return ResponseEntity.created(URI.create("/api/videos/" + video.getId())).body(VideoResponse.from(video));
	}

	@GetMapping
	VideoPageResponse list(@RequestParam(defaultValue = "0") int page) {
		return VideoPageResponse.from(videoService.listPublished(page));
	}

	@GetMapping("/{id}")
	VideoResponse get(@PathVariable UUID id) {
		return VideoResponse.from(videoService.get(id));
	}
}
