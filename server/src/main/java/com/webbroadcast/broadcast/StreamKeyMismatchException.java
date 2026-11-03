package com.webbroadcast.broadcast;

public class StreamKeyMismatchException extends RuntimeException {

	public StreamKeyMismatchException() {
		super("방송 키가 올바르지 않습니다.");
	}
}
