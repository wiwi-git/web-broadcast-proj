package com.webbroadcast.video;

/** User가 올린 Original의 합계가 상한을 넘게 되는 업로드. */
public class UploadQuotaExceededException extends RuntimeException {

	public UploadQuotaExceededException(String message) {
		super(message);
	}
}
