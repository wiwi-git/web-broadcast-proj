package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
 * 공개 범위와 Owner·Author 권한을 실제 PostgreSQL과 보안 필터를 거쳐 확인한다.
 * 로그인은 실제 가입 요청으로 받은 SESSION 쿠키로 유지한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccessControlTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@TempDir
	static Path storage;

	static Path sampleVideo;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("media.storage-root", storage::toString);
		// 실제 미디어 서버에 닿지 않게 한다. 동기화는 조회 실패로 매번 건너뛴다
		registry.add("mediamtx.api-url", () -> "http://127.0.0.1:1");
		// 개발자의 config/local.properties에 키가 있어도 키가 없는 상태로 확인한다
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

	// 인증

	@Test
	void signUpLogsInAndLogoutEndsTheSession() throws Exception {
		Cookie session = signUp("닉네임");

		mvc.perform(get("/api/auth/me").cookie(session))
			.andExpect(jsonPath("$.user.nickname").value("닉네임"));

		mvc.perform(post("/api/auth/logout").cookie(session).with(csrf())).andExpect(status().isNoContent());

		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.user").isEmpty());
	}

	@Test
	void loginChecksThePassword() throws Exception {
		String username = "login" + users.incrementAndGet();
		mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"%s\",\"password\":\"password1\",\"nickname\":\"로그인%s\"}".formatted(username, username)))
			.andExpect(status().isCreated());

		mvc.perform(json(post("/api/auth/login"), "{\"username\":\"%s\",\"password\":\"wrong-pass\"}".formatted(username)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("아이디 또는 비밀번호가 올바르지 않습니다."));

		MvcResult ok = mvc.perform(json(post("/api/auth/login"),
				"{\"username\":\"%s\",\"password\":\"password1\"}".formatted(username)))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(ok.getResponse().getCookie("SESSION")).isNotNull();
	}

	@Test
	void signUpRejectsBadOrDuplicateValues() throws Exception {
		signUp("중복닉네임");

		mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"Upper\",\"password\":\"password1\",\"nickname\":\"새닉네임\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"newuser1\",\"password\":\"short\",\"nickname\":\"새닉네임\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"newuser2\",\"password\":\"password1\",\"nickname\":\"중복닉네임\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("이미 사용 중인 닉네임입니다."));
	}

	@Test
	void changingRequestsNeedLoginAndCsrfToken() throws Exception {
		mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"방송\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("로그인이 필요합니다."));

		Cookie session = signUp("토큰없음");
		mvc.perform(post("/api/broadcasts").cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"방송\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void watchingAndListingArePublic() throws Exception {
		mvc.perform(get("/api/broadcasts")).andExpect(status().isOk());
		mvc.perform(get("/api/videos")).andExpect(status().isOk());
	}

	// Broadcast

	@Test
	void onlyTheOwnerControlsABroadcast() throws Exception {
		Cookie alice = signUp("앨리스");
		Cookie bob = signUp("보브");
		String id = startBroadcast(alice);

		mvc.perform(json(patch("/api/broadcasts/" + id), "{\"title\":\"남의 방송\"}").cookie(bob))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/broadcasts/" + id + "/end").cookie(bob).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(delete("/api/broadcasts/" + id).cookie(bob).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(post("/api/broadcasts/" + id + "/stream-key").cookie(bob).with(csrf()))
			.andExpect(status().isForbidden());

		mvc.perform(json(patch("/api/broadcasts/" + id), "{\"title\":\"바뀐 제목\"}").cookie(alice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("바뀐 제목"))
			.andExpect(jsonPath("$.owner.nickname").value("앨리스"));
		mvc.perform(post("/api/broadcasts/" + id + "/end").cookie(alice).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/broadcasts/" + id)).andExpect(jsonPath("$.status").value("ENDED"));

		mvc.perform(delete("/api/broadcasts/" + id).cookie(alice).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/broadcasts/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void aUserRunsOneBroadcastAtATime() throws Exception {
		Cookie alice = signUp("한번에하나");
		String first = startBroadcast(alice);

		mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"두 번째\"}").cookie(alice))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.activeBroadcastId").value(first));

		mvc.perform(post("/api/broadcasts/" + first + "/end").cookie(alice).with(csrf()));
		startBroadcast(alice);
	}

	@Test
	void publishingUsesTheLatestStreamKey() throws Exception {
		Cookie alice = signUp("방송키");
		MvcResult started = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"키\"}").cookie(alice))
			.andExpect(status().isCreated())
			.andReturn();
		String id = read(started, "$.broadcast.id");
		String oldKey = read(started, "$.streamKey");

		assertThat(mediaAuth(id, oldKey)).isEqualTo(200);

		MvcResult reissued = mvc.perform(post("/api/broadcasts/" + id + "/stream-key").cookie(alice).with(csrf()))
			.andExpect(status().isOk())
			.andReturn();
		String newKey = read(reissued, "$.streamKey");

		assertThat(mediaAuth(id, oldKey)).isEqualTo(401);
		assertThat(mediaAuth(id, newKey)).isEqualTo(200);
	}

	// Comment

	@Test
	void onlyTheAuthorEditsOrDeletesAComment() throws Exception {
		Cookie owner = signUp("방송주인");
		Cookie author = signUp("댓글러");
		String broadcast = startBroadcast(owner);

		mvc.perform(get("/api/broadcasts/" + broadcast + "/comments")).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(json(post("/api/broadcasts/" + broadcast + "/comments"), "{\"body\":\"익명\"}"))
			.andExpect(status().isUnauthorized());

		MvcResult written = mvc.perform(
				json(post("/api/broadcasts/" + broadcast + "/comments"), "{\"body\":\" 안녕하세요 \"}").cookie(author))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("안녕하세요"))
			.andExpect(jsonPath("$.author.nickname").value("댓글러"))
			.andExpect(jsonPath("$.updatedAt").isEmpty())
			.andReturn();
		String comment = read(written, "$.id");

		// Content의 Owner라도 남의 Comment는 고치거나 지울 수 없다
		mvc.perform(json(patch("/api/comments/" + comment), "{\"body\":\"고침\"}").cookie(owner))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/comments/" + comment).cookie(owner).with(csrf())).andExpect(status().isForbidden());

		mvc.perform(json(patch("/api/comments/" + comment), "{\"body\":\"고침\"}").cookie(author))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty());
		mvc.perform(get("/api/broadcasts/" + broadcast + "/comments"))
			.andExpect(jsonPath("$[0].body").value("고침"));

		mvc.perform(delete("/api/comments/" + comment).cookie(author).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/broadcasts/" + broadcast + "/comments")).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void commentRulesApply() throws Exception {
		Cookie user = signUp("규칙");
		String broadcast = startBroadcast(user);

		mvc.perform(json(post("/api/broadcasts/" + broadcast + "/comments"), "{\"body\":\"   \"}").cookie(user))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/broadcasts/" + broadcast + "/comments"),
				"{\"body\":\"%s\"}".formatted("가".repeat(501))).cookie(user))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/broadcasts/" + broadcast + "/comments"),
				"{\"body\":\"%s\"}".formatted("가".repeat(500))).cookie(user))
			.andExpect(status().isCreated());
	}

	// Video

	@Test
	void onlyTheOwnerEditsOrDeletesAVideoAndItsCommentsGoWithIt() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "sample.mp4", "video/mp4",
				Files.readAllBytes(sampleVideo));
		mvc.perform(multipart("/api/videos").file(file).param("title", "익명 업로드").with(csrf()))
			.andExpect(status().isUnauthorized());

		Cookie owner = signUp("업로더");
		Cookie other = signUp("구경꾼");
		MvcResult uploaded = mvc.perform(multipart("/api/videos").file(file).param("title", "첫 영상")
				.cookie(owner).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner.nickname").value("업로더"))
			.andReturn();
		String id = read(uploaded, "$.id");

		mvc.perform(json(patch("/api/videos/" + id), "{\"title\":\"남이 고침\"}").cookie(other))
			.andExpect(status().isForbidden());
		mvc.perform(json(patch("/api/videos/" + id), "{\"title\":\"고친 제목\",\"description\":\"설명\"}").cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("고친 제목"))
			.andExpect(jsonPath("$.description").value("설명"));

		// 최신순 20개씩, nextCursor로 이어서
		for (int i = 1; i <= 25; i++) {
			mvc.perform(json(post("/api/videos/" + id + "/comments"), "{\"body\":\"댓글 %d\"}".formatted(i)).cookie(other))
				.andExpect(status().isCreated());
		}
		MvcResult first = mvc.perform(get("/api/videos/" + id + "/comments"))
			.andExpect(jsonPath("$.items.length()").value(20))
			.andExpect(jsonPath("$.items[0].body").value("댓글 25"))
			.andExpect(jsonPath("$.totalCount").value(25))
			.andReturn();
		mvc.perform(get("/api/videos/" + id + "/comments").param("before", read(first, "$.nextCursor")))
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[4].body").value("댓글 1"))
			.andExpect(jsonPath("$.nextCursor").isEmpty());

		mvc.perform(delete("/api/videos/" + id).cookie(other).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(delete("/api/videos/" + id).cookie(owner).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/videos/" + id)).andExpect(status().isNotFound());
		mvc.perform(get("/api/videos/" + id + "/comments")).andExpect(status().isNotFound());
	}

	@Test
	void donationsNeedPaymentKeys() throws Exception {
		mvc.perform(get("/api/payments/config")).andExpect(jsonPath("$.clientKey").isEmpty());
		Cookie viewer = signUp("키없음");
		mvc.perform(json(post("/api/broadcasts/{id}/donations", startBroadcast(signUp("키없는방송자"))), "{\"amount\":1000}")
				.cookie(viewer))
			.andExpect(status().isServiceUnavailable());
	}

	// 도우미

	private Cookie signUp(String nickname) throws Exception {
		String username = "user" + users.incrementAndGet();
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"%s\",\"password\":\"password1\",\"nickname\":\"%s\"}".formatted(username, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		return session;
	}

	private String startBroadcast(Cookie session) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"테스트 방송\"}").cookie(session))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.broadcast.id");
	}

	private int mediaAuth(String broadcastId, String key) throws Exception {
		return mvc.perform(post("/api/media-server/auth").contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"publish\",\"path\":\"live/%s\",\"query\":\"key=%s\"}".formatted(broadcastId, key)))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
	}

	private static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}
}
