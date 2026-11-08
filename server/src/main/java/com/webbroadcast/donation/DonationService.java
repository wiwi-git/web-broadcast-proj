package com.webbroadcast.donation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.webbroadcast.broadcast.Broadcast;
import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.broadcast.BroadcastStatus;
import com.webbroadcast.user.NotOwnerException;
import com.webbroadcast.user.UserService;

/**
 * Donation의 흐름.
 * <ol>
 * <li>start: 로그인한 Donor가 방송 중인 Broadcast에 금액을 정하면 결제 대기로 만든다. 웹은 이 id를 orderId로 결제를 요청한다.</li>
 * <li>approve: 결제 서비스가 돌려준 paymentKey와 금액을 받아, 저장한 금액과 같을 때만 승인을 요청한다.</li>
 * <li>abandon: Donor가 결제창을 닫았거나 결제 인증이 실패하면 취소나 실패로 끝낸다.</li>
 * </ol>
 * 승인 결과를 모르면 결제 대기로 둔다. Donor가 같은 승인을 다시 요청하거나(PaymentGateway는 멱등),
 * 오래되면 settleStale이 결제 서비스에 orderId로 조회해 결과를 맞춘다.
 */
@Service
public class DonationService {

	private static final Logger log = LoggerFactory.getLogger(DonationService.class);

	/** Broadcast 화면에는 최근 완료된 Donation만 보여 준다 */
	public static final int BROADCAST_RECENT_LIMIT = 100;
	/** 후원 내역은 최근 것만 보여 준다 */
	public static final int HISTORY_LIMIT = 50;
	/**
	 * 결제 대기가 이보다 오래되면 만료하거나, 승인을 요청했던 건은 결과를 조회해 맞춘다.
	 * 결제 서비스의 결제 인증은 10분 뒤 만료되므로, 이때까지 승인되지 않은 결제는 앞으로도 승인되지 않는다.
	 */
	static final Duration PENDING_TIMEOUT = Duration.ofMinutes(30);

	/** 결제창을 Donor가 닫았을 때 결제 서비스가 주는 코드 */
	static final String CANCELED_BY_DONOR = "PAY_PROCESS_CANCELED";
	private static final int FAILURE_MESSAGE_MAX_LENGTH = 500;

	public record Received(List<Donation> items, long totalCount, long totalAmount) {
	}

	private final DonationRepository repository;
	private final BroadcastService broadcastService;
	private final UserService userService;
	private final PaymentGateway paymentGateway;
	private final Clock clock;

	DonationService(DonationRepository repository, BroadcastService broadcastService, UserService userService,
			PaymentGateway paymentGateway, Clock clock) {
		this.repository = repository;
		this.broadcastService = broadcastService;
		this.userService = userService;
		this.paymentGateway = paymentGateway;
		this.clock = clock;
	}

	public Donation start(UUID donorId, UUID broadcastId, Integer amount, String rawMessage) {
		validAmount(amount);
		String message = validMessage(rawMessage);
		Broadcast broadcast = broadcastService.get(broadcastId);
		if (broadcast.getStatus() != BroadcastStatus.LIVE) {
			throw new InvalidDonationException("방송 중일 때만 후원할 수 있습니다.");
		}
		if (broadcast.getOwner().is(donorId)) {
			throw new InvalidDonationException("자기 방송에는 후원할 수 없습니다.");
		}
		return repository.save(Donation.pending(userService.get(donorId), broadcast, amount, message, clock.instant()));
	}

	/**
	 * 결제 인증을 마치고 돌아온 Donor의 승인 요청. 이미 끝난 Donation이면 그 결과를 그대로 돌려준다.
	 * 승인 요청 사이에 Broadcast가 끝났거나 지워졌어도 승인한다. 후원 대상은 Recipient이기 때문이다.
	 *
	 * @throws PaymentUnavailableException 승인 결과를 아직 모를 때. 같은 요청을 다시 보내면 된다
	 */
	public Donation approve(UUID id, UUID userId, String paymentKey, Long amount) {
		if (paymentKey == null || paymentKey.isBlank()) {
			throw new InvalidDonationException("결제 정보가 없습니다.");
		}
		Donation donation = sentBy(id, userId);
		if (!donation.isPending()) {
			return donation;
		}
		if (donation.getPaymentKey() != null && !donation.getPaymentKey().equals(paymentKey)) {
			throw new InvalidDonationException("다른 결제 정보로 승인할 수 없습니다.");
		}
		if (amount == null || amount != donation.getAmount()) {
			// 웹에서 금액을 바꾼 결제는 승인하지 않는다. 승인하지 않은 결제 인증은 결제 서비스에서 곧 만료된다
			donation.fail("AMOUNT_MISMATCH", "결제 금액이 후원 금액과 다릅니다.", clock.instant());
			return saveOrCurrent(donation);
		}
		donation.requestApproval(paymentKey);
		donation = saveOrCurrent(donation);
		return donation.isPending() ? settle(donation) : donation;
	}

	/** 결제 인증 단계에서 돌아온 경우. 승인을 요청한 뒤라면 결과는 승인 쪽이 정하므로 그대로 둔다. */
	public Donation abandon(UUID id, UUID userId, String code, String message) {
		Donation donation = sentBy(id, userId);
		if (!donation.isPending() || donation.getPaymentKey() != null) {
			return donation;
		}
		if (CANCELED_BY_DONOR.equals(code)) {
			donation.cancel(clock.instant());
		}
		else {
			donation.fail(code == null || code.isBlank() ? "PAY_PROCESS_ABORTED" : truncate(code, 100),
					message == null || message.isBlank() ? "결제에 실패했습니다." : truncate(message, FAILURE_MESSAGE_MAX_LENGTH),
					clock.instant());
		}
		return saveOrCurrent(donation);
	}

	/** 한 Broadcast의 최근 완료된 Donation을 오래된 순으로. 누구나 볼 수 있다. */
	public List<Donation> listDoneOnBroadcast(UUID broadcastId) {
		broadcastService.get(broadcastId);
		return repository.findLatestDoneOnBroadcast(broadcastId, Limit.of(BROADCAST_RECENT_LIMIT)).reversed();
	}

	/** 보낸 Donation. 상태와 상관없이 최근 만든 순 */
	public List<Donation> listSent(UUID donorId) {
		return repository.findByDonorIdOrderByCreatedAtDesc(donorId, Limit.of(HISTORY_LIMIT));
	}

	/** 받은 Donation. 완료된 것만, 합계는 전체 기간 */
	public Received listReceived(UUID recipientId) {
		List<Donation> items = repository.findByRecipientIdAndStatusOrderByCompletedAtDesc(recipientId,
				DonationStatus.DONE, Limit.of(HISTORY_LIMIT));
		DonationRepository.Total total = repository.totalReceived(recipientId);
		return new Received(items, total.getCount(), total.getAmount());
	}

	/**
	 * 오래된 결제 대기를 정리한다. 승인을 요청하지 않은 건은 만료하고,
	 * 승인을 요청했지만 결과를 모르는 건은 결제 서비스에 조회해 결과를 맞춘다.
	 */
	public void settleStale() {
		Instant cutoff = clock.instant().minus(PENDING_TIMEOUT);
		for (Donation donation : repository.findByStatusAndCreatedAtBefore(DonationStatus.PENDING, cutoff)) {
			try {
				if (donation.getPaymentKey() == null) {
					donation.expire(clock.instant());
					saveOrCurrent(donation);
				}
				else {
					reconcile(donation);
				}
			}
			catch (PaymentUnavailableException e) {
				log.warn("Donation {}의 승인 결과를 아직 알 수 없습니다: {}", donation.getId(), e.getMessage());
			}
		}
	}

	/** 승인을 요청하고 결과를 반영한다 */
	private Donation settle(Donation donation) {
		PaymentGateway.Approval approval = paymentGateway.approve(donation.getPaymentKey(), donation.getOrderId(),
				donation.getAmount());
		switch (approval) {
			case PaymentGateway.Approved approved -> donation.complete(clock.instant());
			case PaymentGateway.Rejected rejected -> donation.fail(rejected.code(),
					rejected.message() == null ? "결제가 거절되었습니다." : truncate(rejected.message(), FAILURE_MESSAGE_MAX_LENGTH),
					clock.instant());
		}
		return saveOrCurrent(donation);
	}

	private void reconcile(Donation donation) {
		Instant now = clock.instant();
		switch (paymentGateway.lookup(donation.getOrderId())) {
			case PaymentGateway.Found found when found.isApproved() && found.amount() == donation.getAmount() ->
				donation.complete(now);
			case PaymentGateway.Found found -> {
				log.warn("Donation {}의 결제가 승인되지 않은 상태로 남았습니다: {}", donation.getId(), found);
				donation.fail("NOT_APPROVED", "결제가 승인되지 않았습니다.", now);
			}
			case PaymentGateway.NotFound notFound -> donation.fail("NOT_APPROVED", "결제가 승인되지 않았습니다.", now);
		}
		saveOrCurrent(donation);
	}

	/** 같은 Donation을 다른 요청이 먼저 바꿨다면 그 결과를 따른다 */
	private Donation saveOrCurrent(Donation donation) {
		try {
			return repository.saveAndFlush(donation);
		}
		catch (OptimisticLockingFailureException e) {
			return repository.findById(donation.getId()).orElseThrow(DonationNotFoundException::new);
		}
	}

	private Donation sentBy(UUID id, UUID userId) {
		Donation donation = repository.findById(id).orElseThrow(DonationNotFoundException::new);
		if (!donation.isSentBy(userId)) {
			throw new NotOwnerException("본인이 보낸 후원만 처리할 수 있습니다.");
		}
		return donation;
	}

	private static void validAmount(Integer amount) {
		if (amount == null || amount < Donation.MIN_AMOUNT || amount > Donation.MAX_AMOUNT
				|| amount % Donation.AMOUNT_UNIT != 0) {
			throw new InvalidDonationException("후원 금액은 1,000원부터 500,000원까지 1,000원 단위로 정할 수 있습니다.");
		}
	}

	private static String validMessage(String rawMessage) {
		String message = rawMessage == null ? "" : rawMessage.strip();
		if (message.length() > Donation.MESSAGE_MAX_LENGTH) {
			throw new InvalidDonationException("후원 메시지는 " + Donation.MESSAGE_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return message.isEmpty() ? null : message;
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
