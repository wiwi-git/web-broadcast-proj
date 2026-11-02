package com.webbroadcast.video;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "video")
public class Video {

	public static final int TITLE_MAX_LENGTH = 100;
	public static final int DESCRIPTION_MAX_LENGTH = 5000;

	@Id
	private UUID id;

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
	static Video uploaded(UUID id, String title, String description, String originalExtension, long sizeBytes,
			Double durationSeconds) {
		Video video = new Video();
		video.id = id;
		video.title = title;
		video.description = description;
		video.status = VideoStatus.PROCESSING;
		video.originalExtension = originalExtension;
		video.sizeBytes = sizeBytes;
		video.durationSeconds = durationSeconds;
		video.createdAt = Instant.now();
		return video;
	}

	void publish() {
		this.status = VideoStatus.PUBLISHED;
	}

	void fail() {
		this.status = VideoStatus.FAILED;
	}

	public UUID getId() {
		return id;
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
