package com.webbroadcast.broadcast;

import java.util.Map;

/**
 * 라이브 영상을 실제로 받고 내보내는 미디어 서버 (ADR-0002).
 */
public interface MediaServer {

	/** 송출 주체. 강제로 끊을 때 쓴다. */
	record Publisher(String type, String id) {
	}

	/** 지금 영상이 들어오고 있는 경로와 그 송출 주체. 미디어 서버에 닿지 않으면 예외. */
	Map<String, Publisher> activePaths();

	void disconnect(Publisher publisher);
}
