package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.AuthRateLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthRateLimitRepository extends JpaRepository<AuthRateLimit, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthRateLimit> findByScopeAndKeyHash(String scope, String keyHash);

    @Modifying
    @Query("DELETE FROM AuthRateLimit r WHERE r.scope = :scope AND r.keyHash = :keyHash")
    void deleteByScopeAndKeyHash(@Param("scope") String scope, @Param("keyHash") String keyHash);
}
