package com.webbroadcast.user;

/** 로그인한 User가 대상의 Owner(또는 Comment의 Author)가 아니다. */
public class NotOwnerException extends RuntimeException {

	public NotOwnerException(String message) {
		super(message);
	}
}
