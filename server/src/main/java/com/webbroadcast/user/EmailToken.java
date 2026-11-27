package com.webbroadcast.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 메일 속 링크에 넣는 일회용 값 하나. 값 자체는 메일에만 있고 DB에는 SHA-256 해시만 둔다.
 * 한 번 쓰거나, 같은 용도의 새 링크를 보내거나, 유효 시간이 지나면 쓸 수 없다.
 */
@Entity
@Table(name = "email_token")
class EmailToken {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EmailTokenPurpose purpose;

	@Column(nullable = false, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant usedAt;

	protected EmailToken() {
	}

	/** 메일에 넣을 값과 저장할 행 */
	record Issued(String token, EmailToken row) {
	}

	static Issued issue(User user, EmailTokenPurpose purpose, Instant now) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		EmailToken row = new EmailToken();
		row.id = UUID.randomUUID();
		row.user = user;
		row.purpose = purpose;
		row.tokenHash = hash(token);
		row.createdAt = now;
		row.expiresAt = now.plus(purpose.validFor());
		return new Issued(token, row);
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	boolean isUsable(EmailTokenPurpose expected, Instant now) {
		return purpose == expected && usedAt == null && now.isBefore(expiresAt);
	}

	void use(Instant now) {
		usedAt = now;
	}

	User getUser() {
		return user;
	}

	Instant getCreatedAt() {
		return createdAt;
	}
}
