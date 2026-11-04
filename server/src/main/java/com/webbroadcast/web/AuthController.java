package com.webbroadcast.web;

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

import com.webbroadcast.user.CurrentUser;
import com.webbroadcast.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 회원가입·로그인. 로그아웃은 Spring Security가 SecurityConfig.LOGOUT_PATH에서 처리한다. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	static final String SIGN_UP_PATH = "/api/auth/signup";
	static final String LOGIN_PATH = "/api/auth/login";

	record SignUpRequest(String username, String password, String nickname) {
	}

	record LoginRequest(String username, String password) {
	}

	record MeUser(UUID id, String username, String nickname) {
	}

	/** 로그인하지 않았으면 user가 null */
	record MeResponse(MeUser user) {
	}

	private final UserService userService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(UserService userService, AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository) {
		this.userService = userService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
	}

	/** 가입하면 바로 로그인된다 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	MeResponse signUp(@RequestBody SignUpRequest body, HttpServletRequest request, HttpServletResponse response) {
		userService.signUp(body.username(), body.password(), body.nickname());
		return logIn(body.username().strip(), body.password(), request, response);
	}

	@PostMapping("/login")
	MeResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
		return logIn(body.username(), body.password(), request, response);
	}

	/** 웹은 시작할 때 이 요청으로 로그인 상태를 확인하고 CSRF 토큰 쿠키를 받는다 */
	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal CurrentUser user, CsrfToken csrfToken) {
		// 토큰 값을 읽어야 XSRF-TOKEN 쿠키가 내려간다
		csrfToken.getToken();
		return response(user);
	}

	private MeResponse logIn(String username, String password, HttpServletRequest request,
			HttpServletResponse response) {
		// 실패하면 AuthenticationException → ApiExceptionHandler에서 401
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(username, password));

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

	private static MeResponse response(CurrentUser user) {
		return new MeResponse(user == null ? null : new MeUser(user.id(), user.getUsername(), user.nickname()));
	}
}
