package com.webbroadcast.web.donation;

import java.util.List;
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
import com.webbroadcast.web.donation.DonationResponses.PublicDonation;
import com.webbroadcast.web.donation.DonationResponses.ReceivedDonation;
import com.webbroadcast.web.donation.DonationResponses.SentDonation;

/**
 * 완료된 Donation 목록은 누구나 본다. 후원과 후원 내역은 로그인한 User만, 승인·취소는 Donor 본인만 한다
 * (SecurityConfig, DonationService).
 */
@RestController
public class DonationController {

	public static final String DONATIONS_PATH = "/api/donations/**";

	/** 결제 키가 설정되지 않았으면 clientKey가 null이다 */
	record PaymentConfig(String clientKey) {
	}

	record StartRequest(Integer amount, String message) {
	}

	/** 웹은 이 값들로 결제위젯의 결제를 요청한다. orderId는 Donation의 id다 */
	record Checkout(SentDonation donation, String orderId, String orderName, String customerKey) {
	}

	record ApproveRequest(String paymentKey, Long amount) {
	}

	/** 결제 서비스가 failUrl로 넘겨준 값 */
	record AbandonRequest(String code, String message) {
	}

	record ReceivedResponse(List<ReceivedDonation> items, long totalCount, long totalAmount) {
	}

	private final DonationService donationService;
	private final TossPaymentsProperties tossPayments;

	DonationController(DonationService donationService, TossPaymentsProperties tossPayments) {
		this.donationService = donationService;
		this.tossPayments = tossPayments;
	}

	@GetMapping("/api/payments/config")
	PaymentConfig paymentConfig() {
		return new PaymentConfig(tossPayments.clientKey());
	}

	@PostMapping("/api/broadcasts/{broadcastId}/donations")
	@ResponseStatus(HttpStatus.CREATED)
	Checkout start(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID broadcastId,
			@RequestBody StartRequest request) {
		if (!tossPayments.isConfigured()) {
			throw new PaymentNotConfiguredException();
		}
		Donation donation = donationService.start(user.id(), broadcastId, request.amount(), request.message());
		return new Checkout(SentDonation.from(donation), donation.getOrderId(),
				donation.getRecipient().getNickname() + "님에게 후원", user.id().toString());
	}

	/** 최근 완료된 Donation을 오래된 순으로 */
	@GetMapping("/api/broadcasts/{broadcastId}/donations")
	List<PublicDonation> listOnBroadcast(@PathVariable UUID broadcastId) {
		return donationService.listDoneOnBroadcast(broadcastId).stream().map(PublicDonation::from).toList();
	}

	/** 결과를 아직 모르면 503. 같은 요청을 다시 보내면 된다 */
	@PostMapping("/api/donations/{id}/approve")
	SentDonation approve(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody ApproveRequest request) {
		return SentDonation.from(donationService.approve(id, user.id(), request.paymentKey(), request.amount()));
	}

	@PostMapping("/api/donations/{id}/abandon")
	SentDonation abandon(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody AbandonRequest request) {
		return SentDonation.from(donationService.abandon(id, user.id(), request.code(), request.message()));
	}

	@GetMapping("/api/donations/sent")
	List<SentDonation> sent(@AuthenticationPrincipal CurrentUser user) {
		return donationService.listSent(user.id()).stream().map(SentDonation::from).toList();
	}

	@GetMapping("/api/donations/received")
	ReceivedResponse received(@AuthenticationPrincipal CurrentUser user) {
		DonationService.Received received = donationService.listReceived(user.id());
		return new ReceivedResponse(received.items().stream().map(ReceivedDonation::from).toList(),
				received.totalCount(), received.totalAmount());
	}
}
