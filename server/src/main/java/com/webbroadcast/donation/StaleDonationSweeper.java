package com.webbroadcast.donation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 결제 대기로 오래 남은 Donation을 1분마다 정리한다 */
@Component
class StaleDonationSweeper {

	private final DonationService donationService;

	StaleDonationSweeper(DonationService donationService) {
		this.donationService = donationService;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	void sweep() {
		donationService.settleStale();
	}
}
