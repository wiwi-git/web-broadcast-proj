package com.webbroadcast.donation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 API 개별 연동 키(통합결제창). 사업자 등록 없이 가입만 하면 받을 수 있다.
 * 실제 돈이 오가지 않도록 테스트 키만 받는다. clientKey는 웹에 내려주고, secretKey는 서버에서 승인·조회 API를 부를 때만 쓴다.
 * 두 키가 모두 비어 있으면 후원 결제만 쓸 수 없고 나머지 기능은 동작한다.
 */
@ConfigurationProperties("toss")
public record TossPaymentsProperties(String clientKey, String secretKey, String apiUrl) {

	public TossPaymentsProperties {
		clientKey = blankToNull(clientKey);
		secretKey = blankToNull(secretKey);
		boolean unset = clientKey == null && secretKey == null;
		// 결제위젯 키(test_gck_, test_gsk_)는 통합결제창에서 쓸 수 없고, 라이브 키는 실제 돈이 오간다
		boolean testPair = clientKey != null && clientKey.startsWith("test_ck_") && secretKey != null
				&& secretKey.startsWith("test_sk_");
		if (!unset && !testPair) {
			throw new IllegalStateException("토스페이먼츠 API 개별 연동 테스트 키(test_ck_, test_sk_로 시작) 한 쌍만 쓸 수 있습니다. "
					+ "server/config/local.properties의 toss.client-key, toss.secret-key를 확인하세요.");
		}
	}

	public boolean isConfigured() {
		return clientKey != null;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
