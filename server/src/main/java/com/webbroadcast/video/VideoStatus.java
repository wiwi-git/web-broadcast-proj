package com.webbroadcast.video;

public enum VideoStatus {
	/** 업로드는 끝났지만 Rendition이 아직 준비되지 않음 */
	PROCESSING,
	/** 시청 가능 */
	PUBLISHED,
	/** 변환 실패 */
	FAILED
}
