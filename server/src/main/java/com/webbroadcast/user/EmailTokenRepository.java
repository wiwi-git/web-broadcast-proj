package com.webbroadcast.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

	@EntityGraph(attributePaths = "user")
	Optional<EmailToken> findByTokenHash(String tokenHash);

	Optional<EmailToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, EmailTokenPurpose purpose);

	/** 새 링크를 보내면 같은 용도의 이전 링크는 쓸 수 없게 한다 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update EmailToken t set t.usedAt = :now
			where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null""")
	int retireAll(UUID userId, EmailTokenPurpose purpose, Instant now);
}
