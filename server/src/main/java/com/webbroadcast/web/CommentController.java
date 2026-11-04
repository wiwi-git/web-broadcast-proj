package com.webbroadcast.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.comment.CommentService;
import com.webbroadcast.user.CurrentUser;

/** 읽기는 누구나, 쓰기는 로그인한 User, 수정·삭제는 Author만 (SecurityConfig, CommentService). */
@RestController
class CommentController {

	record BodyRequest(String body) {
	}

	/** nextCursor를 다음 요청의 before로 넘기면 더 오래된 Comment가 온다. null이면 끝이다. */
	record VideoCommentsResponse(List<CommentResponse> items, UUID nextCursor, long totalCount) {
	}

	private final CommentService commentService;

	CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@GetMapping("/api/videos/{videoId}/comments")
	VideoCommentsResponse listOnVideo(@PathVariable UUID videoId, @RequestParam(required = false) UUID before) {
		CommentService.VideoComments page = commentService.listOnVideo(videoId, before);
		return new VideoCommentsResponse(page.items().stream().map(CommentResponse::from).toList(),
				page.nextCursor(), page.totalCount());
	}

	@PostMapping("/api/videos/{videoId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	CommentResponse writeOnVideo(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID videoId,
			@RequestBody BodyRequest request) {
		return CommentResponse.from(commentService.writeOnVideo(videoId, user.id(), request.body()));
	}

	/** 최근 Comment들을 오래된 순으로 */
	@GetMapping("/api/broadcasts/{broadcastId}/comments")
	List<CommentResponse> listOnBroadcast(@PathVariable UUID broadcastId) {
		return commentService.listOnBroadcast(broadcastId).stream().map(CommentResponse::from).toList();
	}

	@PostMapping("/api/broadcasts/{broadcastId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	CommentResponse writeOnBroadcast(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID broadcastId,
			@RequestBody BodyRequest request) {
		return CommentResponse.from(commentService.writeOnBroadcast(broadcastId, user.id(), request.body()));
	}

	@PatchMapping("/api/comments/{id}")
	CommentResponse edit(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody BodyRequest request) {
		return CommentResponse.from(commentService.edit(id, user.id(), request.body()));
	}

	@DeleteMapping("/api/comments/{id}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		commentService.delete(id, user.id());
		return ResponseEntity.noContent().build();
	}
}
