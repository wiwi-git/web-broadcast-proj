package com.webbroadcast.donation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.webbroadcast.broadcast.Broadcast;
import com.webbroadcast.user.User;

/**
 * Donor가 방송 중인 Broadcast의 Broadcaster(Recipient)에게 보내는 후원 한 건.
 * 결제를 요청하기 전에 결제 대기로 만들어 두고, 결제가 승인되면 완료가 된다. 완료된 Donation은 되돌리지 않는다.
 * id를 결제 서비스의 orderId로 쓴다.
 */
@Entity
@Table(name = "donation")
public class Donation {

	public static final int MIN_AMOUNT = 1_000;
	public static final int MAX_AMOUNT = 500_000;
	public static final int AMOUNT_UNIT = 1_000;
	public static final int MESSAGE_MAX_LENGTH = 100;

	@Id
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "donor_id")
	private User donor;

	@ManyToOne(optional = false)
	@JoinColumn(name = "recipient_id")
	private User recipient;

	/** Broadcast가 삭제되면 null */
	private UUID broadcastId;

	@Column(nullable = false, length = Broadcast.TITLE_MAX_LENGTH)
	private String broadcastTitle;

	@Column(nullable = false)
	private int amount;

	/** 없으면 null */
	@Column(length = MESSAGE_MAX_LENGTH)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DonationStatus status;

	/** 승인을 요청한 적이 있으면 채워진다 */
	private String paymentKey;

	private String failureCode;

	private String failureMessage;

	@Column(nullable = false)
	private Instant createdAt;

	/** 결제 대기를 벗어난 시각 */
	private Instant completedAt;

	/** 앱이 브라우저 결제용으로 발급받은 결제 토큰의 해시 (ADR-0005). 발급한 적이 없으면 null */
	private String checkoutTokenHash;

	/** 이 시각까지 결제 토큰으로 결제 화면을 열 수 있다 */
	private Instant checkoutTokenExpiresAt;

	@Version
	private long version;

	protected Donation() {
	}

	static Donation pending(User donor, Broadcast broadcast, int amount, String message, Instant now) {
		Donation donation = new Donation();
		donation.id = UUID.randomUUID();
		donation.donor = donor;
		donation.recipient = broadcast.getOwner();
		donation.broadcastId = broadcast.getId();
		donation.broadcastTitle = broadcast.getTitle();
		donation.amount = amount;
		donation.message = message;
		donation.status = DonationStatus.PENDING;
		donation.createdAt = now;
		return donation;
	}

	boolean isPending() {
		return status == DonationStatus.PENDING;
	}

	boolean isSentBy(UUID userId) {
		return donor.is(userId);
	}

	/** 다시 발급하면 이전 토큰은 쓸 수 없다 */
	void issueCheckoutToken(String tokenHash, Instant expiresAt) {
		if (!isPending()) {
			throw new IllegalStateException("결제 대기가 아닌 Donation: " + id + " " + status);
		}
		this.checkoutTokenHash = tokenHash;
		this.checkoutTokenExpiresAt = expiresAt;
	}

	/** 결제 토큰으로 결제 화면을 열 수 있는 시간인지 */
	boolean isCheckoutOpen(Instant now) {
		return checkoutTokenExpiresAt != null && now.isBefore(checkoutTokenExpiresAt);
	}

	/** 승인을 요청하기 직전에 남긴다. 결과를 모르게 되면 이 값으로 다시 확인한다. */
	void requestApproval(String paymentKey) {
		this.paymentKey = paymentKey;
	}

	void complete(Instant now) {
		finish(DonationStatus.DONE, now);
	}

	void fail(String code, String message, Instant now) {
		this.failureCode = code;
		this.failureMessage = message;
		finish(DonationStatus.FAILED, now);
	}

	void cancel(Instant now) {
		finish(DonationStatus.CANCELED, now);
	}

	void expire(Instant now) {
		finish(DonationStatus.EXPIRED, now);
	}

	private void finish(DonationStatus status, Instant now) {
		if (!isPending()) {
			throw new IllegalStateException("이미 끝난 Donation: " + id + " " + this.status);
		}
		this.status = status;
		this.completedAt = now;
	}

	public UUID getId() {
		return id;
	}

	/** 결제 서비스에 보내는 주문 번호 */
	public String getOrderId() {
		return id.toString();
	}

	public User getDonor() {
		return donor;
	}

	public User getRecipient() {
		return recipient;
	}

	public UUID getBroadcastId() {
		return broadcastId;
	}

	public String getBroadcastTitle() {
		return broadcastTitle;
	}

	public int getAmount() {
		return amount;
	}

	public String getMessage() {
		return message;
	}

	public DonationStatus getStatus() {
		return status;
	}

	String getPaymentKey() {
		return paymentKey;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
