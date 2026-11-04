package com.webbroadcast.broadcast;

import java.util.UUID;

/** 한 User는 한 번에 하나의 Broadcast만 진행할 수 있다. */
public class BroadcastInProgressException extends RuntimeException {

	private final UUID activeBroadcastId;

	BroadcastInProgressException(UUID activeBroadcastId) {
		super("이미 진행 중인 방송이 있습니다. 그 방송을 종료한 뒤 다시 시작해 주세요.");
		this.activeBroadcastId = activeBroadcastId;
	}

	public UUID getActiveBroadcastId() {
		return activeBroadcastId;
	}
}
