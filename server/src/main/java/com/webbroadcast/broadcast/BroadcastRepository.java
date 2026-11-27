package com.webbroadcast.broadcast;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

	/** 탈퇴하는 User의 Broadcast 모두. Comment는 DB에서 함께 지워진다 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Broadcast b where b.owner.id = :ownerId")
	int deleteByOwnerId(UUID ownerId);
}
