package com.webbroadcast.video;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 변환 워커 설정 (ADR-0004).
 *
 * @param enabled 이 프로세스가 변환을 맡을지. API 서버만 띄울 때는 false
 * @param pollInterval 할 일이 없을 때 대기열을 다시 보는 간격
 * @param lease 점유 기한. 워커가 죽으면 이 시간이 지난 뒤 다른 워커가 이어받는다
 */
@ConfigurationProperties("video.worker")
public record VideoWorkerProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("1s") Duration pollInterval,
		@DefaultValue("60s") Duration lease) {
}
