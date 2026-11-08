package com.webbroadcast.donation;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스페이먼츠 결제 승인(POST /v1/payments/confirm)·조회(GET /v1/payments/orders/{orderId}) 클라이언트.
 * API 개별 연동 키를 쓴다. 승인에는 orderId로 만든 멱등키를 함께 보내므로,
 * 같은 승인을 다시 요청하면 토스페이먼츠가 처음 결과를 그대로 돌려준다.
 */
@Component
class TossPaymentsGateway implements PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(TossPaymentsGateway.class);

	/** 응답 중 필요한 것만 */
	record Payment(String orderId, String status, Long totalAmount) {
	}

	record Failure(String code, String message) {
	}

	private final RestClient client;

	TossPaymentsGateway(TossPaymentsProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(5));
		// 토스페이먼츠는 승인 API의 읽기 타임아웃을 넉넉히 두라고 권한다
		requestFactory.setReadTimeout(Duration.ofSeconds(30));
		String credentials = Base64.getEncoder()
			.encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		this.client = RestClient.builder()
			.baseUrl(properties.apiUrl())
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
			.build();
	}

	@Override
	public Approval approve(String paymentKey, String orderId, long amount) {
		try {
			return client.post()
				.uri("/v1/payments/confirm")
				.header("Idempotency-Key", "confirm-" + orderId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
				.exchange((request, response) -> {
					HttpStatusCode status = response.getStatusCode();
					if (status.is2xxSuccessful()) {
						return approved(response.bodyTo(Payment.class), orderId, amount);
					}
					Failure failure = readFailure(response);
					if (isDefinite(status) && failure != null && failure.code() != null) {
						return new Rejected(failure.code(), failure.message());
					}
					throw new PaymentUnavailableException("승인 결과를 알 수 없음: " + status + " " + failure, null);
				});
		}
		catch (RestClientException e) {
			throw new PaymentUnavailableException("결제 서비스에 닿지 못함", e);
		}
	}

	@Override
	public Lookup lookup(String orderId) {
		try {
			return client.get()
				.uri("/v1/payments/orders/{orderId}", orderId)
				.exchange((request, response) -> {
					HttpStatusCode status = response.getStatusCode();
					if (status.is2xxSuccessful()) {
						Payment payment = response.bodyTo(Payment.class);
						if (payment != null && payment.status() != null && payment.totalAmount() != null) {
							return new Found(payment.status(), payment.totalAmount());
						}
					}
					else if (status.value() == HttpStatus.NOT_FOUND.value()) {
						return new NotFound();
					}
					throw new PaymentUnavailableException("결제 조회 실패: " + status + " " + readFailure(response), null);
				});
		}
		catch (RestClientException e) {
			throw new PaymentUnavailableException("결제 서비스에 닿지 못함", e);
		}
	}

	private static Approval approved(Payment payment, String orderId, long amount) {
		if (payment != null && "DONE".equals(payment.status()) && orderId.equals(payment.orderId())
				&& payment.totalAmount() != null && payment.totalAmount() == amount) {
			return new Approved();
		}
		// 웹은 카드·간편결제(method: CARD)만 요청하므로 가상계좌처럼 입금을 기다리는 결제는 나오지 않는다
		log.warn("예상과 다른 승인 응답: {}", payment);
		return new Rejected("UNSUPPORTED_PAYMENT", "지원하지 않는 결제 방식입니다. 다른 결제수단을 선택해 주세요.");
	}

	/** 4xx는 거절이 확정된 것이다. 다만 동시에 처리 중(409)이거나 요청이 많은(429) 경우는 결과를 아직 모른다. */
	private static boolean isDefinite(HttpStatusCode status) {
		return status.is4xxClientError() && status.value() != HttpStatus.CONFLICT.value()
				&& status.value() != HttpStatus.TOO_MANY_REQUESTS.value();
	}

	private static Failure readFailure(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
		try {
			return response.bodyTo(Failure.class);
		}
		catch (RuntimeException e) {
			return null;
		}
	}
}
