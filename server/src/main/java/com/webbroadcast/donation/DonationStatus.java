package com.webbroadcast.donation;

public enum DonationStatus {
	/** 결제 대기 */
	PENDING,
	/** 완료. 이 상태만 공개된다 */
	DONE,
	/** 실패 */
	FAILED,
	/** Donor가 결제를 그만둠 */
	CANCELED,
	/** 결제 대기인 채로 오래 지남 */
	EXPIRED
}
