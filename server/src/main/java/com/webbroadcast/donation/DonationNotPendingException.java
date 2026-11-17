package com.webbroadcast.donation;

/** 결제 대기가 아닌 Donation으로 결제를 시작하려 했다. 현재 상태를 함께 알린다. */
public class DonationNotPendingException extends RuntimeException {

	private final DonationStatus status;

	public DonationNotPendingException(DonationStatus status) {
		super("이미 결과가 정해진 후원입니다.");
		this.status = status;
	}

	public DonationStatus getStatus() {
		return status;
	}
}
