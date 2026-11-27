package com.webbroadcast.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * 유튜브 동시 송출의 YouTube Key 저장과 MediaMTX 중계 내부 API (ADR-0007).
 * 실제 중계(ffmpeg)는 mediamtx/simulcast.sh가 하며 여기서는 서버가 주고받는 값만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SimulcastTest {

	private static final String RELAY_SECRET = "relay-secret-for-test";
	private static final String YOUTUBE_URL = "rtmp://sink.test/live2";
	private static final String YOUTUBE_KEY = "abcd-efgh-ijkl-mnop-wxyz";

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
		registry.add("simulcast.encryption-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
		registry.add("simulcast.relay-secret", () -> RELAY_SECRET);
		registry.add("simulcast.youtube-url", () -> YOUTUBE_URL);
	}

	private static final AtomicInteger users = new AtomicInteger();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void theYouTubeKeyIsStoredEncryptedAndShownByItsLastFourCharacters() throws Exception {
		Cookie alice = signUp("키주인");
		mvc.perform(get("/api/simulcast").cookie(alice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.youtubeKeyLast4").doesNotExist());

		mvc.perform(json(put("/api/simulcast/youtube-key"), "{\"key\":\" " + YOUTUBE_KEY + " \"}").cookie(alice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.youtubeKeyLast4").value("wxyz"))
			.andExpect(jsonPath("$.onByDefault").value(true));

		List<String> stored = jdbc.queryForList("select youtube_key_encrypted from simulcast_setting", String.class);
		assertThat(stored).isNotEmpty().noneMatch(value -> value.contains(YOUTUBE_KEY));
		assertThat(mvc.perform(get("/api/simulcast").cookie(alice)).andReturn().getResponse().getContentAsString())
			.doesNotContain(YOUTUBE_KEY);

		mvc.perform(json(put("/api/simulcast/youtube-key"), "{\"key\":\"https://youtube.com/\"}").cookie(alice))
			.andExpect(status().isBadRequest());

		mvc.perform(delete("/api/simulcast/youtube-key").cookie(alice).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/simulcast").cookie(alice)).andExpect(jsonPath("$.youtubeKeyLast4").doesNotExist());
	}

	@Test
	void theYouTubeKeyIsOnlyForTheLoggedInUser() throws Exception {
		mvc.perform(get("/api/simulcast")).andExpect(status().isUnauthorized());
		mvc.perform(json(put("/api/simulcast/youtube-key"), "{\"key\":\"" + YOUTUBE_KEY + "\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void simulcastNeedsARegisteredYouTubeKey() throws Exception {
		Cookie bob = signUp("키없음");
		mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"방송\",\"simulcast\":true}").cookie(bob))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theRelayGetsTheYouTubeUrlOnlyForBroadcastsThatChoseSimulcast() throws Exception {
		Cookie alice = signUp("동시송출");
		registerKey(alice);
		String simulcast = startBroadcast(alice, true);
		mvc.perform(post("/api/broadcasts/" + simulcast + "/end").cookie(alice).with(csrf()));
		String plain = startBroadcast(alice, false);

		mvc.perform(relayTarget(simulcast)).andExpect(status().isNoContent());
		mvc.perform(relayTarget(plain)).andExpect(status().isNoContent());

		mvc.perform(post("/api/broadcasts/" + plain + "/end").cookie(alice).with(csrf()));
		String next = startBroadcast(alice, true);
		mvc.perform(relayTarget(next))
			.andExpect(status().isOk())
			.andExpect(content().string(YOUTUBE_URL + "/" + YOUTUBE_KEY));
	}

	@Test
	void theRelayApiNeedsTheSecret() throws Exception {
		Cookie alice = signUp("비밀값");
		registerKey(alice);
		String id = startBroadcast(alice, true);

		mvc.perform(get("/api/internal/simulcast/target").param("path", "live/" + id))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/internal/simulcast/target").param("path", "live/" + id)
				.header("X-Relay-Secret", "wrong"))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/internal/simulcast/status").param("path", "live/" + id).param("status", "FAILED"))
			.andExpect(status().isNotFound());
	}

	@Test
	void onlyTheOwnerSeesTheSimulcastStatus() throws Exception {
		Cookie alice = signUp("상태주인");
		Cookie bob = signUp("상태구경");
		registerKey(alice);
		String id = startBroadcast(alice, true);

		mvc.perform(get("/api/broadcasts/" + id).cookie(alice))
			.andExpect(jsonPath("$.simulcastStatus").value("CONNECTING"));

		mvc.perform(post("/api/internal/simulcast/status").param("path", "live/" + id).param("status", "LIVE")
				.header("X-Relay-Secret", RELAY_SECRET))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/broadcasts/" + id).cookie(alice)).andExpect(jsonPath("$.simulcastStatus").value("LIVE"));
		mvc.perform(get("/api/broadcasts/" + id).cookie(bob)).andExpect(jsonPath("$.simulcastStatus").doesNotExist());
		mvc.perform(get("/api/broadcasts/" + id)).andExpect(jsonPath("$.simulcastStatus").doesNotExist());
	}

	@Test
	void theLastChoiceBecomesTheNextDefault() throws Exception {
		Cookie alice = signUp("기본값");
		registerKey(alice);
		String id = startBroadcast(alice, false);
		mvc.perform(get("/api/simulcast").cookie(alice)).andExpect(jsonPath("$.onByDefault").value(false));

		mvc.perform(post("/api/broadcasts/" + id + "/end").cookie(alice).with(csrf()));
		startBroadcast(alice, true);
		mvc.perform(get("/api/simulcast").cookie(alice)).andExpect(jsonPath("$.onByDefault").value(true));
	}

	// 도우미

	private MockHttpServletRequestBuilder relayTarget(String broadcastId) {
		return get("/api/internal/simulcast/target").param("path", "live/" + broadcastId)
			.header("X-Relay-Secret", RELAY_SECRET);
	}

	private void registerKey(Cookie session) throws Exception {
		mvc.perform(json(put("/api/simulcast/youtube-key"), "{\"key\":\"" + YOUTUBE_KEY + "\"}").cookie(session))
			.andExpect(status().isOk());
	}

	private Cookie signUp(String nickname) throws Exception {
		String username = "cast" + users.incrementAndGet() + "@example.com";
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"email\":\"%s\",\"password\":\"password1\",\"nickname\":\"%s\",\"agreeTerms\":true}".formatted(username, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		return result.getResponse().getCookie("SESSION");
	}

	private String startBroadcast(Cookie session, boolean simulcast) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"),
				"{\"title\":\"테스트 방송\",\"simulcast\":%s}".formatted(simulcast)).cookie(session))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.broadcast.id");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
	}
}
