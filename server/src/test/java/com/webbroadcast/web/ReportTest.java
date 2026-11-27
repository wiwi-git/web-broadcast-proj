package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Report 규칙을 실제 PostgreSQL과 보안 필터를 거쳐 확인한다.
 * 로그인한 User만 남의 Content·Comment를 대상마다 한 번씩 신고할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportTest {

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
	}

	private static final AtomicInteger users = new AtomicInteger();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void reportingNeedsLogin() throws Exception {
		mvc.perform(json(post("/api/reports"), report("BROADCAST", UUID.randomUUID().toString(), "SPAM")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aUserReportsSomeoneElsesBroadcastOnce() throws Exception {
		Cookie owner = signUp("방송자");
		Cookie reporter = signUp("신고자");
		String broadcastId = startBroadcast(owner);

		mvc.perform(json(post("/api/reports"), report("BROADCAST", broadcastId, "SEXUAL")).cookie(reporter))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty());

		mvc.perform(json(post("/api/reports"), report("BROADCAST", broadcastId, "SPAM")).cookie(reporter))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("이미 신고했습니다."));

		// 다른 User는 같은 Broadcast를 따로 신고할 수 있다
		mvc.perform(json(post("/api/reports"), report("BROADCAST", broadcastId, "SPAM")).cookie(signUp("다른신고자")))
			.andExpect(status().isCreated());
	}

	@Test
	void aUserReportsAComment() throws Exception {
		Cookie author = signUp("댓글작성자");
		String broadcastId = startBroadcast(signUp("방송자2"));
		String commentId = writeComment(author, broadcastId);

		mvc.perform(json(post("/api/reports"),
				"{\"targetType\":\"COMMENT\",\"targetId\":\"%s\",\"reason\":\"HATE\",\"detail\":\"  욕설이 있어요  \"}"
					.formatted(commentId))
				.cookie(signUp("댓글신고자")))
			.andExpect(status().isCreated());
	}

	@Test
	void ownContentAndCommentsCannotBeReported() throws Exception {
		Cookie owner = signUp("자기신고");
		String broadcastId = startBroadcast(owner);
		String commentId = writeComment(owner, broadcastId);

		mvc.perform(json(post("/api/reports"), report("BROADCAST", broadcastId, "SPAM")).cookie(owner))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("내가 만든 방송이나 동영상, 내가 쓴 댓글은 신고할 수 없습니다."));
		mvc.perform(json(post("/api/reports"), report("COMMENT", commentId, "SPAM")).cookie(owner))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingTargetsAreNotFound() throws Exception {
		Cookie reporter = signUp("없는대상");
		for (String type : new String[] { "BROADCAST", "VIDEO", "COMMENT" }) {
			mvc.perform(json(post("/api/reports"), report(type, UUID.randomUUID().toString(), "SPAM")).cookie(reporter))
				.andExpect(status().isNotFound());
		}
	}

	@Test
	void badReportsAreRejected() throws Exception {
		Cookie reporter = signUp("잘못된신고");
		String broadcastId = startBroadcast(signUp("방송자3"));

		mvc.perform(json(post("/api/reports"), report("BROADCAST", broadcastId, "NOPE")).cookie(reporter))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/reports"), report("USER", broadcastId, "SPAM")).cookie(reporter))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/reports"), "{\"targetType\":\"BROADCAST\",\"targetId\":\"%s\"}".formatted(broadcastId))
				.cookie(reporter))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("신고 사유를 골라 주세요."));
		mvc.perform(json(post("/api/reports"),
				"{\"targetType\":\"BROADCAST\",\"targetId\":\"%s\",\"reason\":\"OTHER\",\"detail\":\"%s\"}"
					.formatted(broadcastId, "가".repeat(201)))
				.cookie(reporter))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("신고 내용은 200자 이하여야 합니다."));
	}

	private static String report(String targetType, String targetId, String reason) {
		return "{\"targetType\":\"%s\",\"targetId\":\"%s\",\"reason\":\"%s\"}".formatted(targetType, targetId, reason);
	}

	private Cookie signUp(String nickname) throws Exception {
		String username = "report" + users.incrementAndGet() + "@example.com";
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"email\":\"%s\",\"password\":\"password1\",\"nickname\":\"%s\",\"agreeTerms\":true}".formatted(username, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		// 동영상 업로드·댓글은 이메일 인증을 마친 회원만 할 수 있다. 인증 흐름 자체는 AccountTest가 확인한다
		jdbc.update("update app_user set email_verified_at = now() where email = ?", username);
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	private String startBroadcast(Cookie session) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"신고 테스트 방송\"}").cookie(session))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.broadcast.id");
	}

	private String writeComment(Cookie session, String broadcastId) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts/" + broadcastId + "/comments"), "{\"body\":\"댓글\"}")
				.cookie(session))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.id");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
	}

	private static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}
}
