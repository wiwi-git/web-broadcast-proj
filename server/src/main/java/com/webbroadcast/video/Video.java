package com.webbroadcast.video;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.webbroadcast.user.User;

@Entity
@Table(name = "video")
public class Video {

	public static final int TITLE_MAX_LENGTH = 100;
	public static final int DESCRIPTION_MAX_LENGTH = 5000;

	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(length = DESCRIPTION_MAX_LENGTH)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VideoStatus status;

	@Column(nullable = false, length = 10)
	private String originalExtension;

	@Column(nullable = false)
	private long sizeBytes;

	private Double durationSeconds;

	@Column(nullable = false)
	private Instant createdAt;

	protected Video() {
	}

	/** 업로드가 끝난 직후의 Video. 변환 전이므로 처리 중 상태로 시작한다. */
	static Video uploaded(UUID id, User owner, String title, String description, String originalExtension,
			long sizeBytes, Double durationSeconds) {
		Video video = new Video();
		video.id = id;
		video.owner = owner;
		video.title = title;
		video.description = description;
		video.status = VideoStatus.PROCESSING;
		video.originalExtension = originalExtension;
		video.sizeBytes = sizeBytes;
		video.durationSeconds = durationSeconds;
		video.createdAt = Instant.now();
		return video;
	}

	boolean isOwnedBy(UUID userId) {
		return owner.is(userId);
	}

	/** 제목과 설명만 바꿀 수 있다. 영상 파일은 바꿀 수 없다. */
	void edit(String title, String description) {
		this.title = title;
		this.description = description;
	}

	public UUID getId() {
		return id;
	}

	public User getOwner() {
		return owner;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public VideoStatus getStatus() {
		return status;
	}

	public String getOriginalExtension() {
		return originalExtension;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Double getDurationSeconds() {
		return durationSeconds;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
