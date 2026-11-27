package com.webbroadcast.report;

public class AlreadyReportedException extends RuntimeException {

	AlreadyReportedException() {
		super("이미 신고했습니다.");
	}
}
