package com.webbroadcast.web;

import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.webbroadcast.donation.DonationService;
import com.webbroadcast.donation.PaymentGateway;
import com.webbroadcast.donation.PaymentUnavailableException;

import jakarta.servlet.http.Cookie;

/**
 * Donation의 결제 요청·승인·실패·취소 흐름. 결제 서비스는 가짜로 바꿔 결과를 정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DonationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@TempDir
	static Path storage;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("media.storage-root", storage::toString);
		// 실제 미디어 서버에 닿지 않게 한다. 동기화는 조회 실패로 매번 건너뛰므로 방송 상태가 바뀌지 않는다
		registry.add("mediamtx.api-url", () -> "http://127.0.0.1:1");
		// 결제 서비스는 가짜로 바꾸므로 키는 형식만 맞으면 된다
		registry.add("toss.client-key", () -> "test_ck_dummy");
		registry.add("toss.secret-key", () -> "test_sk_dummy");
	}

	private static final AtomicInteger users = new AtomicInteger();

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DonationService donationService;

	@MockitoBean
	PaymentGateway gateway;

	@Test
	void approvedDonationAppearsOnTheBroadcast() throws Exception {
		Cookie broadcaster = signUp();
		String broadcastId = liveBroadcast(broadcaster);
		Cookie donor = signUp();
		approvePayments();

		MvcResult started = mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId),
				"{\"amount\":5000,\"message\":\" 화이팅 \"}").cookie(donor))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.donation.status").value("PENDING"))
			.andExpect(jsonPath("$.donation.message").value("화이팅"))
			.andExpect(jsonPath("$.orderName").value(nickname(broadcaster) + "님에게 후원"))
			.andReturn();
		String orderId = read(started, "$.orderId");

		// 결제 대기는 아무에게도 보이지 않는다
		mvc.perform(get("/api/broadcasts/{id}/donations", broadcastId)).andExpect(jsonPath("$.length()").value(0));

		approve(donor, orderId, "pay-1", 5000).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
		verify(gateway).approve("pay-1", orderId, 5000);

		// 누구나(로그인하지 않아도) 완료된 Donation을 본다
		mvc.perform(get("/api/broadcasts/{id}/donations", broadcastId))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].donor.nickname").value(nickname(donor)))
			.andExpect(jsonPath("$[0].amount").value(5000))
			.andExpect(jsonPath("$[0].message").value("화이팅"));

		// 결과 화면을 새로고침해도 다시 승인하지 않는다
		approve(donor, orderId, "pay-1", 5000).andExpect(jsonPath("$.status").value("DONE"));
		verify(gateway, times(1)).approve(anyString(), anyString(), anyLong());
	}

	@Test
	void startChecksTheAmountMessageAndBroadcast() throws Exception {
		Cookie broadcaster = signUp();
		String broadcastId = liveBroadcast(broadcaster);
		Cookie donor = signUp();

		mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId), "{\"amount\":1000}"))
			.andExpect(status().isUnauthorized());
		for (String amount : new String[] { "null", "500", "1500", "501000", "-1000" }) {
			mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId), "{\"amount\":" + amount + "}")
					.cookie(donor))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId),
				"{\"amount\":1000,\"message\":\"" + "가".repeat(101) + "\"}").cookie(donor))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId), "{\"amount\":500000}").cookie(donor))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.donation.message").isEmpty());

		// 자기 방송에는 후원할 수 없다
		mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId), "{\"amount\":1000}").cookie(broadcaster))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("자기 방송에는 후원할 수 없습니다."));

		// 방송 중일 때만
		Cookie other = signUp();
		String pendingId = startBroadcast(other);
		mvc.perform(json(post("/api/broadcasts/{id}/donations", pendingId), "{\"amount\":1000}").cookie(donor))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("방송 중일 때만 후원할 수 있습니다."));
		mvc.perform(post("/api/broadcasts/{id}/end", broadcastId).cookie(broadcaster).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId), "{\"amount\":1000}").cookie(donor))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/broadcasts/{id}/donations", UUID.randomUUID()), "{\"amount\":1000}").cookie(donor))
			.andExpect(status().isNotFound());
	}

	@Test
	void tamperedAmountFailsWithoutAskingThePaymentService() throws Exception {
		Cookie donor = signUp();
		String orderId = startDonation(liveBroadcast(signUp()), donor, 5000);

		approve(donor, orderId, "pay-tampered", 1000).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureMessage").value("결제 금액이 후원 금액과 다릅니다."));
		verify(gateway, never()).approve(anyString(), anyString(), anyLong());
	}

	@Test
	void onlyTheDonorCanApproveOrAbandon() throws Exception {
		Cookie donor = signUp();
		String orderId = startDonation(liveBroadcast(signUp()), donor, 1000);
		Cookie stranger = signUp();

		approve(stranger, orderId, "pay-x", 1000).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/donations/{id}/abandon", orderId), "{\"code\":\"PAY_PROCESS_CANCELED\"}")
				.cookie(stranger))
			.andExpect(status().isForbidden());
		mvc.perform(json(post("/api/donations/{id}/approve", orderId), "{\"paymentKey\":\"p\",\"amount\":1000}"))
			.andExpect(status().isUnauthorized());
		verify(gateway, never()).approve(anyString(), anyString(), anyLong());
	}

	@Test
	void rejectedPaymentFailsWithTheReason() throws Exception {
		Cookie donor = signUp();
		String broadcastId = liveBroadcast(signUp());
		String orderId = startDonation(broadcastId, donor, 3000);
		given(gateway.approve(anyString(), anyString(), anyLong()))
			.willReturn(new PaymentGateway.Rejected("REJECT_CARD_COMPANY", "카드사에서 결제를 거절했습니다."));

		approve(donor, orderId, "pay-rejected", 3000).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureMessage").value("카드사에서 결제를 거절했습니다."));
		mvc.perform(get("/api/broadcasts/{id}/donations", broadcastId)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void closingThePaymentWindowCancels() throws Exception {
		Cookie donor = signUp();
		String broadcastId = liveBroadcast(signUp());
		String canceled = startDonation(broadcastId, donor, 1000);

		mvc.perform(json(post("/api/donations/{id}/abandon", canceled),
				"{\"code\":\"PAY_PROCESS_CANCELED\",\"message\":\"사용자에 의해 결제가 취소되었습니다.\"}").cookie(donor))
			.andExpect(jsonPath("$.status").value("CANCELED"));
		// 취소한 Donation은 나중에 승인하려 해도 그대로다
		approve(donor, canceled, "pay-late", 1000).andExpect(jsonPath("$.status").value("CANCELED"));
		verify(gateway, never()).approve(anyString(), anyString(), anyLong());

		String aborted = startDonation(broadcastId, donor, 1000);
		mvc.perform(json(post("/api/donations/{id}/abandon", aborted),
				"{\"code\":\"PAY_PROCESS_ABORTED\",\"message\":\"결제 인증에 실패했습니다.\"}").cookie(donor))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureMessage").value("결제 인증에 실패했습니다."));
	}

	@Test
	void unknownResultStaysPendingUntilAskedAgain() throws Exception {
		Cookie donor = signUp();
		String orderId = startDonation(liveBroadcast(signUp()), donor, 2000);
		given(gateway.approve(anyString(), anyString(), anyLong()))
			.willThrow(new PaymentUnavailableException("timeout", null));

		approve(donor, orderId, "pay-slow", 2000).andExpect(status().isServiceUnavailable());
		mvc.perform(get("/api/donations/sent").cookie(donor)).andExpect(jsonPath("$[0].status").value("PENDING"));

		// 승인을 요청한 뒤에는 결제창 실패 보고로 끝내지 않는다. 결과는 승인 쪽이 정한다
		mvc.perform(json(post("/api/donations/{id}/abandon", orderId), "{\"code\":\"PAY_PROCESS_CANCELED\"}")
				.cookie(donor))
			.andExpect(jsonPath("$.status").value("PENDING"));
		// 다른 paymentKey로 바꿔 승인할 수 없다
		approve(donor, orderId, "pay-other", 2000).andExpect(status().isBadRequest());

		approvePayments();
		approve(donor, orderId, "pay-slow", 2000).andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void differentAmountAfterApprovalWasRequestedKeepsItPending() throws Exception {
		Cookie donor = signUp();
		String orderId = startDonation(liveBroadcast(signUp()), donor, 5000);
		given(gateway.approve(anyString(), anyString(), anyLong()))
			.willThrow(new PaymentUnavailableException("timeout", null));
		approve(donor, orderId, "pay-sent", 5000).andExpect(status().isServiceUnavailable());

		// 결제 서비스는 이미 승인했을 수 있으므로, 다른 금액이 와도 실패로 끝내지 않는다
		approve(donor, orderId, "pay-sent", 1000).andExpect(status().isBadRequest());
		assertThat(statusOf(orderId)).isEqualTo("PENDING");

		approvePayments();
		approve(donor, orderId, "pay-sent", 5000).andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void stalePendingDonationsExpireOrSettle() throws Exception {
		Cookie donor = signUp();
		String broadcastId = liveBroadcast(signUp());
		String abandoned = startDonation(broadcastId, donor, 1000);
		String unknown = startDonation(broadcastId, donor, 1000);
		given(gateway.approve(anyString(), anyString(), anyLong()))
			.willThrow(new PaymentUnavailableException("timeout", null));
		approve(donor, unknown, "pay-unknown", 1000).andExpect(status().isServiceUnavailable());
		String fresh = startDonation(broadcastId, donor, 1000);

		String neverApproved = startDonation(broadcastId, donor, 2000);
		approve(donor, neverApproved, "pay-lost", 2000).andExpect(status().isServiceUnavailable());

		jdbc.update("update donation set created_at = now() - interval '31 minutes' where id in (?, ?, ?)",
				UUID.fromString(abandoned), UUID.fromString(unknown), UUID.fromString(neverApproved));
		// 결제 서비스에 조회하면 하나는 승인되어 있고, 하나는 승인된 적이 없다
		given(gateway.lookup(unknown)).willReturn(new PaymentGateway.Found("DONE", 1000));
		given(gateway.lookup(neverApproved)).willReturn(new PaymentGateway.NotFound());
		donationService.settleStale();

		assertThat(statusOf(abandoned)).isEqualTo("EXPIRED");
		assertThat(statusOf(unknown)).isEqualTo("DONE");
		assertThat(statusOf(neverApproved)).isEqualTo("FAILED");
		assertThat(statusOf(fresh)).isEqualTo("PENDING");
		verify(gateway, never()).lookup(abandoned);
	}

	@Test
	void donationOutlivesTheBroadcast() throws Exception {
		Cookie broadcaster = signUp();
		String broadcastId = liveBroadcast(broadcaster);
		Cookie donor = signUp();
		String orderId = startDonation(broadcastId, donor, 10000);
		approvePayments();

		// 결제하는 사이 방송이 끝나도 승인한다
		mvc.perform(post("/api/broadcasts/{id}/end", broadcastId).cookie(broadcaster).with(csrf()))
			.andExpect(status().isNoContent());
		approve(donor, orderId, "pay-after-end", 10000).andExpect(jsonPath("$.status").value("DONE"));

		mvc.perform(delete("/api/broadcasts/{id}", broadcastId).cookie(broadcaster).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/donations/received").cookie(broadcaster))
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.totalAmount").value(10000))
			.andExpect(jsonPath("$.items[0].donor.nickname").value(nickname(donor)))
			.andExpect(jsonPath("$.items[0].broadcast.id").isEmpty())
			.andExpect(jsonPath("$.items[0].broadcast.title").value("테스트 방송"));
		mvc.perform(get("/api/donations/sent").cookie(donor))
			.andExpect(jsonPath("$[0].recipient.nickname").value(nickname(broadcaster)))
			.andExpect(jsonPath("$[0].broadcast.id").isEmpty());
	}

	@Test
	void historyIsPrivate() throws Exception {
		mvc.perform(get("/api/donations/sent")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/donations/received")).andExpect(status().isUnauthorized());

		Cookie donor = signUp();
		startDonation(liveBroadcast(signUp()), donor, 1000);
		mvc.perform(get("/api/donations/sent").cookie(signUp())).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/donations/sent").cookie(donor)).andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/payments/config")).andExpect(jsonPath("$.clientKey").value(startsWith("test_ck_")));
	}

	// 앱의 브라우저 결제 (ADR-0005)

	@Test
	void checkoutTokenLetsTheAppDonorPayWithoutBrowserLogin() throws Exception {
		Cookie broadcaster = signUp();
		String broadcastId = liveBroadcast(broadcaster);
		Cookie donor = signUp();
		approvePayments();
		String orderId = startDonation(broadcastId, donor, 3000);
		String token = issueCheckoutToken(donor, orderId);

		// 브라우저는 로그인하지 않았어도 토큰으로 결제 화면을 연다. 새로고침해도 다시 열린다
		for (int i = 0; i < 2; i++) {
			mvc.perform(get("/api/checkout/{token}", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(orderId))
				.andExpect(jsonPath("$.donation.amount").value(3000))
				.andExpect(jsonPath("$.orderName").value(nickname(broadcaster) + "님에게 후원"))
				.andExpect(jsonPath("$.clientKey").value(startsWith("test_ck_")));
		}

		// 다른 계정으로 로그인한 브라우저여도 Donor는 토큰을 받은 앱의 User다. CSRF 토큰도 필요 없다
		Cookie someoneElse = signUp();
		mvc.perform(checkoutApprove(token, "pay-token", 3000).cookie(someoneElse))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
		verify(gateway).approve("pay-token", orderId, 3000);
		mvc.perform(get("/api/donations/sent").cookie(donor)).andExpect(jsonPath("$[0].status").value("DONE"));
		mvc.perform(get("/api/donations/sent").cookie(someoneElse)).andExpect(jsonPath("$.length()").value(0));

		// 결과가 정해지면 결제 화면은 열리지 않고, 승인을 다시 보내도 결과를 그대로 준다
		mvc.perform(get("/api/checkout/{token}", token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value("DONE"));
		mvc.perform(checkoutApprove(token, "pay-token", 3000)).andExpect(jsonPath("$.status").value("DONE"));
		verify(gateway, times(1)).approve(anyString(), anyString(), anyLong());
	}

	@Test
	void onlyTheDonorGetsACheckoutTokenWhilePending() throws Exception {
		String broadcastId = liveBroadcast(signUp());
		Cookie donor = signUp();
		String orderId = startDonation(broadcastId, donor, 1000);

		mvc.perform(post("/api/donations/{id}/checkout-token", orderId).with(csrf()))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/donations/{id}/checkout-token", orderId).cookie(signUp()).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/donations/{id}/checkout-token", UUID.randomUUID()).cookie(donor).with(csrf()))
			.andExpect(status().isNotFound());

		String token = issueCheckoutToken(donor, orderId);
		mvc.perform(post("/api/checkout/{token}/abandon", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"PAY_PROCESS_CANCELED\",\"message\":\"취소\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELED"));
		mvc.perform(post("/api/donations/{id}/checkout-token", orderId).cookie(donor).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value("CANCELED"));
	}

	@Test
	void checkoutTokenExpiresForOpeningAndIsReplacedWhenReissued() throws Exception {
		String broadcastId = liveBroadcast(signUp());
		Cookie donor = signUp();
		approvePayments();
		String orderId = startDonation(broadcastId, donor, 2000);

		mvc.perform(get("/api/checkout/{token}", "no-such-token")).andExpect(status().isNotFound());

		String first = issueCheckoutToken(donor, orderId);
		String second = issueCheckoutToken(donor, orderId);
		mvc.perform(get("/api/checkout/{token}", first)).andExpect(status().isNotFound());
		mvc.perform(checkoutApprove(first, "pay-old", 2000)).andExpect(status().isNotFound());
		mvc.perform(get("/api/checkout/{token}", second)).andExpect(status().isOk());

		// 결제 화면을 여는 시간이 지나도, 결제창에서 시간이 걸린 승인은 결제 대기인 동안 받는다
		jdbc.update("update donation set checkout_token_expires_at = now() - interval '1 minute' where id = ?",
				UUID.fromString(orderId));
		mvc.perform(get("/api/checkout/{token}", second)).andExpect(status().isNotFound());
		mvc.perform(checkoutApprove(second, "pay-late", 2000))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
	}

	// 도우미

	private final Map<Cookie, String> nicknames = new HashMap<>();

	private String issueCheckoutToken(Cookie donor, String orderId) throws Exception {
		MvcResult result = mvc.perform(post("/api/donations/{id}/checkout-token", orderId).cookie(donor).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andReturn();
		return read(result, "$.token");
	}

	/** 브라우저 결제 화면의 승인 요청. 로그인 쿠키도 CSRF 토큰도 없이 보낸다 */
	private static MockHttpServletRequestBuilder checkoutApprove(String token, String paymentKey, long amount) {
		return post("/api/checkout/{token}/approve", token).contentType(MediaType.APPLICATION_JSON)
			.content("{\"paymentKey\":\"%s\",\"amount\":%d}".formatted(paymentKey, amount));
	}

	/** 이미 예외를 던지도록 정해 둔 경우에도 바꿀 수 있게 given(...) 대신 willReturn(...).given(...)을 쓴다 */
	private void approvePayments() {
		willReturn(new PaymentGateway.Approved()).given(gateway).approve(anyString(), anyString(), anyLong());
	}

	private Cookie signUp() throws Exception {
		int n = users.incrementAndGet();
		String nickname = "회원" + n;
		MvcResult result = mvc.perform(json(post("/api/auth/signup"),
				"{\"username\":\"donor%d\",\"password\":\"password1\",\"nickname\":\"%s\"}".formatted(n, nickname)))
			.andExpect(status().isCreated())
			.andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		nicknames.put(session, nickname);
		return session;
	}

	private String nickname(Cookie session) {
		return nicknames.get(session);
	}

	private String startBroadcast(Cookie session) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts"), "{\"title\":\"테스트 방송\"}").cookie(session))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.broadcast.id");
	}

	/** 미디어 서버 없이 방송 중으로 만든다 */
	private String liveBroadcast(Cookie session) throws Exception {
		String id = startBroadcast(session);
		jdbc.update("update broadcast set status = 'LIVE', started_at = now(), version = version + 1 where id = ?",
				UUID.fromString(id));
		return id;
	}

	private String startDonation(String broadcastId, Cookie donor, int amount) throws Exception {
		MvcResult result = mvc.perform(json(post("/api/broadcasts/{id}/donations", broadcastId),
				"{\"amount\":" + amount + "}").cookie(donor))
			.andExpect(status().isCreated())
			.andReturn();
		return read(result, "$.orderId");
	}

	private ResultActions approve(Cookie donor, String orderId, String paymentKey,
			long amount) throws Exception {
		return mvc.perform(json(post("/api/donations/{id}/approve", orderId),
				"{\"paymentKey\":\"%s\",\"amount\":%d}".formatted(paymentKey, amount)).cookie(donor));
	}

	private String statusOf(String donationId) {
		return jdbc.queryForObject("select status from donation where id = ?", String.class, UUID.fromString(donationId));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
	}

	private static String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}
}
