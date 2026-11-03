package com.webbroadcast.broadcast;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, UUID> {

	List<Broadcast> findByStatusOrderByStartedAtDesc(BroadcastStatus status);

	List<Broadcast> findTop20ByStatusOrderByEndedAtDesc(BroadcastStatus status);

	List<Broadcast> findByStatusIn(Collection<BroadcastStatus> statuses);
}
