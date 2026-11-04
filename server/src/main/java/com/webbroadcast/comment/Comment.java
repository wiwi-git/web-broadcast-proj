package com.webbroadcast.comment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.webbroadcast.user.User;

/**
 * Video나 Broadcast 중 하나에 달린 Comment. Content가 지워지면 DB에서 함께 지워진다.
 */
@Entity
@Table(name = "comment")
public class Comment {

	public static final int BODY_MAX_LENGTH = 500;

	@Id
	private UUID id;

	private UUID videoId;

	private UUID broadcastId;

	@ManyToOne(optional = false)
	@JoinColumn(name = "author_id")
	private User author;

	@Column(nullable = false, length = BODY_MAX_LENGTH)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	/** 수정한 적이 없으면 null */
	private Instant updatedAt;

	protected Comment() {
	}

	static Comment onVideo(UUID videoId, User author, String body, Instant now) {
		Comment comment = written(author, body, now);
		comment.videoId = videoId;
		return comment;
	}

	static Comment onBroadcast(UUID broadcastId, User author, String body, Instant now) {
		Comment comment = written(author, body, now);
		comment.broadcastId = broadcastId;
		return comment;
	}

	private static Comment written(User author, String body, Instant now) {
		Comment comment = new Comment();
		comment.id = UUID.randomUUID();
		comment.author = author;
		comment.body = body;
		comment.createdAt = now;
		return comment;
	}

	boolean isWrittenBy(UUID userId) {
		return author.is(userId);
	}

	void edit(String body, Instant now) {
		this.body = body;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public User getAuthor() {
		return author;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	UUID getVideoId() {
		return videoId;
	}
}
