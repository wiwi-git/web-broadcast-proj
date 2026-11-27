package com.webbroadcast.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	/**
	 * 운영자 회원 목록. query는 이메일이나 닉네임의 일부(대소문자 무시), null이면 모두.
	 * suspended가 null이면 상태와 상관없이, true면 정지된 회원만, false면 정지되지 않은 회원만.
	 */
	@Query("""
			select u from User u
			where (:query is null
			       or lower(u.email) like concat('%', lower(cast(:query as string)), '%')
			       or lower(u.nickname) like concat('%', lower(cast(:query as string)), '%'))
			  and (:suspended is null
			       or (:suspended = true and u.suspendedAt is not null)
			       or (:suspended = false and u.suspendedAt is null))""")
	Page<User> search(@Param("query") String query, @Param("suspended") Boolean suspended, Pageable pageable);
}
