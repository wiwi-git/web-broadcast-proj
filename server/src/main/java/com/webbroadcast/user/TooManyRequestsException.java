package com.webbroadcast.user;

/** 메일을 너무 자주 보내 달라고 했다 (429) */
public class TooManyRequestsException extends RuntimeException {

	TooManyRequestsException(String message) {
		super(message);
	}
}
