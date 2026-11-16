package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.PasswordResetChallenge;
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
public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallenge> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallenge> findByResetToken(String resetToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallenge> findFirstByEmailAndIsConsumedFalseOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("DELETE FROM PasswordResetChallenge c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PasswordResetChallenge c WHERE c.email = :email")
    void deleteByEmail(@Param("email") String email);
}
