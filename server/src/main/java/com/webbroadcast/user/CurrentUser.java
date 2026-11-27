package com.webbroadcast.user;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인한 User. 세션(DB)에 직렬화되어 저장되므로 바뀌지 않는 값만 담는다.
 * 비밀번호 해시는 인증 직후 지워진다.
 */
public final class CurrentUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID id;
	/** 로그인 아이디(이메일). 세션 저장소가 이 값으로 회원의 세션을 찾는다 */
	private final String email;
	private final String nickname;
	/** 로그인할 때의 역할. 역할을 바꾸면 다시 로그인해야 반영된다 */
	private final boolean admin;
	/** 정지된 User는 로그인할 수 없다 (DaoAuthenticationProvider가 isAccountNonLocked로 확인한다) */
	private final boolean suspended;
	private String passwordHash;

	CurrentUser(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.nickname = user.getNickname();
		this.admin = user.isAdmin();
		this.suspended = user.isSuspended();
		this.passwordHash = user.getPasswordHash();
	}

	public UUID id() {
		return id;
	}

	public String nickname() {
		return nickname;
	}

	public boolean admin() {
		return admin;
	}

	public String email() {
		return email;
	}

	/** Spring Security의 로그인 아이디. 이 서비스에서는 이메일이다 */
	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of();
	}

	@Override
	public boolean isAccountNonLocked() {
		return !suspended;
	}

	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}
}
