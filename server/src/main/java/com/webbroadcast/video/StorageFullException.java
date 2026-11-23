package com.webbroadcast.video;

/** 받으면 저장소 디스크의 여유 공간이 기준 아래로 떨어지는 업로드. */
public class StorageFullException extends RuntimeException {

	public StorageFullException(String message) {
		super(message);
	}
}
