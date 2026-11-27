package com.webbroadcast.broadcast;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, UUID> {

	@EntityGraph(attributePaths = "owner")
	List<Broadcast> findByStatusAndHiddenAtIsNullOrderByStartedAtDesc(BroadcastStatus status);

	@EntityGraph(attributePaths = "owner")
	List<Broadcast> findTop20ByStatusAndHiddenAtIsNullOrderByEndedAtDesc(BroadcastStatus status);

	/** 운영자 화면용. 정렬은 pageable로 준다 */
	@EntityGraph(attributePaths = "owner")
	Page<Broadcast> findByHiddenAtIsNotNull(Pageable pageable);

	Optional<Broadcast> findFirstByOwnerIdAndStatusIn(UUID ownerId, Collection<BroadcastStatus> statuses);

	List<Broadcast> findByStatusIn(Collection<BroadcastStatus> statuses);
}
