package com.webbroadcast.web.video;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.webbroadcast.user.CurrentUser;
import com.webbroadcast.video.Video;
import com.webbroadcast.video.VideoService;

/** 목록·상세는 누구나, 업로드는 로그인한 User, 수정·삭제는 Owner만 (SecurityConfig, VideoService). */
@RestController
@RequestMapping("/api/videos")
class VideoController {

	record EditRequest(String title, String description) {
	}

	private final VideoService videoService;

	VideoController(VideoService videoService) {
		this.videoService = videoService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<VideoResponse> upload(
			@AuthenticationPrincipal CurrentUser user,
			@RequestParam(value = "file", required = false) MultipartFile file,
			@RequestParam(value = "title", required = false) String title,
			@RequestParam(value = "description", required = false) String description) {
		Video video = videoService.upload(user.id(), file, title, description);
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

	@PatchMapping("/{id}")
	VideoResponse edit(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody EditRequest request) {
		return VideoResponse.from(videoService.edit(id, user.id(), request.title(), request.description()));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		videoService.delete(id, user.id());
		return ResponseEntity.noContent().build();
	}
}
