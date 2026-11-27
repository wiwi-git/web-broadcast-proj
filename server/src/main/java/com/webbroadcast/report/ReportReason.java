package com.webbroadcast.report;

/** 신고 사유. 화면의 문구는 클라이언트가 정한다 */
public enum ReportReason {
	/** 스팸·광고 */
	SPAM,
	/** 욕설·혐오 */
	HATE,
	/** 성적인 콘텐츠 */
	SEXUAL,
	/** 폭력·위험한 행동 */
	VIOLENCE,
	/** 저작권 침해 */
	COPYRIGHT,
	/** 기타 */
	OTHER
}
