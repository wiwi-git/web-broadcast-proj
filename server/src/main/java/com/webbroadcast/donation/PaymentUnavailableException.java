package com.webbroadcast.donation;

/** 승인 결과를 아직 모른다. Donation은 결제 대기로 남고 나중에 다시 확인한다. */
public class PaymentUnavailableException extends RuntimeException {

	public PaymentUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
