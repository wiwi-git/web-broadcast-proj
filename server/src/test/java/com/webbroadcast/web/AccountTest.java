package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.webbroadcast.mail.MailService;

import jakarta.servlet.http.Cookie;

/**
 * 이메일 인증, 인증한 회원만 업로드·댓글, 비밀번호 변경, 비밀번호 재설정(메일 링크).
 * 메일은 보내지 않고 MailService를 가짜로 바꿔 보낸 내용에서 링크를 꺼낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@TempDir
	static Path storage;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("media.storage-root", storage::toString);
		registry.add("mediamtx.api-url", () -> "http://127.0.0.1:1");
		registry.add("toss.client-key", () -> "");
		registry.add("toss.secret-key", () -> "");
		registry.add("mail.web-origin", () -> "https://web.example");
	}

	private static final Pattern VERIFY_LINK = Pattern.compile("https://web\\.example/verify-email\\?token=([A-Za-z0-9_-]+)");
	private static final Pattern RESET_LINK = Pattern.compile("https://web\\.example/reset-password\\?token=([A-Za-z0-9_-]+)");
	private static final AtomicInteger users = new AtomicInteger();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	MailService mailService;

	record Account(String email, Cookie session) {
	}

	// 이메일 인증

	@Test
	void signUpSendsAVerificationLinkThatWorksOnce() throws Exception {
		Account member = signUp("인증회원");
		mvc.perform(get("/api/auth/me").cookie(member.session())).andExpect(jsonPath("$.user.emailVerified").value(false));

		String token = lastToken(member.email(), VERIFY_LINK);
		// 링크는 로그인하지 않은 브라우저에서도 열린다
		mvc.perform(json(post("/api/auth/verify-email"), "{\"token\":\"%s\"}".formatted(token)))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/me").cookie(member.session())).andExpect(jsonPath("$.user.emailVerified").value(true));

		mvc.perform(json(post("/api/auth/verify-email"), "{\"token\":\"%s\"}".formatted(token)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("인증 링크가 만료되었거나 이미 사용했습니다. 내 계정에서 인증 메일을 다시 받아 주세요."));
		mvc.perform(json(post("/api/auth/verify-email/resend"), "{}").cookie(member.session()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("이미 인증한 이메일입니다."));
	}

	@Test
	void verificationMailCanBeResentOncePerMinuteAndOldLinksStopWorking() throws Exception {
		Account member = signUp("재전송회원");
		String first = lastToken(member.email(), VERIFY_LINK);

		mvc.perform(json(post("/api/auth/verify-email/resend"), "{}").cookie(member.session()))
			.andExpect(status().isTooManyRequests());

		backdateTokens(member.email(), "2 minutes");
		mvc.perform(json(post("/api/auth/verify-email/resend"), "{}").cookie(member.session()))
			.andExpect(status().isNoContent());
		String second = lastToken(member.email(), VERIFY_LINK);
		assertThat(second).isNotEqualTo(first);

		mvc.perform(json(post("/api/auth/verify-email"), "{\"token\":\"%s\"}".formatted(first)))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/verify-email"), "{\"token\":\"%s\"}".formatted(second)))
			.andExpect(status().isNoContent());
	}

	@Test
	void expiredOrMadeUpLinksAreRejected() throws Exception {
		Account member = signUp("만료회원");
		String token = lastToken(member.email(), VERIFY_LINK);
		jdbc.update("update email_token set expires_at = now() - interval '1 minute' "
				+ "where user_id = (select id from app_user where email = ?)", member.email());

		mvc.perform(json(post("/api/auth/verify-email"), "{\"token\":\"%s\"}".formatted(token)))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/verify-email"), "{\"token\":\"made-up\"}")).andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/verify-email"), "{}")).andExpect(status().isBadRequest());
	}

	// 인증한 회원만 업로드·댓글

	@Test
	void onlyVerifiedMembersUploadAndComment() throws Exception {
		Account member = signUp("미인증회원");
		String broadcastId = startBroadcast(member);
		String notVerified = "이메일 인증을 마친 회원만 할 수 있습니다. 내 계정에서 인증 메일을 다시 받을 수 있습니다.";

		mvc.perform(json(post("/api/broadcasts/" + broadcastId + "/comments"), "{\"body\":\"댓글\"}")
				.cookie(member.session()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(notVerified));
		mvc.perform(multipart("/api/videos")
				.file(new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[16]))
				.param("title", "영상")
				.cookie(member.session())
				.with(csrf()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(notVerified));

		verify(member);
		mvc.perform(json(post("/api/broadcasts/" + broadcastId + "/comments"), "{\"body\":\"댓글\"}")
				.cookie(member.session()))
			.andExpect(status().isCreated());
	}

	// 비밀번호 변경

	@Test
	void membersChangeTheirPasswordWithTheCurrentOne() throws Exception {
		Account member = signUp("변경회원");
		Cookie otherDevice = login(member.email(), "password1");

		mvc.perform(json(post("/api/auth/password"), "{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"newpass12\"}")
				.cookie(member.session()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("지금 비밀번호가 올바르지 않습니다."));
		mvc.perform(json(post("/api/auth/password"), "{\"currentPassword\":\"password1\",\"newPassword\":\"short\"}")
				.cookie(member.session()))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/password"), "{\"currentPassword\":\"password1\",\"newPassword\":\"newpass12\"}"))
			.andExpect(status().isUnauthorized());

		mvc.perform(json(post("/api/auth/password"), "{\"currentPassword\":\"password1\",\"newPassword\":\"newpass12\"}")
				.cookie(member.session()))
			.andExpect(status().isNoContent());

		// 이 기기는 그대로, 다른 기기는 로그아웃
		mvc.perform(get("/api/auth/me").cookie(member.session())).andExpect(jsonPath("$.user.email").value(member.email()));
		mvc.perform(get("/api/auth/me").cookie(otherDevice)).andExpect(jsonPath("$.user").isEmpty());
		mvc.perform(json(post("/api/auth/login"), loginBody(member.email(), "password1")))
			.andExpect(status().isUnauthorized());
		login(member.email(), "newpass12");
	}

	// 비밀번호 재설정

	@Test
	void forgottenPasswordsAreResetThroughAMailedLink() throws Exception {
		Account member = signUp("분실회원");
		Cookie loggedIn = member.session();

		// 가입하지 않은 이메일도 같은 응답이고 메일은 가지 않는다
		mvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"nobody@example.com\"}"))
			.andExpect(status().isNoContent());
		verifySentNothingTo("nobody@example.com");

		mvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"%s\"}".formatted(member.email().toUpperCase())))
			.andExpect(status().isNoContent());
		String token = lastToken(member.email(), RESET_LINK);
		// 1분 안에 다시 요청해도 응답은 같고 메일은 더 보내지 않는다
		mvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"%s\"}".formatted(member.email())))
			.andExpect(status().isNoContent());
		assertThat(allTokens(member.email(), RESET_LINK)).hasSize(1);

		mvc.perform(json(post("/api/auth/password-reset"), "{\"token\":\"made-up\",\"newPassword\":\"newpass12\"}"))
			.andExpect(status().isBadRequest());
		// 비밀번호 규칙에 맞지 않으면 링크는 그대로 남는다
		mvc.perform(json(post("/api/auth/password-reset"), "{\"token\":\"%s\",\"newPassword\":\"short\"}".formatted(token)))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/password-reset"), "{\"token\":\"%s\",\"newPassword\":\"newpass12\"}".formatted(token)))
			.andExpect(status().isNoContent());

		// 모든 기기의 로그인이 끊기고, 메일을 받았으니 이메일도 인증된다
		mvc.perform(get("/api/auth/me").cookie(loggedIn)).andExpect(jsonPath("$.user").isEmpty());
		mvc.perform(json(post("/api/auth/login"), loginBody(member.email(), "password1")))
			.andExpect(status().isUnauthorized());
		Cookie again = login(member.email(), "newpass12");
		mvc.perform(get("/api/auth/me").cookie(again)).andExpect(jsonPath("$.user.emailVerified").value(true));
		mvc.perform(json(post("/api/auth/password-reset"), "{\"token\":\"%s\",\"newPassword\":\"another12\"}".formatted(token)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("비밀번호 재설정 링크가 만료되었거나 이미 사용했습니다. 비밀번호 찾기를 다시 해 주세요."));
	}

	@Test
	void suspendedMembersGetNoResetMail() throws Exception {
		Account member = signUp("정지분실");
		jdbc.update("update app_user set suspended_at = now() where email = ?", member.email());

		mvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"%s\"}".formatted(member.email())))
			.andExpect(status().isNoContent());
		assertThat(allTokens(member.email(), RESET_LINK)).isEmpty();
	}

	private void verify(Account member) throws Exception {
		mvc.perform(json(post("/api/auth/verify-email"),
				"{\"token\":\"%s\"}".formatted(lastToken(member.email(), VERIFY_LINK))))
			.andExpect(status().isNoContent());
	}

	private void backdateTokens(String email, String interval) {
		jdbc.update("update email_token set created_at = created_at - interval '" + interval + "' "
				+ "where user_id = (select id from app_user where email = ?)", email);
	}

	/** 그 주소로 보낸 메일들에서 링크 속 값을 보낸 순서대로 */
	private List<String> allTokens(String email, Pattern link) {
		ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
		try {
			org.mockito.Mockito.verify(mailService, atLeastOnce()).send(eq(email), anyString(), bodies.capture());
		}
		catch (AssertionError none) {
			return List.of();
		}
		return bodies.getAllValues().stream().map(link::matcher).filter(Matcher::find).map(m -> m.group(1)).toList();
	}

	private String lastToken(String email, Pattern link) {
		List<String> found = allTokens(email, link);
		assertThat(found).as("%s로 보낸 링크", email).isNotEmpty();
		return found.getLast();
	}

	private void verifySentNothingTo(String email) {
		org.mockito.Mockito.verify(mailService, never()).send(eq(email), anyString(), anyString());
	}

	private String startBroadcast(Account owner) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"계정 테스트 방송\"}").cookie(owner.session()))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.broadcast.id");
	}

	private Cookie login(String email, String password) throws Exception {
		return mvc.perform(json(post("/api/auth/login"), loginBody(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private Account signUp(String nickname) throws Exception {
		String email = "account" + users.incrementAndGet() + "@example.com";
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"email\":\"%s\",\"password\":\"password1\",\"nickname\":\"%s\",\"agreeTerms\":true}"
					.formatted(email, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		org.mockito.Mockito.verify(mailService, times(1)).send(eq(email), anyString(), anyString());
		return new Account(email, result.getResponse().getCookie("SESSION"));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
	}
}
