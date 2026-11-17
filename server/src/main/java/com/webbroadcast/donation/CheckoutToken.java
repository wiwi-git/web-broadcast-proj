package com.webbroadcast.donation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 앱이 브라우저에 넘기는 결제 토큰 (ADR-0005). 이 토큰만 있으면 로그인 없이 그 Donation 하나를 결제·취소할 수 있다.
 * DB에는 해시만 저장한다. Stream Key와 같은 방식이다.
 */
final class CheckoutToken {

	private static final SecureRandom RANDOM = new SecureRandom();

	private CheckoutToken() {
	}

	static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
