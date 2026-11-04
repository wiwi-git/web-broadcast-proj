package com.webbroadcast.web;

import java.time.Instant;
import java.util.UUID;

import com.webbroadcast.comment.Comment;

/** updatedAt이 있으면 수정된 Comment다. */
public record CommentResponse(UUID id, String body, UserSummary author, Instant createdAt, Instant updatedAt) {

	static CommentResponse from(Comment comment) {
		return new CommentResponse(comment.getId(), comment.getBody(), UserSummary.from(comment.getAuthor()),
				comment.getCreatedAt(), comment.getUpdatedAt());
	}
}
