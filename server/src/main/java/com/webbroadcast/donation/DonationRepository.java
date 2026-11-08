package com.webbroadcast.donation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DonationRepository extends JpaRepository<Donation, UUID> {

	/** 한 Broadcast의 완료된 Donation, 최근 완료된 순 */
	@EntityGraph(attributePaths = "donor")
	@Query("""
			select d from Donation d
			where d.broadcastId = :broadcastId and d.status = com.webbroadcast.donation.DonationStatus.DONE
			order by d.completedAt desc, d.id desc""")
	List<Donation> findLatestDoneOnBroadcast(UUID broadcastId, Limit limit);

	@EntityGraph(attributePaths = "recipient")
	List<Donation> findByDonorIdOrderByCreatedAtDesc(UUID donorId, Limit limit);

	@EntityGraph(attributePaths = "donor")
	List<Donation> findByRecipientIdAndStatusOrderByCompletedAtDesc(UUID recipientId, DonationStatus status,
			Limit limit);

	interface Total {
		long getCount();

		long getAmount();
	}

	@Query("""
			select count(d) as count, coalesce(sum(d.amount), 0) as amount
			from Donation d
			where d.recipient.id = :recipientId and d.status = com.webbroadcast.donation.DonationStatus.DONE""")
	Total totalReceived(UUID recipientId);

	List<Donation> findByStatusAndCreatedAtBefore(DonationStatus status, Instant createdAt);
}
