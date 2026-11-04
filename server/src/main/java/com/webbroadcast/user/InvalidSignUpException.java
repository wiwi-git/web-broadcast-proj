package com.webbroadcast.user;

public class InvalidSignUpException extends RuntimeException {

	public InvalidSignUpException(String message) {
		super(message);
	}
}
