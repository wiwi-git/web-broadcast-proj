package com.webbroadcast.broadcast;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, UUID> {

	@EntityGraph(attributePaths = "owner")
	List<Broadcast> findByStatusAndHiddenAtIsNullOrderByStartedAtDesc(BroadcastStatus status);

	@EntityGraph(attributePaths = "owner")
	List<Broadcast> findTop20ByStatusAndHiddenAtIsNullOrderByEndedAtDesc(BroadcastStatus status);

	Optional<Broadcast> findFirstByOwnerIdAndStatusIn(UUID ownerId, Collection<BroadcastStatus> statuses);

	List<Broadcast> findByStatusIn(Collection<BroadcastStatus> statuses);
}
