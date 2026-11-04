package com.webbroadcast.user;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

	public static final Pattern USERNAME = Pattern.compile("[a-z0-9]{4,20}");
	public static final int PASSWORD_MIN_LENGTH = 8;
	/** BCrypt는 72바이트까지만 쓰므로 그보다 긴 비밀번호는 받지 않는다 */
	public static final int PASSWORD_MAX_LENGTH = 64;
	public static final int NICKNAME_MIN_LENGTH = 2;
	public static final int NICKNAME_MAX_LENGTH = 20;

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public User signUp(String rawUsername, String password, String rawNickname) {
		String username = rawUsername == null ? "" : rawUsername.strip();
		String nickname = rawNickname == null ? "" : rawNickname.strip();
		if (!USERNAME.matcher(username).matches()) {
			throw new InvalidSignUpException("아이디는 영문 소문자와 숫자로 4~20자여야 합니다.");
		}
		if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
			throw new InvalidSignUpException(
					"비밀번호는 " + PASSWORD_MIN_LENGTH + "~" + PASSWORD_MAX_LENGTH + "자여야 합니다.");
		}
		int nicknameLength = nickname.codePointCount(0, nickname.length());
		if (nicknameLength < NICKNAME_MIN_LENGTH || nicknameLength > NICKNAME_MAX_LENGTH) {
			throw new InvalidSignUpException(
					"닉네임은 " + NICKNAME_MIN_LENGTH + "~" + NICKNAME_MAX_LENGTH + "자여야 합니다.");
		}
		if (repository.existsByUsername(username)) {
			throw new DuplicateUserException("이미 사용 중인 아이디입니다.");
		}
		if (repository.existsByNickname(nickname)) {
			throw new DuplicateUserException("이미 사용 중인 닉네임입니다.");
		}
		try {
			return repository.saveAndFlush(User.signedUp(UUID.randomUUID(), username,
					passwordEncoder.encode(password), nickname, clock.instant()));
		}
		catch (DataIntegrityViolationException e) {
			// 동시에 같은 값으로 가입한 경우
			throw new DuplicateUserException("이미 사용 중인 아이디나 닉네임입니다.");
		}
	}

	/** 로그인한 User. 세션이 살아 있는 동안 탈퇴는 없으므로 항상 존재한다. */
	public User get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new IllegalStateException("없는 회원: " + id));
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return repository.findByUsername(username)
			.map(CurrentUser::new)
			.orElseThrow(() -> new UsernameNotFoundException(username));
	}
}
