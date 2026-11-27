package com.webbroadcast.user;

import java.time.Duration;

/** 메일로 보내는 일회용 링크의 용도와 유효 시간 */
public enum EmailTokenPurpose {
	VERIFY_EMAIL(Duration.ofHours(24)),
	RESET_PASSWORD(Duration.ofMinutes(30));

	private final Duration validFor;

	EmailTokenPurpose(Duration validFor) {
		this.validFor = validFor;
	}

	public Duration validFor() {
		return validFor;
	}
}
