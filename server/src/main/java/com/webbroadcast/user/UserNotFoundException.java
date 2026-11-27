package com.webbroadcast.user;

public class UserNotFoundException extends RuntimeException {

	UserNotFoundException() {
		super("회원을 찾을 수 없습니다.");
	}
}
