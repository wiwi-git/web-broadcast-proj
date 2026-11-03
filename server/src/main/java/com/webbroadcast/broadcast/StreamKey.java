package com.webbroadcast.broadcast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Broadcaster에게만 주는 비밀 값. DB에는 해시만 저장한다.
 */
final class StreamKey {

	private static final SecureRandom RANDOM = new SecureRandom();

	private StreamKey() {
	}

	static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static boolean matches(String key, String expectedHash) {
		if (key == null || key.isEmpty()) {
			return false;
		}
		return MessageDigest.isEqual(
				hash(key).getBytes(StandardCharsets.US_ASCII),
				expectedHash.getBytes(StandardCharsets.US_ASCII));
	}
}
