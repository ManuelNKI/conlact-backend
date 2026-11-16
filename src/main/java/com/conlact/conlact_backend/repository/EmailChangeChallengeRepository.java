package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.EmailChangeChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailChangeChallengeRepository extends JpaRepository<EmailChangeChallenge, UUID> {

    Optional<EmailChangeChallenge> findByRequestId(String requestId);

    @Modifying
    @Query("DELETE FROM EmailChangeChallenge c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
