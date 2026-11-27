package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 회원 탈퇴. 비밀번호를 다시 확인한 뒤 그 회원의 모든 것(계정, 동영상과 파일, 방송, 댓글, 후원, 신고, 세션)을 지운다.
 * 실제 PostgreSQL, 보안 필터, 세션 저장소, 파일 저장소를 거쳐 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WithdrawalTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@TempDir
	static Path storage;

	static Path sampleVideo;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("media.storage-root", storage::toString);
		registry.add("mediamtx.api-url", () -> "http://127.0.0.1:1");
		registry.add("toss.client-key", () -> "");
		registry.add("toss.secret-key", () -> "");
	}

	@BeforeAll
	static void makeSampleVideo() throws IOException, InterruptedException {
		sampleVideo = storage.resolve("sample.mp4");
		Process ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-f", "lavfi", "-i",
				"testsrc=size=320x240:rate=15", "-t", "1", "-pix_fmt", "yuv420p", sampleVideo.toString())
			.inheritIO().start();
		assertThat(ffmpeg.waitFor()).isZero();
	}

	private static final AtomicInteger users = new AtomicInteger();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	record Account(String username, String id, Cookie session) {
	}

	@Test
	void withdrawalNeedsLoginAndTheRightPassword() throws Exception {
		mvc.perform(json(post("/api/auth/withdraw"), "{\"password\":\"password1\"}"))
			.andExpect(status().isUnauthorized());

		Account member = signUp("비번확인");
		mvc.perform(json(post("/api/auth/withdraw"), "{\"password\":\"wrong-pass\"}").cookie(member.session()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("비밀번호가 올바르지 않습니다."));
		mvc.perform(get("/api/auth/me").cookie(member.session()))
			.andExpect(jsonPath("$.user.id").value(member.id()));
	}

	@Test
	void adminsCannotWithdraw() throws Exception {
		Account admin = signUp("탈퇴운영자");
		jdbc.update("update app_user set role = 'ADMIN' where id = ?::uuid", admin.id());
		Cookie session = login(admin);

		mvc.perform(json(post("/api/auth/withdraw"), "{\"password\":\"password1\"}").cookie(session))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("운영자 계정은 탈퇴할 수 없습니다. 먼저 운영자 역할을 해제해 주세요."));
	}

	@Test
	void withdrawalDeletesEverythingOfTheMemberAndNothingElse() throws Exception {
		Account leaving = signUp("떠나는회원");
		Account other = signUp("남는회원");
		Cookie leavingSecondDevice = login(leaving);

		// 떠나는 회원의 것
		String video = upload(leaving);
		String broadcast = startBroadcast(leaving);
		String othersBroadcast = startBroadcast(other);
		String leavingCommentElsewhere = comment(leaving, "/api/broadcasts/" + othersBroadcast, "떠나는 회원의 댓글");
		// 남는 회원이 떠나는 회원의 콘텐츠에 쓴 댓글은 콘텐츠와 함께 지워진다
		comment(other, "/api/videos/" + video, "영상에 단 댓글");
		comment(other, "/api/broadcasts/" + broadcast, "방송에 단 댓글");
		String keptComment = comment(other, "/api/broadcasts/" + othersBroadcast, "남는 댓글");
		donation(leaving.id(), other.id(), othersBroadcast);
		donation(other.id(), leaving.id(), broadcast);
		String othersDonation = donation(signUp("제3회원").id(), other.id(), othersBroadcast);
		report(leaving, "COMMENT", keptComment);
		jdbc.update("insert into simulcast_setting (user_id, youtube_key_encrypted, youtube_key_last4, on_by_default) "
				+ "values (?::uuid, 'encrypted', 'qrst', false)", leaving.id());
		assertThat(Files.exists(storage.resolve("originals"))).isTrue();

		mvc.perform(json(post("/api/auth/withdraw"), "{\"password\":\"password1\"}").cookie(leaving.session()))
			.andExpect(status().isNoContent());

		// 계정과 모든 기기의 로그인
		assertThat(count("select count(*) from app_user where id = ?::uuid", leaving.id())).isZero();
		mvc.perform(get("/api/auth/me").cookie(leaving.session())).andExpect(jsonPath("$.user").isEmpty());
		mvc.perform(get("/api/auth/me").cookie(leavingSecondDevice)).andExpect(jsonPath("$.user").isEmpty());
		mvc.perform(json(post("/api/auth/login"), login(leaving.username()))).andExpect(status().isUnauthorized());

		// 콘텐츠와 파일
		mvc.perform(get("/api/videos/" + video)).andExpect(status().isNotFound());
		mvc.perform(get("/api/broadcasts/" + broadcast)).andExpect(status().isNotFound());
		try (var files = Files.walk(storage)) {
			assertThat(files.map(Path::toString).filter(p -> p.contains(video))).isEmpty();
		}
		// 댓글, 후원, 신고, 유튜브 키
		assertThat(count("select count(*) from comment where id = ?::uuid", leavingCommentElsewhere)).isZero();
		assertThat(count("select count(*) from comment where video_id = ?::uuid or broadcast_id = ?::uuid", video,
				broadcast)).isZero();
		assertThat(count("select count(*) from donation where donor_id = ?::uuid or recipient_id = ?::uuid",
				leaving.id(), leaving.id())).isZero();
		assertThat(count("select count(*) from report where reporter_id = ?::uuid", leaving.id())).isZero();
		assertThat(count("select count(*) from simulcast_setting where user_id = ?::uuid", leaving.id())).isZero();

		// 남는 회원의 것은 그대로
		mvc.perform(get("/api/auth/me").cookie(other.session())).andExpect(jsonPath("$.user.id").value(other.id()));
		mvc.perform(get("/api/broadcasts/" + othersBroadcast + "/comments"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(keptComment));
		assertThat(count("select count(*) from donation where id = ?::uuid", othersDonation)).isOne();

		// 같은 아이디로 다시 가입할 수 있다
		mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"%s\",\"password\":\"password1\",\"nickname\":\"다시가입\",\"agreeTerms\":true}"
					.formatted(leaving.username())))
			.andExpect(status().isCreated());
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	/** 결제 서비스 없이 완료된 후원 기록을 바로 넣는다 */
	private String donation(String donorId, String recipientId, String broadcastId) {
		String id = UUID.randomUUID().toString();
		jdbc.update("""
				insert into donation (id, donor_id, recipient_id, broadcast_id, broadcast_title, amount, message, status,
				                      created_at, completed_at)
				values (?::uuid, ?::uuid, ?::uuid, ?::uuid, '방송', 1000, '응원해요', 'DONE', now(), now())""", id, donorId,
				recipientId, broadcastId);
		return id;
	}

	private void report(Account reporter, String type, String targetId) throws Exception {
		mvc.perform(json(post("/api/reports"),
				"{\"targetType\":\"%s\",\"targetId\":\"%s\",\"reason\":\"SPAM\"}".formatted(type, targetId))
				.cookie(reporter.session()))
			.andExpect(status().isCreated());
	}

	private String upload(Account owner) throws Exception {
		MvcResult uploaded = mvc.perform(multipart("/api/videos")
				.file(new MockMultipartFile("file", "sample.mp4", "video/mp4", Files.readAllBytes(sampleVideo)))
				.param("title", "탈퇴 영상")
				.cookie(owner.session())
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn();
		return read(uploaded, "$.id");
	}

	private String startBroadcast(Account owner) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"탈퇴 방송\"}").cookie(owner.session()))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.broadcast.id");
	}

	private String comment(Account author, String contentPath, String body) throws Exception {
		MvcResult result = mvc.perform(json(post(contentPath + "/comments"), "{\"body\":\"%s\"}".formatted(body))
				.cookie(author.session()))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.id");
	}

	private Cookie login(Account account) throws Exception {
		return mvc.perform(json(post("/api/auth/login"), login(account.username())))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
	}

	private static String login(String username) {
		return "{\"username\":\"%s\",\"password\":\"password1\"}".formatted(username);
	}

	private Account signUp(String nickname) throws Exception {
		String username = "bye" + users.incrementAndGet();
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"%s\",\"password\":\"password1\",\"nickname\":\"%s\",\"agreeTerms\":true}"
					.formatted(username, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return new Account(username, read(result, "$.user.id"), session);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
	}

	private static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}
}
