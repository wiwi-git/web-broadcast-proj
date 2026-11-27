package com.webbroadcast.report;

/** 신고 처리 상태 */
public enum ReportStatus {
	/** 운영자가 아직 보지 않았다 */
	OPEN,
	/** 운영자가 대상을 내렸다 */
	ACTIONED,
	/** 운영자가 문제없다고 보고 닫았다 */
	DISMISSED
}
