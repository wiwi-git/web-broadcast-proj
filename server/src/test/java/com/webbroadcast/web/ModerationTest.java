package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

	static Path sampleVideo;

	@BeforeAll
	static void makeSampleVideo() throws IOException, InterruptedException {
		sampleVideo = storage.resolve("sample.mp4");
		Process ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i",
				"testsrc=size=320x240:rate=15", "-t", "1", "-pix_fmt", "yuv420p", sampleVideo.toString())
			.inheritIO().start();
		assertThat(ffmpeg.waitFor()).isZero();
	}

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
	void hiddenVideoCommentIdsAreListedSoOpenScreensCanDropThem() throws Exception {
		Account uploader = signUp("댓글영상주인");
		Account author = signUp("영상댓글작성자");
		String videoId = upload(uploader, "댓글 숨김 영상");
		String hidden = writeVideoComment(author, videoId, "숨길 영상 댓글");
		String kept = writeVideoComment(author, videoId, "남을 영상 댓글");

		// 숨긴 것이 없으면 빈 목록. 로그인 없이도 본다
		mvc.perform(get("/api/videos/" + videoId + "/comments/hidden"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));

		report(signUp("영상댓글신고자"), "COMMENT", hidden, "SPAM", "");
		mvc.perform(post("/api/admin/reports/COMMENT/" + hidden + "/hide").with(csrf())
				.cookie(admin("운영자9").session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/videos/" + videoId + "/comments/hidden"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0]").value(hidden));
		mvc.perform(get("/api/videos/" + videoId + "/comments"))
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.items[0].id").value(kept));
		mvc.perform(get("/api/videos/" + UUID.randomUUID() + "/comments/hidden"))
			.andExpect(status().isNotFound());
	}

	@Test
	void adminsSeeHiddenContentWithWhyItWasHidden() throws Exception {
		Account owner = signUp("내린콘텐츠주인");
		Account admin = admin("운영자10");
		String videoId = upload(owner, "내린 영상");
		String broadcastId = startBroadcast(owner);
		String commentId = writeVideoComment(owner, videoId, "내린 댓글 본문");
		report(signUp("내림신고1"), "VIDEO", videoId, "COPYRIGHT", "");
		report(signUp("내림신고2"), "VIDEO", videoId, "COPYRIGHT", "");
		report(signUp("내림신고3"), "COMMENT", commentId, "HATE", "");
		for (String target : new String[] { "COMMENT/" + commentId, "VIDEO/" + videoId, "BROADCAST/" + broadcastId }) {
			mvc.perform(post("/api/admin/reports/" + target + "/hide").with(csrf()).cookie(admin.session()))
				.andExpect(status().isNoContent());
		}

		mvc.perform(get("/api/admin/hidden").param("type", "VIDEO").cookie(owner.session()))
			.andExpect(status().isForbidden());

		// 최근에 내린 순. 운영자는 내린 동영상을 재생할 수 있다 (파일은 그대로 남는다)
		String videos = mvc.perform(get("/api/admin/hidden").param("type", "VIDEO").cookie(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(videoId))
			.andExpect(jsonPath("$.items[0].type").value("VIDEO"))
			.andExpect(jsonPath("$.items[0].summary").value("내린 영상"))
			.andExpect(jsonPath("$.items[0].owner.nickname").value("내린콘텐츠주인"))
			.andExpect(jsonPath("$.items[0].hiddenAt").isNotEmpty())
			.andExpect(jsonPath("$.items[0].reportCount").value(2))
			.andExpect(jsonPath("$.items[0].reasons.COPYRIGHT").value(2))
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Object>read(videos, "$.items[0].status")).isNotNull();

		mvc.perform(get("/api/admin/hidden").param("type", "COMMENT").cookie(admin.session()))
			.andExpect(jsonPath("$.items[0].id").value(commentId))
			.andExpect(jsonPath("$.items[0].summary").value("내린 댓글 본문"))
			.andExpect(jsonPath("$.items[0].parentType").value("VIDEO"))
			.andExpect(jsonPath("$.items[0].parentId").value(videoId))
			.andExpect(jsonPath("$.items[0].reasons.HATE").value(1));

		// 신고 없이 내렸어도 나온다 (방송에는 신고가 없었다)
		mvc.perform(get("/api/admin/hidden").param("type", "BROADCAST").cookie(admin.session()))
			.andExpect(jsonPath("$.items[0].id").value(broadcastId))
			.andExpect(jsonPath("$.items[0].reportCount").value(0));
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
	void adminsListAndSearchUsersWithTheirStatus() throws Exception {
		Account member = signUp("목록회원");
		Account admin = admin("운영자8");

		mvc.perform(get("/api/admin/users").cookie(member.session()))
			.andExpect(status().isForbidden());

		// 닉네임이나 아이디 일부로 찾는다
		mvc.perform(get("/api/admin/users").param("q", "목록회").cookie(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(member.id()))
			.andExpect(jsonPath("$.items[0].username").value(member.username()))
			.andExpect(jsonPath("$.items[0].nickname").value("목록회원"))
			.andExpect(jsonPath("$.items[0].admin").value(false))
			.andExpect(jsonPath("$.items[0].suspended").value(false))
			.andExpect(jsonPath("$.items[0].createdAt").isNotEmpty());
		mvc.perform(get("/api/admin/users").param("q", member.username().toUpperCase()).cookie(admin.session()))
			.andExpect(jsonPath("$.totalItems").value(1));

		mvc.perform(post("/api/admin/users/" + member.id() + "/suspend").with(csrf()).cookie(admin.session()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/users").param("q", "목록회원").cookie(admin.session()))
			.andExpect(jsonPath("$.items[0].suspended").value(true))
			.andExpect(jsonPath("$.items[0].suspendedAt").isNotEmpty());

		// 정지된 회원만 걸러 본다
		mvc.perform(get("/api/admin/users").param("suspended", "true").cookie(admin.session()))
			.andExpect(jsonPath("$.items[*].suspended").value(everyItem(is(true))))
			.andExpect(jsonPath("$.items[*].id").value(hasItem(member.id())));

		// 검색어 없이 보면 최근 가입한 순이고, 운영자도 나온다
		mvc.perform(get("/api/admin/users").cookie(admin.session()))
			.andExpect(jsonPath("$.items[0].id").value(admin.id()))
			.andExpect(jsonPath("$.items[0].admin").value(true));
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

	private String upload(Account owner, String title) throws Exception {
		MvcResult uploaded = mvc.perform(multipart("/api/videos")
				.file(new MockMultipartFile("file", "sample.mp4", "video/mp4", Files.readAllBytes(sampleVideo)))
				.param("title", title)
				.cookie(owner.session())
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn();
		return read(uploaded, "$.id");
	}

	private String writeVideoComment(Account author, String videoId, String body) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/videos/" + videoId + "/comments"),
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
