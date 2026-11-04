package com.webbroadcast.user;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인한 User. 세션(DB)에 직렬화되어 저장되므로 바뀌지 않는 값만 담는다.
 * 비밀번호 해시는 인증 직후 지워진다.
 */
public final class CurrentUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID id;
	private final String username;
	private final String nickname;
	private String passwordHash;

	CurrentUser(User user) {
		this.id = user.getId();
		this.username = user.getUsername();
		this.nickname = user.getNickname();
		this.passwordHash = user.getPasswordHash();
	}

	public UUID id() {
		return id;
	}

	public String nickname() {
		return nickname;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}
}
