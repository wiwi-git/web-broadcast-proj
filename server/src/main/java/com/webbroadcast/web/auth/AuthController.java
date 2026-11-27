package com.webbroadcast.web.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.user.AccountMailService;
import com.webbroadcast.user.CurrentUser;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;
import com.webbroadcast.withdrawal.WithdrawalService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** 회원가입·로그인. 로그아웃은 Spring Security가 SecurityConfig.LOGOUT_PATH에서 처리한다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public static final String SIGN_UP_PATH = "/api/auth/signup";
	public static final String LOGIN_PATH = "/api/auth/login";
	/** 메일 속 링크로 여는 일이라 로그인하지 않아도 된다 */
	public static final String VERIFY_EMAIL_PATH = "/api/auth/verify-email";
	public static final String PASSWORD_RESET_REQUEST_PATH = "/api/auth/password-reset/request";
	public static final String PASSWORD_RESET_PATH = "/api/auth/password-reset";

	/** agreeTerms: 만 14세 이상이며 이용약관과 개인정보처리방침에 동의함. true여야 가입된다 */
	record SignUpRequest(String email, String password, String nickname, Boolean agreeTerms) {
	}

	record LoginRequest(String email, String password) {
	}

	record WithdrawRequest(String password) {
	}

	record TokenRequest(String token) {
	}

	record PasswordChangeRequest(String currentPassword, String newPassword) {
	}

	record PasswordResetRequest(String email) {
	}

	record PasswordResetConfirm(String token, String newPassword) {
	}

	/** admin이면 운영자 화면을 쓸 수 있다 */
	/** emailVerified가 false면 동영상 업로드·댓글을 할 수 없다 */
	record MeUser(UUID id, String email, String nickname, boolean admin, boolean emailVerified) {
	}

	/** 로그인하지 않았으면 user가 null */
	record MeResponse(MeUser user) {
	}

	private final UserService userService;
	private final WithdrawalService withdrawalService;
	private final AccountMailService accountMailService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(UserService userService, WithdrawalService withdrawalService, AccountMailService accountMailService,
			AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
		this.userService = userService;
		this.withdrawalService = withdrawalService;
		this.accountMailService = accountMailService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
	}

	/** 가입하면 바로 로그인된다 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	MeResponse signUp(@RequestBody SignUpRequest body, HttpServletRequest request, HttpServletResponse response) {
		User user = userService.signUp(body.email(), body.password(), body.nickname(),
				Boolean.TRUE.equals(body.agreeTerms()));
		accountMailService.sendVerification(user.getId());
		return logIn(body.email(), body.password(), request, response);
	}

	@PostMapping("/login")
	MeResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
		return logIn(body.email(), body.password(), request, response);
	}

	/** 메일 속 인증 링크(웹 /verify-email)가 부른다. 로그인하지 않아도 된다 */
	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void verifyEmail(@RequestBody TokenRequest body) {
		accountMailService.verifyEmail(body.token());
	}

	/** 인증 메일을 다시 보낸다. 1분에 한 번 (429) */
	@PostMapping("/verify-email/resend")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resendVerification(@AuthenticationPrincipal CurrentUser user) {
		accountMailService.resendVerification(user.id());
	}

	/** 로그인한 회원이 지금 비밀번호를 알면 바꾼다. 이 기기의 로그인은 유지하고 다른 기기는 끊는다 */
	@PostMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal CurrentUser user, @RequestBody PasswordChangeRequest body,
			HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		accountMailService.changePassword(user.id(), body.currentPassword(), body.newPassword(),
				session == null ? null : session.getId());
	}

	/** 비밀번호 찾기. 가입 여부를 알려 주지 않도록 언제나 204다 */
	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void requestPasswordReset(@RequestBody PasswordResetRequest body) {
		accountMailService.requestPasswordReset(body.email());
	}

	/** 메일 속 재설정 링크(웹 /reset-password)에서 새 비밀번호를 정한다. 모든 기기의 로그인이 끊긴다 */
	@PostMapping("/password-reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@RequestBody PasswordResetConfirm body) {
		accountMailService.resetPassword(body.token(), body.newPassword());
	}

	/**
	 * 회원 탈퇴. 비밀번호를 다시 받아 확인하고 그 User의 모든 것을 지운다 (WithdrawalService).
	 * 모든 기기의 로그인이 끊기고, 이 요청의 세션도 무효가 된다
	 */
	@PostMapping("/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal CurrentUser user, @RequestBody WithdrawRequest body,
			HttpServletRequest request) {
		withdrawalService.withdraw(user.id(), body.password());
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		contextHolder.clearContext();
	}

	/** 웹은 시작할 때 이 요청으로 로그인 상태를 확인하고 CSRF 토큰 쿠키를 받는다 */
	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal CurrentUser user, CsrfToken csrfToken) {
		// 토큰 값을 읽어야 XSRF-TOKEN 쿠키가 내려간다
		csrfToken.getToken();
		return response(user);
	}

	private MeResponse logIn(String email, String password, HttpServletRequest request,
			HttpServletResponse response) {
		// 실패하면 AuthenticationException → ApiExceptionHandler에서 401
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(UserService.normalizeEmail(email), password));

		// 세션 고정 공격을 막기 위해 로그인할 때 세션 ID를 바꾼다
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return response((CurrentUser) authentication.getPrincipal());
	}

	/**
	 * 세션의 로그인 정보는 로그인할 때의 모습이라, 로그인한 뒤에 바뀔 수 있는 인증 여부는 DB에서 읽는다.
	 * 운영자 여부는 권한과 맞추려고 세션의 값을 쓴다(역할을 바꾸면 다시 로그인해야 한다).
	 */
	private MeResponse response(CurrentUser user) {
		if (user == null) {
			return new MeResponse(null);
		}
		return new MeResponse(userService.find(user.id())
			.map(u -> new MeUser(u.getId(), u.getEmail(), u.getNickname(), user.admin(), u.isEmailVerified()))
			.orElse(null));
	}
}
