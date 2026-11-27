package com.webbroadcast.user;

/** 이메일 인증, 비밀번호 변경·재설정 요청이 올바르지 않다 (400) */
public class InvalidAccountRequestException extends RuntimeException {

	InvalidAccountRequestException(String message) {
		super(message);
	}
}
