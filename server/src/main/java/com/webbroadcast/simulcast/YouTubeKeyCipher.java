package com.webbroadcast.simulcast;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * YouTube Key를 AES-GCM으로 암호화한다. 저장 형식은 base64(IV 12바이트 + 암호문).
 * YouTube Key가 있으면 누구나 그 채널로 방송할 수 있으므로 DB에 평문으로 두지 않는다.
 */
final class YouTubeKeyCipher {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKey key;

	YouTubeKeyCipher(String base64Key) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64Key);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("simulcast.encryption-key는 base64여야 합니다.", e);
		}
		if (bytes.length != 32) {
			throw new IllegalStateException("simulcast.encryption-key는 32바이트(AES-256)여야 합니다. 예: openssl rand -base64 32");
		}
		this.key = new SecretKeySpec(bytes, "AES");
	}

	String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[IV_BYTES + encrypted.length];
			System.arraycopy(iv, 0, out, 0, IV_BYTES);
			System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
			return Base64.getEncoder().encodeToString(out);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("YouTube Key를 암호화하지 못했습니다.", e);
		}
	}

	String decrypt(String stored) {
		try {
			byte[] in = Base64.getDecoder().decode(stored);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
			return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("YouTube Key를 복호화하지 못했습니다. 암호화 키가 바뀌었는지 확인하세요.", e);
		}
	}
}
