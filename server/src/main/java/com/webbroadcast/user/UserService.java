package com.webbroadcast.user;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {

	/** 이메일 형식. 실제로 받을 수 있는 주소인지는 인증 메일로 확인한다 */
	public static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
	public static final int EMAIL_MAX_LENGTH = 254;
	public static final int PASSWORD_MIN_LENGTH = 8;
	/** BCrypt는 72바이트까지만 쓰므로 그보다 긴 비밀번호는 받지 않는다 */
	public static final int PASSWORD_MAX_LENGTH = 64;
	public static final int NICKNAME_MIN_LENGTH = 2;
	public static final int NICKNAME_MAX_LENGTH = 20;
	/**
	 * 지금 이용약관·개인정보처리방침의 버전(시행일). 문서를 바꾸면 웹·앱의 문서 날짜와 함께 바꾼다.
	 * 가입할 때 이 값이 동의 기록으로 남는다.
	 */
	public static final String TERMS_VERSION = "2026-11-27";

	/** 운영자 회원 목록 한 쪽 */
	public static final int ADMIN_PAGE_SIZE = 20;

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	UserService(UserRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/** agreedToTerms: 만 14세 이상이며 이용약관과 개인정보처리방침에 동의했는지 */
	public User signUp(String rawEmail, String password, String rawNickname, boolean agreedToTerms) {
		String email = normalizeEmail(rawEmail);
		String nickname = rawNickname == null ? "" : rawNickname.strip();
		if (email.length() > EMAIL_MAX_LENGTH || !EMAIL.matcher(email).matches()) {
			throw new InvalidSignUpException("이메일 주소를 확인해 주세요.");
		}
		checkPassword(password);
		int nicknameLength = nickname.codePointCount(0, nickname.length());
		if (nicknameLength < NICKNAME_MIN_LENGTH || nicknameLength > NICKNAME_MAX_LENGTH) {
			throw new InvalidSignUpException(
					"닉네임은 " + NICKNAME_MIN_LENGTH + "~" + NICKNAME_MAX_LENGTH + "자여야 합니다.");
		}
		if (!agreedToTerms) {
			throw new InvalidSignUpException("만 14세 이상이며 이용약관과 개인정보처리방침에 동의해야 가입할 수 있습니다.");
		}
		if (repository.existsByEmail(email)) {
			throw new DuplicateUserException("이미 가입한 이메일입니다.");
		}
		if (repository.existsByNickname(nickname)) {
			throw new DuplicateUserException("이미 사용 중인 닉네임입니다.");
		}
		try {
			return repository.saveAndFlush(User.signedUp(UUID.randomUUID(), email,
					passwordEncoder.encode(password), nickname, TERMS_VERSION, clock.instant()));
		}
		catch (DataIntegrityViolationException e) {
			// 동시에 같은 값으로 가입한 경우
			throw new DuplicateUserException("이미 가입한 이메일이거나 사용 중인 닉네임입니다.");
		}
	}

	/** 가입, 비밀번호 변경·재설정에서 같은 규칙을 쓴다 */
	public static void checkPassword(String password) {
		if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
			throw new InvalidSignUpException(
					"비밀번호는 " + PASSWORD_MIN_LENGTH + "~" + PASSWORD_MAX_LENGTH + "자여야 합니다.");
		}
	}

	/** 이메일은 앞뒤 공백을 빼고 소문자로 다룬다 */
	public static String normalizeEmail(String rawEmail) {
		return rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);
	}

	public Optional<User> find(UUID id) {
		return repository.findById(id);
	}

	/** 이메일 인증이 필요한 일(동영상 업로드, 댓글)을 하기 전에 확인한다 */
	public User requireVerified(UUID id) {
		User user = get(id);
		if (!user.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		return user;
	}

	/** 탈퇴할 때 비밀번호를 다시 확인한다 */
	public boolean passwordMatches(UUID id, String rawPassword) {
		return rawPassword != null && passwordEncoder.matches(rawPassword, get(id).getPasswordHash());
	}

	/** 회원 탈퇴의 마지막 단계. 이 User를 가리키는 기록은 그 전에 지운다 (WithdrawalService). 유튜브 키는 함께 지워진다 */
	public void delete(UUID id) {
		repository.deleteById(id);
		repository.flush();
	}

	/** 운영자 회원 목록, 최근 가입한 순 */
	public Page<User> search(String rawQuery, Boolean suspended, int page) {
		String query = rawQuery == null || rawQuery.isBlank() ? null : rawQuery.strip();
		return repository.search(query, suspended,
				PageRequest.of(Math.max(page, 0), ADMIN_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
	}

	/** 운영자가 정지한다. 운영자인지 확인하고 세션을 끊는 일은 ModerationService가 한다 */
	public User suspend(UUID id) {
		User user = repository.findById(id).orElseThrow(UserNotFoundException::new);
		user.suspend(clock.instant());
		return repository.save(user);
	}

	public User unsuspend(UUID id) {
		User user = repository.findById(id).orElseThrow(UserNotFoundException::new);
		user.unsuspend();
		return repository.save(user);
	}

	/** 로그인한 User. 탈퇴하면 세션을 모두 지우므로 로그인한 동안에는 항상 존재한다. */
	public User get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new IllegalStateException("없는 회원: " + id));
	}

	@Override
	/** 로그인 아이디는 이메일이다 */
	public UserDetails loadUserByUsername(String email) {
		return repository.findByEmail(normalizeEmail(email))
			.map(CurrentUser::new)
			.orElseThrow(() -> new UsernameNotFoundException(email));
	}
}
