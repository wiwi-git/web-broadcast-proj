package com.webbroadcast.broadcast;

/** MediaMTX의 중계 스크립트가 마지막으로 알린 유튜브 연결 상태 (ADR-0007) */
public enum SimulcastStatus {
	/** 유튜브에 연결하는 중이거나 끊겨서 다시 연결하는 중 */
	CONNECTING,
	/** 유튜브로 보내고 있다 */
	LIVE,
	/** 다시 시도해도 연결되지 않아 이 방송에서는 그만두었다 */
	FAILED
}
