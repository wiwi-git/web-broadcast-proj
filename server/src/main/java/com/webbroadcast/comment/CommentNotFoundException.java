package com.webbroadcast.comment;

public class CommentNotFoundException extends RuntimeException {

	CommentNotFoundException() {
		super("Comment를 찾을 수 없습니다.");
	}
}
