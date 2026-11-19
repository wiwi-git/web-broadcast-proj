package com.webbroadcast.simulcast;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User의 YouTube Key와 동시 송출 기본값을 다룬다 (ADR-0007).
 * YouTube Key는 저장한 뒤 끝 네 자리만 돌려주고, 전체 값은 중계할 때만 꺼낸다.
 */
@Service
public class SimulcastService {

	/** 화면에 보여 줄 값. 키가 없으면 youtubeKeyLast4가 null */
	public record Setting(boolean available, String youtubeKeyLast4, boolean onByDefault) {
	}

	/** 유튜브 스튜디오의 키는 영문·숫자·하이픈으로 된 짧은 값이다(xxxx-xxxx-xxxx-xxxx-xxxx) */
	private static final Pattern YOUTUBE_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

	private final SimulcastSettingRepository repository;
	private final SimulcastProperties properties;
	private final YouTubeKeyCipher cipher;

	SimulcastService(SimulcastSettingRepository repository, SimulcastProperties properties) {
		this.repository = repository;
		this.properties = properties;
		this.cipher = properties.encryptionKey() == null ? null : new YouTubeKeyCipher(properties.encryptionKey());
	}

	public boolean isAvailable() {
		return properties.isConfigured();
	}

	public Setting setting(UUID userId) {
		return repository.findById(userId)
			.map(s -> new Setting(isAvailable(), s.getYoutubeKeyLast4(), s.isOnByDefault()))
			.orElse(new Setting(isAvailable(), null, false));
	}

	@Transactional
	public Setting saveYouTubeKey(UUID userId, String rawKey) {
		requireAvailable();
		String key = rawKey == null ? "" : rawKey.strip();
		if (!YOUTUBE_KEY.matcher(key).matches()) {
			throw new InvalidYouTubeKeyException("유튜브 스튜디오의 스트림 키를 그대로 붙여넣어 주세요.");
		}
		SimulcastSetting setting = repository.findById(userId).orElseGet(() -> SimulcastSetting.of(userId));
		setting.replaceKey(cipher.encrypt(key), key.substring(key.length() - 4));
		repository.save(setting);
		return setting(userId);
	}

	@Transactional
	public void deleteYouTubeKey(UUID userId) {
		repository.deleteById(userId);
	}

	/** 방송을 시작할 때 고른 값을 다음 방송의 기본값으로 남긴다. 키가 없으면 남길 곳이 없다 */
	@Transactional
	public void rememberChoice(UUID userId, boolean on) {
		repository.findById(userId).ifPresent(setting -> {
			setting.rememberChoice(on);
			repository.save(setting);
		});
	}

	public boolean hasYouTubeKey(UUID userId) {
		return repository.existsById(userId);
	}

	/** 중계할 유튜브 주소(YouTube Key 포함). 키가 없거나 동시 송출을 쓸 수 없으면 비어 있다 */
	public Optional<String> youtubeUrl(UUID userId) {
		if (!isAvailable()) {
			return Optional.empty();
		}
		return repository.findById(userId)
			.map(s -> properties.youtubeUrl() + "/" + cipher.decrypt(s.getYoutubeKeyEncrypted()));
	}

	/** MediaMTX 중계 스크립트가 보낸 비밀 값이 맞는지. 설정이 없으면 항상 거부한다 */
	public boolean acceptsRelaySecret(String secret) {
		if (properties.relaySecret() == null || secret == null) {
			return false;
		}
		return MessageDigest.isEqual(properties.relaySecret().getBytes(StandardCharsets.UTF_8),
				secret.getBytes(StandardCharsets.UTF_8));
	}

	public void requireAvailable() {
		if (!isAvailable()) {
			throw new SimulcastNotConfiguredException();
		}
	}
}
