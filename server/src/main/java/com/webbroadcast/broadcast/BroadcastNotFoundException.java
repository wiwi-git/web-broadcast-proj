package com.webbroadcast.broadcast;

import java.util.UUID;

public class BroadcastNotFoundException extends RuntimeException {

	public BroadcastNotFoundException(UUID id) {
		super("Broadcast를 찾을 수 없습니다: " + id);
	}
}
