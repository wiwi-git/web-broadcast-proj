package com.webbroadcast.web;

import java.time.Instant;
import java.util.UUID;

import com.webbroadcast.donation.Donation;
import com.webbroadcast.donation.DonationStatus;

/** Donation을 보는 사람에 따라 내보내는 정보가 다르다. */
final class DonationResponses {

	private DonationResponses() {
	}

	/** 방송 화면에 공개되는 완료된 Donation */
	record PublicDonation(UUID id, UserSummary donor, int amount, String message, Instant completedAt) {

		static PublicDonation from(Donation donation) {
			return new PublicDonation(donation.getId(), UserSummary.from(donation.getDonor()), donation.getAmount(),
					donation.getMessage(), donation.getCompletedAt());
		}
	}

	/** Broadcast가 삭제되었으면 id가 null이고 title만 남는다 */
	record BroadcastRef(UUID id, String title) {
	}

	/** Donor 본인에게 보이는 Donation. 실패했으면 failureMessage가 있다 */
	record SentDonation(UUID id, UserSummary recipient, BroadcastRef broadcast, int amount, String message,
			DonationStatus status, String failureMessage, Instant createdAt, Instant completedAt) {

		static SentDonation from(Donation donation) {
			return new SentDonation(donation.getId(), UserSummary.from(donation.getRecipient()),
					new BroadcastRef(donation.getBroadcastId(), donation.getBroadcastTitle()), donation.getAmount(),
					donation.getMessage(), donation.getStatus(), donation.getFailureMessage(), donation.getCreatedAt(),
					donation.getCompletedAt());
		}
	}

	/** Recipient에게 보이는 받은 Donation */
	record ReceivedDonation(UUID id, UserSummary donor, BroadcastRef broadcast, int amount, String message,
			Instant completedAt) {

		static ReceivedDonation from(Donation donation) {
			return new ReceivedDonation(donation.getId(), UserSummary.from(donation.getDonor()),
					new BroadcastRef(donation.getBroadcastId(), donation.getBroadcastTitle()), donation.getAmount(),
					donation.getMessage(), donation.getCompletedAt());
		}
	}
}
