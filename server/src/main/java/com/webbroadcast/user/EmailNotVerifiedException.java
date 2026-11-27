package com.webbroadcast.user;

/** 이메일 인증을 하지 않은 회원이 인증이 필요한 일(동영상 업로드, 댓글)을 하려 했다 */
public class EmailNotVerifiedException extends RuntimeException {

	public EmailNotVerifiedException() {
		super("이메일 인증을 마친 회원만 할 수 있습니다. 내 계정에서 인증 메일을 다시 받을 수 있습니다.");
	}
}
