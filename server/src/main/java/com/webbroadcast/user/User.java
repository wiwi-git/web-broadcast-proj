package com.webbroadcast.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원. 이메일은 로그인과 안내 메일에만 쓰고, 다른 회원에게는 닉네임으로 보인다.
 */
@Entity
@Table(name = "app_user")
public class User {

	@Id
	private UUID id;

	/** 로그인 아이디. 소문자로 저장한다 */
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, unique = true, length = 20)
	private String nickname;

	@Column(nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role = UserRole.USER;

	/** 정지되지 않았으면 null */
	private Instant suspendedAt;

	/** 메일의 인증 링크를 누른 시각. 인증하지 않았으면 null */
	private Instant emailVerifiedAt;

	/** 가입할 때 이용약관·개인정보처리방침에 동의한 시각. 이 기능 전에 가입했으면 null */
	private Instant termsAgreedAt;

	/** 동의한 문서의 버전(시행일) */
	@Column(length = 20)
	private String termsVersion;

	protected User() {
	}

	/** 이용약관·개인정보처리방침(termsVersion)에 동의하고 가입한다 */
	public static User signedUp(UUID id, String email, String passwordHash, String nickname, String termsVersion,
			Instant now) {
		User user = new User();
		user.termsAgreedAt = now;
		user.termsVersion = termsVersion;
		user.id = id;
		user.email = email;
		user.passwordHash = passwordHash;
		user.nickname = nickname;
		user.createdAt = now;
		return user;
	}

	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}

	public boolean isSuspended() {
		return suspendedAt != null;
	}

	/** 정지되지 않았으면 null */
	public Instant getSuspendedAt() {
		return suspendedAt;
	}

	void suspend(Instant now) {
		if (suspendedAt == null) {
			suspendedAt = now;
		}
	}

	void unsuspend() {
		suspendedAt = null;
	}

	/** 인증한 회원만 동영상을 올리고 댓글을 쓸 수 있다 */
	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	void verifyEmail(Instant now) {
		if (emailVerifiedAt == null) {
			emailVerifiedAt = now;
		}
	}

	void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public boolean is(UUID userId) {
		return id.equals(userId);
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	public String getNickname() {
		return nickname;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
