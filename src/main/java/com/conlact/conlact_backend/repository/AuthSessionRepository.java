package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
