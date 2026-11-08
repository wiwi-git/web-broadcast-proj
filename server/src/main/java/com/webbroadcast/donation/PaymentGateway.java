package com.webbroadcast.donation;

/**
 * 결제 서비스의 승인·조회 API. 승인은 같은 orderId로 다시 부르면 처음 결과를 그대로 돌려줘야 한다(멱등).
 * 결과를 모르게 된 승인은 다시 승인을 요청하거나 orderId로 조회해 맞춘다.
 */
public interface PaymentGateway {

	/**
	 * @throws PaymentUnavailableException 결제 서비스에 닿지 못했거나 응답을 해석할 수 없어 결과를 모를 때
	 */
	Approval approve(String paymentKey, String orderId, long amount);

	/**
	 * orderId로 승인된 결제를 찾는다.
	 *
	 * @throws PaymentUnavailableException 결제 서비스에 닿지 못해 알 수 없을 때
	 */
	Lookup lookup(String orderId);

	/** 결제 서비스에 남은 결제 상태. 승인된 적이 없으면 NotFound */
	sealed interface Lookup {
	}

	record Found(String status, long amount) implements Lookup {

		public boolean isApproved() {
			return "DONE".equals(status);
		}
	}

	record NotFound() implements Lookup {
	}

	sealed interface Approval {
	}

	record Approved() implements Approval {
	}

	/** message는 사용자에게 그대로 보여줄 수 있는 문구다 */
	record Rejected(String code, String message) implements Approval {
	}
}
