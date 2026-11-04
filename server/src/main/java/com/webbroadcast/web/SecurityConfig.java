package com.webbroadcast.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인은 세션 쿠키로 유지한다 (ADR-0003). 시청과 목록 조회는 누구나 할 수 있고,
 * 상태를 바꾸는 요청은 로그인과 CSRF 토큰이 필요하다. Owner·Author 확인은 각 서비스가 한다.
 */
@Configuration
class SecurityConfig {

	static final String LOGOUT_PATH = "/api/auth/logout";

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
			throws Exception {
		http
			// 웹은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 돌려보낸다. MediaMTX 인증 훅은 쿠키를 쓰지 않는다
			.csrf(csrf -> csrf.spa().ignoringRequestMatchers(MediaServerAuthController.PATH))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.GET, "/api/**", "/media/**").permitAll()
				.requestMatchers(HttpMethod.POST, AuthController.SIGN_UP_PATH, AuthController.LOGIN_PATH,
						MediaServerAuthController.PATH).permitAll()
				.requestMatchers("/error").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, e) ->
						writeProblem(response, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."))
				// 로그인한 상태에서 거부되는 경우는 CSRF 토큰이 없거나 맞지 않을 때다
				.accessDeniedHandler((request, response, e) ->
						writeProblem(response, HttpStatus.FORBIDDEN, "요청이 거부되었습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.")))
			.logout(logout -> logout
				.logoutUrl(LOGOUT_PATH)
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.requestCache(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/** ApiExceptionHandler와 같은 ProblemDetail 형식. detail은 고정 문구라 따로 이스케이프하지 않는다. */
	private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
				+ "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
	}
}
