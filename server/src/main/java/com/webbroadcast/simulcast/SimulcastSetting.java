package com.webbroadcast.simulcast;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** User 한 명의 YouTube Key(암호화)와 지난번 방송에서 동시 송출을 골랐는지 */
@Entity
@Table(name = "simulcast_setting")
class SimulcastSetting {

	@Id
	private UUID userId;

	@Column(nullable = false, length = 512)
	private String youtubeKeyEncrypted;

	@Column(name = "youtube_key_last4", nullable = false, length = 4)
	private String youtubeKeyLast4;

	@Column(nullable = false)
	private boolean onByDefault;

	protected SimulcastSetting() {
	}

	static SimulcastSetting of(UUID userId) {
		SimulcastSetting setting = new SimulcastSetting();
		setting.userId = userId;
		// 키를 처음 등록하면 쓰려는 것이므로 켜 둔다
		setting.onByDefault = true;
		return setting;
	}

	void replaceKey(String encrypted, String last4) {
		this.youtubeKeyEncrypted = encrypted;
		this.youtubeKeyLast4 = last4;
	}

	void rememberChoice(boolean on) {
		this.onByDefault = on;
	}

	String getYoutubeKeyEncrypted() {
		return youtubeKeyEncrypted;
	}

	String getYoutubeKeyLast4() {
		return youtubeKeyLast4;
	}

	boolean isOnByDefault() {
		return onByDefault;
	}
}
