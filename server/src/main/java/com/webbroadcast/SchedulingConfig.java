package com.webbroadcast;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(Broadcast 동기화, 오래된 Donation 정리)은 API 서버에서만 돈다.
 * 웹 요청을 받지 않는 변환 워커(ADR-0004)는 변환만 한다.
 */
@Configuration
@ConditionalOnWebApplication
@EnableScheduling
class SchedulingConfig {
}
