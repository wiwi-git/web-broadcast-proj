package com.webbroadcast.web.donation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.donation.Donation;
import com.webbroadcast.donation.DonationService;
import com.webbroadcast.donation.TossPaymentsProperties;
import com.webbroadcast.user.CurrentUser;
import com.webbroadcast.web.donation.DonationController.AbandonRequest;
import com.webbroadcast.web.donation.DonationController.ApproveRequest;
import com.webbroadcast.web.donation.DonationResponses.SentDonation;

/**
 * 앱(데스크톱·모바일)의 브라우저 결제 (ADR-0005).
 * 앱은 로그인 세션으로 결제 토큰을 받아 기본 브라우저로 웹 결제 화면을 연다. 결제 화면은 로그인 없이 토큰만으로
 * 그 Donation 하나를 조회·승인·취소한다. 그래서 브라우저의 로그인 상태와 상관없이 Donor는 앱의 User다.
 */
@RestController
public class CheckoutController {

	/** 로그인과 CSRF 없이 받는다. 경로의 토큰이 인증 수단이다 (SecurityConfig) */
	public static final String CHECKOUT_PATH = "/api/checkout/**";

	record IssuedToken(String token, Instant expiresAt) {
	}

	/** 웹 결제 화면은 이 값으로 결제창을 연다. 결제 키도 함께 준다 */
	record CheckoutView(SentDonation donation, String orderId, String orderName, String customerKey, String clientKey) {
	}

	private final DonationService donationService;
	private final TossPaymentsProperties tossPayments;

	CheckoutController(DonationService donationService, TossPaymentsProperties tossPayments) {
		this.donationService = donationService;
		this.tossPayments = tossPayments;
	}

	/** Donor 본인이, 결제 대기일 때만. 다시 발급하면 이전 토큰은 쓸 수 없다 */
	@PostMapping("/api/donations/{id}/checkout-token")
	@ResponseStatus(HttpStatus.CREATED)
	IssuedToken issue(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		DonationService.IssuedCheckout issued = donationService.issueCheckoutToken(id, user.id());
		return new IssuedToken(issued.token(), issued.expiresAt());
	}

	@GetMapping("/api/checkout/{token}")
	CheckoutView open(@PathVariable String token) {
		if (!tossPayments.isConfigured()) {
			throw new PaymentNotConfiguredException();
		}
		Donation donation = donationService.openCheckout(token);
		return new CheckoutView(SentDonation.from(donation), donation.getOrderId(),
				DonationController.orderName(donation), donation.getDonor().getId().toString(), tossPayments.clientKey());
	}

	/** 결과를 아직 모르면 503. 같은 요청을 다시 보내면 된다. 이미 끝났으면 그 결과를 그대로 준다 */
	@PostMapping("/api/checkout/{token}/approve")
	SentDonation approve(@PathVariable String token, @RequestBody ApproveRequest request) {
		return SentDonation.from(donationService.approveCheckout(token, request.paymentKey(), request.amount()));
	}

	@PostMapping("/api/checkout/{token}/abandon")
	SentDonation abandon(@PathVariable String token, @RequestBody AbandonRequest request) {
		return SentDonation.from(donationService.abandonCheckout(token, request.code(), request.message()));
	}
}
