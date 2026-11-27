package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;
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
 * 운영자가 신고를 보고 콘텐츠를 내리거나 기각하고, User를 정지하는 흐름을
 * 실제 PostgreSQL·보안 필터·세션 저장소를 거쳐 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ModerationTest {

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

	record Account(String username, String id, Cookie session) {
	}

	@Test
	void onlyAdminsUseTheModerationApi() throws Exception {
		mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());

		Account user = signUp("일반회원");
		mvc.perform(get("/api/admin/reports").cookie(user.session()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("운영자만 할 수 있습니다."));
		mvc.perform(get("/api/auth/me").cookie(user.session())).andExpect(jsonPath("$.user.admin").value(false));

		Account admin = admin("운영자1");
		mvc.perform(get("/api/auth/me").cookie(admin.session())).andExpect(jsonPath("$.user.admin").value(true));
		mvc.perform(get("/api/admin/reports").cookie(admin.session())).andExpect(status().isOk());
	}

	@Test
	void openReportsAreGroupedByTarget() throws Exception {
		Account author = signUp("묶음작성자");
		String broadcastId = startBroadcast(signUp("묶음방송자"));
		String commentId = writeComment(author, broadcastId, "나쁜 댓글");
		report(signUp("묶음신고1"), "COMMENT", commentId, "HATE", "욕설");
		report(signUp("묶음신고2"), "COMMENT", commentId, "SPAM", "");
		report(signUp("묶음신고3"), "COMMENT", commentId, "HATE", "");

		String json = mvc.perform(get("/api/admin/reports").cookie(admin("운영자2").session()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Object> groups = JsonPath.read(json, "$[?(@.targetId == '%s')]".formatted(commentId));
		assertThat(groups).hasSize(1);
		String group = "$[?(@.targetId == '%s')]".formatted(commentId);
		assertThat(JsonPath.<List<Object>>read(json, group + ".reportCount")).containsExactly(3);
		assertThat(JsonPath.<List<Object>>read(json, group + ".summary")).containsExactly("나쁜 댓글");
		assertThat(JsonPath.<List<Object>>read(json, group + ".owner.nickname")).containsExactly("묶음작성자");
		assertThat(JsonPath.<List<Object>>read(json, group + ".parentType")).containsExactly("BROADCAST");
		assertThat(JsonPath.<List<Object>>read(json, group + ".parentId")).containsExactly(broadcastId);
		assertThat(JsonPath.<List<Object>>read(json, group + ".reasons.HATE")).containsExactly(2);
		assertThat(JsonPath.<List<Object>>read(json, group + ".reports.length()")).containsExactly(3);
	}

	@Test
	void hidingABroadcastEndsItAndHidesItFromEveryone() throws Exception {
		Account owner = signUp("숨김방송자");
		String broadcastId = startBroadcast(owner);
		report(signUp("방송신고자"), "BROADCAST", broadcastId, "SEXUAL", "");
		Account admin = admin("운영자3");

		mvc.perform(post("/api/admin/reports/BROADCAST/" + broadcastId + "/hide").with(csrf()).cookie(admin.session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/broadcasts/" + broadcastId)).andExpect(status().isNotFound());
		mvc.perform(get("/api/broadcasts/" + broadcastId).cookie(owner.session())).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select status from broadcast where id = ?::uuid", String.class, broadcastId))
			.isEqualTo("ENDED");
		assertThat(openGroupIds(admin)).doesNotContain(broadcastId);
		// 방송자는 새 방송을 시작할 수 있다 (숨긴 방송은 끝났다)
		startBroadcast(owner);
	}

	@Test
	void hidingACommentRemovesItFromTheList() throws Exception {
		Account author = signUp("숨김댓글작성자");
		String broadcastId = startBroadcast(signUp("숨김댓글방송자"));
		String hidden = writeComment(author, broadcastId, "숨길 댓글");
		writeComment(author, broadcastId, "남을 댓글");
		report(signUp("댓글신고자2"), "COMMENT", hidden, "HATE", "");

		mvc.perform(post("/api/admin/reports/COMMENT/" + hidden + "/hide").with(csrf())
				.cookie(admin("운영자4").session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/broadcasts/" + broadcastId + "/comments"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].body").value("남을 댓글"));
		// 숨긴 댓글은 Author도 고치거나 다시 신고할 수 없다
		mvc.perform(json(post("/api/reports"),
				"{\"targetType\":\"COMMENT\",\"targetId\":\"%s\",\"reason\":\"SPAM\"}".formatted(hidden))
				.cookie(signUp("늦은신고자").session()))
			.andExpect(status().isNotFound());
	}

	@Test
	void dismissingClosesReportsAndKeepsTheContent() throws Exception {
		String broadcastId = startBroadcast(signUp("기각방송자"));
		report(signUp("기각신고자"), "BROADCAST", broadcastId, "OTHER", "");
		Account admin = admin("운영자5");

		mvc.perform(post("/api/admin/reports/BROADCAST/" + broadcastId + "/dismiss").with(csrf())
				.cookie(admin.session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/broadcasts/" + broadcastId)).andExpect(status().isOk());
		assertThat(openGroupIds(admin)).doesNotContain(broadcastId);
		assertThat(jdbc.queryForList("select status from report where target_id = ?::uuid", String.class, broadcastId))
			.containsExactly("DISMISSED");
	}

	@Test
	void suspendingAUserLogsThemOutEndsTheirBroadcastAndBlocksLogin() throws Exception {
		Account suspect = signUp("정지대상");
		String broadcastId = startBroadcast(suspect);
		Account admin = admin("운영자6");

		mvc.perform(post("/api/admin/users/" + suspect.id() + "/suspend").with(csrf()).cookie(admin.session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").cookie(suspect.session())).andExpect(jsonPath("$.user").isEmpty());
		mvc.perform(json(post("/api/auth/login"), login(suspect)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("운영 정책 위반으로 정지된 계정입니다."));
		assertThat(jdbc.queryForObject("select status from broadcast where id = ?::uuid", String.class, broadcastId))
			.isEqualTo("ENDED");

		mvc.perform(post("/api/admin/users/" + suspect.id() + "/unsuspend").with(csrf()).cookie(admin.session()))
			.andExpect(status().isNoContent());
		mvc.perform(json(post("/api/auth/login"), login(suspect))).andExpect(status().isOk());
	}

	@Test
	void adminsCannotBeSuspended() throws Exception {
		Account admin = admin("운영자7");
		mvc.perform(post("/api/admin/users/" + admin.id() + "/suspend").with(csrf()).cookie(admin.session()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("운영자 계정은 정지할 수 없습니다."));
	}

	private List<String> openGroupIds(Account admin) throws Exception {
		String json = mvc.perform(get("/api/admin/reports").cookie(admin.session()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$[*].targetId");
	}

	private void report(Account reporter, String type, String targetId, String reason, String detail) throws Exception {
		mvc.perform(json(post("/api/reports"),
				"{\"targetType\":\"%s\",\"targetId\":\"%s\",\"reason\":\"%s\",\"detail\":\"%s\"}"
					.formatted(type, targetId, reason, detail))
				.cookie(reporter.session()))
			.andExpect(status().isCreated());
	}

	/** 역할은 로그인할 때 세션에 담기므로 DB에서 바꾼 뒤 다시 로그인한다 */
	private Account admin(String nickname) throws Exception {
		Account account = signUp(nickname);
		jdbc.update("update app_user set role = 'ADMIN' where username = ?", account.username());
		MvcResult result = mvc.perform(json(post("/api/auth/login"), login(account))).andExpect(status().isOk()).andReturn();
		return new Account(account.username(), account.id(), result.getResponse().getCookie("SESSION"));
	}

	private static String login(Account account) {
		return "{\"username\":\"%s\",\"password\":\"password1\"}".formatted(account.username());
	}

	private Account signUp(String nickname) throws Exception {
		String username = "mod" + users.incrementAndGet();
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"%s\",\"password\":\"password1\",\"nickname\":\"%s\"}".formatted(username, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return new Account(username, read(result, "$.user.id"), session);
	}

	private String startBroadcast(Account owner) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"운영 테스트 방송\"}").cookie(owner.session()))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.broadcast.id");
	}

	private String writeComment(Account author, String broadcastId, String body) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts/" + broadcastId + "/comments"),
				"{\"body\":\"%s\"}".formatted(body)).cookie(author.session()))
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
