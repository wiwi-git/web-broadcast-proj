package com.webbroadcast.simulcast;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 유튜브 동시 송출 설정 (ADR-0007).
 *
 * @param encryptionKey YouTube Key를 DB에 암호화할 AES-256 키 (base64, 32바이트)
 * @param relaySecret MediaMTX의 중계 스크립트가 내부 API를 부를 때 보내는 비밀 값
 * @param youtubeUrl 유튜브 수신 주소. 뒤에 "/YouTube Key"를 붙여 보낸다. 로컬 확인 때는 RTMP 수신기 주소로 바꾼다
 *
 * 앞의 둘 중 하나라도 비어 있으면 동시 송출만 쓸 수 없고 나머지 기능은 동작한다.
 */
@ConfigurationProperties("simulcast")
public record SimulcastProperties(String encryptionKey, String relaySecret, String youtubeUrl) {

	public SimulcastProperties {
		encryptionKey = blankToNull(encryptionKey);
		relaySecret = blankToNull(relaySecret);
		youtubeUrl = blankToNull(youtubeUrl);
	}

	public boolean isConfigured() {
		return encryptionKey != null && relaySecret != null && youtubeUrl != null;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
