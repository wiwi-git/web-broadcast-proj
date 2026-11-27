package com.webbroadcast.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.webbroadcast.mail.MailProperties;
import com.webbroadcast.mail.MailService;

/**
 * 메일 속 일회용 링크로 하는 일과 비밀번호 관리.
 * <ul>
 * <li>이메일 인증: 가입하면 인증 메일을 보낸다(24시간). 인증한 회원만 동영상 업로드·댓글을 할 수 있다</li>
 * <li>비밀번호 변경: 로그인한 회원이 지금 비밀번호를 알면 바꾼다. 다른 기기의 로그인은 끊는다</li>
 * <li>비밀번호 재설정: 이메일을 넣으면 재설정 링크를 보낸다(30분). 링크로 새 비밀번호를 정하면 모든 기기의 로그인이 끊긴다</li>
 * </ul>
 * 메일은 트랜잭션이 커밋된 뒤에 보낸다. 같은 메일은 1분에 한 번만 보낸다.
 */
@Service
public class AccountMailService {

	/** 같은 회원에게 같은 용도의 메일을 다시 보내기까지 기다리는 시간 */
	static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

	private final UserRepository users;
	private final EmailTokenRepository tokens;
	private final MailService mailService;
	private final MailProperties mailProperties;
	private final PasswordEncoder passwordEncoder;
	private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;
	private final Clock clock;

	AccountMailService(UserRepository users, EmailTokenRepository tokens, MailService mailService,
			MailProperties mailProperties, PasswordEncoder passwordEncoder,
			ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions, Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.mailService = mailService;
		this.mailProperties = mailProperties;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.clock = clock;
	}

	// 이메일 인증

	/** 가입 직후 인증 메일을 보낸다 */
	@Transactional
	public void sendVerification(UUID userId) {
		User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
		if (!user.isEmailVerified()) {
			sendVerificationMail(user);
		}
	}

	/** 내 계정에서 인증 메일을 다시 받는다. 이전 링크는 쓸 수 없게 된다 */
	@Transactional
	public void resendVerification(UUID userId) {
		User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
		if (user.isEmailVerified()) {
			throw new InvalidAccountRequestException("이미 인증한 이메일입니다.");
		}
		if (sentRecently(user, EmailTokenPurpose.VERIFY_EMAIL)) {
			throw new TooManyRequestsException("인증 메일을 방금 보냈습니다. 1분 뒤에 다시 시도해 주세요.");
		}
		sendVerificationMail(user);
	}

	/** 메일 속 링크의 값으로 인증한다. 로그인하지 않아도 된다 */
	@Transactional
	public User verifyEmail(String token) {
		Instant now = clock.instant();
		EmailToken row = usable(token, EmailTokenPurpose.VERIFY_EMAIL, now, "인증 링크가 만료되었거나 이미 사용했습니다. 내 계정에서 인증 메일을 다시 받아 주세요.");
		row.use(now);
		User user = row.getUser();
		user.verifyEmail(now);
		return user;
	}

	private void sendVerificationMail(User user) {
		Instant now = clock.instant();
		tokens.retireAll(user.getId(), EmailTokenPurpose.VERIFY_EMAIL, now);
		EmailToken.Issued issued = EmailToken.issue(user, EmailTokenPurpose.VERIFY_EMAIL, now);
		tokens.save(issued.row());
		String link = mailProperties.link("/verify-email?token=" + issued.token());
		afterCommit(() -> mailService.send(user.getEmail(), "[Web Broadcast] 이메일 주소를 인증해 주세요", """
				%s님, Web Broadcast에 가입해 주셔서 감사합니다.

				아래 링크를 누르면 이메일 인증이 끝나고, 동영상을 올리고 댓글을 쓸 수 있습니다.
				링크는 24시간 동안 한 번 쓸 수 있습니다.

				%s

				가입한 적이 없다면 이 메일은 무시해 주세요.
				""".formatted(user.getNickname(), link)));
	}

	// 비밀번호 변경 (로그인한 회원)

	/**
	 * 지금 비밀번호를 확인하고 바꾼다. keepSessionId(지금 요청의 세션)를 뺀 다른 기기의 로그인은 끊는다.
	 */
	@Transactional
	public void changePassword(UUID userId, String currentPassword, String newPassword, String keepSessionId) {
		User user = users.findById(userId).orElseThrow(UserNotFoundException::new);
		if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidAccountRequestException("지금 비밀번호가 올바르지 않습니다.");
		}
		setPassword(user, newPassword);
		String email = user.getEmail();
		afterCommit(() -> endSessions(email, keepSessionId));
	}

	// 비밀번호 재설정 (로그인하지 않은 회원)

	/**
	 * 가입한 이메일이면 재설정 링크를 보낸다. 가입 여부를 알려 주지 않으려고 결과는 언제나 같다.
	 * 정지된 계정에는 보내지 않는다.
	 */
	@Transactional
	public void requestPasswordReset(String rawEmail) {
		users.findByEmail(UserService.normalizeEmail(rawEmail))
			.filter(user -> !user.isSuspended())
			.filter(user -> !sentRecently(user, EmailTokenPurpose.RESET_PASSWORD))
			.ifPresent(this::sendResetMail);
	}

	/** 메일 속 링크의 값으로 새 비밀번호를 정한다. 링크를 받았으니 이메일도 인증된 것으로 본다. 모든 로그인을 끊는다 */
	@Transactional
	public void resetPassword(String token, String newPassword) {
		Instant now = clock.instant();
		EmailToken row = usable(token, EmailTokenPurpose.RESET_PASSWORD, now,
				"비밀번호 재설정 링크가 만료되었거나 이미 사용했습니다. 비밀번호 찾기를 다시 해 주세요.");
		User user = row.getUser();
		setPassword(user, newPassword);
		row.use(now);
		user.verifyEmail(now);
		String email = user.getEmail();
		afterCommit(() -> endSessions(email, null));
	}

	private void sendResetMail(User user) {
		Instant now = clock.instant();
		tokens.retireAll(user.getId(), EmailTokenPurpose.RESET_PASSWORD, now);
		EmailToken.Issued issued = EmailToken.issue(user, EmailTokenPurpose.RESET_PASSWORD, now);
		tokens.save(issued.row());
		String link = mailProperties.link("/reset-password?token=" + issued.token());
		afterCommit(() -> mailService.send(user.getEmail(), "[Web Broadcast] 비밀번호 재설정 링크", """
				%s님, 비밀번호 재설정을 요청하셨습니다.

				아래 링크를 눌러 새 비밀번호를 정해 주세요. 링크는 30분 동안 한 번 쓸 수 있습니다.

				%s

				요청한 적이 없다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않습니다.
				""".formatted(user.getNickname(), link)));
	}

	// 공통

	private void setPassword(User user, String newPassword) {
		UserService.checkPassword(newPassword);
		user.changePassword(passwordEncoder.encode(newPassword));
	}

	private EmailToken usable(String token, EmailTokenPurpose purpose, Instant now, String failure) {
		if (token == null || token.isBlank()) {
			throw new InvalidAccountRequestException(failure);
		}
		return tokens.findByTokenHash(EmailToken.hash(token.strip()))
			.filter(row -> row.isUsable(purpose, now))
			.orElseThrow(() -> new InvalidAccountRequestException(failure));
	}

	private boolean sentRecently(User user, EmailTokenPurpose purpose) {
		Instant since = clock.instant().minus(RESEND_INTERVAL);
		return tokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
			.filter(latest -> latest.getCreatedAt().isAfter(since))
			.isPresent();
	}

	private void endSessions(String email, String keepSessionId) {
		FindByIndexNameSessionRepository<? extends Session> repository = sessions.getIfAvailable();
		if (repository == null) {
			return;
		}
		repository.findByPrincipalName(email)
			.keySet()
			.stream()
			.filter(id -> !id.equals(keepSessionId))
			.forEach(repository::deleteById);
	}

	private static void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
		}
	}
}
